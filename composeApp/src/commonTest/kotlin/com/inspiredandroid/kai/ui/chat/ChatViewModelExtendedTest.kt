package com.inspiredandroid.kai.ui.chat

import app.cash.turbine.test
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.data.ServiceEntry
import com.inspiredandroid.kai.data.TaskScheduler
import com.inspiredandroid.kai.testutil.FakeDataRepository
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelExtendedTest {

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

    private fun makeServiceEntry(instanceId: String, service: Service) = ServiceEntry(
        instanceId = instanceId,
        serviceId = service.id,
        serviceName = service.displayName,
        modelId = "test-model",
        icon = service.icon,
    )

    // ---- Concurrent ask prevention ----

    @Test
    fun `concurrent ask is ignored while a previous ask is still loading`() = runTest {
        // Gate the first ask so it stays in flight
        val gate = CompletableDeferred<Unit>()
        fakeRepository.askGate = gate
        val viewModel = createViewModel()

        viewModel.state.test {
            val initialState = awaitItem()
            initialState.actions.ask("first")
            // First ask is now suspended at the gate, isLoading should be true
            var loadingState: ChatUiState
            do {
                loadingState = awaitItem()
            } while (!loadingState.isLoading)
            assertTrue(loadingState.isLoading)
            assertEquals(1, fakeRepository.askCalls.size)

            // Second ask should be ignored entirely while loading
            loadingState.actions.ask("second")
            testDispatcher.scheduler.advanceUntilIdle()
            // No new ask call recorded
            assertEquals(1, fakeRepository.askCalls.size)

            // Release the gate so the first ask completes
            fakeRepository.askGate = null
            gate.complete(Unit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- selectService ----

    @Test
    fun `selectService reorders configured services so the selected instance is first`() = runTest {
        fakeRepository.setConfiguredServices(Service.Gemini, Service.OpenAI, Service.Anthropic)
        fakeRepository.fakeServiceEntries = listOf(
            makeServiceEntry("gemini", Service.Gemini),
            makeServiceEntry("openai", Service.OpenAI),
            makeServiceEntry("anthropic", Service.Anthropic),
        )

        val viewModel = createViewModel()
        viewModel.state.test {
            skipItems(1)
            val initialState = awaitItem()
            assertEquals("gemini", initialState.availableServices.first().instanceId)

            // Update fakeServiceEntries to reflect the reorder happens via getServiceEntries
            // The fake's reorderConfiguredServices changes configuredInstances, but
            // fakeServiceEntries is independent — update it after reorder happens.
            fakeRepository.fakeServiceEntries = listOf(
                makeServiceEntry("openai", Service.OpenAI),
                makeServiceEntry("gemini", Service.Gemini),
                makeServiceEntry("anthropic", Service.Anthropic),
            )

            initialState.actions.selectService("openai")
            testDispatcher.scheduler.advanceUntilIdle()

            val reordered = awaitItem()
            assertEquals("openai", reordered.availableServices.first().instanceId)
            // Repository should have been told to reorder
            val configured = fakeRepository.getConfiguredServiceInstances()
            assertEquals("openai", configured.first().instanceId)
        }
    }

    @Test
    fun `selectService is a no-op when instanceId is unknown`() = runTest {
        fakeRepository.setConfiguredServices(Service.Gemini, Service.OpenAI)
        fakeRepository.fakeServiceEntries = listOf(
            makeServiceEntry("gemini", Service.Gemini),
            makeServiceEntry("openai", Service.OpenAI),
        )
        val viewModel = createViewModel()

        viewModel.state.test {
            skipItems(1)
            val initialState = awaitItem()
            initialState.actions.selectService("nonexistent_id")
            testDispatcher.scheduler.advanceUntilIdle()

            // Order should be unchanged
            val configured = fakeRepository.getConfiguredServiceInstances()
            assertEquals("gemini", configured[0].instanceId)
            assertEquals("openai", configured[1].instanceId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- startNewChat ----

    @Test
    fun `startNewChat clears history error and isLoading`() = runTest {
        // Pre-populate some history
        fakeRepository.chatHistory.value = listOf(
            History(role = History.Role.USER, content = "Hi"),
            History(role = History.Role.ASSISTANT, content = "Hello"),
        )
        val viewModel = createViewModel()

        viewModel.state.test {
            // Wait for initial state to surface the pre-populated history
            var stateWithHistory: ChatUiState
            do {
                stateWithHistory = awaitItem()
            } while (stateWithHistory.history.isEmpty())
            assertEquals(2, stateWithHistory.history.size)

            stateWithHistory.actions.startNewChat()
            testDispatcher.scheduler.advanceUntilIdle()

            var clearedState: ChatUiState
            do {
                clearedState = awaitItem()
            } while (clearedState.history.isNotEmpty())
            assertTrue(clearedState.history.isEmpty())
            assertNull(clearedState.error)
            assertFalse(clearedState.isLoading)
            assertFalse(clearedState.isInteractiveMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- regenerate ----

    @Test
    fun `regenerate truncates to last user message and re-asks with null`() = runTest {
        fakeRepository.chatHistory.value = listOf(
            History(role = History.Role.USER, content = "First"),
            History(role = History.Role.ASSISTANT, content = "Old answer"),
        )
        val viewModel = createViewModel()

        viewModel.state.test {
            // Wait for initial state to surface
            var initialState: ChatUiState
            do {
                initialState = awaitItem()
            } while (initialState.history.size < 2)

            initialState.actions.regenerate()
            testDispatcher.scheduler.advanceUntilIdle()

            // Repository.regenerate must have been called
            assertEquals(1, fakeRepository.regenerateCalls)
            // ask was invoked again with a null question (retry semantics)
            assertTrue(fakeRepository.askCalls.any { it.first == null })
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- enter / exit interactive mode ----

    @Test
    fun `enterInteractiveMode sets the flag and clears error`() = runTest {
        val viewModel = createViewModel()
        viewModel.state.test {
            val initialState = awaitItem()
            assertFalse(initialState.isInteractiveMode)

            initialState.actions.enterInteractiveMode()
            testDispatcher.scheduler.advanceUntilIdle()

            var interactiveState: ChatUiState
            do {
                interactiveState = awaitItem()
            } while (!interactiveState.isInteractiveMode)
            assertTrue(interactiveState.isInteractiveMode)
            assertNull(interactiveState.error)
            assertTrue(fakeRepository.isInteractiveModeActive())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `exitInteractiveMode clears the flag and stops loading`() = runTest {
        val viewModel = createViewModel()
        viewModel.state.test {
            val initial = awaitItem()
            initial.actions.enterInteractiveMode()
            testDispatcher.scheduler.advanceUntilIdle()

            var interactiveState: ChatUiState
            do {
                interactiveState = awaitItem()
            } while (!interactiveState.isInteractiveMode)

            interactiveState.actions.exitInteractiveMode()
            testDispatcher.scheduler.advanceUntilIdle()

            var exitedState: ChatUiState
            do {
                exitedState = awaitItem()
            } while (exitedState.isInteractiveMode)
            assertFalse(exitedState.isInteractiveMode)
            assertFalse(exitedState.isLoading)
            assertNull(exitedState.error)
            assertFalse(fakeRepository.isInteractiveModeActive())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- showPrivacyInfo ----

    @Test
    fun `showPrivacyInfo is false when current service is not Free`() = runTest {
        fakeRepository.setCurrentService(Service.Gemini)
        val viewModel = createViewModel()
        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.showPrivacyInfo)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- setFile ----

    @Test
    fun `setFile null clears the file from state`() = runTest {
        val viewModel = createViewModel()
        viewModel.state.test {
            val state = awaitItem()
            assertNull(state.file)
            state.actions.setFile(null)
            testDispatcher.scheduler.advanceUntilIdle()
            // File remains null; no exception
            assertNull(state.file)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- clearSnackbar ----

    @Test
    fun `clearSnackbar clears the snackbar message`() = runTest {
        val viewModel = createViewModel()
        viewModel.state.test {
            val initialState = awaitItem()
            assertNull(initialState.snackbarMessage)
            // Calling clearSnackbar when there's nothing to clear should be safe
            initialState.actions.clearSnackbar()
            testDispatcher.scheduler.advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- cancel ----

    @Test
    fun `cancel stops an in-flight ask and resets isLoading`() = runTest {
        val gate = CompletableDeferred<Unit>()
        fakeRepository.askGate = gate
        val viewModel = createViewModel()

        viewModel.state.test {
            val initialState = awaitItem()
            initialState.actions.ask("hello")

            var loadingState: ChatUiState
            do {
                loadingState = awaitItem()
            } while (!loadingState.isLoading)
            assertTrue(loadingState.isLoading)

            loadingState.actions.cancel()
            testDispatcher.scheduler.advanceUntilIdle()

            var cancelledState: ChatUiState
            do {
                cancelledState = awaitItem()
            } while (cancelledState.isLoading)
            assertFalse(cancelledState.isLoading)
            // Release gate so the cancelled coroutine can finish unwinding
            gate.complete(Unit)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
