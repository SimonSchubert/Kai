@file:OptIn(InternalCoilApi::class, ExperimentalEncodingApi::class, ExperimentalTime::class)

package com.inspiredandroid.kai.data

import androidx.compose.runtime.mutableStateOf
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
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.readBytes
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.gemini_model_2_5_flash_description
import kai.composeapp.generated.resources.gemini_model_2_5_flash_lite_description
import kai.composeapp.generated.resources.gemini_model_2_5_pro_description
import kai.composeapp.generated.resources.gemini_model_3_flash_description
import kai.composeapp.generated.resources.gemini_model_3_pro_preview_description
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.YearMonth
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.DateTimeComponents.Companion.Format
import kotlinx.datetime.format.DayOfWeekNames.Companion.ENGLISH_FULL
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import org.jetbrains.compose.resources.getString
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class RemoteDataRepository(
    private val requests: Requests,
    private val settings: Settings,
) : DataRepository {

    val groqModels: MutableStateFlow<List<SettingsModel>> = MutableStateFlow(
        listOf(
            SettingsModel(
                id = "llama-3.3-70b-versatile",
                subtitle = "Meta",
                description = "Open source llama 3.3 model",
                isSelected = true,
            ),
        ),
    )
    val geminiModels: MutableStateFlow<List<SettingsModel>> = MutableStateFlow(emptyList())
    val services: MutableStateFlow<List<Service>> = MutableStateFlow(emptyList())
    val chatHistory: MutableStateFlow<List<History>> = MutableStateFlow(emptyList())

    init {
        updateServices()
        CoroutineScope(Dispatchers.Default).launch {
            geminiModels.value = listOf(
                SettingsModel(
                    id = "gemini-3-pro-preview",
                    subtitle = "Gemini 3 Pro",
                    description = getString(Res.string.gemini_model_3_pro_preview_description),
                ),
                SettingsModel(
                    id = "gemini-3-flash-preview",
                    subtitle = "Gemini 3 Flash",
                    description = getString(Res.string.gemini_model_3_flash_description),
                ),
                SettingsModel(
                    id = "gemini-2.5-flash",
                    subtitle = "Gemini 2.5 Flash",
                    description = getString(Res.string.gemini_model_2_5_flash_description),
                ),
                SettingsModel(
                    id = "gemini-2.5-flash-lite",
                    subtitle = "Gemini 2.5 Flash Lite",
                    description = getString(Res.string.gemini_model_2_5_flash_lite_description),
                ),
                SettingsModel(
                    id = "gemini-2.5-pro",
                    subtitle = "Gemini 2.5 Pro",
                    description = getString(Res.string.gemini_model_2_5_pro_description),
                ),
            )
            updateGeminiModels()
        }
    }

    override fun updateSelectedService(id: String) {
        settings.putString(Key.CURRENT_SERVICE_ID, id)
        updateServices()
    }

    private fun updateServices() {
        val currentServiceId = settings.getString(Key.CURRENT_SERVICE_ID, Value.DEFAULT_SERVICE)
        services.update {
            listOf(
                Service(Value.SERVICE_FREE, "Free"),
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
                        description = if (it.created != null) {
                            Instant.fromEpochSeconds(it.created).format(
                                Format {
                                    day()
                                    char(' ')
                                    monthName(MonthNames.ENGLISH_FULL)
                                    char(' ')
                                    year()
                                },
                            )
                        } else {
                            null
                        },
                        createdAt = it.created ?: 0L,
                    )
                },
            )
        }.onFailure {
            // Network error - models remain at default values
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
        val currentService = settings.getString(Key.CURRENT_SERVICE_ID, Value.DEFAULT_SERVICE)

        val messages = chatHistory.value

        val responseText = when (currentService) {
            Value.SERVICE_FREE -> {
                val freeMessages = messages.map { it.toGroqMessageDto() }
                val response = requests.freeChat(messages = freeMessages).getOrThrow()
                response.choices.firstOrNull()?.message?.content ?: ""
            }
            Value.SERVICE_GROQ -> {
                val groqMessages = messages.map { it.toGroqMessageDto() }
                val response = requests.groqChat(messages = groqMessages).getOrThrow()
                response.choices.firstOrNull()?.message?.content ?: ""
            }
            Value.SERVICE_GEMINI -> {
                val geminiMessages = messages.map { it.toGeminiMessageDto() }
                val response = requests.geminiChat(geminiMessages).getOrThrow()
                response.candidates.firstOrNull()?.content?.parts?.joinToString("\n") { part ->
                    part.text ?: ""
                } ?: ""
            }
            else -> {
                ""
            }
        }

        chatHistory.update {
            it + History(role = History.Role.ASSISTANT, content = responseText)
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
