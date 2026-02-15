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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreDetailViewModelTest {

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

    private fun createViewModel() = ExploreDetailViewModel(fakeRepository, unconfinedDispatcher)

    @Test
    fun `loadItem sets loading then returns content`() = runTest {
        fakeRepository.askExploreResponse = """# The Sun
The Sun is a star.
---REFERENCES---["Stars","Planets","Galaxy","Solar System","Astronomy"]"""
        val viewModel = createViewModel()

        viewModel.state.test {
            val initial = awaitItem()
            assertFalse(initial.isLoading)
            assertTrue(initial.markdownContent.isEmpty())

            viewModel.loadItem("The Sun")
            testDispatcher.scheduler.advanceUntilIdle()

            var state: ExploreDetailUiState
            do {
                state = awaitItem()
            } while (state.isLoading || state.markdownContent.isEmpty())

            assertFalse(state.isLoading)
            assertTrue(state.markdownContent.contains("The Sun is a star."))
            assertEquals(5, state.references.size)
            assertEquals("Stars", state.references[0])
            assertEquals("Astronomy", state.references[4])
            assertEquals("The Sun", state.itemName)
        }
    }

    @Test
    fun `loadItem with error sets error state`() = runTest {
        fakeRepository.askExploreException = GenericNetworkException("Network error")
        val viewModel = createViewModel()

        viewModel.state.test {
            awaitItem()

            viewModel.loadItem("The Sun")
            testDispatcher.scheduler.advanceUntilIdle()

            var state: ExploreDetailUiState
            do {
                state = awaitItem()
            } while (state.error == null)

            assertNotNull(state.error)
            assertFalse(state.isLoading)
        }
    }

    @Test
    fun `retry reloads the item`() = runTest {
        fakeRepository.askExploreException = GenericNetworkException("Error")
        val viewModel = createViewModel()

        viewModel.state.test {
            awaitItem()

            viewModel.loadItem("The Sun")
            testDispatcher.scheduler.advanceUntilIdle()

            var errorState: ExploreDetailUiState
            do {
                errorState = awaitItem()
            } while (errorState.error == null)
            assertEquals(1, fakeRepository.askExploreCalls.size)

            // Clear error and retry
            fakeRepository.askExploreException = null
            fakeRepository.askExploreResponse = "# The Sun\nContent here."

            viewModel.retry()
            testDispatcher.scheduler.advanceUntilIdle()

            var successState: ExploreDetailUiState
            do {
                successState = awaitItem()
            } while (successState.isLoading || successState.markdownContent.isEmpty())

            assertEquals(2, fakeRepository.askExploreCalls.size)
            assertTrue(successState.markdownContent.contains("Content here."))
        }
    }

    @Test
    fun `parseDetailResponse handles missing references separator`() = runTest {
        fakeRepository.askExploreResponse = "# The Sun\nJust content, no references."
        val viewModel = createViewModel()

        viewModel.state.test {
            awaitItem()

            viewModel.loadItem("The Sun")
            testDispatcher.scheduler.advanceUntilIdle()

            var state: ExploreDetailUiState
            do {
                state = awaitItem()
            } while (state.isLoading || state.markdownContent.isEmpty())

            assertTrue(state.markdownContent.contains("Just content, no references."))
            assertTrue(state.references.isEmpty())
        }
    }
}
