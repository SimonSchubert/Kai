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

    private val modelsByService: Map<Service, MutableStateFlow<List<SettingsModel>>> =
        Service.all.associateWith { service ->
            MutableStateFlow(service.defaultModels.toSettingsModels(service))
        }

    val chatHistory: MutableStateFlow<List<History>> = MutableStateFlow(emptyList())

    override fun selectService(service: Service) {
        appSettings.selectService(service)
    }

    override fun updateApiKey(service: Service, apiKey: String) {
        if (service.requiresApiKey) {
            appSettings.setApiKey(service, apiKey)
            if (service == Service.Groq) {
                requests.clearBearerToken()
            }
        }
    }

    override fun getApiKey(service: Service): String = appSettings.getApiKey(service)

    override fun updateSelectedModel(service: Service, modelId: String) {
        if (service.requiresApiKey && service.modelIdKey.isNotEmpty()) {
            appSettings.setSelectedModelId(service, modelId)
            updateModelsSelection(service)
        }
    }

    override fun getModels(service: Service): StateFlow<List<SettingsModel>> = modelsByService[service] ?: MutableStateFlow(emptyList())

    override suspend fun fetchModels(service: Service) {
        when (service) {
            Service.Groq -> fetchGroqModels()

            Service.Gemini, Service.Free -> {
                // Gemini models are static, Free has no models
            }
        }
    }

    private suspend fun fetchGroqModels() {
        requests.getGroqModels().onSuccess { response ->
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
            modelsByService[Service.Groq]?.value = models
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

            Service.Gemini -> {
                val geminiMessages = messages.map { it.toGeminiMessageDto() }
                val response = requests.geminiChat(geminiMessages).getOrThrow()
                response.candidates.firstOrNull()?.content?.parts?.joinToString("\n") { part ->
                    part.text ?: ""
                } ?: ""
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
