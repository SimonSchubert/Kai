package com.inspiredandroid.kai.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.Value
import com.inspiredandroid.kai.data.RemoteDataRepository
import com.inspiredandroid.kai.getBackgroundDispatcher
import com.inspiredandroid.kai.network.UnauthorizedException
import io.github.vinceglb.filekit.core.PlatformFile
import kotlinx.coroutines.flow.MutableStateFlow
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

    private fun ask(question: String?, file: PlatformFile? = null) {
        viewModelScope.launch(getBackgroundDispatcher()) {
            _state.update {
                it.copy(
                    isLoading = true,
                    error = null,
                )
            }
            dataRepository.ask(question, file).onSuccess {
                _state.update {
                    it.copy(isLoading = false)
                }
            }.onFailure { exception ->
                _state.update {
                    it.copy(
                        error = if (exception == UnauthorizedException) {
                            "Invalid API Key"
                        } else {
                            "Something went wrong"
                        },
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
