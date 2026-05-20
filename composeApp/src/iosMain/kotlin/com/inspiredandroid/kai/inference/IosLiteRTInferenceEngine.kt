@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.inspiredandroid.kai.inference

import com.inspiredandroid.kai.httpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

class IosLiteRTInferenceEngine : LocalInferenceEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var downloadJob: Job? = null
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

    private fun requireBridge(): LiteRTSwiftBridge = LiteRTBridgeRegistry.bridge
        ?: throw IllegalStateException("LiteRTSwiftBridge not installed. iosApp must call KaiLiteRTBridgeInstaller.install().")

    override suspend fun initialize(model: DownloadedModel, contextTokens: Int) {
        idleReleaseJob?.cancel()
        if (currentModelId == model.id && currentContextTokens == contextTokens && _engineState.value == EngineState.READY) return

        val bridge = requireBridge()
        _engineState.value = EngineState.INITIALIZING
        try {
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
        } catch (e: Throwable) {
            _engineState.value = EngineState.ERROR
            throw e
        }
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
        return MODEL_CATALOG.mapNotNull { catalogModel ->
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
    }

    override fun getAvailableModels(): List<LocalModel> = MODEL_CATALOG

    override fun getFreeSpaceBytes(): Long = getAvailableDiskSpaceBytes(getModelStorageDirectory())

    override fun startDownload(model: LocalModel) {
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

                val (bytesWritten, expectedBytes) = downloadToFile(
                    url = model.downloadUrl,
                    tempPath = tempPath,
                    fallbackSize = model.sizeBytes,
                    onProgress = { percent -> _downloadProgress.value = percent / 100f },
                )

                if (bytesWritten < expectedBytes * 0.95) {
                    NSFileManager.defaultManager.removeItemAtPath(tempPath, null)
                    _downloadError.value = DownloadError.DOWNLOAD_INCOMPLETE
                    return@launch
                }

                val fileManager = NSFileManager.defaultManager
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

    override suspend fun deleteModel(modelId: String) {
        withContext(Dispatchers.Default) {
            idleReleaseJob?.cancelAndJoin()
            idleReleaseJob = null
            if (currentModelId == modelId) {
                release()
            }
            val modelDir = "${getModelStorageDirectory()}/$modelId"
            NSFileManager.defaultManager.removeItemAtPath(modelDir, null)
        }
    }

    companion object {
        private const val IDLE_RELEASE_MS = 5L * 60 * 1000
        private const val INFERENCE_TIMEOUT_MS = 120_000L
        private const val DOWNLOAD_SPACE_BUFFER_BYTES = 500L * 1024 * 1024
        private const val GPU_DRAIN_DELAY_MS = 750L
    }
}

private suspend fun downloadToFile(
    url: String,
    tempPath: String,
    fallbackSize: Long,
    onProgress: (Int) -> Unit,
): Pair<Long, Long> {
    val fileManager = NSFileManager.defaultManager
    if (fileManager.fileExistsAtPath(tempPath)) {
        fileManager.removeItemAtPath(tempPath, null)
    }
    val fp = fopen(tempPath, "wb") ?: throw IllegalStateException("Cannot open $tempPath for writing")

    val client = httpClient()
    var totalBytes = 0L
    var expectedBytes = fallbackSize
    try {
        client.prepareGet(url).execute { response ->
            if (!response.status.isSuccess()) {
                throw IllegalStateException("HTTP ${response.status.value}")
            }
            expectedBytes = response.contentLength()?.takeIf { it > 0 } ?: fallbackSize
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(65536)
            var lastPercent = -1
            while (!channel.isClosedForRead) {
                val n = channel.readAvailable(buffer, 0, buffer.size)
                if (n <= 0) break
                buffer.usePinned { pinned ->
                    fwrite(pinned.addressOf(0), 1.convert(), n.convert(), fp)
                }
                totalBytes += n
                val percent = (totalBytes * 100 / expectedBytes).toInt().coerceIn(1, 100)
                if (percent != lastPercent) {
                    lastPercent = percent
                    onProgress(percent)
                }
            }
        }
    } finally {
        fclose(fp)
        client.close()
    }
    return totalBytes to expectedBytes
}
