@file:OptIn(InternalCoilApi::class, ExperimentalEncodingApi::class)

package com.inspiredandroid.kai.data

import coil3.annotation.InternalCoilApi
import coil3.util.MimeTypeMap
import com.inspiredandroid.kai.Key
import com.inspiredandroid.kai.Value
import com.inspiredandroid.kai.network.Requests
import com.inspiredandroid.kai.ui.chat.History
import com.inspiredandroid.kai.ui.chat.toGeminiMessageDto
import com.inspiredandroid.kai.ui.chat.toGroqMessageDto
import com.inspiredandroid.kai.ui.settings.SettingsUiState.Service
import com.inspiredandroid.kai.ui.settings.SettingsUiState.SettingsModel
import com.russhwolf.settings.Settings
import io.github.vinceglb.filekit.core.PlatformFile
import io.github.vinceglb.filekit.core.extension
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.compose.resources.getString
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class RemoteDataRepository(
    private val requests: Requests,
    private val settings: Settings,
) : DataRepository {

    val groqModels: MutableStateFlow<List<SettingsModel>> = MutableStateFlow(
        listOf(
            SettingsModel(
                id = "llama-3.3-70b-versatile",
                subtitle = "Meta",
                description = "Context window: 128000",
                isSelected = true,
            ),
        ),
    )
    val geminiModels: MutableStateFlow<List<SettingsModel>> = MutableStateFlow(
        listOf(
            SettingsModel(
                id = "gemini-2.5-flash-preview-05-20",
                subtitle = "Gemini 2.5 Flash Preview",
                description = "Our best model in terms of price-performance, offering well-rounded capabilities.",
            ),
            SettingsModel(
                id = "gemini-2.0-flash",
                subtitle = "Gemini 2.0 Flash",
                description = "Next generation features, speed, and multimodal generation for a diverse variety of tasks",
            ),
            SettingsModel(
                id = "gemini-1.5-flash",
                subtitle = "Gemini 1.5 Flash",
                description = "Fast and versatile performance across a diverse variety of tasks",
            ),
            SettingsModel(
                id = "gemini-1.5-pro",
                subtitle = "Gemini 1.5 Pro",
                description = "Complex reasoning tasks requiring more intelligence",
            ),
        ),
    )
    val services: MutableStateFlow<List<Service>> = MutableStateFlow(emptyList())
    val chatHistory: MutableStateFlow<List<History>> = MutableStateFlow(emptyList())

    init {
        updateServices()
        updateGeminiModels()
    }

    override fun updateSelectedService(id: String) {
        settings.putString(Key.CURRENT_SERVICE_ID, id)
        updateServices()
    }

    private fun updateServices() {
        val currentServiceId = settings.getString(Key.CURRENT_SERVICE_ID, Value.DEFAULT_SERVICE)
        services.update {
            listOf(
                Service(Value.SERVICE_GEMINI, "Gemini"),
                Service(Value.SERVICE_GROQ, "GroqCloud"),
            ).map { it.copy(isSelected = it.id == currentServiceId) }
        }
    }

    override suspend fun fetchGroqModels() {
        requests.getGroqModels().onSuccess { response ->
            updateGroqModels(
                response.data.filter { it.isActive == true }.sortedByDescending { it.context_window }.map {
                    SettingsModel(
                        id = it.id,
                        subtitle = it.owned_by ?: "",
                        description = "Context window: ${it.context_window}",
                    )
                },
            )
        }
    }

    override fun updateGroqModel(id: String) {
        settings.putString(Key.GROQ_MODEL_ID, id)
        updateGroqModels(groqModels.value)
    }

    override fun updateGeminiModel(id: String) {
        settings.putString(Key.GEMINI_MODEL_ID, id)
        updateGeminiModels()
    }

    override fun changeGroqApiKey(apiKey: String) {
        settings.putString(Key.GROQ_API_KEY, apiKey)
        requests.clearBearerToken()
    }

    override fun changeGeminiApiKey(apiKey: String) {
        settings.putString(Key.GEMINI_API_KEY, apiKey)
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
        if (settings.getString(
                Key.CURRENT_SERVICE_ID,
                Value.DEFAULT_SERVICE,
            ) == Value.SERVICE_GROQ
        ) {
            val messages = chatHistory.value.map {
                it.toGroqMessageDto()
            }
            val response = requests.groqChat( // Will throw on failure
                messages = messages,
            ).getOrThrow() // Propagate exceptions
            val text = response.choices.firstOrNull()?.message?.content ?: ""
            chatHistory.update {
                it + History(role = History.Role.ASSISTANT, content = text)
            }
        } else {
            val messages = chatHistory.value.map {
                it.toGeminiMessageDto()
            }
            val response = requests.geminiChat( // Will throw on failure
                messages,
            ).getOrThrow() // Propagate exceptions
            chatHistory.update {
                val text =
                    response.candidates.firstOrNull()?.content?.parts?.joinToString("\n") { part ->
                        part.text ?: ""
                    } ?: ""
                it + History(role = History.Role.ASSISTANT, content = text)
            }
        }
    }

    private fun updateGroqModels(models: List<SettingsModel>) {
        val selectedModelId = settings.getString(Key.GROQ_MODEL_ID, Value.DEFAULT_GROQ_MODEL)
        groqModels.update {
            models.map { it.copy(isSelected = it.id == selectedModelId) }
        }
    }

    private fun updateGeminiModels() {
        val selectedModelId = settings.getString(Key.GEMINI_MODEL_ID, Value.DEFAULT_GEMINI_MODEL)
        geminiModels.update {
            it.map { it.copy(isSelected = it.id == selectedModelId) }
        }
    }

    override fun clearHistory() {
        chatHistory.update {
            emptyList()
        }
    }

    override fun isUsingSharedKey(): Boolean = settings.getString(Key.CURRENT_SERVICE_ID, Value.DEFAULT_SERVICE) == Value.SERVICE_GROQ &&
        settings.getStringOrNull(Key.GROQ_API_KEY) == null

    override fun currentService(): String = settings.getString(Key.CURRENT_SERVICE_ID, Value.DEFAULT_SERVICE)
}
