package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.network.dtos.anthropic.AnthropicModelsResponseDto
import com.inspiredandroid.kai.network.dtos.gemini.GeminiModelsResponseDto
import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleModelResponseDto
import com.inspiredandroid.kai.toIsoDate
import com.inspiredandroid.kai.ui.settings.SettingsModel

/**
 * Model id substrings that mark a model as non-chat. Any model whose
 * lowercased id contains one of these is filtered out of the picker.
 * Covers: voice/TTS, embeddings, moderation, OCR, safety, image gen,
 * video gen, retrieval, reward, translation, and other non-chat APIs.
 */
private val nonChatPatterns = listOf(
    "embed",
    "tts",
    "transcribe",
    "realtime",
    "moderation",
    "ocr",
    "guard",
    "safety",
    "reward",
    "voxtral",
    "whisper",
    "orpheus",
    "leanstral",
    "vibe-cli",
    "streampetr",
    "nvclip",
    "deplot",
    "paligemma",
    "gliner",
    "nemoretriever",
    "nemotron-parse",
    "riva-translate",
    "kosmos",
    "nano-banana",
    "lyria",
    "imagen",
    "image",
    "aqa",
    "veo",
    "native-audio",
    "live-",
    "bge-",
    "shieldgemma",
)

internal fun isChatModel(modelId: String): Boolean {
    val lower = modelId.lowercase()
    return nonChatPatterns.none { lower.contains(it) }
}

/**
 * Unified "newest first" sort applied to every provider's model list.
 *
 *  1. Release date descending (nulls last)
 *  2. Context window descending (nulls last)
 *  3. Model id ascending (stable tiebreaker)
 */
internal val newestFirstComparator: Comparator<SettingsModel> = Comparator { a, b ->
    val dateA = a.releaseDate
    val dateB = b.releaseDate
    when {
        dateA != null && dateB == null -> return@Comparator -1

        dateA == null && dateB != null -> return@Comparator 1

        dateA != null && dateB != null -> {
            val cmp = dateB.compareTo(dateA)
            if (cmp != 0) return@Comparator cmp
        }
    }
    val ctxA = a.contextWindow
    val ctxB = b.contextWindow
    when {
        ctxA != null && ctxB == null -> return@Comparator -1

        ctxA == null && ctxB != null -> return@Comparator 1

        ctxA != null && ctxB != null -> {
            val cmp = ctxB.compareTo(ctxA)
            if (cmp != 0) return@Comparator cmp
        }
    }
    a.id.compareTo(b.id)
}

internal fun mapAnthropicModels(
    models: List<AnthropicModelsResponseDto.ModelInfo>,
    selectedModelId: String,
): List<SettingsModel> = models
    .map {
        val curated = ModelCatalog.lookup(it.id)
        SettingsModel(
            id = it.id,
            displayName = curated?.displayName ?: it.display_name,
            subtitle = "",
            isSelected = it.id == selectedModelId,
            contextWindow = curated?.contextWindow,
            releaseDate = curated?.releaseDate,
            parameterCount = curated?.parameterCount,
            arenaScore = curated?.arenaScore,
        )
    }
    .sortedWith(newestFirstComparator)

internal fun mapGeminiModels(
    models: List<GeminiModelsResponseDto.Model>,
    selectedModelId: String,
): List<SettingsModel> = models
    .filter { it.supportedGenerationMethods?.contains("generateContent") == true }
    .map { it to it.name.removePrefix("models/") }
    .filter { (_, modelId) -> isChatModel(modelId) }
    .map { (dto, modelId) ->
        val curated = ModelCatalog.lookup(modelId)
        SettingsModel(
            id = modelId,
            displayName = curated?.displayName ?: dto.displayName,
            subtitle = "",
            isSelected = modelId == selectedModelId,
            contextWindow = curated?.contextWindow ?: dto.inputTokenLimit,
            releaseDate = curated?.releaseDate,
            parameterCount = curated?.parameterCount,
            arenaScore = curated?.arenaScore,
        )
    }
    .sortedWith(newestFirstComparator)

internal fun mapOpenAICompatibleModels(
    models: List<OpenAICompatibleModelResponseDto.Model>,
    service: Service,
    selectedModelId: String,
): List<SettingsModel> {
    val activeFiltered = if (service.filterActiveStrictly) {
        models.filter { it.isActive == true }
    } else {
        models.filter { it.isActive != false }
    }
    val typeFiltered = if (service.filterByModelType && activeFiltered.any { it.type != null }) {
        activeFiltered.filter { it.type == "chat" }
    } else {
        activeFiltered
    }
    val filtered = if (service is Service.OpenAI) {
        val chatPrefixes = listOf("gpt-", "o1", "o3", "o4", "chatgpt-")
        typeFiltered.filter { model -> chatPrefixes.any { model.id.startsWith(it) } }
    } else {
        typeFiltered
    }
    val chatOnly = filtered.filter { isChatModel(it.id) }
    val unique = chatOnly.distinctBy { it.id }
    val mapped = unique.map {
        val curated = ModelCatalog.lookup(it.id)
        SettingsModel(
            id = it.id,
            displayName = curated?.displayName ?: it.name,
            subtitle = "",
            isSelected = it.id == selectedModelId,
            contextWindow = curated?.contextWindow ?: it.context_window ?: it.context_length,
            releaseDate = curated?.releaseDate ?: it.created?.toIsoDate(),
            parameterCount = curated?.parameterCount,
            arenaScore = curated?.arenaScore,
        )
    }
    return if (service.sortModelsById) {
        mapped.sortedBy { it.id }
    } else {
        mapped.sortedWith(newestFirstComparator)
    }
}
