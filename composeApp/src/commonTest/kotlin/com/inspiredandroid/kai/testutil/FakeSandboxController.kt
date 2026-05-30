package com.inspiredandroid.kai.testutil

import com.inspiredandroid.kai.CommandHandle
import com.inspiredandroid.kai.NoOpCommandHandle
import com.inspiredandroid.kai.SandboxController
import com.inspiredandroid.kai.SandboxFileEntry
import com.inspiredandroid.kai.SandboxStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory [SandboxController] for tests. Backs the file ops with a flat
 * path→content map (directories are implicit from path prefixes), which is all
 * [com.inspiredandroid.kai.skills.SkillManager] needs to store and read skills.
 */
class FakeSandboxController(installed: Boolean = true) : SandboxController {

    val files = mutableMapOf<String, String>()

    private val _status = MutableStateFlow(SandboxStatus(installed = installed, ready = installed))
    override val status: StateFlow<SandboxStatus> = _status
    override val sessions: StateFlow<List<String>> = MutableStateFlow(emptyList())

    override fun setup() {}
    override fun cancel() {}
    override fun reset() {}
    override fun installPackages() {}

    override suspend fun executeCommand(command: String, sessionId: String): String = ""
    override suspend fun executeCommandStreaming(
        command: String,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
        sessionId: String,
    ): CommandHandle = NoOpCommandHandle

    override suspend fun listDirectory(path: String): List<SandboxFileEntry> {
        val prefix = if (path.endsWith("/")) path else "$path/"
        val children = linkedMapOf<String, Boolean>() // name -> isDirectory
        for (p in files.keys) {
            if (!p.startsWith(prefix)) continue
            val rest = p.removePrefix(prefix)
            val slash = rest.indexOf('/')
            if (slash < 0) {
                children[rest] = false // direct file
            } else {
                val dir = rest.substring(0, slash)
                if (children[dir] != false) children[dir] = true
            }
        }
        return children.map { (name, isDir) ->
            SandboxFileEntry(name = name, path = "$prefix$name", isDirectory = isDir, sizeBytes = 0, lastModifiedMs = 0)
        }
    }

    override suspend fun readTextFile(path: String, maxBytes: Int): String? = files[path]

    override suspend fun writeTextFile(path: String, content: String): Boolean {
        files[path] = content
        return true
    }

    override suspend fun openFile(path: String): Result<Unit> = Result.success(Unit)

    override suspend fun deleteEntry(path: String, recursive: Boolean): Boolean {
        val prefix = "$path/"
        val toRemove = files.keys.filter { it == path || (recursive && it.startsWith(prefix)) }
        toRemove.forEach { files.remove(it) }
        return toRemove.isNotEmpty()
    }

    override suspend fun renameEntry(path: String, newName: String): Result<String> = Result.success(path)
}
