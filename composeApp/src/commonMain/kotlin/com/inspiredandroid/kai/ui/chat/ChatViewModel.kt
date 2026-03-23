package com.inspiredandroid.kai.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.data.Conversation
import com.inspiredandroid.kai.data.DataRepository
import com.inspiredandroid.kai.data.FileCategory
import com.inspiredandroid.kai.data.TaskScheduler
import com.inspiredandroid.kai.data.classifyFile
import com.inspiredandroid.kai.getBackgroundDispatcher
import com.inspiredandroid.kai.network.toUiError
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.mimeType
import io.github.vinceglb.filekit.name
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.conversation_untitled
import kai.composeapp.generated.resources.error_unsupported_file_type
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
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
        cancel = ::cancel,
        selectService = ::selectService,
        loadConversation = ::loadConversation,
        deleteConversation = ::deleteConversation,
        clearUnreadHeartbeat = ::clearUnreadHeartbeat,
        clearSnackbar = ::clearSnackbar,
        undoDeleteConversation = ::undoDeleteConversation,
    )
    private var currentJob: Job? = null
    private var pendingConversationDeleteJob: Job? = null
    private val _state = MutableStateFlow(
        ChatUiState(
            actions = actions,
            showPrivacyInfo = true,
        ),
    )

    init {
        viewModelScope.launch(backgroundDispatcher) {
            _state.update { it.copy(availableServices = dataRepository.getServiceEntries().toImmutableList()) }
            dataRepository.loadConversations()
            dataRepository.restoreLatestConversation()
        }
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.connectEnabledMcpServers()
        }
        taskScheduler.start(viewModelScope) { _state.value.isLoading }
    }

    val state = combine(
        _state,
        dataRepository.chatHistory,
        dataRepository.savedConversations,
        dataRepository.currentConversationId,
        dataRepository.hasUnreadHeartbeat,
    ) { state, history, conversations, conversationId, hasUnreadHeartbeat ->
        val summaries = conversations
            .sortedByDescending { it.updatedAt }
            .map {
                val isHeartbeat = it.type == Conversation.TYPE_HEARTBEAT
                ConversationSummary(
                    id = it.id,
                    title = if (isHeartbeat) "" else it.title.ifEmpty { getString(Res.string.conversation_untitled) },
                    updatedAt = it.updatedAt,
                    isHeartbeat = isHeartbeat,
                )
            }
        state.copy(
            history = history.toImmutableList(),
            allowFileAttachment = dataRepository.supportsFileAttachment(),
            savedConversations = summaries.toImmutableList(),
            currentConversationId = conversationId,
            hasUnreadHeartbeat = hasUnreadHeartbeat,
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

        currentJob = viewModelScope.launch(backgroundDispatcher) {
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

                _state.update {
                    it.copy(
                        error = exception.toUiError(),
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
        if (file != null) {
            val category = classifyFile(file.mimeType()?.toString(), file.name)
            if (category == FileCategory.UNSUPPORTED) {
                _state.update {
                    it.copy(snackbarMessage = Res.string.error_unsupported_file_type)
                }
                return
            }
        }
        _state.update {
            it.copy(file = file)
        }
    }

    private fun clearSnackbar() {
        _state.update {
            it.copy(snackbarMessage = null)
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

    private fun cancel() {
        currentJob?.cancel()
        currentJob = null
        _state.update {
            it.copy(isLoading = false)
        }
    }

    private fun selectService(instanceId: String) {
        val instances = dataRepository.getConfiguredServiceInstances()
        val currentIds = instances.map { it.instanceId }
        if (instanceId !in currentIds) return
        val reordered = listOf(instanceId) + currentIds.filter { it != instanceId }
        dataRepository.reorderConfiguredServices(reordered)
        _state.update { it.copy(availableServices = dataRepository.getServiceEntries().toImmutableList()) }
    }

    private fun regenerate() {
        dataRepository.regenerate()
        ask(null)
    }

    private fun loadConversation(id: String) {
        dataRepository.loadConversation(id)
        _state.update {
            it.copy(error = null)
        }
    }

    private fun deleteConversation(id: String) {
        commitPendingConversationDeletion()
        _state.update { it.copy(pendingConversationDeletion = id) }
        pendingConversationDeleteJob = viewModelScope.launch {
            delay(4000)
            dataRepository.deleteConversation(id)
            _state.update { it.copy(pendingConversationDeletion = null) }
        }
    }

    private fun undoDeleteConversation() {
        pendingConversationDeleteJob?.cancel()
        pendingConversationDeleteJob = null
        _state.update { it.copy(pendingConversationDeletion = null) }
    }

    private fun commitPendingConversationDeletion() {
        pendingConversationDeleteJob?.cancel()
        pendingConversationDeleteJob = null
        val pendingId = _state.value.pendingConversationDeletion ?: return
        _state.update { it.copy(pendingConversationDeletion = null) }
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.deleteConversation(pendingId)
        }
    }

    override fun onCleared() {
        commitPendingConversationDeletion()
        super.onCleared()
    }

    private fun clearUnreadHeartbeat() {
        dataRepository.clearUnreadHeartbeat()
    }

    private fun startNewChat() {
        dataRepository.startNewChat()
        _state.update {
            it.copy(error = null)
        }
    }

    fun refreshSettings() {
        _state.update { it.copy(availableServices = dataRepository.getServiceEntries().toImmutableList()) }
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.restoreLatestConversation()
        }
    }
}
