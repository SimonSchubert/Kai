package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.network.dtos.anthropic.AnthropicModelsResponseDto
import com.inspiredandroid.kai.network.dtos.gemini.GeminiModelsResponseDto
import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleModelResponseDto
import com.inspiredandroid.kai.toHumanReadableDate
import com.inspiredandroid.kai.ui.settings.SettingsModel

internal val geminiVersionRegex = Regex("""gemini-(\d+\.?\d*)""")

internal fun extractGeminiVersion(modelId: String): Double {
    val match = geminiVersionRegex.find(modelId)
    return match?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
}

internal fun getGeminiModelPriority(modelId: String): Int {
    val lowerId = modelId.lowercase()
    return when {
        lowerId.contains("pro") && !lowerId.contains("flash") -> 0
        lowerId.contains("flash") -> 1
        else -> 2
    }
}

/**
 * Comparator for Gemini models that sorts by:
 * 1. Version number (descending) - e.g., 2.5 > 2.0 > 1.5
 * 2. Model type priority: pro > flash > others
 */
internal val geminiModelComparator = Comparator<SettingsModel> { a, b ->
    val versionA = extractGeminiVersion(a.id)
    val versionB = extractGeminiVersion(b.id)

    // Compare versions (descending - higher versions first)
    val versionCompare = versionB.compareTo(versionA)
    if (versionCompare != 0) return@Comparator versionCompare

    // Same version, compare by model type priority
    val priorityA = getGeminiModelPriority(a.id)
    val priorityB = getGeminiModelPriority(b.id)
    priorityA.compareTo(priorityB)
}

internal fun mapAnthropicModels(
    models: List<AnthropicModelsResponseDto.ModelInfo>,
    selectedModelId: String,
): List<SettingsModel> = models.map {
    SettingsModel(
        id = it.id,
        subtitle = it.display_name ?: it.id,
        isSelected = it.id == selectedModelId,
    )
}

internal fun mapGeminiModels(
    models: List<GeminiModelsResponseDto.Model>,
    selectedModelId: String,
): List<SettingsModel> = models
    .filter { it.supportedGenerationMethods?.contains("generateContent") == true }
    .map {
        val modelId = it.name.removePrefix("models/")
        SettingsModel(
            id = modelId,
            subtitle = it.displayName ?: modelId,
            description = it.description,
            isSelected = modelId == selectedModelId,
        )
    }
    .sortedWith(geminiModelComparator)

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
    val unique = filtered.distinctBy { it.id }
    val sorted = if (service.sortModelsById) {
        unique.sortedBy { it.id }
    } else {
        unique.sortedByDescending { it.context_window }
    }
    return sorted.map {
        SettingsModel(
            id = it.id,
            subtitle = it.owned_by ?: "",
            description = if (service.includeModelDate) it.created?.toHumanReadableDate() else null,
            isSelected = it.id == selectedModelId,
        )
    }
}
