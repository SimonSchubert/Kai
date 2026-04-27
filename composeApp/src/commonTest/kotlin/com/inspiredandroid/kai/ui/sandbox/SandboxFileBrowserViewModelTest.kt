package com.inspiredandroid.kai.ui.sandbox

import app.cash.turbine.test
import com.inspiredandroid.kai.CommandHandle
import com.inspiredandroid.kai.NoOpCommandHandle
import com.inspiredandroid.kai.SandboxController
import com.inspiredandroid.kai.SandboxFileEntry
import com.inspiredandroid.kai.SandboxStatus
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.sandbox_files_delete_failed
import kai.composeapp.generated.resources.sandbox_files_delete_success
import kai.composeapp.generated.resources.sandbox_files_editor_closed_after_delete
import kai.composeapp.generated.resources.sandbox_files_rename_error_collision
import kai.composeapp.generated.resources.sandbox_files_rename_error_invalid
import kai.composeapp.generated.resources.sandbox_files_rename_success
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SandboxFileBrowserViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var controller: FakeFileBrowserController

    private class FakeFileBrowserController : SandboxController {
        override val status = MutableStateFlow(SandboxStatus())

        val entriesByPath = mutableMapOf<String, MutableList<SandboxFileEntry>>()
        val files = mutableMapOf<String, String>() // path -> text content
        var deleteResult: Boolean = true
        var renameResult: Result<String>? = null

        var lastDeleteCall: Pair<String, Boolean>? = null
        var lastRenameCall: Pair<String, String>? = null

        override fun setup() {}
        override fun cancel() {}
        override fun reset() {}
        override fun installPackages() {}
        override suspend fun executeCommand(command: String): String = ""
        override suspend fun executeCommandStreaming(
            command: String,
            onStdout: (String) -> Unit,
            onStderr: (String) -> Unit,
        ): CommandHandle = NoOpCommandHandle

        override suspend fun listDirectory(path: String): List<SandboxFileEntry> = entriesByPath[path]?.toList().orEmpty()

        override suspend fun readTextFile(path: String, maxBytes: Int): String? = files[path]
        override suspend fun writeTextFile(path: String, content: String): Boolean {
            files[path] = content
            return true
        }
        override suspend fun openFile(path: String): Result<Unit> = Result.success(Unit)

        override suspend fun deleteEntry(path: String, recursive: Boolean): Boolean {
            lastDeleteCall = path to recursive
            if (deleteResult) {
                files.remove(path)
                entriesByPath.values.forEach { it.removeAll { entry -> entry.path == path } }
            }
            return deleteResult
        }

        override suspend fun renameEntry(path: String, newName: String): Result<String> {
            lastRenameCall = path to newName
            val override = renameResult
            if (override != null) return override
            val parent = path.substringBeforeLast('/', "")
            val newPath = if (parent.isEmpty()) "/$newName" else "$parent/$newName"
            files[path]?.let { content ->
                files.remove(path)
                files[newPath] = content
            }
            entriesByPath.values.forEach { list ->
                list.replaceAll { entry ->
                    if (entry.path == path) entry.copy(name = newName, path = newPath) else entry
                }
            }
            return Result.success(newPath)
        }
    }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        controller = FakeFileBrowserController()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fileEntry(name: String, parent: String = "/root"): SandboxFileEntry {
        val path = if (parent == "/") "/$name" else "$parent/$name"
        return SandboxFileEntry(name = name, path = path, isDirectory = false, sizeBytes = 0, lastModifiedMs = 0)
    }

    private fun dirEntry(name: String, parent: String = "/root"): SandboxFileEntry {
        val path = if (parent == "/") "/$name" else "$parent/$name"
        return SandboxFileEntry(name = name, path = path, isDirectory = true, sizeBytes = 0, lastModifiedMs = 0)
    }

    private fun seedDir(path: String, vararg entries: SandboxFileEntry) {
        controller.entriesByPath[path] = entries.toMutableList()
    }

    @Test
    fun `requestDelete sets pendingDelete and cancelDelete clears it`() = runTest {
        val entry = fileEntry("a.txt")
        seedDir("/root", entry)
        val vm = SandboxFileBrowserViewModel(controller)
        vm.start("/root")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.requestDelete(entry)
        assertEquals(entry, vm.state.value.pendingDelete)

        vm.cancelDelete()
        assertNull(vm.state.value.pendingDelete)
    }

    @Test
    fun `confirmDelete on file calls controller with recursive false and refreshes`() = runTest {
        val entry = fileEntry("a.txt")
        seedDir("/root", entry)
        val vm = SandboxFileBrowserViewModel(controller)
        vm.start("/root")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.requestDelete(entry)
        vm.confirmDelete()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(entry.path to false, controller.lastDeleteCall)
        assertTrue(vm.state.value.entries.none { it.path == entry.path })
        assertEquals(Res.string.sandbox_files_delete_success, vm.state.value.snackbarMessage)
        assertNull(vm.state.value.pendingDelete)
    }

    @Test
    fun `confirmDelete on directory calls controller with recursive true`() = runTest {
        val entry = dirEntry("subdir")
        seedDir("/root", entry)
        val vm = SandboxFileBrowserViewModel(controller)
        vm.start("/root")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.requestDelete(entry)
        vm.confirmDelete()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(entry.path to true, controller.lastDeleteCall)
    }

    @Test
    fun `confirmDelete clears editor and shows dedicated snackbar when deleted file is open`() = runTest {
        val entry = fileEntry("a.txt")
        seedDir("/root", entry)
        controller.files[entry.path] = "hello"
        val vm = SandboxFileBrowserViewModel(controller)
        vm.start("/root")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.openEntry(entry)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.editor is EditorState.Loaded)

        vm.requestDelete(entry)
        vm.confirmDelete()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.state.value.editor)
        assertEquals(Res.string.sandbox_files_editor_closed_after_delete, vm.state.value.snackbarMessage)
    }

    @Test
    fun `confirmDelete failure surfaces failed snackbar and keeps list`() = runTest {
        val entry = fileEntry("a.txt")
        seedDir("/root", entry)
        controller.deleteResult = false
        val vm = SandboxFileBrowserViewModel(controller)
        vm.start("/root")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.requestDelete(entry)
        vm.confirmDelete()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(Res.string.sandbox_files_delete_failed, vm.state.value.snackbarMessage)
        assertTrue(vm.state.value.entries.any { it.path == entry.path })
    }

    @Test
    fun `requestRename seeds input with entry name`() = runTest {
        val entry = fileEntry("notes.md")
        seedDir("/root", entry)
        val vm = SandboxFileBrowserViewModel(controller)
        vm.start("/root")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.requestRename(entry)

        val rename = vm.state.value.renaming
        assertNotNull(rename)
        assertEquals("notes.md", rename.input)
        assertEquals(entry, rename.originalEntry)
    }

    @Test
    fun `confirmRename success refreshes list and shows success snackbar`() = runTest {
        val entry = fileEntry("a.txt")
        seedDir("/root", entry)
        val vm = SandboxFileBrowserViewModel(controller)
        vm.start("/root")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.requestRename(entry)
        vm.updateRenameInput("b.txt")
        vm.confirmRename()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(entry.path to "b.txt", controller.lastRenameCall)
        assertNull(vm.state.value.renaming)
        assertEquals(Res.string.sandbox_files_rename_success, vm.state.value.snackbarMessage)
        assertTrue(vm.state.value.entries.any { it.name == "b.txt" })
    }

    @Test
    fun `confirmRename of currently-open file updates editor path without reload`() = runTest {
        val entry = fileEntry("a.txt")
        seedDir("/root", entry)
        controller.files[entry.path] = "hello"
        val vm = SandboxFileBrowserViewModel(controller)
        vm.start("/root")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.openEntry(entry)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.updateEditorContent("hello dirty")
        assertTrue((vm.state.value.editor as EditorState.Loaded).dirty)

        vm.requestRename(entry)
        vm.updateRenameInput("b.txt")
        vm.confirmRename()
        testDispatcher.scheduler.advanceUntilIdle()

        val editor = vm.state.value.editor
        assertTrue(editor is EditorState.Loaded)
        assertEquals("/root/b.txt", editor.path)
        assertEquals("hello dirty", editor.current) // dirty edits preserved
    }

    @Test
    fun `confirmRename collision sets error on renaming state and keeps dialog open`() = runTest {
        val entry = fileEntry("a.txt")
        seedDir("/root", entry)
        controller.renameResult = Result.failure(IllegalStateException("collision"))
        val vm = SandboxFileBrowserViewModel(controller)
        vm.start("/root")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.requestRename(entry)
        vm.updateRenameInput("b.txt")
        vm.confirmRename()
        testDispatcher.scheduler.advanceUntilIdle()

        val rename = vm.state.value.renaming
        assertNotNull(rename)
        assertEquals(Res.string.sandbox_files_rename_error_collision, rename.error)
    }

    @Test
    fun `confirmRename invalid name short-circuits without controller call`() = runTest {
        val entry = fileEntry("a.txt")
        seedDir("/root", entry)
        val vm = SandboxFileBrowserViewModel(controller)
        vm.start("/root")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.requestRename(entry)
        vm.updateRenameInput("with/slash")
        vm.confirmRename()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(controller.lastRenameCall)
        val rename = vm.state.value.renaming
        assertNotNull(rename)
        assertEquals(Res.string.sandbox_files_rename_error_invalid, rename.error)
    }

    @Test
    fun `state stream emits expected snackbar transitions on delete`() = runTest {
        val entry = fileEntry("a.txt")
        seedDir("/root", entry)
        val vm = SandboxFileBrowserViewModel(controller)
        vm.start("/root")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.state.test {
            // initial state with seeded entry
            val current = awaitItem()
            assertTrue(current.entries.any { it.path == entry.path })

            vm.requestDelete(entry)
            assertEquals(entry, awaitItem().pendingDelete)

            vm.confirmDelete()
            testDispatcher.scheduler.advanceUntilIdle()
            // multiple updates may emit; consume until we see the snackbar
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(Res.string.sandbox_files_delete_success, vm.state.value.snackbarMessage)
    }
}
