package com.inspiredandroid.kai.ui.chat

import app.cash.turbine.test
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.data.ServiceEntry
import com.inspiredandroid.kai.data.TaskScheduler
import com.inspiredandroid.kai.inference.DownloadedModel
import com.inspiredandroid.kai.testutil.FakeDataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelLiteRTTest {

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

    private fun litertServiceEntry() = ServiceEntry(
        instanceId = "litert",
        serviceId = Service.LiteRT.id,
        serviceName = Service.LiteRT.displayName,
        modelId = "",
        icon = Service.LiteRT.icon,
    )

    private fun geminiServiceEntry() = ServiceEntry(
        instanceId = "gemini",
        serviceId = Service.Gemini.id,
        serviceName = Service.Gemini.displayName,
        modelId = "gemini-2.0-flash",
        icon = Service.Gemini.icon,
    )

    @Test
    fun `warning shown when LiteRT is primary service with no downloaded models`() = runTest {
        fakeRepository.fakeServiceEntries = listOf(litertServiceEntry())
        fakeRepository.fakeLocalDownloadedModels = emptyList()

        val viewModel = createViewModel()

        viewModel.state.test {
            val state = awaitItem()
            assertNotNull(state.warning)
        }
    }

    @Test
    fun `no warning when LiteRT has downloaded models`() = runTest {
        fakeRepository.fakeServiceEntries = listOf(litertServiceEntry())
        fakeRepository.fakeLocalDownloadedModels = listOf(
            DownloadedModel(
                id = "gemma-4-e2b-it",
                displayName = "Gemma 4 E2B IT",
                filePath = "/fake/path/model.litertlm",
                sizeBytes = 2_580_000_000L,
            ),
        )

        val viewModel = createViewModel()

        viewModel.state.test {
            val state = awaitItem()
            assertNull(state.warning)
        }
    }

    @Test
    fun `no warning when primary service is not on-device`() = runTest {
        fakeRepository.fakeServiceEntries = listOf(geminiServiceEntry())

        val viewModel = createViewModel()

        viewModel.state.test {
            val state = awaitItem()
            assertNull(state.warning)
        }
    }

    @Test
    fun `no warning when LiteRT is secondary service with no models`() = runTest {
        fakeRepository.fakeServiceEntries = listOf(geminiServiceEntry(), litertServiceEntry())
        fakeRepository.fakeLocalDownloadedModels = emptyList()

        val viewModel = createViewModel()

        viewModel.state.test {
            val state = awaitItem()
            assertNull(state.warning)
        }
    }

    @Test
    fun `warning cleared after switching to non-on-device service`() = runTest {
        fakeRepository.fakeServiceEntries = listOf(litertServiceEntry())
        fakeRepository.fakeLocalDownloadedModels = emptyList()

        val viewModel = createViewModel()

        viewModel.state.test {
            val state = awaitItem()
            assertNotNull(state.warning)

            // Simulate switching primary service to Gemini
            fakeRepository.fakeServiceEntries = listOf(geminiServiceEntry(), litertServiceEntry())
            viewModel.refreshSettings()
            testDispatcher.scheduler.advanceUntilIdle()

            var updated: ChatUiState
            do {
                updated = awaitItem()
            } while (updated.warning != null)

            assertNull(updated.warning)
        }
    }
}
