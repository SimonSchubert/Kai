@file:OptIn(InternalCoilApi::class, ExperimentalEncodingApi::class, ExperimentalTime::class)

package com.inspiredandroid.kai.data

import coil3.annotation.InternalCoilApi
import coil3.util.MimeTypeMap
import com.inspiredandroid.kai.network.Requests
import com.inspiredandroid.kai.toHumanReadableDate
import com.inspiredandroid.kai.ui.chat.History
import com.inspiredandroid.kai.ui.chat.toGeminiMessageDto
import com.inspiredandroid.kai.ui.chat.toGroqMessageDto
import com.inspiredandroid.kai.ui.settings.SettingsModel
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.ExperimentalTime

class RemoteDataRepository(
    private val requests: Requests,
    private val appSettings: AppSettings,
) : DataRepository {

    /**
     * Comparator for Gemini models that sorts by:
     * 1. Version number (descending) - e.g., 2.5 > 2.0 > 1.5
     * 2. Model type priority: pro > flash > others
     */
    private val geminiModelComparator = Comparator<SettingsModel> { a, b ->
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

    private fun extractGeminiVersion(modelId: String): Double {
        // Match patterns like "gemini-2.5-pro", "gemini-1.5-flash-8b"
        val versionRegex = Regex("""gemini-(\d+\.?\d*)""")
        val match = versionRegex.find(modelId)
        return match?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    }

    private fun getGeminiModelPriority(modelId: String): Int {
        val lowerId = modelId.lowercase()
        return when {
            lowerId.contains("pro") && !lowerId.contains("flash") -> 0
            lowerId.contains("flash") -> 1
            else -> 2
        }
    }

    private val modelsByService: Map<Service, MutableStateFlow<List<SettingsModel>>> =
        Service.all.associateWith { service ->
            MutableStateFlow(service.defaultModels.toSettingsModels(service))
        }

    override val chatHistory: MutableStateFlow<List<History>> = MutableStateFlow(emptyList())

    override fun selectService(service: Service) {
        appSettings.selectService(service)
    }

    override fun updateApiKey(service: Service, apiKey: String) {
        if (service.requiresApiKey || service.supportsOptionalApiKey) {
            appSettings.setApiKey(service, apiKey)
            if (service == Service.Groq) {
                requests.clearBearerToken()
            }
        }
    }

    override fun getApiKey(service: Service): String = appSettings.getApiKey(service)

    override fun updateSelectedModel(service: Service, modelId: String) {
        if (service.modelIdKey.isNotEmpty()) {
            appSettings.setSelectedModelId(service, modelId)
            updateModelsSelection(service)
        }
    }

    override fun updateBaseUrl(service: Service, baseUrl: String) {
        appSettings.setBaseUrl(service, baseUrl)
    }

    override fun getBaseUrl(service: Service): String = appSettings.getBaseUrl(service)

    override fun getModels(service: Service): StateFlow<List<SettingsModel>> = modelsByService[service] ?: MutableStateFlow(emptyList())

    override fun clearModels(service: Service) {
        modelsByService[service]?.update { emptyList() }
    }

    override suspend fun fetchModels(service: Service) {
        when (service) {
            Service.Groq -> fetchGroqModels()
            Service.XAI -> fetchXaiModels()
            Service.OpenRouter -> fetchOpenRouterModels()
            Service.OpenAICompatible -> fetchOpenAICompatibleModels()
            Service.Gemini -> fetchGeminiModels()
            Service.Free -> { /* Free has no models */ }
        }
    }

    override suspend fun validateConnection(service: Service) {
        when (service) {
            Service.Gemini -> fetchGeminiModels()

            Service.Groq -> fetchGroqModels()

            Service.XAI -> fetchXaiModels()

            Service.OpenRouter -> {
                // Validate API key first, then fetch models
                requests.validateOpenRouterApiKey().getOrThrow()
                fetchOpenRouterModels()
            }

            Service.OpenAICompatible -> fetchOpenAICompatibleModels()

            Service.Free -> { /* Always valid */ }
        }
    }

    private suspend fun fetchGeminiModels() {
        val response = requests.getGeminiModels().getOrThrow()
        val selectedModelId = appSettings.getSelectedModelId(Service.Gemini)
        val models = response.models
            .filter { it.supportedGenerationMethods?.contains("generateContent") == true }
            .map {
                // Convert "models/gemini-1.5-pro" to "gemini-1.5-pro"
                val modelId = it.name.removePrefix("models/")
                SettingsModel(
                    id = modelId,
                    subtitle = it.displayName ?: modelId,
                    description = it.description,
                    isSelected = modelId == selectedModelId,
                )
            }
            .sortedWith(geminiModelComparator)
        modelsByService[Service.Gemini]?.update { models }
        // Auto-select first model if none selected or selected model not in list
        // The list is already sorted with the best models at the top
        if (models.isNotEmpty() && models.none { it.isSelected }) {
            appSettings.setSelectedModelId(Service.Gemini, models.first().id)
            updateModelsSelection(Service.Gemini)
        }
    }

    private suspend fun fetchGroqModels() {
        val response = requests.getGroqModels().getOrThrow()
        val selectedModelId = appSettings.getSelectedModelId(Service.Groq)
        val models = response.data
            .filter { it.isActive == true }
            .sortedByDescending { it.context_window }
            .map {
                SettingsModel(
                    id = it.id,
                    subtitle = it.owned_by ?: "",
                    description = it.created?.toHumanReadableDate(),
                    createdAt = it.created ?: 0L,
                    isSelected = it.id == selectedModelId,
                )
            }
        modelsByService[Service.Groq]?.update { models }
        // Auto-select first model if none selected or selected model not in list
        if (models.isNotEmpty() && models.none { it.isSelected }) {
            appSettings.setSelectedModelId(Service.Groq, models.first().id)
            updateModelsSelection(Service.Groq)
        }
    }

    private suspend fun fetchXaiModels() {
        val response = requests.getXaiModels().getOrThrow()
        val selectedModelId = appSettings.getSelectedModelId(Service.XAI)
        val models = response.data
            .filter { it.isActive != false }
            .sortedByDescending { it.context_window }
            .map {
                SettingsModel(
                    id = it.id,
                    subtitle = it.owned_by ?: "",
                    description = it.created?.toHumanReadableDate(),
                    createdAt = it.created ?: 0L,
                    isSelected = it.id == selectedModelId,
                )
            }
        modelsByService[Service.XAI]?.update { models }
        // Auto-select first model if none selected or selected model not in list
        if (models.isNotEmpty() && models.none { it.isSelected }) {
            appSettings.setSelectedModelId(Service.XAI, models.first().id)
            updateModelsSelection(Service.XAI)
        }
    }

    private suspend fun fetchOpenRouterModels() {
        val response = requests.getOpenRouterModels().getOrThrow()
        val selectedModelId = appSettings.getSelectedModelId(Service.OpenRouter)
        val models = response.data
            .filter { it.isActive != false }
            .sortedByDescending { it.context_window }
            .map {
                SettingsModel(
                    id = it.id,
                    subtitle = it.owned_by ?: "",
                    description = it.created?.toHumanReadableDate(),
                    createdAt = it.created ?: 0L,
                    isSelected = it.id == selectedModelId,
                )
            }
        modelsByService[Service.OpenRouter]?.update { models }
        // Auto-select first model if none selected or selected model not in list
        if (models.isNotEmpty() && models.none { it.isSelected }) {
            appSettings.setSelectedModelId(Service.OpenRouter, models.first().id)
            updateModelsSelection(Service.OpenRouter)
        }
    }

    private suspend fun fetchOpenAICompatibleModels() {
        val baseUrl = appSettings.getBaseUrl(Service.OpenAICompatible)
        val response = requests.getOpenAICompatibleModels(baseUrl).getOrThrow()
        val selectedModelId = appSettings.getSelectedModelId(Service.OpenAICompatible)
        val models = response.models
            .sortedBy { it.name }
            .map {
                SettingsModel(
                    id = it.name,
                    subtitle = it.details?.family ?: "",
                    description = it.details?.parameter_size,
                    isSelected = it.name == selectedModelId,
                )
            }
        modelsByService[Service.OpenAICompatible]?.update { models }
        // Auto-select first model if none selected or selected model not in list
        if (models.isNotEmpty() && models.none { it.isSelected }) {
            appSettings.setSelectedModelId(Service.OpenAICompatible, models.first().id)
            updateModelsSelection(Service.OpenAICompatible)
        }
    }

    private fun List<ModelDefinition>.toSettingsModels(service: Service): List<SettingsModel> {
        val selectedModelId = appSettings.getSelectedModelId(service)
        return map {
            SettingsModel(
                id = it.id,
                subtitle = it.subtitle,
                descriptionRes = it.descriptionRes,
                isSelected = it.id == selectedModelId,
            )
        }
    }

    private fun updateModelsSelection(service: Service) {
        val selectedModelId = appSettings.getSelectedModelId(service)
        modelsByService[service]?.update { models ->
            models.map { it.copy(isSelected = it.id == selectedModelId) }
        }
    }

    override suspend fun ask(question: String?, file: PlatformFile?) {
        if (question != null) {
            chatHistory.update {
                it + History(
                    role = History.Role.USER,
                    content = question,
                    mimeType = file?.extension?.let { MimeTypeMap.getMimeTypeFromExtension(it) },
                    data = file?.readBytes()?.let { Base64.encode(it) },
                )
            }
        }
        val service = currentService()
        val messages = chatHistory.value

        val responseText = when (service) {
            Service.Free -> {
                val freeMessages = messages.map { it.toGroqMessageDto() }
                val response = requests.freeChat(messages = freeMessages).getOrThrow()
                response.choices.firstOrNull()?.message?.content ?: ""
            }

            Service.Groq -> {
                val groqMessages = messages.map { it.toGroqMessageDto() }
                val response = requests.groqChat(messages = groqMessages).getOrThrow()
                response.choices.firstOrNull()?.message?.content ?: ""
            }

            Service.XAI -> {
                val xaiMessages = messages.map { it.toGroqMessageDto() }
                val response = requests.xaiChat(messages = xaiMessages).getOrThrow()
                response.choices.firstOrNull()?.message?.content ?: ""
            }

            Service.OpenRouter -> {
                val openRouterMessages = messages.map { it.toGroqMessageDto() }
                val response = requests.openRouterChat(messages = openRouterMessages).getOrThrow()
                response.choices.firstOrNull()?.message?.content ?: ""
            }

            Service.Gemini -> {
                val geminiMessages = messages.map { it.toGeminiMessageDto() }
                val response = requests.geminiChat(geminiMessages).getOrThrow()
                response.candidates.firstOrNull()?.content?.parts?.joinToString("\n") { part ->
                    part.text ?: ""
                } ?: ""
            }

            Service.OpenAICompatible -> {
                val openAIMessages = messages.map { it.toGroqMessageDto() }
                val baseUrl = appSettings.getBaseUrl(Service.OpenAICompatible)
                val response = requests.openAICompatibleChat(messages = openAIMessages, baseUrl = baseUrl).getOrThrow()
                response.choices.firstOrNull()?.message?.content ?: ""
            }
        }

        chatHistory.update {
            it + History(role = History.Role.ASSISTANT, content = responseText)
        }
    }

    override fun clearHistory() {
        chatHistory.update {
            emptyList()
        }
    }

    override fun isUsingSharedKey(): Boolean = currentService() == Service.Free

    override fun currentService(): Service = appSettings.currentService()
}
