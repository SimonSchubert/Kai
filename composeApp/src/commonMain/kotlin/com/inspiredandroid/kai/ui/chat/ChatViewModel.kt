package com.inspiredandroid.kai.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.data.DataRepository
import com.inspiredandroid.kai.data.TaskScheduler
import com.inspiredandroid.kai.getBackgroundDispatcher
import com.inspiredandroid.kai.network.toUserMessage
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class ChatViewModel(
    private val dataRepository: DataRepository,
    private val taskScheduler: TaskScheduler,
    private val backgroundDispatcher: CoroutineContext = getBackgroundDispatcher(),
) : ViewModel() {

    private val actions = ChatActions(
        ask = ::ask,
        retry = ::retry,
        toggleSpeechOutput = ::toggleSpeechOutput,
        clearHistory = ::clearHistory,
        setIsSpeaking = ::setIsSpeaking,
        setFile = ::setFile,
        startNewChat = ::startNewChat,
        regenerate = ::regenerate,
    )
    private val _state = MutableStateFlow(
        ChatUiState(
            actions = actions,
            showPrivacyInfo = true,
        ),
    )

    init {
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.loadConversations()
            dataRepository.restoreLatestConversation()
        }
        taskScheduler.start(viewModelScope) { _state.value.isLoading }
    }

    val state = combine(
        _state,
        dataRepository.chatHistory,
    ) { state, history ->
        state.copy(
            history = history,
            allowFileAttachment = dataRepository.supportsFileAttachment(),
        )
    }.distinctUntilChanged().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _state.value,
    )

    private fun ask(question: String?) {
        // Prevent concurrent requests
        if (_state.value.isLoading) return

        // Capture file before launching coroutine to avoid race with setFile(null)
        val file = _state.value.file

        viewModelScope.launch(backgroundDispatcher) {
            _state.update {
                it.copy(
                    isLoading = true,
                    error = null,
                )
            }
            try {
                dataRepository.ask(question, file)
                _state.update {
                    it.copy(isLoading = false)
                }
            } catch (exception: Exception) {
                // CancellationException must be re-thrown to properly propagate coroutine cancellation
                if (exception is CancellationException) throw exception

                val errorMessage = exception.toUserMessage()
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

    private fun regenerate() {
        dataRepository.regenerate()
        ask(null)
    }

    private fun startNewChat() {
        dataRepository.startNewChat()
        _state.update {
            it.copy(error = null)
        }
    }

    fun refreshSettings() {
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.restoreLatestConversation()
        }
    }
}
