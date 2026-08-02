package com.inspiredandroid.kai.ui.sandbox

import app.cash.turbine.test
import com.inspiredandroid.kai.CommandHandle
import com.inspiredandroid.kai.NoOpCommandHandle
import com.inspiredandroid.kai.SandboxController
import com.inspiredandroid.kai.SandboxFileEntry
import com.inspiredandroid.kai.SandboxStatus
import com.inspiredandroid.kai.TextFileResult
import io.github.vinceglb.filekit.PlatformFile
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.sandbox_files_delete_failed
import kai.composeapp.generated.resources.sandbox_files_delete_success
import kai.composeapp.generated.resources.sandbox_files_editor_closed_after_delete
import kai.composeapp.generated.resources.sandbox_files_rename_error_collision
import kai.composeapp.generated.resources.sandbox_files_rename_error_invalid
import kai.composeapp.generated.resources.sandbox_files_rename_success
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SandboxFileBrowserViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var controller: FakeFileBrowserController

    private class FakeFileBrowserController : SandboxController {
        override val status = MutableStateFlow(SandboxStatus())
        override val sessions = MutableStateFlow<List<String>>(emptyList())

        val entriesByPath = mutableMapOf<String, MutableList<SandboxFileEntry>>()
        val files = mutableMapOf<String, String>() // path -> text content
        val listDelays = mutableMapOf<String, Long>() // path -> virtual ms listDirectory takes
        val binaryPaths = mutableSetOf<String>()
        var deleteResult: Boolean = true
        var renameResult: Result<String>? = null

        var lastDeleteCall: Pair<String, Boolean>? = null
        var lastRenameCall: Pair<String, String>? = null

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
            listDelays[path]?.let { delay(it) }
            return entriesByPath[path]?.toList().orEmpty()
        }

        override suspend fun readTextFile(path: String, maxBytes: Int, force: Boolean): TextFileResult {
            val content = files[path] ?: return TextFileResult.Unreadable
            if (content.length > maxBytes) return TextFileResult.TooLarge(content.length.toLong())
            // Stand-in for "not valid UTF-8": forcing decodes it read-only, as on device.
            if (path in binaryPaths) {
                return if (force) TextFileResult.Text(content, editable = false) else TextFileResult.Binary
            }
            return TextFileResult.Text(content)
        }

        override suspend fun writeTextFile(path: String, content: String): Boolean {
            files[path] = content
            return true
        }
        override suspend fun openFile(path: String): Result<Unit> = Result.success(Unit)

        // PlatformFile is an expect class with no common constructor, so the import path
        // is not reachable from commonTest.
        override suspend fun importFile(directoryPath: String, source: PlatformFile): Result<String> = Result.failure(UnsupportedOperationException("Not reachable from commonTest"))

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

    @Test
    fun `start re-lists a directory that changed while the browser was hidden`() = runTest {
        seedDir("/root", fileEntry("a.txt"))
        val vm = SandboxFileBrowserViewModel(controller)
        vm.start("/root")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("a.txt"), vm.state.value.entries.map { it.name })

        // Agent writes a file while the Files tab is off screen.
        controller.entriesByPath.getValue("/root").add(fileEntry("agent.log"))
        vm.start("/root")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("a.txt", "agent.log"), vm.state.value.entries.map { it.name })
    }

    @Test
    fun `start does not emit when the listing is unchanged`() = runTest {
        seedDir("/root", fileEntry("a.txt"))
        val vm = SandboxFileBrowserViewModel(controller)
        vm.start("/root")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.state.test {
            awaitItem()
            vm.start("/root")
            testDispatcher.scheduler.advanceUntilIdle()
            // No state change means no recomposition, so the list keeps its scroll.
            expectNoEvents()
        }
    }

    @Test
    fun `start on an already-loaded directory never flips loading on`() = runTest {
        seedDir("/root", fileEntry("a.txt"))
        controller.listDelays["/root"] = 50
        val vm = SandboxFileBrowserViewModel(controller)
        vm.start("/root")
        testDispatcher.scheduler.advanceUntilIdle()

        controller.entriesByPath.getValue("/root").add(fileEntry("agent.log"))
        vm.start("/root")
        testDispatcher.scheduler.advanceTimeBy(10)

        assertFalse(vm.state.value.loading)
        assertEquals(listOf("a.txt"), vm.state.value.entries.map { it.name })

        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("a.txt", "agent.log"), vm.state.value.entries.map { it.name })
    }

    @Test
    fun `navigateTo back to a previously visited directory re-lists it`() = runTest {
        val sub = dirEntry("sub")
        seedDir("/root", sub)
        seedDir("/root/sub", fileEntry("old.txt", parent = "/root/sub"))
        val vm = SandboxFileBrowserViewModel(controller)
        vm.start("/root")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.navigateTo("/root/sub")
        testDispatcher.scheduler.advanceUntilIdle()
        vm.navigateTo("/root")
        testDispatcher.scheduler.advanceUntilIdle()

        controller.entriesByPath.getValue("/root/sub").add(fileEntry("new.txt", parent = "/root/sub"))
        vm.navigateTo("/root/sub")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("old.txt", "new.txt"), vm.state.value.entries.map { it.name })
    }

    @Test
    fun `refresh that resolves after navigating away does not overwrite the new directory`() = runTest {
        val sub = dirEntry("sub")
        seedDir("/root", sub)
        seedDir("/root/sub", fileEntry("inner.txt", parent = "/root/sub"))
        val vm = SandboxFileBrowserViewModel(controller)
        vm.start("/root")
        testDispatcher.scheduler.advanceUntilIdle()

        controller.listDelays["/root"] = 100
        vm.start("/root") // slow silent refresh of /root
        vm.navigateTo("/root/sub") // user steps into the subdirectory meanwhile
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("/root/sub", vm.state.value.currentPath)
        assertEquals(listOf("inner.txt"), vm.state.value.entries.map { it.name })
    }

    @Test
    fun `start reloads the open editor when the buffer is clean`() = runTest {
        val entry = fileEntry("notes.md")
        seedDir("/root", entry)
        controller.files[entry.path] = "before"
        val vm = SandboxFileBrowserViewModel(controller)
        vm.start("/root")
        testDispatcher.scheduler.advanceUntilIdle()
        vm.openEntry(entry)
        testDispatcher.scheduler.advanceUntilIdle()

        controller.files[entry.path] = "after"
        vm.start("/root")
        testDispatcher.scheduler.advanceUntilIdle()

        val editor = vm.state.value.editor
        assertTrue(editor is EditorState.Loaded)
        assertEquals("after", editor.current)
        assertEquals("after", editor.original)
        assertFalse(editor.dirty)
    }

    @Test
    fun `start keeps unsaved editor edits instead of reloading`() = runTest {
        val entry = fileEntry("notes.md")
        seedDir("/root", entry)
        controller.files[entry.path] = "before"
        val vm = SandboxFileBrowserViewModel(controller)
        vm.start("/root")
        testDispatcher.scheduler.advanceUntilIdle()
        vm.openEntry(entry)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.updateEditorContent("my unsaved work")

        controller.files[entry.path] = "after"
        vm.start("/root")
        testDispatcher.scheduler.advanceUntilIdle()

        val editor = vm.state.value.editor
        assertTrue(editor is EditorState.Loaded)
        assertEquals("my unsaved work", editor.current)
        assertEquals("before", editor.original)
    }

    @Test
    fun `start leaves the editor loaded when the file becomes unreadable`() = runTest {
        val entry = fileEntry("notes.md")
        seedDir("/root", entry)
        controller.files[entry.path] = "before"
        val vm = SandboxFileBrowserViewModel(controller)
        vm.start("/root")
        testDispatcher.scheduler.advanceUntilIdle()
        vm.openEntry(entry)
        testDispatcher.scheduler.advanceUntilIdle()

        controller.files.remove(entry.path) // readTextFile now reports Unreadable
        vm.start("/root")
        testDispatcher.scheduler.advanceUntilIdle()

        val editor = vm.state.value.editor
        assertTrue(editor is EditorState.Loaded)
        assertEquals("before", editor.current)
    }

    @Test
    fun `a non-UTF-8 file opens as Binary and force opens it read-only`() = runTest {
        val entry = fileEntry("latin1.txt")
        seedDir("/root", entry)
        controller.files[entry.path] = "\uFFFDbytes"
        controller.binaryPaths += entry.path
        val vm = SandboxFileBrowserViewModel(controller)
        vm.start("/root")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.openEntry(entry)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.editor is EditorState.Binary)

        // The escape hatch has to actually change state — it used to re-run the same
        // read and land back on Binary.
        vm.loadAsText(entry.path)
        testDispatcher.scheduler.advanceUntilIdle()
        val editor = vm.state.value.editor
        assertTrue(editor is EditorState.Loaded)
        assertTrue(editor.readOnly)
    }

    @Test
    fun `a read-only buffer cannot be edited or saved`() = runTest {
        val entry = fileEntry("latin1.txt")
        seedDir("/root", entry)
        controller.files[entry.path] = "original"
        controller.binaryPaths += entry.path
        val vm = SandboxFileBrowserViewModel(controller)
        vm.start("/root")
        testDispatcher.scheduler.advanceUntilIdle()
        vm.loadAsText(entry.path)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.updateEditorContent("clobbered")
        vm.save()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("original", controller.files[entry.path])
        val editor = vm.state.value.editor
        assertTrue(editor is EditorState.Loaded)
        assertFalse(editor.dirty)
    }

    @Test
    fun `an oversized file reports TooLarge rather than Binary`() = runTest {
        val entry = fileEntry("huge.log")
        seedDir("/root", entry)
        controller.files[entry.path] = "x".repeat(600_000)
        val vm = SandboxFileBrowserViewModel(controller)
        vm.start("/root")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.openEntry(entry)
        testDispatcher.scheduler.advanceUntilIdle()

        val editor = vm.state.value.editor
        assertTrue(editor is EditorState.TooLarge)
        assertEquals(600_000L, editor.sizeBytes)
    }

    @Test
    fun `a missing file reports Unreadable`() = runTest {
        val entry = fileEntry("gone.txt")
        seedDir("/root", entry)
        val vm = SandboxFileBrowserViewModel(controller)
        vm.start("/root")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.openEntry(entry)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.editor is EditorState.Unreadable)
    }
}
