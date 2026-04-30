package com.inspiredandroid.kai.ui.sandbox

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.CommandHandle
import com.inspiredandroid.kai.SandboxController
import com.inspiredandroid.kai.SandboxSessions
import com.inspiredandroid.kai.TerminalLine
import com.inspiredandroid.kai.data.DataRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class SessionTab(
    val id: String,
    val label: String,
    val isTerminal: Boolean,
)

class SandboxSessionViewModel(
    private val sandboxController: SandboxController,
    private val dataRepository: DataRepository,
) : ViewModel() {

    /**
     * Per-session UI state held entirely in the VM. The output buffer lives on
     * the manager (see [SandboxController.transcriptFor]) so commands the agent
     * runs through the chat tool show up here too.
     */
    private class SessionState {
        var inputText: String = ""
        var isRunning: Boolean = false
        var activeHandle: CommandHandle? = null
    }

    private val statesMap = mutableMapOf<String, SessionState>()

    /** Stable monotonic numbers for chat-shell chip labels. Never reused. */
    private val sessionNumbers = mutableMapOf<String, Int>()
    private var nextSessionNumber = 1

    private val selectedTabState = MutableStateFlow(SandboxSubTab.Terminal)
    internal val selectedTab = selectedTabState.asStateFlow()

    private val _selectedSessionId = MutableStateFlow(SandboxSessions.TERMINAL)
    val selectedSessionId = _selectedSessionId.asStateFlow()

    private val _visibleSessions = MutableStateFlow<List<SessionTab>>(
        listOf(SessionTab(SandboxSessions.TERMINAL, "Terminal", isTerminal = true)),
    )
    val visibleSessions = _visibleSessions.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText = _inputText.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val _activeHandle = MutableStateFlow<CommandHandle?>(null)
    val activeHandle = _activeHandle.asStateFlow()

    val outputLines: SnapshotStateList<TerminalLine>
        get() = sandboxController.transcriptFor(_selectedSessionId.value)

    init {
        sessionState(SandboxSessions.TERMINAL)

        // First open from a chat: bias the initial selection toward that chat's
        // shell so the user sees the same state the agent is operating on.
        val initialChatId = dataRepository.currentConversationId.value
        if (initialChatId != null) selectSession(initialChatId)

        viewModelScope.launch {
            combine(
                sandboxController.sessions,
                dataRepository.savedConversations,
            ) { activeIds, conversations ->
                buildVisibleSessions(activeIds, conversations)
            }.collect { tabs ->
                _visibleSessions.value = tabs
                if (tabs.none { it.id == _selectedSessionId.value }) {
                    selectSession(SandboxSessions.TERMINAL)
                }
            }
        }
    }

    private fun buildVisibleSessions(
        activeIds: List<String>,
        conversations: List<com.inspiredandroid.kai.data.Conversation>,
    ): List<SessionTab> {
        val terminal = SessionTab(SandboxSessions.TERMINAL, "Terminal", isTerminal = true)
        val chatTabs = activeIds
            .filter {
                it != SandboxSessions.TERMINAL &&
                    it != SandboxSessions.SYSTEM &&
                    it != SandboxSessions.DEFAULT
            }
            .map { id -> SessionTab(id = id, label = "#${numberFor(id)}", isTerminal = false) }
        return listOf(terminal) + chatTabs
    }

    private fun numberFor(id: String): Int =
        sessionNumbers.getOrPut(id) { nextSessionNumber++ }

    private fun sessionState(id: String): SessionState =
        statesMap.getOrPut(id) { SessionState() }

    internal fun selectTab(tab: SandboxSubTab) {
        selectedTabState.value = tab
    }

    fun selectSession(id: String) {
        // Save the live flow values into the *previous* session before switching,
        // so input the user typed doesn't get lost.
        val prev = sessionState(_selectedSessionId.value)
        prev.inputText = _inputText.value

        val target = sessionState(id)
        _selectedSessionId.value = id
        _inputText.value = target.inputText
        _isRunning.value = target.isRunning
        _activeHandle.value = target.activeHandle
    }

    fun setInputText(text: String) {
        _inputText.value = text
        sessionState(_selectedSessionId.value).inputText = text
    }

    fun submit() {
        val sid = _selectedSessionId.value
        val s = sessionState(sid)
        val line = _inputText.value
        if (line.isBlank()) return
        _inputText.value = ""
        s.inputText = ""
        if (s.isRunning && s.activeHandle != null) {
            // Echo what the user typed so they can see they were heard. The
            // shell will swallow the line as stdin to the foreground command,
            // so it won't otherwise appear in the transcript.
            sandboxController.transcriptFor(sid).add(TerminalLine.Output(line))
            val handle = s.activeHandle ?: return
            viewModelScope.launch { handle.writeInput(line) }
        } else if (!s.isRunning) {
            viewModelScope.launch { runCommand(sid, s, line.trim()) }
        }
    }

    fun cancelRunning() {
        sessionState(_selectedSessionId.value).activeHandle?.cancel()
    }

    private suspend fun runCommand(sessionId: String, s: SessionState, command: String) {
        if (command == "clear") {
            sandboxController.clearTranscript(sessionId)
            return
        }
        s.isRunning = true
        if (sessionId == _selectedSessionId.value) _isRunning.value = true

        try {
            coroutineScope {
                val handle = sandboxController.executeCommandStreaming(
                    command = command,
                    onStdout = { /* transcript is populated by the shell wrapper */ },
                    onStderr = { /* transcript is populated by the shell wrapper */ },
                    sessionId = sessionId,
                )
                s.activeHandle = handle
                if (sessionId == _selectedSessionId.value) _activeHandle.value = handle
                handle.awaitExit()
                if (handle.isCancelled()) {
                    sandboxController.transcriptFor(sessionId).add(TerminalLine.Output("^C"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            sandboxController.transcriptFor(sessionId)
                .add(TerminalLine.Error(e.message ?: "Command failed"))
        } finally {
            s.activeHandle = null
            if (sessionId == _selectedSessionId.value) _activeHandle.value = null
            s.isRunning = false
            if (sessionId == _selectedSessionId.value) _isRunning.value = false
        }
    }

    override fun onCleared() {
        statesMap.values.forEach { it.activeHandle?.cancel() }
        super.onCleared()
    }
}
