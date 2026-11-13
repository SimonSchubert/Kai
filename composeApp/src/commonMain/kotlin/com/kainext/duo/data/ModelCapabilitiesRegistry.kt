package com.kainext.duo.data

import com.kainext.duo.network.dtos.openaicompatible.OpenAICompatibleModelResponseDto

/**
 * What a provider's model listing declared about a model's input modalities.
 *
 * Providers increasingly publish this themselves (OpenRouter's `architecture.input_modalities`,
 * and a flat `modality` string on some gateways). Reading it is strictly better than guessing
 * from the model id: the vendor is the authority on its own model, and a new generation needs no
 * app update. The id-based rules in `ModelCapabilities` remain the fallback for providers that
 * declare nothing.
 */
data class ModelCapabilities(
    val supportsImages: Boolean,
)

/**
 * Process-wide cache of provider-declared model capabilities, keyed by lower-cased model id.
 *
 * Populated whenever a model list is fetched. Entries are per model id rather than per instance
 * because the declaration describes the model, not the account. Absence of an entry means the
 * provider said nothing, in which case callers fall back to the id-based heuristics.
 */
internal object ModelCapabilityRegistry {

    private val declared = mutableMapOf<String, ModelCapabilities>()

    /** Registers what [service]'s model listing said about [models]. */
    fun record(service: Service, models: List<OpenAICompatibleModelResponseDto.Model>) {
        val entries = models.mapNotNull { model ->
            declaredImages(model)?.let { normalizeId(model.id) to ModelCapabilities(supportsImages = it) }
        }
        if (entries.isEmpty()) return
        synchronized(declared) { declared.putAll(entries) }
    }

    /** The provider's declaration for [modelId], or null when it declared nothing usable. */
    fun lookup(modelId: String): ModelCapabilities? = synchronized(declared) { declared[normalizeId(modelId)] }

    /** Test seam: drops every recorded declaration. */
    fun clear() = synchronized(declared) { declared.clear() }

    private fun normalizeId(modelId: String): String = modelId.substringAfterLast('/').lowercase()

    /**
     * Reads an image-input declaration out of a model entry, tolerating both shapes:
     * `architecture.input_modalities` (OpenRouter) and the flat `modality` string
     * ("text+image->text"). Returns null when the entry carries neither.
     */
    private fun declaredImages(model: OpenAICompatibleModelResponseDto.Model): Boolean? {
        val modalities = model.architecture?.inputModalities
        if (!modalities.isNullOrEmpty()) {
            return modalities.any { it.equals("image", ignoreCase = true) }
        }
        val flat = model.modality ?: return null
        // "text+image->text": only the part before the arrow describes inputs.
        val input = flat.substringBefore("->").ifBlank { flat }
        val tokens = input.split('+', ',', '/').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        return tokens.contains("image")
    }
}
