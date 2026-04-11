package com.inspiredandroid.kai.ui.chat

import app.cash.turbine.test
import com.inspiredandroid.kai.data.TaskScheduler
import com.inspiredandroid.kai.testutil.FakeDataRepository
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression tests for [ChatViewModel.addFile] / [ChatViewModel.removeFile]. These live in
 * desktopTest instead of commonTest because constructing a [PlatformFile] requires a
 * platform-specific constructor (JVM wraps a real [java.io.File]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelFileAttachmentTest {

    private val testDispatcher = StandardTestDispatcher()
    private val unconfinedDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeRepository: FakeDataRepository

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeDataRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ChatViewModel {
        val noOpScheduler = TaskScheduler(fakeRepository, enabled = false)
        return ChatViewModel(fakeRepository, noOpScheduler, unconfinedDispatcher)
    }

    private fun tempPlatformFile(extension: String): PlatformFile {
        val file = File.createTempFile("kai-test-", ".$extension")
        file.deleteOnExit()
        return PlatformFile(file)
    }

    @Test
    fun `addFile stores file when extension is supported`() = runTest {
        fakeRepository.fileAttachmentSupported = true
        val viewModel = createViewModel()
        val pngFile = tempPlatformFile("png")

        viewModel.state.test {
            skipItems(1)
            viewModel.state.value.actions.addFile(pngFile)
            val state = awaitItem()
            assertEquals(1, state.files.size)
            assertEquals(pngFile, state.files.first())
            assertNull(state.snackbarMessage)
        }
    }

    @Test
    fun `addFile surfaces snackbar when extension is not supported`() = runTest {
        fakeRepository.fileAttachmentSupported = true
        val viewModel = createViewModel()
        val unsupportedFile = tempPlatformFile("xyz")

        viewModel.state.test {
            skipItems(1)
            viewModel.state.value.actions.addFile(unsupportedFile)
            val state = awaitItem()
            assertTrue(state.files.isEmpty())
            assertNotNull(state.snackbarMessage)
        }
    }

    @Test
    fun `addFile appends multiple files so all are queued for the next prompt`() = runTest {
        fakeRepository.fileAttachmentSupported = true
        val viewModel = createViewModel()
        val first = tempPlatformFile("png")
        val second = tempPlatformFile("txt")

        viewModel.state.test {
            skipItems(1)
            viewModel.state.value.actions.addFile(first)
            val afterFirst = awaitItem()
            assertEquals(listOf(first), afterFirst.files.toList())

            viewModel.state.value.actions.addFile(second)
            val afterSecond = awaitItem()
            assertEquals(listOf(first, second), afterSecond.files.toList())
        }
    }

    @Test
    fun `removeFile removes the specified file and keeps the rest`() = runTest {
        fakeRepository.fileAttachmentSupported = true
        val viewModel = createViewModel()
        val first = tempPlatformFile("png")
        val second = tempPlatformFile("txt")

        viewModel.state.test {
            skipItems(1)
            viewModel.state.value.actions.addFile(first)
            awaitItem()
            viewModel.state.value.actions.addFile(second)
            awaitItem()

            viewModel.state.value.actions.removeFile(first)
            val state = awaitItem()
            assertEquals(listOf(second), state.files.toList())
        }
    }

    @Test
    fun `ask clears the queued files after submitting`() = runTest {
        fakeRepository.fileAttachmentSupported = true
        val viewModel = createViewModel()
        val pngFile = tempPlatformFile("png")

        viewModel.state.test {
            skipItems(1)
            viewModel.state.value.actions.addFile(pngFile)
            awaitItem()

            viewModel.state.value.actions.ask("describe this")
            // drain states until files is empty (ask clears them on dispatch)
            var filesCleared = false
            while (true) {
                val next = awaitItem()
                if (next.files.isEmpty()) {
                    filesCleared = true
                    break
                }
            }
            assertTrue(filesCleared)
            assertEquals(1, fakeRepository.askCalls.size)
            assertEquals(listOf(pngFile), fakeRepository.askCalls.first().second.toList())
        }
    }

    @Test
    fun `truncateFileName returns short names unchanged`() {
        assertEquals("cat.png", com.inspiredandroid.kai.ui.chat.composables.truncateFileName("cat.png"))
    }

    @Test
    fun `truncateFileName shortens long names and keeps extension`() {
        val result = com.inspiredandroid.kai.ui.chat.composables.truncateFileName(
            "a-really-long-screenshot-name.png",
            maxChars = 16,
        )
        assertTrue(result.endsWith(".png"), "extension must be preserved, got: $result")
        assertTrue(result.contains("…"), "ellipsis must be present, got: $result")
        assertTrue(result.length <= 16, "result must respect maxChars, got ${result.length}: $result")
        assertFalse(result == "a-really-long-screenshot-name.png")
    }

    @Test
    fun `truncateFileName handles names without extension`() {
        val result = com.inspiredandroid.kai.ui.chat.composables.truncateFileName(
            "a-really-long-name-without-ext",
            maxChars = 10,
        )
        assertTrue(result.endsWith("…"))
        assertEquals(10, result.length)
    }
}
