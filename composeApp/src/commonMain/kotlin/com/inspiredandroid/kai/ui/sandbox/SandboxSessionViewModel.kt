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
import kotlinx.coroutines.launch

data class SessionTab(
    val id: String,
    /** True for the standalone scratch shell ("Temporary"); false for a chat-bound shell ("Session"). */
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

    private val selectedTabState = MutableStateFlow(SandboxSubTab.Terminal)
    internal val selectedTab = selectedTabState.asStateFlow()

    private val _selectedSessionId = MutableStateFlow(SandboxSessions.TERMINAL)
    val selectedSessionId = _selectedSessionId.asStateFlow()

    private val _visibleSessions = MutableStateFlow<List<SessionTab>>(
        listOf(SessionTab(SandboxSessions.TERMINAL, isTerminal = true)),
    )
    val visibleSessions = _visibleSessions.asStateFlow()

    /** Pulse that fires when a session is (re)selected — used by the terminal UI to jump to the tail. */
    private val _scrollToEndPulse = MutableStateFlow(0L)
    val scrollToEndPulse = _scrollToEndPulse.asStateFlow()

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
            dataRepository.currentConversationId.collect { currentChatId ->
                val tabs = buildVisibleSessions(currentChatId)
                _visibleSessions.value = tabs
                if (currentChatId != null && currentChatId.isNotBlank()) {
                    // Terminal follows the active chat. Users who want the
                    // scratch tab can pick Temporary explicitly.
                    if (_selectedSessionId.value != currentChatId) {
                        selectSession(currentChatId)
                    }
                } else if (tabs.none { it.id == _selectedSessionId.value }) {
                    selectSession(SandboxSessions.TERMINAL)
                }
            }
        }
    }

    private fun buildVisibleSessions(currentChatId: String?): List<SessionTab> {
        val temporaryTab = SessionTab(SandboxSessions.TERMINAL, isTerminal = true)
        val sessionTab = currentChatId?.takeIf { it.isNotBlank() }
            ?.let { SessionTab(it, isTerminal = false) }
        // Session first so the user's chat shell is the dominant entry; scratch
        // is offered alongside.
        return listOfNotNull(sessionTab, temporaryTab)
    }

    private fun sessionState(id: String): SessionState = statesMap.getOrPut(id) { SessionState() }

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
        _scrollToEndPulse.value = _scrollToEndPulse.value + 1
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
