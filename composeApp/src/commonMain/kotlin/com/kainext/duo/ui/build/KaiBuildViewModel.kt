package com.kainext.duo.ui.build

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kainext.duo.KaiBuildController
import com.kainext.duo.TerminalLine
import com.kainext.duo.data.DataRepository
import kotlinx.coroutines.launch

class KaiBuildViewModel(
    private val controller: KaiBuildController,
    private val dataRepository: DataRepository,
) : ViewModel() {
    val state = controller.state
    fun install(agentIds: Set<String>) = viewModelScope.launch { controller.install(agentIds) }
    fun cancel() = controller.cancel()
    fun uninstall() = viewModelScope.launch { controller.uninstall() }
    fun refresh() = viewModelScope.launch { controller.refresh() }
    fun createProject(name: String) = controller.createProject(name)
    fun deleteProject(name: String) = viewModelScope.launch { controller.deleteProject(name) }
    fun renameProject(name: String, newName: String) = controller.renameProject(name, newName)
    fun startSession(project: String, agentId: String?) = controller.startSession(project, agentId)
    fun selectSession(id: String) = controller.selectSession(id)
    fun closeSession(id: String) = controller.closeSession(id)
    fun resumeProject(project: String) = controller.resumeProject(project)
    fun leaveProject(project: String) = controller.leaveProject(project)
    fun writeToTerminal(text: String) = controller.writeToTerminal(text)
    fun resizeTerminal(columns: Int, rows: Int) = controller.resizeTerminal(columns, rows)
    fun transcriptFor(sessionId: String): SnapshotStateList<TerminalLine> = controller.transcriptFor(sessionId)
}
