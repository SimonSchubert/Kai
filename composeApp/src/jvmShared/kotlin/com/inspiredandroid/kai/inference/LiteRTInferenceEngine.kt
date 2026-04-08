package com.inspiredandroid.kai.inference

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private val MODEL_CATALOG = listOf(
    LocalModel(
        id = "gemma-4-e2b-it",
        displayName = "Gemma 4 E2B IT",
        fileName = "gemma-4-E2B-it.litertlm",
        sizeBytes = 2_580_000_000L,
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
    ),
    LocalModel(
        id = "gemma-4-e4b-it",
        displayName = "Gemma 4 E4B IT",
        fileName = "gemma-4-E4B-it.litertlm",
        sizeBytes = 3_650_000_000L,
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
    ),
)

class LiteRTInferenceEngine : LocalInferenceEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private var idleReleaseJob: Job? = null

    private var engine: Engine? = null
    private var conversation: com.google.ai.edge.litertlm.Conversation? = null
    private var currentModelId: String? = null

    private val _engineState = MutableStateFlow(EngineState.UNINITIALIZED)
    override val engineState: StateFlow<EngineState> = _engineState

    private val _downloadingModelId = MutableStateFlow<String?>(null)
    override val downloadingModelId: StateFlow<String?> = _downloadingModelId

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    override val downloadProgress: StateFlow<Float?> = _downloadProgress

    private val _downloadError = MutableStateFlow<DownloadError?>(null)
    override val downloadError: StateFlow<DownloadError?> = _downloadError

    override suspend fun initialize(model: DownloadedModel) {
        withContext(Dispatchers.IO) {
            idleReleaseJob?.cancel()
            if (currentModelId == model.id && _engineState.value == EngineState.READY) return@withContext
            _engineState.value = EngineState.INITIALIZING
            try {
                val modelFile = File(model.filePath)
                if (!modelFile.exists() || modelFile.length() < 1_000_000) {
                    throw IllegalStateException("Model file missing or too small: ${model.filePath}")
                }

                val availMem = getAvailableMemoryBytes()
                if (availMem < MIN_MEMORY_HEADROOM_BYTES) {
                    throw InsufficientMemoryException()
                }

                release()

                fun initWithBackend(backend: Backend): Engine {
                    val config = EngineConfig(
                        modelPath = model.filePath,
                        backend = backend,
                        cacheDir = getModelCacheDirectory(),
                    )
                    val e = Engine(config)
                    e.initialize()
                    return e
                }

                val newEngine = try {
                    initWithBackend(Backend.GPU())
                } catch (e: Exception) {
                    // GPU not available, fall back to CPU
                    initWithBackend(Backend.CPU())
                }

                engine = newEngine
                conversation = newEngine.createConversation()
                currentModelId = model.id
                _engineState.value = EngineState.READY
            } catch (e: Exception) {
                _engineState.value = EngineState.ERROR
                throw e
            }
        }
    }

    override suspend fun release() {
        withContext(Dispatchers.IO) {
            conversation?.close()
            conversation = null
            engine?.close()
            engine = null
            currentModelId = null
            _engineState.value = EngineState.UNINITIALIZED
        }
    }

    override suspend fun chat(
        messages: List<InferenceMessage>,
        systemPrompt: String?,
    ): String = withContext(Dispatchers.IO) {
        idleReleaseJob?.cancel()
        try {
            val currentEngine = engine ?: throw IllegalStateException("Engine not initialized")

            val lastUserIndex = messages.indexOfLast { it.role == "user" }
            if (lastUserIndex < 0) throw IllegalStateException("No user message found")

            val initialMessages = messages.subList(0, lastUserIndex).map { msg ->
                when (msg.role) {
                    "user" -> Message.user(msg.content)
                    else -> Message.model(msg.content)
                }
            }

            val config = ConversationConfig(
                systemInstruction = systemPrompt?.let { Contents.of(it) },
                initialMessages = initialMessages,
                samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.8),
            )
            conversation?.close()
            val conv = currentEngine.createConversation(config)
            conversation = conv

            val lastMessage = messages[lastUserIndex].content
            val response = try {
                withTimeout(INFERENCE_TIMEOUT_MS) {
                    conv.sendMessage(lastMessage)
                }
            } catch (e: TimeoutCancellationException) {
                throw InferenceTimeoutException()
            }
            response.toString()
        } finally {
            scheduleIdleRelease()
        }
    }

    private fun scheduleIdleRelease() {
        idleReleaseJob?.cancel()
        idleReleaseJob = scope.launch {
            delay(IDLE_RELEASE_MS)
            release()
        }
    }

    companion object {
        private const val IDLE_RELEASE_MS = 5L * 60 * 1000 // 5 minutes
        private const val INFERENCE_TIMEOUT_MS = 120_000L // 2 minutes
        private const val MIN_MEMORY_HEADROOM_BYTES = 512L * 1024 * 1024 // 512 MB
        private const val DOWNLOAD_SPACE_BUFFER_BYTES = 500L * 1024 * 1024 // 500 MB
    }

    override fun getDownloadedModels(): List<DownloadedModel> {
        val modelsDir = File(getModelStorageDirectory())
        if (!modelsDir.exists()) return emptyList()
        return MODEL_CATALOG.mapNotNull { catalogModel ->
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
    }

    override fun getAvailableModels(): List<LocalModel> = MODEL_CATALOG

    override fun getFreeSpaceBytes(): Long = getAvailableDiskSpaceBytes(getModelStorageDirectory())

    override fun startDownload(model: LocalModel) {
        cancelDownload()
        startDownloadNotificationService()
        downloadJob = scope.launch {
            _downloadingModelId.value = model.id
            _downloadProgress.value = 0f
            _downloadError.value = null
            val modelsDir = getModelStorageDirectory()
            val modelDir = File(modelsDir, model.id)
            modelDir.mkdirs()
            val targetFile = File(modelDir, model.fileName)
            val tempFile = File(modelDir, "${model.fileName}.tmp")
            var lastNotifiedPercent = -1

            try {
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

                val contentLength = connection.contentLengthLong.takeIf { it > 0 } ?: model.sizeBytes
                val buffer = ByteArray(65536)
                var totalBytesRead = 0L

                connection.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        while (true) {
                            ensureActive()
                            val bytesRead = input.read(buffer)
                            if (bytesRead <= 0) break
                            output.write(buffer, 0, bytesRead)
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

                val downloadedSize = tempFile.length()
                if (downloadedSize < contentLength * 0.95) {
                    tempFile.delete()
                    throw IOException("Download incomplete: got $downloadedSize bytes, expected ~$contentLength")
                }

                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
            } catch (e: Exception) {
                if (tempFile.exists()) tempFile.delete()
                if (e is CancellationException) throw e
                _downloadError.value = DownloadError.NETWORK_ERROR
            } finally {
                _downloadingModelId.value = null
                _downloadProgress.value = null
                stopDownloadNotificationService()
            }
        }
    }

    override fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    override suspend fun deleteModel(modelId: String) {
        withContext(Dispatchers.IO) {
            if (currentModelId == modelId) {
                release()
            }
            val modelDir = File(getModelStorageDirectory(), modelId)
            modelDir.deleteRecursively()
        }
    }
}
