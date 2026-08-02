@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.inspiredandroid.kai.inference

import com.inspiredandroid.kai.httpClient
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.sink
import io.github.vinceglb.filekit.size
import io.github.vinceglb.filekit.source
import io.github.vinceglb.filekit.withScopedAccess
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.CoreCrypto.CC_SHA256_CTX
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA256_Final
import platform.CoreCrypto.CC_SHA256_Init
import platform.CoreCrypto.CC_SHA256_Update
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fwrite
import platform.posix.memcpy
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

class IosLiteRTInferenceEngine : LocalInferenceEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var downloadJob: Job? = null
    private var importJob: Job? = null
    private var idleReleaseJob: Job? = null

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

    private fun requireBridge(): LiteRTSwiftBridge = LiteRTBridgeRegistry.bridge
        ?: throw IllegalStateException("LiteRTSwiftBridge not installed. iosApp must call KaiLiteRTBridgeInstaller.install().")

    // Serializes initialization. The Swift-side load is not interruptible, so a cancelled
    // init keeps running; without the lock, a follow-up ask would see state != READY and
    // start a second concurrent load.
    private val initMutex = Mutex()

    override suspend fun initialize(model: DownloadedModel, contextTokens: Int) {
        initMutex.withLock { initializeLocked(model, contextTokens) }
    }

    private suspend fun initializeLocked(model: DownloadedModel, contextTokens: Int) {
        idleReleaseJob?.cancel()
        if (currentModelId == model.id && currentContextTokens == contextTokens && _engineState.value == EngineState.READY) return

        val bridge = requireBridge()
        _engineState.value = EngineState.INITIALIZING
        try {
            verifyModelIntegrity(model.id, model.filePath)

            // Release any existing engine before loading the next one. The Swift actor holds
            // the native handle until its retain count drops; give Metal a beat to reclaim.
            bridge.releaseEngine()
            delay(GPU_DRAIN_DELAY_MS.milliseconds)

            val errorMessage = suspendCancellableCoroutine<String?> { cont ->
                bridge.initializeEngine(
                    modelPath = model.filePath,
                    cacheDir = getModelCacheDirectory(),
                    maxNumTokens = contextTokens,
                    onComplete = { msg -> if (cont.isActive) cont.resume(msg) },
                )
            }
            if (errorMessage != null) throw IllegalStateException(errorMessage)

            currentModelId = model.id
            currentContextTokens = contextTokens
            _engineState.value = EngineState.READY
        } catch (e: CancellationException) {
            // User stop, not an engine failure — the old engine was already released
            // above, so UNINITIALIZED reflects reality and the next ask re-inits cleanly.
            _engineState.value = EngineState.UNINITIALIZED
            throw e
        } catch (e: Throwable) {
            _engineState.value = EngineState.ERROR
            throw e
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
    private suspend fun verifyModelIntegrity(modelId: String, filePath: String) {
        val catalogModel = findCatalogModelById(modelId) ?: return
        if (catalogModel.sha256.isBlank()) return

        val markerPath = "${filePath.substringBeforeLast('/')}/${digestMarkerFileName(filePath.substringAfterLast('/'))}"
        val marker = readDigestMarker(markerPath)?.trim()
        if (marker == USER_SUPPLIED_MARKER) return
        if (digestMatches(catalogModel.sha256, marker)) return
        // A marker that records some other digest means this file has already been hashed
        // and is known not to be the pinned build; re-reading gigabytes to learn that again
        // on every attempt helps nobody.
        if (!marker.isNullOrBlank()) throw ModelIntegrityException()

        val actual = sha256OfFile(filePath)
        if (actual != null) writeDigestMarker(markerPath, actual)
        if (!digestMatches(catalogModel.sha256, actual)) throw ModelIntegrityException()
    }

    override suspend fun release() {
        val bridge = LiteRTBridgeRegistry.bridge ?: return
        bridge.releaseEngine()
        currentModelId = null
        _engineState.value = EngineState.UNINITIALIZED
    }

    override fun releaseInBackground() {
        idleReleaseJob?.cancel()
        idleReleaseJob = scope.launch { release() }
    }

    override suspend fun chat(
        messages: List<InferenceMessage>,
        systemPrompt: String?,
        tools: List<LocalTool>,
    ): String {
        idleReleaseJob?.cancel()
        val bridge = requireBridge()
        if (!bridge.isEngineReady()) throw IllegalStateException("Engine not initialized")

        val sanitizedMessages = messages.map {
            mapOf("role" to it.role, "content" to (sanitizeForLiteRt(it.content) ?: ""))
        }
        val messagesJson = Json.encodeToString(sanitizedMessages)

        try {
            val (response, errorMessage) = withTimeout(INFERENCE_TIMEOUT_MS.milliseconds) {
                suspendCancellableCoroutine<Pair<String?, String?>> { cont ->
                    bridge.chat(
                        messagesJson = messagesJson,
                        systemPrompt = sanitizeForLiteRt(systemPrompt),
                        onResult = { resp, err -> if (cont.isActive) cont.resume(resp to err) },
                    )
                }
            }
            if (errorMessage != null) throw IllegalStateException(errorMessage)
            return stripThinkBlocks(response ?: "")
        } catch (e: TimeoutCancellationException) {
            throw InferenceTimeoutException()
        } finally {
            scheduleIdleRelease()
        }
    }

    private fun scheduleIdleRelease() {
        idleReleaseJob?.cancel()
        idleReleaseJob = scope.launch {
            delay(IDLE_RELEASE_MS.milliseconds)
            release()
        }
    }

    override fun getDownloadedModels(): List<DownloadedModel> {
        val modelsDir = getModelStorageDirectory()
        val fileManager = NSFileManager.defaultManager
        val catalog = MODEL_CATALOG.mapNotNull { catalogModel ->
            val modelPath = "$modelsDir/${catalogModel.id}/${catalogModel.fileName}"
            val attrs = fileManager.attributesOfItemAtPath(modelPath, null) ?: return@mapNotNull null
            val size = (attrs[NSFileSize] as? NSNumber)?.longLongValue ?: catalogModel.sizeBytes
            DownloadedModel(
                id = catalogModel.id,
                displayName = catalogModel.displayName,
                filePath = modelPath,
                sizeBytes = size,
            )
        }
        val imported = scanImportedModels().map { scanned ->
            DownloadedModel(
                id = scanned.model.id,
                displayName = scanned.model.displayName,
                filePath = scanned.path,
                sizeBytes = scanned.sizeBytes,
            )
        }
        return catalog + imported
    }

    override fun getAvailableModels(): List<LocalModel> = MODEL_CATALOG

    override fun getImportedLocalModels(): List<LocalModel> = scanImportedModels().map { it.model }

    private data class ImportedScan(val model: LocalModel, val path: String, val sizeBytes: Long)

    private fun scanImportedModels(): List<ImportedScan> {
        val importsDir = "${getModelStorageDirectory()}/$IMPORTS_DIR"
        val fileManager = NSFileManager.defaultManager
        val contents = fileManager.contentsOfDirectoryAtPath(importsDir, null) as? List<*> ?: return emptyList()
        return contents.mapNotNull { nameObj ->
            val name = nameObj as? String ?: return@mapNotNull null
            if (!isLitertlmExtension(name) || name.endsWith(".tmp") || name.endsWith(".importing")) {
                return@mapNotNull null
            }
            val path = "$importsDir/$name"
            if (!fileManager.fileExistsAtPath(path)) return@mapNotNull null
            val attrs = fileManager.attributesOfItemAtPath(path, null)
            val size = (attrs?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
            val modelId = CUSTOM_MODEL_ID_PREFIX + name.substringBeforeLast('.')
            ImportedScan(
                model = customLocalModel(name, size, modelId).copy(isImported = true),
                path = path,
                sizeBytes = size,
            )
        }.sortedBy { it.model.fileName.lowercase() }
    }

    override fun getFreeSpaceBytes(): Long = getAvailableDiskSpaceBytes(getModelStorageDirectory())

    override fun startDownload(model: LocalModel) {
        if (_importingFileName.value != null) return
        cancelDownload()
        downloadJob = scope.launch {
            _downloadingModelId.value = model.id
            _downloadProgress.value = 0f
            _downloadError.value = null

            val modelDir = "${getModelStorageDirectory()}/${model.id}"
            NSFileManager.defaultManager.createDirectoryAtPath(modelDir, true, null, null)
            val targetPath = "$modelDir/${model.fileName}"
            val tempPath = "$modelDir/${model.fileName}.tmp"

            try {
                if (getFreeSpaceBytes() < model.sizeBytes + DOWNLOAD_SPACE_BUFFER_BYTES) {
                    _downloadError.value = DownloadError.NOT_ENOUGH_DISK_SPACE
                    return@launch
                }

                val outcome = downloadToFile(
                    url = model.downloadUrl,
                    tempPath = tempPath,
                    fallbackSize = model.sizeBytes,
                    expectedSha256 = model.sha256,
                    onProgress = { percent -> _downloadProgress.value = percent / 100f },
                )

                if (outcome.sha256 != null) {
                    // Catalog model with a pinned digest: the size is known exactly, so
                    // anything short is a truncated transfer rather than a swapped file.
                    if (outcome.bytesWritten != model.sizeBytes) {
                        NSFileManager.defaultManager.removeItemAtPath(tempPath, null)
                        _downloadError.value = DownloadError.DOWNLOAD_INCOMPLETE
                        return@launch
                    }
                    if (!digestMatches(model.sha256, outcome.sha256)) {
                        NSFileManager.defaultManager.removeItemAtPath(tempPath, null)
                        _downloadError.value = DownloadError.CHECKSUM_MISMATCH
                        return@launch
                    }
                } else if (outcome.bytesWritten < outcome.expectedBytes * 0.95) {
                    NSFileManager.defaultManager.removeItemAtPath(tempPath, null)
                    _downloadError.value = DownloadError.DOWNLOAD_INCOMPLETE
                    return@launch
                }

                val fileManager = NSFileManager.defaultManager
                if (outcome.sha256 != null) {
                    // Record the verified digest before the file becomes visible under its
                    // real name, so a model is never present without its marker.
                    writeDigestMarker("$modelDir/${digestMarkerFileName(model.fileName)}", model.sha256)
                }
                if (fileManager.fileExistsAtPath(targetPath)) {
                    fileManager.removeItemAtPath(targetPath, null)
                }
                fileManager.moveItemAtPath(tempPath, targetPath, null)
            } catch (e: CancellationException) {
                NSFileManager.defaultManager.removeItemAtPath(tempPath, null)
                throw e
            } catch (e: Throwable) {
                NSFileManager.defaultManager.removeItemAtPath(tempPath, null)
                _downloadError.value = DownloadError.NETWORK_ERROR
            } finally {
                _downloadingModelId.value = null
                _downloadProgress.value = null
            }
        }
    }

    override fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    override suspend fun importModel(source: PlatformFile): ModelImportResult = withContext(Dispatchers.Default) {
        if (_downloadingModelId.value != null) {
            return@withContext ModelImportResult.Failure(ModelImportError.COPY_FAILED, "Download in progress")
        }
        cancelImport()
        importJob = currentCoroutineContext().job
        val fileName = source.name
        _importingFileName.value = fileName
        _importProgress.value = 0f
        _importError.value = null

        val fileManager = NSFileManager.defaultManager
        var tempPath: String? = null
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

            val modelsDir = getModelStorageDirectory()
            val importsDir = "$modelsDir/$IMPORTS_DIR"
            val existing = (fileManager.contentsOfDirectoryAtPath(importsDir, null) as? List<*>)
                ?.mapNotNull { it as? String }
                ?.toSet()
                .orEmpty()

            val target = resolveImportTarget(fileName, existing)
                ?: run {
                    _importError.value = ModelImportError.INVALID_EXTENSION
                    return@withContext ModelImportResult.Failure(ModelImportError.INVALID_EXTENSION)
                }

            val sizeForSpace = if (sourceSize > 0) sourceSize else 0L
            if (getFreeSpaceBytes() < sizeForSpace + DOWNLOAD_SPACE_BUFFER_BYTES) {
                _importError.value = ModelImportError.NOT_ENOUGH_DISK_SPACE
                return@withContext ModelImportResult.Failure(ModelImportError.NOT_ENOUGH_DISK_SPACE)
            }

            val destDir = "$modelsDir/${target.relativeDir}"
            fileManager.createDirectoryAtPath(destDir, true, null, null)
            val destPath = "$destDir/${target.fileName}"
            val tmp = "$destDir/${target.fileName}.importing"
            tempPath = tmp
            if (fileManager.fileExistsAtPath(tmp)) {
                fileManager.removeItemAtPath(tmp, null)
            }

            streamCopyWithProgress(source, tmp, sourceSize) { progress ->
                _importProgress.value = progress
            }

            val attrs = fileManager.attributesOfItemAtPath(tmp, null)
            val copiedSize = (attrs?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
            if (copiedSize < MIN_MODEL_FILE_BYTES) {
                fileManager.removeItemAtPath(tmp, null)
                _importError.value = ModelImportError.FILE_TOO_SMALL
                return@withContext ModelImportResult.Failure(ModelImportError.FILE_TOO_SMALL)
            }
            if (sourceSize > 0 && copiedSize < sourceSize * 0.95) {
                fileManager.removeItemAtPath(tmp, null)
                _importError.value = ModelImportError.COPY_FAILED
                return@withContext ModelImportResult.Failure(ModelImportError.COPY_FAILED, "Incomplete copy")
            }

            if (target.matchedCatalog) {
                // This import takes over a catalog slot. Record that the bytes are the
                // user's own so the load-time check does not hold them to that entry's
                // pinned digest.
                writeDigestMarker("$destDir/${digestMarkerFileName(target.fileName)}", USER_SUPPLIED_MARKER)
            }

            if (fileManager.fileExistsAtPath(destPath)) {
                fileManager.removeItemAtPath(destPath, null)
            }
            fileManager.moveItemAtPath(tmp, destPath, null)
            tempPath = null
            _importProgress.value = 1f
            ModelImportResult.Success(modelId = target.modelId, matchedCatalog = target.matchedCatalog)
        } catch (e: CancellationException) {
            tempPath?.let { fileManager.removeItemAtPath(it, null) }
            _importError.value = ModelImportError.CANCELLED
            throw e
        } catch (e: Exception) {
            tempPath?.let { fileManager.removeItemAtPath(it, null) }
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

    private suspend fun streamCopyWithProgress(
        source: PlatformFile,
        destPath: String,
        totalBytes: Long,
        onProgress: (Float) -> Unit,
    ) {
        val dest = PlatformFile(destPath)
        source.withScopedAccess {
            source.source().use { rawSource ->
                dest.sink().use { rawSink ->
                    val buffer = Buffer()
                    var copied = 0L
                    var lastProgressTs = 0.0
                    while (true) {
                        coroutineContext.ensureActive()
                        val bytesRead = rawSource.readAtMostTo(buffer, COPY_BUFFER_SIZE_BYTES)
                        if (bytesRead == -1L) break
                        rawSink.write(buffer, bytesRead)
                        copied += bytesRead
                        if (totalBytes > 0) {
                            val now = NSDate().timeIntervalSince1970
                            if (now - lastProgressTs > 0.2) {
                                lastProgressTs = now
                                onProgress((copied.toFloat() / totalBytes).coerceIn(0f, 1f))
                            }
                        }
                    }
                    rawSink.flush()
                }
            }
        }
        onProgress(1f)
    }

    override suspend fun deleteModel(modelId: String) {
        withContext(Dispatchers.Default) {
            idleReleaseJob?.cancelAndJoin()
            idleReleaseJob = null
            if (currentModelId == modelId) {
                release()
            }
            val modelsDir = getModelStorageDirectory()
            val fileManager = NSFileManager.defaultManager
            if (isCustomModelId(modelId)) {
                val importsDir = "$modelsDir/$IMPORTS_DIR"
                val contents = fileManager.contentsOfDirectoryAtPath(importsDir, null) as? List<*>
                contents?.mapNotNull { it as? String }?.forEach { name ->
                    if ((CUSTOM_MODEL_ID_PREFIX + name.substringBeforeLast('.')) == modelId) {
                        fileManager.removeItemAtPath("$importsDir/$name", null)
                    }
                }
            } else {
                fileManager.removeItemAtPath("$modelsDir/$modelId", null)
            }
        }
    }

    companion object {
        private const val IDLE_RELEASE_MS = 5L * 60 * 1000
        private const val INFERENCE_TIMEOUT_MS = 120_000L
        private const val DOWNLOAD_SPACE_BUFFER_BYTES = 500L * 1024 * 1024
        private const val GPU_DRAIN_DELAY_MS = 750L
        private const val COPY_BUFFER_SIZE_BYTES = 64L * 1024
    }
}

private const val HASH_BUFFER_SIZE_BYTES = 64 * 1024

private class DownloadOutcome(
    val bytesWritten: Long,
    val expectedBytes: Long,
    /** Lowercase hex SHA-256 of the written bytes, or null when no digest was requested. */
    val sha256: String?,
)

private suspend fun downloadToFile(
    url: String,
    tempPath: String,
    fallbackSize: Long,
    expectedSha256: String,
    onProgress: (Int) -> Unit,
): DownloadOutcome {
    val fileManager = NSFileManager.defaultManager
    if (fileManager.fileExistsAtPath(tempPath)) {
        fileManager.removeItemAtPath(tempPath, null)
    }
    val fp = fopen(tempPath, "wb") ?: throw IllegalStateException("Cannot open $tempPath for writing")

    val client = httpClient()
    var totalBytes = 0L
    var expectedBytes = fallbackSize
    var digestHex: String? = null
    try {
        memScoped {
            // Hash as the bytes stream past, so verification costs no extra read of a
            // file that can be several GB.
            val hashing = expectedSha256.isNotBlank()
            val context = alloc<CC_SHA256_CTX>()
            if (hashing) CC_SHA256_Init(context.ptr)

            client.prepareGet(url).execute { response ->
                if (!response.status.isSuccess()) {
                    throw IllegalStateException("HTTP ${response.status.value}")
                }
                expectedBytes = response.contentLength()?.takeIf { it > 0 } ?: fallbackSize
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(HASH_BUFFER_SIZE_BYTES)
                var lastPercent = -1
                while (!channel.isClosedForRead) {
                    val n = channel.readAvailable(buffer, 0, buffer.size)
                    if (n <= 0) break
                    buffer.usePinned { pinned ->
                        fwrite(pinned.addressOf(0), 1.convert(), n.convert(), fp)
                        if (hashing) CC_SHA256_Update(context.ptr, pinned.addressOf(0), n.convert())
                    }
                    totalBytes += n
                    val percent = (totalBytes * 100 / expectedBytes).toInt().coerceIn(1, 100)
                    if (percent != lastPercent) {
                        lastPercent = percent
                        onProgress(percent)
                    }
                }
            }

            if (hashing) digestHex = finalizeSha256(context)
        }
    } finally {
        fclose(fp)
        client.close()
    }
    return DownloadOutcome(totalBytes, expectedBytes, digestHex)
}

/** Streams an on-disk file through SHA-256. Null when the file cannot be opened. */
private suspend fun sha256OfFile(path: String): String? {
    val fp = fopen(path, "rb") ?: return null
    try {
        return memScoped {
            val context = alloc<CC_SHA256_CTX>()
            CC_SHA256_Init(context.ptr)
            val buffer = ByteArray(HASH_BUFFER_SIZE_BYTES)
            while (true) {
                coroutineContext.ensureActive()
                val read = buffer.usePinned { pinned ->
                    fread(pinned.addressOf(0), 1.convert(), buffer.size.convert(), fp).toLong()
                }
                if (read <= 0L) break
                buffer.usePinned { pinned ->
                    CC_SHA256_Update(context.ptr, pinned.addressOf(0), read.convert())
                }
            }
            finalizeSha256(context)
        }
    } finally {
        fclose(fp)
    }
}

private fun MemScope.finalizeSha256(context: CC_SHA256_CTX): String {
    val digest = allocArray<UByteVar>(CC_SHA256_DIGEST_LENGTH)
    CC_SHA256_Final(digest, context.ptr)
    return ByteArray(CC_SHA256_DIGEST_LENGTH) { digest[it].toByte() }.toDigestHex()
}

private fun readDigestMarker(path: String): String? {
    val data = NSData.dataWithContentsOfFile(path) ?: return null
    val size = data.length.toInt()
    if (size == 0) return null
    val bytes = ByteArray(size)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), data.bytes, data.length)
    }
    return bytes.decodeToString()
}

private fun writeDigestMarker(path: String, digest: String) {
    val fp = fopen(path, "wb") ?: return
    try {
        val bytes = digest.encodeToByteArray()
        bytes.usePinned { pinned ->
            fwrite(pinned.addressOf(0), 1.convert(), bytes.size.convert(), fp)
        }
    } finally {
        fclose(fp)
    }
}
