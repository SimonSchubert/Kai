package com.inspiredandroid.kai.ui.settings

import app.cash.turbine.test
import com.inspiredandroid.kai.data.Service
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

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
    fun `initial state reflects current service`() = runTest {
        fakeRepository.setCurrentService(Service.Gemini)

        val viewModel = SettingsViewModel(fakeRepository)

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(Service.Gemini, state.currentService)
        }
    }

    @Test
    fun `onSelectService updates current service`() = runTest {
        fakeRepository.setCurrentService(Service.Free)
        val viewModel = SettingsViewModel(fakeRepository)

        viewModel.state.test {
            val initialState = awaitItem()
            assertEquals(Service.Free, initialState.currentService)

            initialState.onSelectService(Service.Groq)
            testDispatcher.scheduler.advanceUntilIdle()

            val updatedState = awaitItem()
            assertEquals(Service.Groq, updatedState.currentService)
            assertTrue(fakeRepository.selectServiceCalls.contains(Service.Groq))
        }
    }

    @Test
    fun `onSelectService updates API key for selected service`() = runTest {
        fakeRepository.setCurrentService(Service.Free)
        fakeRepository.setApiKey(Service.Groq, "groq-api-key")
        fakeRepository.setApiKey(Service.Gemini, "gemini-api-key")

        val viewModel = SettingsViewModel(fakeRepository)

        viewModel.state.test {
            val initialState = awaitItem()
            assertEquals("", initialState.apiKey)

            initialState.onSelectService(Service.Groq)
            testDispatcher.scheduler.advanceUntilIdle()

            val groqState = awaitItem()
            assertEquals("groq-api-key", groqState.apiKey)

            groqState.onSelectService(Service.Gemini)
            testDispatcher.scheduler.advanceUntilIdle()

            val geminiState = awaitItem()
            assertEquals("gemini-api-key", geminiState.apiKey)
        }
    }

    @Test
    fun `onChangeApiKey updates API key in repository`() = runTest {
        fakeRepository.setCurrentService(Service.Groq)
        val viewModel = SettingsViewModel(fakeRepository)

        viewModel.state.test {
            val initialState = awaitItem()

            initialState.onChangeApiKey("new-api-key")

            // Wait for state update (may have multiple emissions due to connection check)
            val updatedState = awaitItem()
            assertEquals("new-api-key", updatedState.apiKey)
            assertTrue(fakeRepository.updateApiKeyCalls.contains(Service.Groq to "new-api-key"))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onSelectModel updates selected model`() = runTest {
        val models = listOf(
            SettingsModel(id = "model-1", subtitle = "Model 1", isSelected = true),
            SettingsModel(id = "model-2", subtitle = "Model 2", isSelected = false),
        )
        fakeRepository.setModels(Service.Gemini, models)
        fakeRepository.setCurrentService(Service.Gemini)

        val viewModel = SettingsViewModel(fakeRepository)

        viewModel.state.test {
            // Skip initial value from stateIn, wait for flatMapLatest to emit
            skipItems(1)
            val initialState = awaitItem()
            assertEquals("model-1", initialState.selectedModel?.id)

            initialState.onSelectModel("model-2")
            testDispatcher.scheduler.advanceUntilIdle()

            val updatedState = awaitItem()
            assertEquals("model-2", updatedState.selectedModel?.id)
            assertTrue(fakeRepository.updateSelectedModelCalls.contains(Service.Gemini to "model-2"))
        }
    }

    @Test
    fun `models are sorted by createdAt for Groq service`() = runTest {
        val models = listOf(
            SettingsModel(id = "model-old", subtitle = "Old Model", createdAt = 1000L),
            SettingsModel(id = "model-new", subtitle = "New Model", createdAt = 3000L),
            SettingsModel(id = "model-mid", subtitle = "Mid Model", createdAt = 2000L),
        )
        fakeRepository.setModels(Service.Groq, models)
        fakeRepository.setCurrentService(Service.Groq)

        val viewModel = SettingsViewModel(fakeRepository)

        viewModel.state.test {
            // Skip initial value from stateIn, wait for flatMapLatest to emit
            skipItems(1)
            val state = awaitItem()
            assertEquals(3, state.models.size)
            assertEquals("model-new", state.models[0].id)
            assertEquals("model-mid", state.models[1].id)
            assertEquals("model-old", state.models[2].id)
        }
    }

    @Test
    fun `services list contains all available services`() = runTest {
        val viewModel = SettingsViewModel(fakeRepository)

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(5, state.services.size)
            assertTrue(state.services.contains(Service.Free))
            assertTrue(state.services.contains(Service.Gemini))
            assertTrue(state.services.contains(Service.XAI))
            assertTrue(state.services.contains(Service.Groq))
            assertTrue(state.services.contains(Service.OpenAICompatible))
        }
    }
}
