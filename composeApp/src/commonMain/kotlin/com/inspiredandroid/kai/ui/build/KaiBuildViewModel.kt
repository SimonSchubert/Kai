package com.inspiredandroid.kai.ui.build

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.KaiBuildController
import com.inspiredandroid.kai.build.KaiBuildState
import com.inspiredandroid.kai.build.terminal.TerminalKey
import com.inspiredandroid.kai.build.terminal.TerminalKeyEncoder
import com.inspiredandroid.kai.build.terminal.TerminalModifiers
import com.inspiredandroid.kai.data.DataRepository
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

@Immutable
data class KaiBuildUiState(
    val build: KaiBuildState = KaiBuildState(),
    /** Agents ticked on the setup screen, before Debian exists. */
    val selectedAgents: ImmutableSet<String> = persistentSetOf(),
    /** Agent a project opens with — null means a plain shell. */
    val launchAgentId: String? = null,
    /** Non-null while the user is inside a project's terminal. */
    val openProject: String? = null,
)

class KaiBuildViewModel(
    private val controller: KaiBuildController,
    private val dataRepository: DataRepository,
) : ViewModel() {

    private val selectedAgents = MutableStateFlow<ImmutableSet<String>>(persistentSetOf())
    private val launchAgentId = MutableStateFlow(dataRepository.getKaiBuildLaunchAgent())
    private val openProject = MutableStateFlow<String?>(null)

    val uiState: StateFlow<KaiBuildUiState> = combine(
        controller.state,
        selectedAgents,
        launchAgentId,
        openProject,
    ) { build, selected, launchAgent, project ->
        KaiBuildUiState(
            build = build,
            selectedAgents = selected,
            launchAgentId = launchAgent.installedIn(build),
            openProject = project,
        )
    // viewModelScope is Main.immediate, so without this the merge runs on the
    // main thread once per terminal repaint.
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = KaiBuildUiState(
            build = controller.state.value,
            launchAgentId = launchAgentId.value.installedIn(controller.state.value),
        ),
    )

    init {
        controller.refresh()
    }

    /**
     * Drops a remembered agent that the environment no longer has — the choice
     * outlives an uninstall, and starting a session on a binary that is gone
     * would only print "command not found".
     */
    private fun String?.installedIn(build: KaiBuildState): String? = this?.takeIf { it in build.installedAgents }

    fun toggleAgent(id: String) {
        val current = selectedAgents.value
        selectedAgents.value = if (id in current) {
            (current - id).toImmutableSet()
        } else {
            (current + id).toImmutableSet()
        }
    }

    fun install() = controller.install(selectedAgents.value)

    /** Installs a single agent into an environment that is already set up. */
    fun installAgent(id: String) {
        selectedAgents.value = (selectedAgents.value + id).toImmutableSet()
        controller.install(setOf(id))
    }

    fun cancel() = controller.cancel()
    fun uninstall() = controller.uninstall()

    /**
     * Picks what the next opened project starts with; null is a plain shell.
     * Remembered across app runs — the row is a preference, not a per-visit pick.
     */
    fun setLaunchAgent(agentId: String?) {
        launchAgentId.value = agentId
        dataRepository.setKaiBuildLaunchAgent(agentId)
    }

    /** Opens the project on the shells it already had, or starts its first one. */
    fun openProject(name: String) {
        openProject.value = name
        if (!controller.resumeProject(name)) {
            controller.startSession(name, launchAgentId.value.installedIn(controller.state.value))
        }
    }

    /** Steps back to the list; the project's shells keep running until closed. */
    fun closeProject() {
        openProject.value?.let(controller::leaveProject)
        openProject.value = null
    }

    fun createProject(name: String) {
        controller.createProject(name)?.let(::openProject)
    }

    /** Both are offered from the list only, so neither can pull the ground from under an open project. */
    fun deleteProject(name: String) = controller.deleteProject(name)

    fun renameProject(name: String, newName: String) {
        controller.renameProject(name, newName)
    }

    /** Opens another session in the project the user is already in. */
    fun startSession(agentId: String?) {
        val project = openProject.value ?: return
        controller.startSession(project, agentId)
    }

    fun selectSession(id: String) = controller.selectSession(id)

    /** Closes one tab; closing the last one of the project steps back to the list. */
    fun closeSession(id: String) {
        controller.closeSession(id)
        val project = openProject.value ?: return
        if (controller.state.value.sessions.none { it.project == project }) {
            openProject.value = null
        }
    }

    /** Submits a finished input line (caller includes the trailing carriage return). */
    fun submitLine(text: String) {
        if (openProject.value == null) return
        controller.writeToTerminal(text)
    }

    /** Sends one key press from the key row (arrows, Esc, Ctrl+C, Shift+Tab, …). */
    fun sendKey(key: TerminalKey, modifiers: TerminalModifiers) {
        if (openProject.value == null) return
        val encoded = TerminalKeyEncoder.encode(
            key = key,
            modifiers = modifiers,
            applicationCursorKeys = controller.state.value.activeSession?.terminal?.applicationCursorKeys ?: false,
        )
        controller.writeToTerminal(encoded)
    }

    /** Sends characters typed on the soft or hardware keyboard, one press at a time. */
    fun sendText(text: String, modifiers: TerminalModifiers) {
        if (openProject.value == null || text.isEmpty()) return
        controller.writeToTerminal(TerminalKeyEncoder.encodeText(text, modifiers))
    }

    /**
     * Sends a mouse report for a touch on the cell grid. Already encoded by the
     * grid, which is the only place that knows which cell was under the finger.
     */
    fun sendMouse(report: String) {
        if (openProject.value == null || report.isEmpty()) return
        controller.writeToTerminal(report)
    }

    fun resizeTerminal(columns: Int, rows: Int) = controller.resizeTerminal(columns, rows)
}
