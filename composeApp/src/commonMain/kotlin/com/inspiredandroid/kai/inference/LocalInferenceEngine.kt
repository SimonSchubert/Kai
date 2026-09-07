package com.inspiredandroid.kai.inference

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.StateFlow

data class LocalModel(
    val id: String,
    val displayName: String,
    val fileName: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    /**
     * Expected SHA-256 of the downloaded file, lowercase hex. Blank for imported models,
     * whose bytes the user supplies directly and for which no digest is known.
     */
    val sha256: String = "",
    val gpuMemoryMb: Int,
    val defaultContextTokens: Int,
    val maxContextTokens: Int,
    val kvPerTokenBytes: Int,
    val isRecommended: Boolean = false,
    /** True for user-imported models that are not in [MODEL_CATALOG]. */
    val isImported: Boolean = false,
)

enum class DevicePerformance {
    GOOD,
    OK,
    POOR,
}

fun estimateGpuMemoryMb(model: LocalModel, contextTokens: Int): Int {
    val modelFileMb = (model.sizeBytes / (1024 * 1024)).toInt()
    val extraTokens = contextTokens - model.defaultContextTokens
    val extraMemoryMb = (extraTokens.toLong() * model.kvPerTokenBytes) / (1024 * 1024)
    return modelFileMb + model.gpuMemoryMb + extraMemoryMb.toInt()
}

fun calculateDevicePerformance(totalMemoryBytes: Long, estimatedGpuMemoryMb: Int): DevicePerformance {
    val gpuMemoryBytes = estimatedGpuMemoryMb.toLong() * 1024 * 1024
    val ratio = totalMemoryBytes.toDouble() / gpuMemoryBytes
    return when {
        ratio >= 2.5 -> DevicePerformance.GOOD
        ratio >= 1.85 -> DevicePerformance.OK
        else -> DevicePerformance.POOR
    }
}

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

/**
 * A tool definition handed to the on-device inference engine.
 *
 * @param name the tool's identifier as the model will see it
 * @param descriptionJsonString a complete OpenAPI/OpenAI-style JSON object describing the
 *        tool, e.g. `{"name":"get_time","description":"...","parameters":{"type":"object",...}}`
 * @param execute receives the JSON arguments object as a string and returns the
 *        JSON-encoded result string
 */
data class LocalTool(
    val name: String,
    val descriptionJsonString: String,
    val execute: suspend (jsonArgs: String) -> String,
)

/**
 * Sampling defaults a `.litertlm` bundle ships for itself. Models converted from different
 * upstream families want different values — one hardcoded triple is right for none of them.
 */
data class LocalSamplerDefaults(
    val temperature: Float,
    val topK: Int,
    val topP: Float,
)

/**
 * The bundle's declared sampling defaults, or null when it declares none. A bundle with
 * nothing to say reports zeroes, and passing those straight through would pin the model to
 * greedy decoding — so zero (or negative) means "no opinion", not "sample greedily".
 */
fun localSamplerDefaultsOrNull(temperature: Float, topK: Int, topP: Float): LocalSamplerDefaults? = if (topK > 0 && temperature > 0f) LocalSamplerDefaults(temperature, topK, topP) else null

/**
 * What a model file declares about itself, read from the `.litertlm` bundle's own metadata.
 *
 * A model that does not declare function calling carries no tool section in its chat
 * template. Handing it tools anyway does not make it ignore them — it makes it invent the
 * answer a tool would have produced, which is exactly how Qwen3 0.6B reports a fictional
 * time instead of calling `get_local_time`.
 */
data class LocalModelCapabilities(
    val supportsFunctionCalling: Boolean,
    val supportsThinking: Boolean,
    val supportsVision: Boolean,
    val supportsAudio: Boolean,
    /** Null when the bundle declares no usable defaults; callers keep their own values. */
    val sampler: LocalSamplerDefaults?,
)

class InsufficientMemoryException : Exception()
class InferenceTimeoutException : Exception()
class NoModelDownloadedException : Exception()

/** A model file on disk did not match the digest pinned in the catalog and was removed. */
class ModelIntegrityException : Exception()

enum class DownloadError {
    NOT_ENOUGH_DISK_SPACE,
    NETWORK_ERROR,
    DOWNLOAD_INCOMPLETE,
    CHECKSUM_MISMATCH,
}

interface LocalInferenceEngine {
    val engineState: StateFlow<EngineState>
    val downloadingModelId: StateFlow<String?>
    val downloadProgress: StateFlow<Float?>
    val downloadError: StateFlow<DownloadError?>

    /** Non-null file name while a local import copy is in progress. */
    val importingFileName: StateFlow<String?>
    val importProgress: StateFlow<Float?>
    val importError: StateFlow<ModelImportError?>

    val currentModelId: String?

    /**
     * What the model file for [modelId] declares it can do. Reads the bundle's own
     * metadata, so it answers before the model is ever loaded, and the answer is cached
     * for as long as the file stays put.
     *
     * Null means *unknown*, not *incapable* — the id may not be downloaded, the bundle's
     * metadata may predate the declaration, or the platform may not implement the probe at
     * all (the iOS bridge predates it). Callers that get null must fall back to their own
     * assumptions rather than treat the model as supporting nothing.
     */
    suspend fun modelCapabilities(modelId: String): LocalModelCapabilities? = null

    suspend fun initialize(model: DownloadedModel, contextTokens: Int = 0)
    suspend fun release()

    /**
     * Fire-and-forget release, run on the engine's own coroutine scope. Called from
     * non-suspend contexts (e.g. Settings UI when the user picks a different model) so
     * the GPU driver has time to reclaim memory before the next inference.
     */
    fun releaseInBackground()

    suspend fun chat(
        messages: List<InferenceMessage>,
        systemPrompt: String?,
        tools: List<LocalTool> = emptyList(),
    ): String

    fun getDownloadedModels(): List<DownloadedModel>
    fun getAvailableModels(): List<LocalModel>

    /**
     * Synthetic [LocalModel] entries for user-imported files under `imports/`, so the
     * settings UI can show context sliders and performance labels.
     */
    fun getImportedLocalModels(): List<LocalModel>

    fun getFreeSpaceBytes(): Long
    fun startDownload(model: LocalModel)
    fun cancelDownload()
    suspend fun deleteModel(modelId: String)

    /**
     * Copy a user-picked `.litertlm` into app storage. Streams bytes — never loads the
     * full file into memory. Catalog file names land in the matching catalog path;
     * everything else goes under `imports/`.
     */
    suspend fun importModel(source: PlatformFile): ModelImportResult
    fun cancelImport()
}
