package com.inspiredandroid.kai.inference

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class LocalModel(
    val id: String,
    val displayName: String,
    val fileName: String,
    val sizeBytes: Long,
    val downloadUrl: String,
)

data class DownloadedModel(
    val id: String,
    val displayName: String,
    val filePath: String,
    val sizeBytes: Long,
)

enum class EngineState {
    UNINITIALIZED,
    INITIALIZING,
    READY,
    ERROR,
}

data class InferenceMessage(
    val role: String,
    val content: String,
)

class InsufficientMemoryException : Exception()
class InferenceTimeoutException : Exception()
class NoModelDownloadedException : Exception()

enum class DownloadError {
    NOT_ENOUGH_DISK_SPACE,
    NETWORK_ERROR,
    DOWNLOAD_INCOMPLETE,
}

interface LocalInferenceEngine {
    val engineState: StateFlow<EngineState>
    val downloadingModelId: StateFlow<String?>
    val downloadProgress: StateFlow<Float?>
    val downloadError: StateFlow<DownloadError?>

    suspend fun initialize(model: DownloadedModel)
    suspend fun release()

    suspend fun chat(
        messages: List<InferenceMessage>,
        systemPrompt: String?,
    ): String

    fun getDownloadedModels(): List<DownloadedModel>
    fun getAvailableModels(): List<LocalModel>
    fun getFreeSpaceBytes(): Long
    fun startDownload(model: LocalModel)
    fun cancelDownload()
    suspend fun deleteModel(modelId: String)
}
