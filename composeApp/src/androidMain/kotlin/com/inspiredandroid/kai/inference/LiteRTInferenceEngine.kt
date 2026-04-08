package com.inspiredandroid.kai.inference

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

actual fun createLocalInferenceEngine(): LocalInferenceEngine? = LiteRTInferenceEngine()

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

    private val context: Context by inject(Context::class.java)
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private var downloadJob: kotlinx.coroutines.Job? = null
    private var idleReleaseJob: kotlinx.coroutines.Job? = null

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var currentModelId: String? = null

    private val _engineState = MutableStateFlow(EngineState.UNINITIALIZED)
    override val engineState: StateFlow<EngineState> = _engineState

    private val _downloadingModelId = MutableStateFlow<String?>(null)
    override val downloadingModelId: StateFlow<String?> = _downloadingModelId

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    override val downloadProgress: StateFlow<Float?> = _downloadProgress

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

                release()

                fun initWithBackend(backend: Backend): Engine {
                    val config = EngineConfig(
                        modelPath = model.filePath,
                        backend = backend,
                        cacheDir = context.cacheDir.absolutePath,
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
            initialMessages = initialMessages.ifEmpty { emptyList() },
            samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.8),
        )
        conversation?.close()
        val conv = currentEngine.createConversation(config)
        conversation = conv

        val lastMessage = messages[lastUserIndex].content
        val response = conv.sendMessage(lastMessage)
        scheduleIdleRelease()
        response.toString()
    }

    private fun scheduleIdleRelease() {
        idleReleaseJob?.cancel()
        idleReleaseJob = scope.launch {
            kotlinx.coroutines.delay(IDLE_RELEASE_MS)
            release()
        }
    }

    companion object {
        private const val IDLE_RELEASE_MS = 5L * 60 * 1000 // 5 minutes
    }

    override fun getDownloadedModels(): List<DownloadedModel> {
        val modelsDir = getModelsDir()
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

    override fun getFreeSpaceBytes(): Long = android.os.StatFs(context.filesDir.absolutePath).availableBytes

    override fun startDownload(model: LocalModel) {
        cancelDownload()
        try {
            val intent = Intent(context, ModelDownloadService::class.java)
            ContextCompat.startForegroundService(context, intent)
        } catch (_: Exception) {
            // Service start may fail if app is in restricted state
        }
        downloadJob = scope.launch {
            _downloadingModelId.value = model.id
            _downloadProgress.value = 0f
            val modelDir = File(getModelsDir(), model.id)
            modelDir.mkdirs()
            val targetFile = File(modelDir, model.fileName)
            val tempFile = File(modelDir, "${model.fileName}.tmp")
            var lastNotifiedPercent = -1

            try {
                val connection = URL(model.downloadUrl).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 30_000
                connection.readTimeout = 60_000
                connection.connect()

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
                            val progress = (totalBytesRead.toFloat() / contentLength).coerceIn(0f, 1f)
                            _downloadProgress.value = progress
                            val percent = (progress * 100).toInt()
                            if (percent != lastNotifiedPercent) {
                                lastNotifiedPercent = percent
                                updateNotificationProgress(percent)
                            }
                        }
                    }
                }
                connection.disconnect()
                tempFile.renameTo(targetFile)
            } catch (e: Exception) {
                if (tempFile.exists()) tempFile.delete()
                if (e is kotlinx.coroutines.CancellationException) throw e
            } finally {
                _downloadingModelId.value = null
                _downloadProgress.value = null
                stopDownloadService()
            }
        }
    }

    override fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    private fun updateNotificationProgress(percent: Int) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val builder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                android.app.Notification.Builder(context, "kai_model_download_channel")
            } else {
                @Suppress("DEPRECATION")
                android.app.Notification.Builder(context)
            }
            val notification = builder
                .setContentTitle(context.getString(com.inspiredandroid.kai.shared.R.string.app_name))
                .setContentText("$percent%")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setProgress(100, percent, false)
                .build()
            manager.notify(ModelDownloadService.NOTIFICATION_ID, notification)
        } catch (_: Exception) { }
    }

    private fun stopDownloadService() {
        try {
            context.stopService(Intent(context, ModelDownloadService::class.java))
        } catch (_: Exception) { }
    }

    override suspend fun deleteModel(modelId: String) {
        withContext(Dispatchers.IO) {
            if (currentModelId == modelId) {
                release()
            }
            val modelDir = File(getModelsDir(), modelId)
            modelDir.deleteRecursively()
        }
    }

    private fun getModelsDir(): File = File(context.filesDir, "litert_models")
}
