package com.inspiredandroid.kai.ui.settings

import app.cash.turbine.test
import com.inspiredandroid.kai.CommandHandle
import com.inspiredandroid.kai.NoOpCommandHandle
import com.inspiredandroid.kai.SandboxController
import com.inspiredandroid.kai.SandboxFileEntry
import com.inspiredandroid.kai.SandboxStatus
import com.inspiredandroid.kai.testutil.FakeDataRepository
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SandboxViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeDataRepository
    private lateinit var fakeSandboxController: FakeSandboxController

    private class FakeSandboxController : SandboxController {
        override val status = MutableStateFlow(SandboxStatus())
        var setupCalls = 0
        var cancelCalls = 0
        var resetCalls = 0
        var installPackagesCalls = 0

        override fun setup() {
            setupCalls++
        }

        override fun cancel() {
            cancelCalls++
        }

        override fun reset() {
            resetCalls++
        }

        override fun installPackages() {
            installPackagesCalls++
        }

        override suspend fun executeCommand(command: String): String = ""

        override suspend fun executeCommandStreaming(
            command: String,
            onStdout: (String) -> Unit,
            onStderr: (String) -> Unit,
        ): CommandHandle = NoOpCommandHandle

        override suspend fun listDirectory(path: String): List<SandboxFileEntry> = emptyList()
        override suspend fun readTextFile(path: String, maxBytes: Int): String? = null
        override suspend fun writeTextFile(path: String, content: String): Boolean = false
        override suspend fun openFile(path: String): Result<Unit> = Result.failure(UnsupportedOperationException())
        override suspend fun deleteEntry(path: String, recursive: Boolean): Boolean = false
        override suspend fun renameEntry(path: String, newName: String): Result<String> = Result.failure(UnsupportedOperationException())
    }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeDataRepository()
        fakeSandboxController = FakeSandboxController()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state reflects sandbox enabled flag from repository`() = runTest {
        val viewModel = SandboxViewModel(fakeRepository, fakeSandboxController)

        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.isSandboxEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onToggleSandbox persists to repository`() = runTest {
        val viewModel = SandboxViewModel(fakeRepository, fakeSandboxController)

        viewModel.state.test {
            val initial = awaitItem()
            assertTrue(initial.isSandboxEnabled)

            viewModel.onToggleSandbox(false)
            val updated = awaitItem()
            assertFalse(updated.isSandboxEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onSetupSandbox delegates to controller`() = runTest {
        val viewModel = SandboxViewModel(fakeRepository, fakeSandboxController)
        viewModel.onSetupSandbox()
        assertEquals(1, fakeSandboxController.setupCalls)
    }

    @Test
    fun `onCancelSandbox delegates to controller`() = runTest {
        val viewModel = SandboxViewModel(fakeRepository, fakeSandboxController)
        viewModel.onCancelSandbox()
        assertEquals(1, fakeSandboxController.cancelCalls)
    }

    @Test
    fun `onResetSandbox delegates to controller`() = runTest {
        val viewModel = SandboxViewModel(fakeRepository, fakeSandboxController)
        viewModel.onResetSandbox()
        assertEquals(1, fakeSandboxController.resetCalls)
    }

    @Test
    fun `onInstallPackages delegates to controller`() = runTest {
        val viewModel = SandboxViewModel(fakeRepository, fakeSandboxController)
        viewModel.onInstallPackages()
        assertEquals(1, fakeSandboxController.installPackagesCalls)
    }

    @Test
    fun `controller status updates flow into state`() = runTest {
        val viewModel = SandboxViewModel(fakeRepository, fakeSandboxController)

        viewModel.state.test {
            val initial = awaitItem()
            assertFalse(initial.sandboxReady)

            fakeSandboxController.status.value = SandboxStatus(
                installed = true,
                ready = true,
                working = false,
                progress = 1.0f,
                statusText = "Done",
                diskUsageMB = 250L,
                packagesInstalled = true,
                error = false,
            )
            testDispatcher.scheduler.advanceUntilIdle()

            val updated = awaitItem()
            assertTrue(updated.sandboxInstalled)
            assertTrue(updated.sandboxReady)
            assertEquals(1.0f, updated.sandboxProgress)
            assertEquals("Done", updated.sandboxStatusText)
            assertEquals(250L, updated.sandboxDiskUsageMB)
            assertTrue(updated.sandboxPackagesInstalled)
            assertFalse(updated.isWorking)
            assertFalse(updated.hasError)
        }
    }

    @Test
    fun `controller error status flows into hasError`() = runTest {
        val viewModel = SandboxViewModel(fakeRepository, fakeSandboxController)

        viewModel.state.test {
            skipItems(1)
            fakeSandboxController.status.value = SandboxStatus(error = true, statusText = "Failed")
            testDispatcher.scheduler.advanceUntilIdle()

            val updated = awaitItem()
            assertTrue(updated.hasError)
            assertEquals("Failed", updated.sandboxStatusText)
        }
    }
}
