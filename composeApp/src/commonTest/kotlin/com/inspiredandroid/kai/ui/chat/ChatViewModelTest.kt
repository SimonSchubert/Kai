package com.inspiredandroid.kai.ui.chat

import app.cash.turbine.test
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.network.GeminiInvalidApiKeyException
import com.inspiredandroid.kai.network.GeminiRateLimitExceededException
import com.inspiredandroid.kai.network.GenericNetworkException
import com.inspiredandroid.kai.network.GroqInvalidApiKeyException
import com.inspiredandroid.kai.network.GroqRateLimitExceededException
import com.inspiredandroid.kai.testutil.FakeDataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
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

    @Test
    fun `initial state reflects isUsingSharedKey from repository`() = runTest {
        fakeRepository.setCurrentService(Service.Free)
        val viewModel = ChatViewModel(fakeRepository)

        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.isUsingSharedKey)
        }
    }

    @Test
    fun `initial state with non-free service has isUsingSharedKey false`() = runTest {
        fakeRepository.setCurrentService(Service.Gemini)
        val viewModel = ChatViewModel(fakeRepository)

        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.isUsingSharedKey)
        }
    }

    @Test
    fun `ask sets isLoading to true then false on success`() = runTest {
        val viewModel = ChatViewModel(fakeRepository)

        viewModel.state.test {
            val initialState = awaitItem()
            assertFalse(initialState.isLoading)

            initialState.actions.ask("Hello")
            testDispatcher.scheduler.advanceUntilIdle()

            // Should get loading state and then completed state
            val states = mutableListOf<ChatUiState>()
            while (true) {
                val state = awaitItem()
                states.add(state)
                if (!state.isLoading && state.history.isNotEmpty()) break
            }

            // Verify we saw loading state
            assertTrue(states.any { it.isLoading })
            // Final state should not be loading
            assertFalse(states.last().isLoading)
        }
    }

    @Test
    fun `successful ask adds messages to history`() = runTest {
        val viewModel = ChatViewModel(fakeRepository)

        viewModel.state.test {
            val initialState = awaitItem()
            assertTrue(initialState.history.isEmpty())

            initialState.actions.ask("Hello")
            testDispatcher.scheduler.advanceUntilIdle()

            // Wait for history to be populated
            var finalState: ChatUiState
            do {
                finalState = awaitItem()
            } while (finalState.history.isEmpty() || finalState.isLoading)

            assertEquals(2, finalState.history.size)
            assertEquals(History.Role.USER, finalState.history[0].role)
            assertEquals("Hello", finalState.history[0].content)
            assertEquals(History.Role.ASSISTANT, finalState.history[1].role)
        }
    }

    @Test
    fun `ask clears previous error`() = runTest {
        fakeRepository.askException = GenericNetworkException("First error")
        val viewModel = ChatViewModel(fakeRepository)

        viewModel.state.test {
            val initialState = awaitItem()

            // First call - will fail
            initialState.actions.ask("First")
            testDispatcher.scheduler.advanceUntilIdle()

            // Wait for error
            var errorState: ChatUiState
            do {
                errorState = awaitItem()
            } while (errorState.error == null)
            assertNotNull(errorState.error)

            // Clear exception and ask again
            fakeRepository.askException = null
            errorState.actions.ask("Second")
            testDispatcher.scheduler.advanceUntilIdle()

            // Wait for loading state which should have cleared error
            var loadingState: ChatUiState
            do {
                loadingState = awaitItem()
            } while (!loadingState.isLoading && loadingState.error != null)

            // Error should be cleared when loading starts
            assertNull(loadingState.error)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `failed ask with GeminiInvalidApiKeyException sets error`() = runTest {
        fakeRepository.askException = GeminiInvalidApiKeyException()
        val viewModel = ChatViewModel(fakeRepository)

        viewModel.state.test {
            val initialState = awaitItem()
            initialState.actions.ask("Hello")
            testDispatcher.scheduler.advanceUntilIdle()

            var errorState: ChatUiState
            do {
                errorState = awaitItem()
            } while (errorState.error == null)

            assertNotNull(errorState.error)
            assertFalse(errorState.isLoading)
        }
    }

    @Test
    fun `failed ask with GroqInvalidApiKeyException sets error`() = runTest {
        fakeRepository.askException = GroqInvalidApiKeyException()
        val viewModel = ChatViewModel(fakeRepository)

        viewModel.state.test {
            val initialState = awaitItem()
            initialState.actions.ask("Hello")
            testDispatcher.scheduler.advanceUntilIdle()

            var errorState: ChatUiState
            do {
                errorState = awaitItem()
            } while (errorState.error == null)

            assertNotNull(errorState.error)
            assertFalse(errorState.isLoading)
        }
    }

    @Test
    fun `failed ask with GeminiRateLimitExceededException sets error`() = runTest {
        fakeRepository.askException = GeminiRateLimitExceededException()
        val viewModel = ChatViewModel(fakeRepository)

        viewModel.state.test {
            val initialState = awaitItem()
            initialState.actions.ask("Hello")
            testDispatcher.scheduler.advanceUntilIdle()

            var errorState: ChatUiState
            do {
                errorState = awaitItem()
            } while (errorState.error == null)

            assertNotNull(errorState.error)
            assertFalse(errorState.isLoading)
        }
    }

    @Test
    fun `failed ask with GroqRateLimitExceededException sets error`() = runTest {
        fakeRepository.askException = GroqRateLimitExceededException()
        val viewModel = ChatViewModel(fakeRepository)

        viewModel.state.test {
            val initialState = awaitItem()
            initialState.actions.ask("Hello")
            testDispatcher.scheduler.advanceUntilIdle()

            var errorState: ChatUiState
            do {
                errorState = awaitItem()
            } while (errorState.error == null)

            assertNotNull(errorState.error)
            assertFalse(errorState.isLoading)
        }
    }

    @Test
    fun `clearHistory clears history and error`() = runTest {
        fakeRepository.askException = GenericNetworkException("Error")
        val viewModel = ChatViewModel(fakeRepository)

        viewModel.state.test {
            val initialState = awaitItem()

            // Trigger an error first
            initialState.actions.ask("Hello")
            testDispatcher.scheduler.advanceUntilIdle()

            var errorState: ChatUiState
            do {
                errorState = awaitItem()
            } while (errorState.error == null)
            assertNotNull(errorState.error)

            // Clear history
            errorState.actions.clearHistory()
            testDispatcher.scheduler.advanceUntilIdle()

            var clearedState: ChatUiState
            do {
                clearedState = awaitItem()
            } while (clearedState.error != null || clearedState.history.isNotEmpty())

            assertNull(clearedState.error)
            assertTrue(clearedState.history.isEmpty())
            assertEquals(1, fakeRepository.clearHistoryCalls)
        }
    }

    @Test
    fun `toggleSpeechOutput toggles isSpeechOutputEnabled`() = runTest {
        val viewModel = ChatViewModel(fakeRepository)

        viewModel.state.test {
            val initialState = awaitItem()
            assertFalse(initialState.isSpeechOutputEnabled)

            initialState.actions.toggleSpeechOutput()
            testDispatcher.scheduler.advanceUntilIdle()

            val enabledState = awaitItem()
            assertTrue(enabledState.isSpeechOutputEnabled)

            enabledState.actions.toggleSpeechOutput()
            testDispatcher.scheduler.advanceUntilIdle()

            val disabledState = awaitItem()
            assertFalse(disabledState.isSpeechOutputEnabled)
        }
    }

    @Test
    fun `setIsSpeaking updates speaking state`() = runTest {
        val viewModel = ChatViewModel(fakeRepository)

        viewModel.state.test {
            val initialState = awaitItem()
            assertFalse(initialState.isSpeaking)

            initialState.actions.setIsSpeaking(true, "content-123")
            testDispatcher.scheduler.advanceUntilIdle()

            val speakingState = awaitItem()
            assertTrue(speakingState.isSpeaking)
            assertEquals("content-123", speakingState.isSpeakingContentId)

            speakingState.actions.setIsSpeaking(false, "")
            testDispatcher.scheduler.advanceUntilIdle()

            val notSpeakingState = awaitItem()
            assertFalse(notSpeakingState.isSpeaking)
            // Content ID should be preserved when stopping
            assertEquals("content-123", notSpeakingState.isSpeakingContentId)
        }
    }

    @Test
    fun `retry calls ask with null`() = runTest {
        val viewModel = ChatViewModel(fakeRepository)

        viewModel.state.test {
            val initialState = awaitItem()

            initialState.actions.retry()
            testDispatcher.scheduler.advanceUntilIdle()

            // Wait for completion
            var finalState: ChatUiState
            do {
                finalState = awaitItem()
            } while (finalState.isLoading)

            // Verify ask was called with null
            assertTrue(fakeRepository.askCalls.any { it.first == null })
        }
    }

    @Test
    fun `allowFileAttachment is true only for Gemini service`() = runTest {
        fakeRepository.setCurrentService(Service.Gemini)
        val viewModel = ChatViewModel(fakeRepository)

        viewModel.state.test {
            skipItems(1)
            val state = awaitItem()
            assertTrue(state.allowFileAttachment)
        }
    }

    @Test
    fun `allowFileAttachment is false for Free service`() = runTest {
        fakeRepository.setCurrentService(Service.Free)
        val viewModel = ChatViewModel(fakeRepository)

        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.allowFileAttachment)
        }
    }

    @Test
    fun `allowFileAttachment is false for Groq service`() = runTest {
        fakeRepository.setCurrentService(Service.Groq)
        val viewModel = ChatViewModel(fakeRepository)

        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.allowFileAttachment)
        }
    }
}
