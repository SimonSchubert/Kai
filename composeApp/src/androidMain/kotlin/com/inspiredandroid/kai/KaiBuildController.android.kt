package com.inspiredandroid.kai

import android.content.Context
import com.inspiredandroid.kai.build.KaiBuildState
import com.inspiredandroid.kai.build.runtime.BuildEnvironmentManager
import com.inspiredandroid.kai.build.runtime.BuildFileBrowser
import com.inspiredandroid.kai.linux.LinuxDistro
import com.inspiredandroid.kai.linux.LinuxPaths
import com.inspiredandroid.kai.sandbox.LinuxSandboxManager
import kotlinx.coroutines.flow.StateFlow
import org.koin.java.KoinJavaComponent.inject

actual fun createKaiBuildController(): KaiBuildController = AndroidKaiBuildController()

class AndroidKaiBuildController : KaiBuildController {

    private val context: Context by inject(Context::class.java)
    private val sandboxManager: LinuxSandboxManager by inject(LinuxSandboxManager::class.java)

    /**
     * Kai Build needs Debian. When the chat sandbox is Debian too — the default —
     * both work in the *same* install, so a Linux set up from either surface is
     * immediately there for the other. An Alpine sandbox cannot host the coding
     * agents, so Kai Build falls back to bootstrapping a Debian of its own.
     *
     * Resolved once per process: switching the sandbox distro requires an
     * uninstall and reinstall anyway, and re-pointing live PTY sessions at a
     * different rootfs mid-session is not something to do behind the user's back.
     */
    private val paths: LinuxPaths by lazy {
        val sandbox = sandboxManager.paths
        if (canShareSandbox()) sandbox else LinuxPaths.forBuild(context)
    }

    private fun canShareSandbox(): Boolean {
        val installed = sandboxManager.paths.readMarker()?.distro
        // Nothing installed yet: follow the chat sandbox's pending choice, so
        // picking Debian there and then opening Kai Build still shares one rootfs.
        return (installed ?: sandboxManager.distro) == LinuxDistro.DEBIAN
    }

    private val manager: BuildEnvironmentManager by lazy {
        BuildEnvironmentManager(paths).also { built ->
            if (paths.root == sandboxManager.paths.root) {
                // A sandbox reset deletes the rootfs; sessions holding file
                // descriptors into it have to go first.
                sandboxManager.onBeforeReset = { built.onEnvironmentRemoved() }
                // And the other direction: installing Debian from Kai Build's
                // setup screen is what gives the chat sandbox its Linux.
                built.onEnvironmentChanged = { sandboxManager.refreshInstallState() }
            }
        }
    }

    override val state: StateFlow<KaiBuildState> get() = manager.state
    override val files: FileBrowserSource by lazy { BuildFileBrowser(context, paths) }

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
