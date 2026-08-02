package com.inspiredandroid.kai

import android.content.Context
import com.inspiredandroid.kai.build.KaiBuildState
import com.inspiredandroid.kai.build.runtime.BuildEnvironmentManager
import com.inspiredandroid.kai.build.runtime.BuildFileBrowser
import com.inspiredandroid.kai.build.runtime.BuildPaths
import kotlinx.coroutines.flow.StateFlow
import org.koin.java.KoinJavaComponent.inject

actual fun createKaiBuildController(): KaiBuildController = AndroidKaiBuildController()

class AndroidKaiBuildController : KaiBuildController {

    private val context: Context by inject(Context::class.java)
    private val manager: BuildEnvironmentManager by lazy { BuildEnvironmentManager(context) }

    override val state: StateFlow<KaiBuildState> get() = manager.state
    override val files: FileBrowserSource by lazy { BuildFileBrowser(context, BuildPaths(context)) }

    override fun install(agentIds: Set<String>) = manager.install(agentIds)
    override fun cancel() = manager.cancel()
    override fun uninstall() = manager.uninstall()
    override fun refresh() = manager.refresh()
    override fun createProject(name: String): String? = manager.createProject(name)
    override fun startSession(project: String, agentId: String?) = manager.startSession(project, agentId)
    override fun selectSession(id: String) = manager.selectSession(id)
    override fun closeSession(id: String) = manager.closeSession(id)
    override fun resumeProject(project: String): Boolean = manager.resumeProject(project)
    override fun leaveProject(project: String) = manager.leaveProject(project)
    override fun writeToTerminal(text: String) = manager.writeToTerminal(text)
    override fun resizeTerminal(columns: Int, rows: Int) = manager.resizeTerminal(columns, rows)
}
