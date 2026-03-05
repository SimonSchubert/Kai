package com.inspiredandroid.kai.ui.settings

import app.cash.turbine.test
import com.inspiredandroid.kai.DaemonController
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
    private val fakeDaemonController = object : DaemonController {
        override fun start() {}
        override fun stop() {}
    }

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
    fun `initial state has empty configured services when none configured`() = runTest {
        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController)

        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.configuredServices.isEmpty())
        }
    }

    @Test
    fun `initial state reflects configured services`() = runTest {
        fakeRepository.setConfiguredServices(Service.Gemini, Service.OpenAI)

        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController)

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(2, state.configuredServices.size)
            assertEquals(Service.Gemini, state.configuredServices[0].service)
            assertEquals("gemini", state.configuredServices[0].instanceId)
            assertEquals(Service.OpenAI, state.configuredServices[1].service)
            assertEquals("openai", state.configuredServices[1].instanceId)
        }
    }

    @Test
    fun `onAddService adds a new configured service`() = runTest {
        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController)

        viewModel.state.test {
            val initialState = awaitItem()
            assertTrue(initialState.configuredServices.isEmpty())

            initialState.onAddService(Service.Groq)
            testDispatcher.scheduler.advanceUntilIdle()

            val updatedState = awaitItem()
            assertEquals(1, updatedState.configuredServices.size)
            assertEquals(Service.Groq, updatedState.configuredServices[0].service)
            assertEquals("groqcloud", updatedState.expandedServiceId)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onRemoveService removes a configured service by instanceId`() = runTest {
        fakeRepository.setConfiguredServices(Service.Gemini, Service.OpenAI)
        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController)

        viewModel.state.test {
            val initialState = awaitItem()
            assertEquals(2, initialState.configuredServices.size)

            // Remove by instanceId (which equals serviceId for first instances)
            initialState.onRemoveService("gemini")
            testDispatcher.scheduler.advanceUntilIdle()

            val updatedState = awaitItem()
            assertEquals(1, updatedState.configuredServices.size)
            assertEquals(Service.OpenAI, updatedState.configuredServices[0].service)
        }
    }

    @Test
    fun `availableServicesToAdd contains all non-Free services`() = runTest {
        fakeRepository.setConfiguredServices(Service.Gemini)

        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController)

        viewModel.state.test {
            val state = awaitItem()
            // Should not contain Free
            assertTrue(state.availableServicesToAdd.none { it == Service.Free })
            // Should contain all other services including already-configured ones (multi-instance)
            assertTrue(state.availableServicesToAdd.contains(Service.Gemini))
            assertTrue(state.availableServicesToAdd.contains(Service.OpenAI))
            assertTrue(state.availableServicesToAdd.contains(Service.DeepSeek))
        }
    }

    @Test
    fun `adding same service type twice creates unique instanceIds`() = runTest {
        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController)

        viewModel.state.test {
            val initialState = awaitItem()

            initialState.onAddService(Service.OpenAI)
            testDispatcher.scheduler.advanceUntilIdle()
            val afterFirst = awaitItem()
            assertEquals(1, afterFirst.configuredServices.size)
            assertEquals("openai", afterFirst.configuredServices[0].instanceId)

            afterFirst.onAddService(Service.OpenAI)
            testDispatcher.scheduler.advanceUntilIdle()
            val afterSecond = awaitItem()
            assertEquals(2, afterSecond.configuredServices.size)
            assertEquals("openai", afterSecond.configuredServices[0].instanceId)
            assertEquals("openai_2", afterSecond.configuredServices[1].instanceId)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onChangeApiKey updates API key for specific instance`() = runTest {
        fakeRepository.setConfiguredServices(Service.Groq)
        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController)

        viewModel.state.test {
            val initialState = awaitItem()

            // Use instanceId instead of Service
            initialState.onChangeApiKey("groqcloud", "new-api-key")

            val updatedState = awaitItem()
            assertEquals("new-api-key", updatedState.configuredServices[0].apiKey)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
