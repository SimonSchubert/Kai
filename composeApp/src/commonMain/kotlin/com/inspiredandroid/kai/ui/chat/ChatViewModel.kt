package com.inspiredandroid.kai.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.data.Conversation
import com.inspiredandroid.kai.data.DataRepository
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.data.TaskScheduler
import com.inspiredandroid.kai.getBackgroundDispatcher
import com.inspiredandroid.kai.network.toUiError
import com.inspiredandroid.kai.ui.dynamicui.KaiUiParser
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.mimeType
import io.github.vinceglb.filekit.name
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.conversation_untitled
import kai.composeapp.generated.resources.error_unsupported_file_type
import kai.composeapp.generated.resources.litert_no_model_warning
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
        submitUiCallback = ::submitUiCallback,
        enterInteractiveMode = ::enterInteractiveMode,
        exitInteractiveMode = ::exitInteractiveMode,
        goBackInteractiveMode = ::goBackInteractiveMode,
    )
    private var currentJob: Job? = null
    private var pendingConversationDeleteJob: Job? = null
    private val _state = MutableStateFlow(
        ChatUiState(
            actions = actions,
            showPrivacyInfo = dataRepository.isUsingSharedKey(),
        ),
    )

    init {
        // Synchronous restore so the first composition has the correct interactive mode.
        // Property initializers and init blocks run in declaration order; the `state`
        // property below captures `_state.value` as its initial value, so updating
        // _state here ensures no flash between ChatModeScreen and InteractiveModeScreen.
        updateAvailableServices()
        dataRepository.loadConversations()
        dataRepository.restoreCurrentConversation()
        presetInteractiveModeForCurrentConversation()

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
                val isInteractive = it.type == Conversation.TYPE_INTERACTIVE
                ConversationSummary(
                    id = it.id,
                    title = if (isHeartbeat) "" else it.title.ifEmpty { getString(Res.string.conversation_untitled) },
                    updatedAt = it.updatedAt,
                    isHeartbeat = isHeartbeat,
                    isInteractive = isInteractive,
                )
            }
        state.copy(
            history = history.toImmutableList(),
            supportedFileExtensions = dataRepository.supportedFileExtensions().toImmutableList(),
            savedConversations = summaries.toImmutableList(),
            currentConversationId = conversationId,
            hasUnreadHeartbeat = hasUnreadHeartbeat,
        )
    }.distinctUntilChanged().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _state.value,
    )

    private fun submitUiCallback(event: String, data: Map<String, String>) {
        val message = if (data.isNotEmpty()) {
            val formattedData = data.entries.joinToString(", ") { "${it.key}: ${it.value}" }
            "Responded with: $formattedData"
        } else {
            "Pressed: $event"
        }
        ask(message)
    }

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

                // Auto-retry in interactive mode if the response has no valid kai-ui
                if (_state.value.isInteractiveMode) {
                    retryIfNoValidKaiUi()
                }

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

    private suspend fun retryIfNoValidKaiUi(maxRetries: Int = 2) {
        repeat(maxRetries) {
            currentCoroutineContext().ensureActive()
            val lastAssistant = dataRepository.chatHistory.value
                .lastOrNull { it.role == History.Role.ASSISTANT && it.content.isNotEmpty() && !it.isThinking }
                ?: return

            val segments = KaiUiParser.parse(lastAssistant.content)
            val hasValidUi = segments.any { it is KaiUiParser.UiSegment }
            if (hasValidUi) return

            // Build error feedback for the AI
            val errorSegment = segments.filterIsInstance<KaiUiParser.ErrorSegment>().firstOrNull()
            val errorDetail = if (errorSegment != null) {
                "JSON parse error in: ${errorSegment.rawJson.take(200)}"
            } else {
                "No kai-ui code fence found in your response."
            }
            val retryMessage = "[SYSTEM] Your previous response failed to render as interactive UI. $errorDetail " +
                "Remember: respond with ONLY a single ```kai-ui code fence containing valid JSON. No text outside the fence."

            dataRepository.ask(retryMessage, null)
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
            val ext = file.extension.lowercase()
            if (ext.isEmpty() || ext !in _state.value.supportedFileExtensions) {
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
        updateAvailableServices()
    }

    private fun updateAvailableServices() {
        val entries = dataRepository.getServiceEntries().toImmutableList()
        val primaryService = entries.firstOrNull()?.let { Service.fromId(it.serviceId) }
        val warning = if (primaryService?.isOnDevice == true && dataRepository.getLocalDownloadedModels().isEmpty()) {
            Res.string.litert_no_model_warning
        } else {
            null
        }
        _state.update { it.copy(availableServices = entries, warning = warning, showPrivacyInfo = dataRepository.isUsingSharedKey()) }
    }

    private fun regenerate() {
        dataRepository.regenerate()
        ask(null)
    }

    private fun loadConversation(id: String) {
        currentJob?.cancel()
        currentJob = null
        val conversation = dataRepository.savedConversations.value.find { it.id == id }
        val isInteractive = conversation?.type == Conversation.TYPE_INTERACTIVE
        dataRepository.setInteractiveMode(isInteractive)
        dataRepository.loadConversation(id)
        _state.update {
            it.copy(error = null, isInteractiveMode = isInteractive, isLoading = false)
        }
    }

    private fun deleteConversation(id: String) {
        commitPendingConversationDeletion()
        _state.update { it.copy(pendingConversationDeletion = id) }
        pendingConversationDeleteJob = viewModelScope.launch(backgroundDispatcher) {
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
        currentJob?.cancel()
        currentJob = null
        dataRepository.startNewChat()
        dataRepository.setInteractiveMode(false)
        _state.update {
            it.copy(error = null, isInteractiveMode = false, isLoading = false)
        }
    }

    private fun enterInteractiveMode() {
        dataRepository.startNewChat()
        dataRepository.setInteractiveMode(true)
        _state.update {
            it.copy(isInteractiveMode = true, error = null)
        }
    }

    private fun exitInteractiveMode() {
        currentJob?.cancel()
        currentJob = null
        dataRepository.startNewChat()
        dataRepository.setInteractiveMode(false)
        _state.update {
            it.copy(isInteractiveMode = false, isLoading = false, error = null)
        }
    }

    private fun goBackInteractiveMode() {
        val userCount = dataRepository.chatHistory.value.count { it.role == History.Role.USER }
        if (userCount <= 1) {
            // Go back to initial prompt — clear history but stay in interactive mode
            dataRepository.clearHistory()
        } else {
            dataRepository.popLastExchange()
        }
    }

    fun refreshSettings() {
        updateAvailableServices()
        dataRepository.restoreCurrentConversation()
        presetInteractiveModeForCurrentConversation()
    }

    /**
     * Resolves the interactive mode flag from the currently-loaded conversation, or — when
     * there is no loaded conversation (new empty chat) — falls back to the persisted flag.
     * Runs synchronously so the first composition already has the correct mode.
     */
    private fun presetInteractiveModeForCurrentConversation() {
        val currentId = dataRepository.currentConversationId.value
        val conversation = dataRepository.savedConversations.value.find { it.id == currentId }
        val isInteractive = if (conversation != null) {
            conversation.type == Conversation.TYPE_INTERACTIVE
        } else {
            dataRepository.isInteractiveModeActive()
        }
        dataRepository.setInteractiveMode(isInteractive)
        _state.update { it.copy(isInteractiveMode = isInteractive) }
    }
}
