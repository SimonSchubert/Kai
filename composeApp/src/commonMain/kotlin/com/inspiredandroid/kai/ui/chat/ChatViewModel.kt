package com.inspiredandroid.kai.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.Value
import com.inspiredandroid.kai.data.RemoteDataRepository
import com.inspiredandroid.kai.getBackgroundDispatcher
import com.inspiredandroid.kai.network.GenericNetworkException
import com.inspiredandroid.kai.network.GeminiApiException
import com.inspiredandroid.kai.network.GeminiGenericException
import com.inspiredandroid.kai.network.GeminiInvalidApiKeyException
import com.inspiredandroid.kai.network.GeminiRateLimitExceededException
import com.inspiredandroid.kai.network.GroqApiException
import com.inspiredandroid.kai.network.GroqGenericException
import com.inspiredandroid.kai.network.GroqInvalidApiKeyException
import com.inspiredandroid.kai.network.GroqRateLimitExceededException
import io.github.vinceglb.filekit.core.PlatformFile
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.error_generic
import kai.composeapp.generated.resources.error_invalid_api_key
import kai.composeapp.generated.resources.error_rate_limit_exceeded
import kai.composeapp.generated.resources.error_unknown
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(private val dataRepository: RemoteDataRepository) : ViewModel() {

    private val _state = MutableStateFlow(
        ChatUiState(
            ask = ::ask,
            retry = ::retry,
            toggleSpeechOutput = ::toggleSpeechOutput,
            clearHistory = ::clearHistory,
            isUsingSharedKey = dataRepository.isUsingSharedKey(),
            setIsSpeaking = ::setIsSpeaking,
            setFile = ::setFile,
        ),
    )

    val state = combine(
        _state,
        dataRepository.chatHistory,
    ) { state, history ->
        state.copy(
            history = history,
            allowFileAttachment = dataRepository.currentService() == Value.SERVICE_GEMINI,
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
                    is GeminiGenericException, is GroqGenericException, is GenericNetworkException -> exception.message ?: getString(Res.string.error_generic)
                    // Removed UnauthorizedException case as it no longer exists
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
