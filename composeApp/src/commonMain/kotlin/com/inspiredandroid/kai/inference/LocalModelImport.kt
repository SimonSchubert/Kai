package com.inspiredandroid.kai.inference

/** Subdirectory under the model storage root for user-imported `.litertlm` files. */
const val IMPORTS_DIR = "imports"

/** Prefix for synthetic ids of models not in [MODEL_CATALOG]. */
const val CUSTOM_MODEL_ID_PREFIX = "custom-"

const val MIN_MODEL_FILE_BYTES = 1_000_000L

const val CUSTOM_DEFAULT_CONTEXT_TOKENS = 4_096
const val CUSTOM_MAX_CONTEXT_TOKENS = 32_768
const val CUSTOM_KV_PER_TOKEN_BYTES = 50_000

enum class ModelImportError {
    INVALID_EXTENSION,
    NOT_ENOUGH_DISK_SPACE,
    FILE_TOO_SMALL,
    COPY_FAILED,
    CANCELLED,
}

sealed class ModelImportResult {
    data class Success(val modelId: String, val matchedCatalog: Boolean) : ModelImportResult()
    data class Failure(val error: ModelImportError, val message: String? = null) : ModelImportResult()
}

/**
 * Where an imported file should land on disk and which model id it maps to.
 *
 * @param modelId stable id used for selection / context tokens / delete
 * @param relativeDir directory under the model storage root (catalog id, or [IMPORTS_DIR])
 * @param fileName final file name inside [relativeDir]
 * @param matchedCatalog true when [fileName] matched a [MODEL_CATALOG] entry
 * @param displayName human-readable label for the settings UI
 */
data class ImportTarget(
    val modelId: String,
    val relativeDir: String,
    val fileName: String,
    val matchedCatalog: Boolean,
    val displayName: String,
)

/**
 * Sanitizes a file name for safe storage under the imports directory.
 * Keeps alphanumerics, dots, underscores, and hyphens; replaces other chars with `_`.
 * Collapses path separators and rejects empty / traversal-only results.
 */
fun sanitizeImportFileName(raw: String): String {
    val base = raw.substringAfterLast('/').substringAfterLast('\\').trim()
    if (base.isEmpty() || base == "." || base == "..") return "model.litertlm"
    val sanitized = buildString(base.length) {
        for (c in base) {
            append(
                when {
                    c.isLetterOrDigit() || c == '.' || c == '_' || c == '-' -> c
                    else -> '_'
                },
            )
        }
    }.trim('_', '.')
    return when {
        sanitized.isEmpty() -> "model.litertlm"
        !sanitized.contains('.') -> "$sanitized.litertlm"
        else -> sanitized
    }
}

fun isLitertlmExtension(fileName: String): Boolean =
    fileName.substringAfterLast('.', missingDelimiterValue = "").equals("litertlm", ignoreCase = true)

fun findCatalogModelByFileName(fileName: String): LocalModel? =
    MODEL_CATALOG.find { it.fileName.equals(fileName, ignoreCase = true) }

/**
 * Resolves the destination for [sourceFileName] given files already present under imports.
 *
 * [existingImportFileNames] should be the basenames currently in the imports directory
 * (used only for collision avoidance of custom imports).
 */
fun resolveImportTarget(
    sourceFileName: String,
    existingImportFileNames: Set<String> = emptySet(),
): ImportTarget? {
    if (!isLitertlmExtension(sourceFileName)) return null

    val catalog = findCatalogModelByFileName(sourceFileName)
    if (catalog != null) {
        return ImportTarget(
            modelId = catalog.id,
            relativeDir = catalog.id,
            fileName = catalog.fileName,
            matchedCatalog = true,
            displayName = catalog.displayName,
        )
    }

    var fileName = sanitizeImportFileName(sourceFileName)
    if (!isLitertlmExtension(fileName)) {
        fileName = "$fileName.litertlm"
    }

    val baseNoExt = fileName.substringBeforeLast('.')
    val ext = fileName.substringAfterLast('.', "litertlm")
    var candidate = fileName
    var counter = 2
    val existingLower = existingImportFileNames.map { it.lowercase() }.toSet()
    while (candidate.lowercase() in existingLower) {
        candidate = "${baseNoExt}_$counter.$ext"
        counter++
    }

    val idBase = candidate.substringBeforeLast('.')
    return ImportTarget(
        modelId = CUSTOM_MODEL_ID_PREFIX + idBase,
        relativeDir = IMPORTS_DIR,
        fileName = candidate,
        matchedCatalog = false,
        displayName = idBase,
    )
}

/**
 * Synthetic [LocalModel] for an imported custom file so the settings UI can show
 * a context slider and performance estimate without a catalog entry.
 */
fun customLocalModel(fileName: String, sizeBytes: Long, modelId: String = CUSTOM_MODEL_ID_PREFIX + fileName.substringBeforeLast('.')): LocalModel {
    val sizeMb = (sizeBytes / (1024 * 1024)).toInt().coerceAtLeast(1)
    return LocalModel(
        id = modelId,
        displayName = fileName.substringBeforeLast('.').ifEmpty { fileName },
        fileName = fileName,
        sizeBytes = sizeBytes,
        downloadUrl = "",
        gpuMemoryMb = maxOf(300, sizeMb / 4),
        defaultContextTokens = CUSTOM_DEFAULT_CONTEXT_TOKENS,
        maxContextTokens = CUSTOM_MAX_CONTEXT_TOKENS,
        kvPerTokenBytes = CUSTOM_KV_PER_TOKEN_BYTES,
        isRecommended = false,
    )
}

fun isCustomModelId(modelId: String): Boolean = modelId.startsWith(CUSTOM_MODEL_ID_PREFIX)
