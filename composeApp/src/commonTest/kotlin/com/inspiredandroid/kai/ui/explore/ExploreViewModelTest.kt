package com.inspiredandroid.kai.ui.explore

import app.cash.turbine.test
import com.inspiredandroid.kai.network.GenericNetworkException
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModelTest {

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

    private fun createViewModel() = ExploreViewModel(fakeRepository, unconfinedDispatcher)

    @Test
    fun `loadTopic sets loading then returns items`() = runTest {
        fakeRepository.askExploreResponse = """[
            {"title":"Item 1","description":"Desc 1"},
            {"title":"Item 2","description":"Desc 2","imageUrl":"https://example.com/img.jpg"}
        ]"""
        val viewModel = createViewModel()

        viewModel.state.test {
            val initial = awaitItem()
            assertFalse(initial.isLoading)
            assertTrue(initial.items.isEmpty())

            viewModel.loadTopic("Science")
            testDispatcher.scheduler.advanceUntilIdle()

            var state: ExploreUiState
            do {
                state = awaitItem()
            } while (state.isLoading || state.items.isEmpty())

            assertFalse(state.isLoading)
            assertEquals(2, state.items.size)
            assertEquals("Item 1", state.items[0].title)
            assertEquals("Desc 1", state.items[0].description)
            assertNull(state.items[0].imageUrl)
            assertEquals("Item 2", state.items[1].title)
            assertEquals("https://example.com/img.jpg", state.items[1].imageUrl)
            assertEquals("Science", state.topicTitle)
        }
    }

    @Test
    fun `loadTopic with error sets error state`() = runTest {
        fakeRepository.askExploreException = GenericNetworkException("Network error")
        val viewModel = createViewModel()

        viewModel.state.test {
            awaitItem()

            viewModel.loadTopic("Science")
            testDispatcher.scheduler.advanceUntilIdle()

            var state: ExploreUiState
            do {
                state = awaitItem()
            } while (state.error == null)

            assertNotNull(state.error)
            assertFalse(state.isLoading)
        }
    }

    @Test
    fun `retry reloads the topic`() = runTest {
        fakeRepository.askExploreException = GenericNetworkException("Error")
        val viewModel = createViewModel()

        viewModel.state.test {
            awaitItem()

            viewModel.loadTopic("Science")
            testDispatcher.scheduler.advanceUntilIdle()

            var errorState: ExploreUiState
            do {
                errorState = awaitItem()
            } while (errorState.error == null)
            assertEquals(1, fakeRepository.askExploreCalls.size)

            // Clear error and retry
            fakeRepository.askExploreException = null
            fakeRepository.askExploreResponse = """[{"title":"Item 1","description":"Desc 1"}]"""

            viewModel.retry()
            testDispatcher.scheduler.advanceUntilIdle()

            var successState: ExploreUiState
            do {
                successState = awaitItem()
            } while (successState.isLoading || successState.items.isEmpty())

            assertEquals(2, fakeRepository.askExploreCalls.size)
            assertEquals(1, successState.items.size)
        }
    }

    @Test
    fun `loadTopic prevents concurrent requests`() = runTest {
        fakeRepository.askExploreResponse = """[{"title":"Item 1","description":"Desc 1"}]"""
        // Use StandardTestDispatcher so the coroutine doesn't complete immediately
        val viewModel = ExploreViewModel(fakeRepository, testDispatcher)

        viewModel.state.test {
            awaitItem()

            viewModel.loadTopic("Science")
            // Call again while still loading (coroutine hasn't completed yet)
            viewModel.loadTopic("Science")
            testDispatcher.scheduler.advanceUntilIdle()

            var state: ExploreUiState
            do {
                state = awaitItem()
            } while (state.isLoading || state.items.isEmpty())

            // Only one call should have been made
            assertEquals(1, fakeRepository.askExploreCalls.size)
        }
    }
}
