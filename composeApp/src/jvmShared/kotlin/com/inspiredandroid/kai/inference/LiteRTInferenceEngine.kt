package com.inspiredandroid.kai.inference

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.tool
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.size
import io.github.vinceglb.filekit.source
import io.github.vinceglb.filekit.withScopedAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
import kotlinx.io.asSink
import kotlinx.io.buffered
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.milliseconds

class LiteRTInferenceEngine : LocalInferenceEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private var importJob: Job? = null
    private var idleReleaseJob: Job? = null

    private var engine: Engine? = null
    private var conversation: com.google.ai.edge.litertlm.Conversation? = null
    override var currentModelId: String? = null
        private set
    private var currentContextTokens: Int = 0

    private val _engineState = MutableStateFlow(EngineState.UNINITIALIZED)
    override val engineState: StateFlow<EngineState> = _engineState

    private val _downloadingModelId = MutableStateFlow<String?>(null)
    override val downloadingModelId: StateFlow<String?> = _downloadingModelId

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    override val downloadProgress: StateFlow<Float?> = _downloadProgress

    private val _downloadError = MutableStateFlow<DownloadError?>(null)
    override val downloadError: StateFlow<DownloadError?> = _downloadError

    private val _importingFileName = MutableStateFlow<String?>(null)
    override val importingFileName: StateFlow<String?> = _importingFileName

    private val _importProgress = MutableStateFlow<Float?>(null)
    override val importProgress: StateFlow<Float?> = _importProgress

    private val _importError = MutableStateFlow<ModelImportError?>(null)
    override val importError: StateFlow<ModelImportError?> = _importError

    // Serializes initialization. The native load is not interruptible, so a cancelled
    // init keeps running on its IO thread; without the lock, a follow-up ask would see
    // state != READY and start a second concurrent load of a multi-GB model.
    private val initMutex = Mutex()

    override suspend fun initialize(model: DownloadedModel, contextTokens: Int) {
        initMutex.withLock { initializeLocked(model, contextTokens) }
    }

    private suspend fun initializeLocked(model: DownloadedModel, contextTokens: Int) {
        withContext(Dispatchers.IO) {
            idleReleaseJob?.cancel()
            if (currentModelId == model.id && currentContextTokens == contextTokens && _engineState.value == EngineState.READY) return@withContext
            _engineState.value = EngineState.INITIALIZING
            try {
                val modelFile = File(model.filePath)
                if (!modelFile.exists() || modelFile.length() < MIN_MODEL_FILE_BYTES) {
                    throw IllegalStateException("Model file missing or too small: ${model.filePath}")
                }

                verifyModelIntegrity(model.id, modelFile)

                // Release any currently-loaded engine before measuring available memory,
                // otherwise its GPU/CPU working set counts against the headroom check and
                // switching between models spuriously fails (e.g. Qwen -> Gemma 4).
                val hadExistingEngine = engine != null
                release()
                _engineState.value = EngineState.INITIALIZING

                if (hadExistingEngine) {
                    // engine.close() returns before the OpenCL driver actually reclaims the
                    // previous model's GPU buffers, so loading a second model on top would
                    // briefly hold both resident and trip Android's LMK. Give the driver a
                    // beat to drain before allocating ~GB of new GPU buffers.
                    System.gc()
                    delay(GPU_DRAIN_DELAY_MS.milliseconds)
                }

                val availMem = getAvailableMemoryBytes()
                if (availMem < MIN_MEMORY_HEADROOM_BYTES) {
                    throw InsufficientMemoryException()
                }

                fun initWithBackend(backend: Backend, maxTokens: Int?): Engine {
                    val config = EngineConfig(
                        modelPath = model.filePath,
                        backend = backend,
                        cacheDir = getModelCacheDirectory(),
                        maxNumTokens = maxTokens,
                    )
                    val e = Engine(config)
                    e.initialize()
                    return e
                }

                val requestedTokens = if (contextTokens > 0) contextTokens else null
                println("LiteRT: initializing model=${model.id} maxNumTokens=$requestedTokens")

                val newEngine = try {
                    try {
                        initWithBackend(Backend.GPU(), requestedTokens)
                    } catch (e: Exception) {
                        initWithBackend(Backend.CPU(), requestedTokens)
                    }
                } catch (e: Exception) {
                    // Context size not supported — retry with model default
                    println("LiteRT: init failed with maxNumTokens=$requestedTokens, falling back to default: ${e.message}")
                    if (requestedTokens != null) {
                        try {
                            initWithBackend(Backend.GPU(), null)
                        } catch (e2: Exception) {
                            initWithBackend(Backend.CPU(), null)
                        }
                    } else {
                        throw e
                    }
                }

                engine = newEngine
                conversation = newEngine.createConversation()
                currentModelId = model.id
                currentContextTokens = contextTokens
                _engineState.value = EngineState.READY
            } catch (e: CancellationException) {
                // User stop, not an engine failure — reflect the actual state instead of
                // ERROR. Cancellation lands at a suspension point (release/delay): READY
                // if an engine is still loaded, UNINITIALIZED after it was released. If
                // the native load itself finished, the success path above already ran.
                _engineState.value = if (engine != null) EngineState.READY else EngineState.UNINITIALIZED
                throw e
            } catch (e: Exception) {
                _engineState.value = EngineState.ERROR
                throw e
            }
        }
    }

    /**
     * Refuses to load a catalog model whose bytes do not match the digest pinned in
     * [MODEL_CATALOG]. Files downloaded by earlier app versions predate download-time
     * verification, so their provenance is unknown; they are hashed once here and the
     * result recorded in a marker file, making every later load a string comparison.
     *
     * Files the user supplied are exempt. An import whose name matches a catalog model
     * takes over that catalog slot, so it would otherwise be measured against a digest it
     * was never meant to match. Nothing here deletes a model — a rejected file is left on
     * disk for the user to keep, replace, or remove from Settings.
     */
    private suspend fun verifyModelIntegrity(modelId: String, modelFile: File) {
        val catalogModel = findCatalogModelById(modelId) ?: return
        if (catalogModel.sha256.isBlank()) return

        val markerFile = File(modelFile.parentFile, digestMarkerFileName(modelFile.name))
        val marker = runCatching { markerFile.readText().trim() }.getOrNull()
        if (marker == USER_SUPPLIED_MARKER) return
        if (digestMatches(catalogModel.sha256, marker)) return
        // A marker that records some other digest means this file has already been hashed
        // and is known not to be the pinned build; re-reading gigabytes to learn that again
        // on every attempt helps nobody.
        if (!marker.isNullOrBlank()) throw ModelIntegrityException()

        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(COPY_BUFFER_SIZE_BYTES.toInt())
        modelFile.inputStream().use { input ->
            while (true) {
                coroutineContext.ensureActive()
                val bytesRead = input.read(buffer)
                if (bytesRead <= 0) break
                digest.update(buffer, 0, bytesRead)
            }
        }

        val actual = digest.digest().toDigestHex()
        markerFile.writeText(actual)
        if (!digestMatches(catalogModel.sha256, actual)) throw ModelIntegrityException()
    }

    override suspend fun release() {
        withContext(Dispatchers.IO) {
            // Null before close so a concurrent release() sees null and skips —
            // Conversation.close() / Engine.close() throw IllegalStateException on double-close.
            val convToClose = conversation
            val engineToClose = engine
            conversation = null
            engine = null
            currentModelId = null
            _engineState.value = EngineState.UNINITIALIZED
            runCatching { convToClose?.close() }
            runCatching { engineToClose?.close() }
        }
    }

    override fun releaseInBackground() {
        idleReleaseJob?.cancel()
        idleReleaseJob = scope.launch { release() }
    }

    override suspend fun chat(
        messages: List<InferenceMessage>,
        systemPrompt: String?,
        tools: List<LocalTool>,
    ): String = withContext(Dispatchers.IO) {
        idleReleaseJob?.cancel()
        try {
            val currentEngine = engine ?: throw IllegalStateException("Engine not initialized")

            val lastUserIndex = messages.indexOfLast { it.role == "user" }
            if (lastUserIndex < 0) throw IllegalStateException("No user message found")

            val sanitizedSystemPrompt = sanitizeForLiteRt(systemPrompt)
            val initialMessages = messages.subList(0, lastUserIndex).map { msg ->
                val sanitized = sanitizeForLiteRt(msg.content) ?: ""
                when (msg.role) {
                    "user" -> Message.user(sanitized)
                    else -> Message.model(sanitized)
                }
            }

            val toolProviders = tools.map { tool(LocalToolOpenApiAdapter(it)) }
            val config = ConversationConfig(
                systemInstruction = sanitizedSystemPrompt?.let { Contents.of(it) },
                initialMessages = initialMessages,
                tools = toolProviders,
                samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.8),
                // automaticToolCalling = true drives the parser; only enable when we
                // actually have tools, otherwise plain-text responses get parsed as FCs.
                automaticToolCalling = toolProviders.isNotEmpty(),
            )
            val prev = conversation
            conversation = null
            runCatching { prev?.close() }
            val conv = currentEngine.createConversation(config)
            conversation = conv

            val lastMessage = sanitizeForLiteRt(messages[lastUserIndex].content) ?: ""
            val response = try {
                withTimeout(INFERENCE_TIMEOUT_MS.milliseconds) {
                    conv.sendMessage(lastMessage)
                }
            } catch (e: TimeoutCancellationException) {
                throw InferenceTimeoutException()
            }
            stripThinkBlocks(response.toString())
        } finally {
            scheduleIdleRelease()
        }
    }

    /**
     * Adapter that exposes a Kai [LocalTool] (suspend execute) to litert-lm's [OpenApiTool]
     * (synchronous execute). The bridge uses [runBlocking] because the engine calls
     * [execute] on its own worker thread (we're already inside `Dispatchers.IO` from
     * [chat]) and waits for the result before continuing the tool loop.
     */
    private class LocalToolOpenApiAdapter(private val localTool: LocalTool) : OpenApiTool {
        override fun getToolDescriptionJsonString(): String = localTool.descriptionJsonString
        override fun execute(paramsJsonString: String): String = runBlocking { localTool.execute(paramsJsonString) }
    }

    private fun scheduleIdleRelease() {
        idleReleaseJob?.cancel()
        idleReleaseJob = scope.launch {
            delay(IDLE_RELEASE_MS.milliseconds)
            release()
        }
    }

    override fun getDownloadedModels(): List<DownloadedModel> {
        val modelsDir = File(getModelStorageDirectory())
        if (!modelsDir.exists()) return emptyList()
        val catalog = MODEL_CATALOG.mapNotNull { catalogModel ->
            val modelDir = File(modelsDir, catalogModel.id)
            val modelFile = File(modelDir, catalogModel.fileName)
            if (modelFile.exists()) {
                DownloadedModel(
                    id = catalogModel.id,
                    displayName = catalogModel.displayName,
                    filePath = modelFile.absolutePath,
                    sizeBytes = modelFile.length(),
                )
            } else {
                null
            }
        }
        val imported = scanImportedModels(modelsDir).map { (local, file) ->
            DownloadedModel(
                id = local.id,
                displayName = local.displayName,
                filePath = file.absolutePath,
                sizeBytes = file.length(),
            )
        }
        return catalog + imported
    }

    override fun getAvailableModels(): List<LocalModel> = MODEL_CATALOG

    override fun getImportedLocalModels(): List<LocalModel> {
        val modelsDir = File(getModelStorageDirectory())
        if (!modelsDir.exists()) return emptyList()
        return scanImportedModels(modelsDir).map { it.first }
    }

    private fun scanImportedModels(modelsDir: File): List<Pair<LocalModel, File>> {
        val importsDir = File(modelsDir, IMPORTS_DIR)
        if (!importsDir.isDirectory) return emptyList()
        return importsDir.listFiles()
            ?.filter { it.isFile && isLitertlmExtension(it.name) && !it.name.endsWith(".tmp") && !it.name.endsWith(".importing") }
            ?.sortedBy { it.name.lowercase() }
            ?.map { file ->
                val modelId = CUSTOM_MODEL_ID_PREFIX + file.nameWithoutExtension
                customLocalModel(file.name, file.length(), modelId).copy(isImported = true) to file
            }
            .orEmpty()
    }

    override fun getFreeSpaceBytes(): Long = getAvailableDiskSpaceBytes(getModelStorageDirectory())

    override fun startDownload(model: LocalModel) {
        if (_importingFileName.value != null) return
        cancelDownload()
        downloadJob = scope.launch {
            _downloadingModelId.value = model.id
            _downloadProgress.value = 0f
            _downloadError.value = null
            var tempFile: File? = null
            var notificationStarted = false

            try {
                val modelsDir = getModelStorageDirectory()
                val modelDir = File(modelsDir, model.id)
                modelDir.mkdirs()
                val targetFile = File(modelDir, model.fileName)
                tempFile = File(modelDir, "${model.fileName}.tmp")
                var lastNotifiedPercent = -1

                val freeSpace = getFreeSpaceBytes()
                if (freeSpace < model.sizeBytes + DOWNLOAD_SPACE_BUFFER_BYTES) {
                    _downloadError.value = DownloadError.NOT_ENOUGH_DISK_SPACE
                    return@launch
                }

                @Suppress("DEPRECATION")
                val connection = URL(model.downloadUrl).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 30_000
                connection.readTimeout = 60_000
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    connection.disconnect()
                    throw IOException("Download failed: HTTP $responseCode")
                }

                // Only start the foreground service once we have a live connection.
                // Starting it earlier risks ForegroundServiceDidNotStartInTimeException if
                // the connect() above fails fast (e.g. offline) before the service can run.
                startDownloadNotificationService()
                notificationStarted = true

                val contentLength = connection.contentLengthLong.takeIf { it > 0 } ?: model.sizeBytes
                val buffer = ByteArray(COPY_BUFFER_SIZE_BYTES.toInt())
                var totalBytesRead = 0L
                // Hash as the bytes stream past, so verification costs no extra read of a
                // file that can be several GB.
                val digest = model.sha256.takeIf { it.isNotBlank() }?.let { MessageDigest.getInstance("SHA-256") }

                connection.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        while (true) {
                            ensureActive()
                            val bytesRead = input.read(buffer)
                            if (bytesRead <= 0) break
                            output.write(buffer, 0, bytesRead)
                            digest?.update(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            val percent = (totalBytesRead * 100 / contentLength).toInt().coerceIn(1, 100)
                            if (percent != lastNotifiedPercent) {
                                lastNotifiedPercent = percent
                                _downloadProgress.value = percent / 100f
                                updateDownloadNotificationProgress(percent)
                            }
                        }
                    }
                }
                connection.disconnect()

                if (digest != null) {
                    // Catalog model with a pinned digest: the size is known exactly, so
                    // anything short is a truncated transfer rather than a swapped file.
                    if (totalBytesRead != model.sizeBytes) {
                        tempFile.delete()
                        _downloadError.value = DownloadError.DOWNLOAD_INCOMPLETE
                        return@launch
                    }
                    if (!digestMatches(model.sha256, digest.digest().toDigestHex())) {
                        tempFile.delete()
                        _downloadError.value = DownloadError.CHECKSUM_MISMATCH
                        return@launch
                    }
                    // Record the verified digest before the file becomes visible under its
                    // real name, so a model is never present without its marker.
                    File(modelDir, digestMarkerFileName(model.fileName)).writeText(model.sha256)
                } else {
                    val downloadedSize = tempFile.length()
                    if (downloadedSize < contentLength * 0.95) {
                        tempFile.delete()
                        _downloadError.value = DownloadError.DOWNLOAD_INCOMPLETE
                        return@launch
                    }
                }

                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
            } catch (e: Throwable) {
                if (tempFile?.exists() == true) tempFile.delete()
                if (e is CancellationException) throw e
                _downloadError.value = DownloadError.NETWORK_ERROR
            } finally {
                _downloadingModelId.value = null
                _downloadProgress.value = null
                if (notificationStarted) stopDownloadNotificationService()
            }
        }
    }

    override fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    override suspend fun importModel(source: PlatformFile): ModelImportResult = withContext(Dispatchers.IO) {
        if (_downloadingModelId.value != null) {
            return@withContext ModelImportResult.Failure(ModelImportError.COPY_FAILED, "Download in progress")
        }
        cancelImport()
        importJob = currentCoroutineContext().job
        val fileName = source.name
        _importingFileName.value = fileName
        _importProgress.value = 0f
        _importError.value = null

        var tempFile: File? = null
        try {
            if (!isLitertlmExtension(fileName)) {
                _importError.value = ModelImportError.INVALID_EXTENSION
                return@withContext ModelImportResult.Failure(ModelImportError.INVALID_EXTENSION)
            }

            val sourceSize = runCatching { source.size() }.getOrDefault(-1L)
            if (sourceSize in 0 until MIN_MODEL_FILE_BYTES) {
                _importError.value = ModelImportError.FILE_TOO_SMALL
                return@withContext ModelImportResult.Failure(ModelImportError.FILE_TOO_SMALL)
            }

            val modelsDir = File(getModelStorageDirectory())
            val importsDir = File(modelsDir, IMPORTS_DIR)
            val existingImports = importsDir.listFiles()
                ?.filter { it.isFile }
                ?.map { it.name }
                ?.toSet()
                .orEmpty()

            val target = resolveImportTarget(fileName, existingImports)
                ?: run {
                    _importError.value = ModelImportError.INVALID_EXTENSION
                    return@withContext ModelImportResult.Failure(ModelImportError.INVALID_EXTENSION)
                }

            val sizeForSpace = if (sourceSize > 0) sourceSize else 0L
            if (getFreeSpaceBytes() < sizeForSpace + DOWNLOAD_SPACE_BUFFER_BYTES) {
                _importError.value = ModelImportError.NOT_ENOUGH_DISK_SPACE
                return@withContext ModelImportResult.Failure(ModelImportError.NOT_ENOUGH_DISK_SPACE)
            }

            val destDir = File(modelsDir, target.relativeDir)
            destDir.mkdirs()
            val destFile = File(destDir, target.fileName)
            tempFile = File(destDir, "${target.fileName}.importing")
            if (tempFile.exists()) tempFile.delete()

            streamCopyWithProgress(source, tempFile, sourceSize) { progress ->
                _importProgress.value = progress
            }

            val copiedSize = tempFile.length()
            if (copiedSize < MIN_MODEL_FILE_BYTES) {
                tempFile.delete()
                _importError.value = ModelImportError.FILE_TOO_SMALL
                return@withContext ModelImportResult.Failure(ModelImportError.FILE_TOO_SMALL)
            }
            if (sourceSize > 0 && copiedSize < sourceSize * 0.95) {
                tempFile.delete()
                _importError.value = ModelImportError.COPY_FAILED
                return@withContext ModelImportResult.Failure(ModelImportError.COPY_FAILED, "Incomplete copy")
            }

            if (target.matchedCatalog) {
                // This import takes over a catalog slot. Record that the bytes are the
                // user's own so the load-time check does not hold them to that entry's
                // pinned digest.
                File(destDir, digestMarkerFileName(target.fileName)).writeText(USER_SUPPLIED_MARKER)
            }

            if (destFile.exists()) destFile.delete()
            if (!tempFile.renameTo(destFile)) {
                tempFile.copyTo(destFile, overwrite = true)
                tempFile.delete()
            }
            tempFile = null
            _importProgress.value = 1f
            ModelImportResult.Success(modelId = target.modelId, matchedCatalog = target.matchedCatalog)
        } catch (e: CancellationException) {
            tempFile?.delete()
            _importError.value = ModelImportError.CANCELLED
            throw e
        } catch (e: Exception) {
            tempFile?.delete()
            _importError.value = ModelImportError.COPY_FAILED
            ModelImportResult.Failure(ModelImportError.COPY_FAILED, e.message)
        } finally {
            if (importJob === currentCoroutineContext().job) {
                importJob = null
            }
            _importingFileName.value = null
            _importProgress.value = null
        }
    }

    override fun cancelImport() {
        importJob?.cancel()
        importJob = null
    }

    /**
     * Streams [source] into [dest] without loading the whole file into memory.
     * Reports progress when [totalBytes] is known (> 0).
     */
    private suspend fun streamCopyWithProgress(
        source: PlatformFile,
        dest: File,
        totalBytes: Long,
        onProgress: (Float) -> Unit,
    ) {
        source.withScopedAccess {
            dest.outputStream().use { fileOut ->
                val sink = fileOut.asSink().buffered()
                source.source().use { rawSource ->
                    val buffer = Buffer()
                    var copied = 0L
                    var lastProgressTs = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val bytesRead = rawSource.readAtMostTo(buffer, COPY_BUFFER_SIZE_BYTES)
                        if (bytesRead == -1L) break
                        sink.write(buffer, bytesRead)
                        copied += bytesRead
                        if (totalBytes > 0) {
                            val now = System.currentTimeMillis()
                            if (now - lastProgressTs > 200) {
                                lastProgressTs = now
                                onProgress((copied.toFloat() / totalBytes).coerceIn(0f, 1f))
                            }
                        }
                    }
                    sink.flush()
                }
            }
        }
        onProgress(1f)
    }

    override suspend fun deleteModel(modelId: String) {
        withContext(Dispatchers.IO) {
            // Wait for any in-flight idle release so its native teardown doesn't race with deleteRecursively().
            idleReleaseJob?.cancelAndJoin()
            idleReleaseJob = null
            if (currentModelId == modelId) {
                release()
            }
            val modelsDir = File(getModelStorageDirectory())
            if (isCustomModelId(modelId)) {
                val fileName = modelId.removePrefix(CUSTOM_MODEL_ID_PREFIX) + ".litertlm"
                File(modelsDir, "$IMPORTS_DIR/$fileName").delete()
                // Also remove any collision-suffixed match by id→filename from scan
                val importsDir = File(modelsDir, IMPORTS_DIR)
                importsDir.listFiles()
                    ?.filter { it.isFile && (CUSTOM_MODEL_ID_PREFIX + it.nameWithoutExtension) == modelId }
                    ?.forEach { it.delete() }
            } else {
                File(modelsDir, modelId).deleteRecursively()
            }
        }
    }

    companion object {
        private const val IDLE_RELEASE_MS = 5L * 60 * 1000 // 5 minutes
        private const val INFERENCE_TIMEOUT_MS = 120_000L // 2 minutes
        private const val MIN_MEMORY_HEADROOM_BYTES = 512L * 1024 * 1024 // 512 MB
        private const val DOWNLOAD_SPACE_BUFFER_BYTES = 500L * 1024 * 1024 // 500 MB
        private const val GPU_DRAIN_DELAY_MS = 750L
        private const val COPY_BUFFER_SIZE_BYTES = 64L * 1024
    }
}
