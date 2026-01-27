package com.inspiredandroid.kai.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.data.DataRepository
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.getBackgroundDispatcher
import com.inspiredandroid.kai.network.GeminiGenericException
import com.inspiredandroid.kai.network.GeminiInvalidApiKeyException
import com.inspiredandroid.kai.network.GeminiRateLimitExceededException
import com.inspiredandroid.kai.network.GenericNetworkException
import com.inspiredandroid.kai.network.GroqGenericException
import com.inspiredandroid.kai.network.GroqInvalidApiKeyException
import com.inspiredandroid.kai.network.GroqRateLimitExceededException
import com.inspiredandroid.kai.network.OllamaConnectionException
import com.inspiredandroid.kai.network.OllamaGenericException
import com.inspiredandroid.kai.network.OllamaModelNotFoundException
import io.github.vinceglb.filekit.PlatformFile
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.error_generic
import kai.composeapp.generated.resources.error_invalid_api_key
import kai.composeapp.generated.resources.error_ollama_connection
import kai.composeapp.generated.resources.error_ollama_model_not_found
import kai.composeapp.generated.resources.error_rate_limit_exceeded
import kai.composeapp.generated.resources.error_unknown
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

class ChatViewModel(private val dataRepository: DataRepository) : ViewModel() {

    private val actions = ChatActions(
        ask = ::ask,
        retry = ::retry,
        toggleSpeechOutput = ::toggleSpeechOutput,
        clearHistory = ::clearHistory,
        setIsSpeaking = ::setIsSpeaking,
        setFile = ::setFile,
    )
    private val _state = MutableStateFlow(
        ChatUiState(
            actions = actions,
            isUsingSharedKey = dataRepository.isUsingSharedKey(),
        ),
    )

    val state = combine(
        _state,
        dataRepository.chatHistory,
    ) { state, history ->
        state.copy(
            history = history,
            allowFileAttachment = dataRepository.currentService() == Service.Gemini,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _state.value,
    )

    private fun ask(question: String?) {
        viewModelScope.launch(getBackgroundDispatcher()) {
            _state.update {
                it.copy(
                    isLoading = true,
                    error = null,
                )
            }
            try {
                dataRepository.ask(question, _state.value.file)
                _state.update {
                    it.copy(isLoading = false)
                }
            } catch (exception: Exception) {
                val errorMessage = when (exception) {
                    is GeminiInvalidApiKeyException, is GroqInvalidApiKeyException -> getString(Res.string.error_invalid_api_key)
                    is GeminiRateLimitExceededException, is GroqRateLimitExceededException -> getString(Res.string.error_rate_limit_exceeded)
                    is OllamaConnectionException -> getString(Res.string.error_ollama_connection)
                    is OllamaModelNotFoundException -> getString(Res.string.error_ollama_model_not_found)
                    is GeminiGenericException, is GroqGenericException, is OllamaGenericException, is GenericNetworkException -> exception.message ?: getString(Res.string.error_generic)
                    else -> getString(Res.string.error_unknown)
                }
                _state.update {
                    it.copy(
                        error = errorMessage,
                        isLoading = false,
                    )
                }
            }
        }
    }

    private fun clearHistory() {
        dataRepository.clearHistory()
        _state.update {
            it.copy(error = null)
        }
    }

    private fun setIsSpeaking(isSpeaking: Boolean, contentId: String) {
        _state.update {
            it.copy(
                isSpeaking = isSpeaking,
                isSpeakingContentId = if (isSpeaking) {
                    contentId
                } else {
                    it.isSpeakingContentId
                },
            )
        }
    }

    private fun setFile(file: PlatformFile?) {
        _state.update {
            it.copy(
                file = file,
            )
        }
    }

    private fun retry() {
        ask(null)
    }

    private fun toggleSpeechOutput() {
        _state.update {
            it.copy(
                isSpeechOutputEnabled = !it.isSpeechOutputEnabled,
            )
        }
    }
}
