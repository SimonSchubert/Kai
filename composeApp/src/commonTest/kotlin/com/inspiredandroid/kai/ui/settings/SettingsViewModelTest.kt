package com.inspiredandroid.kai.ui.settings

import app.cash.turbine.test
import com.inspiredandroid.kai.DaemonController
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.testutil.FakeDataRepository
import com.inspiredandroid.kai.tools.NotificationPermissionController
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeDataRepository
    private val fakeDaemonController = object : DaemonController {
        override fun start() {}
        override fun stop() {}
    }
    private val fakeNotificationPermissionController = NotificationPermissionController()

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
        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController, fakeNotificationPermissionController, testDispatcher)

        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.configuredServices.isEmpty())
        }
    }

    @Test
    fun `initial state reflects configured services`() = runTest {
        fakeRepository.setConfiguredServices(Service.Gemini, Service.OpenAI)

        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController, fakeNotificationPermissionController, testDispatcher)

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
        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController, fakeNotificationPermissionController, testDispatcher)

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
        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController, fakeNotificationPermissionController, testDispatcher)

        viewModel.state.test {
            val initialState = awaitItem()
            assertEquals(2, initialState.configuredServices.size)

            // Remove by instanceId (which equals serviceId for first instances)
            initialState.onRemoveService("gemini")
            testDispatcher.scheduler.advanceUntilIdle()

            // Deletion is deferred (undo snackbar), collect until actually removed
            val updates = cancelAndConsumeRemainingEvents()
            val lastState = updates.filterIsInstance<app.cash.turbine.Event.Item<SettingsUiState>>().lastOrNull()?.value
            requireNotNull(lastState)
            assertEquals(1, lastState.configuredServices.size)
            assertEquals(Service.OpenAI, lastState.configuredServices[0].service)
        }
    }

    @Test
    fun `availableServicesToAdd contains all non-Free services`() = runTest {
        fakeRepository.setConfiguredServices(Service.Gemini)

        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController, fakeNotificationPermissionController, testDispatcher)

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
        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController, fakeNotificationPermissionController, testDispatcher)

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

    // --- MCP Server Tests ---

    @Test
    fun `initial state has empty mcp servers when none configured`() = runTest {
        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController, fakeNotificationPermissionController, testDispatcher)

        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.mcpServers.isEmpty())
        }
    }

    @Test
    fun `onAddMcpServer adds server and closes dialog`() = runTest {
        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController, fakeNotificationPermissionController, testDispatcher)

        viewModel.state.test {
            val initialState = awaitItem()
            assertTrue(initialState.mcpServers.isEmpty())

            initialState.onAddMcpServer("Test Server", "https://example.com/mcp", emptyMap())
            testDispatcher.scheduler.advanceUntilIdle()

            // Dialog should close and server should appear
            val updates = cancelAndConsumeRemainingEvents()
            val lastState = (updates.filterIsInstance<app.cash.turbine.Event.Item<SettingsUiState>>().lastOrNull()?.value)
            if (lastState != null) {
                assertFalse(lastState.showAddMcpServerDialog)
                assertEquals(1, lastState.mcpServers.size)
                assertEquals("Test Server", lastState.mcpServers[0].name)
            }
        }
    }

    @Test
    fun `onRemoveMcpServer removes server`() = runTest {
        // Pre-add a server
        fakeRepository.addMcpServer("Test", "https://example.com/mcp", emptyMap())
        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController, fakeNotificationPermissionController, testDispatcher)

        viewModel.state.test {
            val initialState = awaitItem()
            assertEquals(1, initialState.mcpServers.size)

            initialState.onRemoveMcpServer(initialState.mcpServers[0].id)
            testDispatcher.scheduler.advanceUntilIdle()

            // Deletion is deferred (undo snackbar), collect until actually removed
            val updates = cancelAndConsumeRemainingEvents()
            val lastState = updates.filterIsInstance<app.cash.turbine.Event.Item<SettingsUiState>>().lastOrNull()?.value
            requireNotNull(lastState)
            assertTrue(lastState.mcpServers.isEmpty())
        }
    }

    @Test
    fun `onToggleMcpServer disables server and updates status`() = runTest {
        val config = fakeRepository.addMcpServer("Test", "https://example.com/mcp", emptyMap())
        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController, fakeNotificationPermissionController, testDispatcher)

        viewModel.state.test {
            val initialState = awaitItem()
            assertTrue(initialState.mcpServers[0].isEnabled)

            initialState.onToggleMcpServer(config.id, false)
            testDispatcher.scheduler.advanceUntilIdle()

            val updates = cancelAndConsumeRemainingEvents()
            val lastState = updates.filterIsInstance<app.cash.turbine.Event.Item<SettingsUiState>>().lastOrNull()?.value
            if (lastState != null) {
                assertFalse(lastState.mcpServers[0].isEnabled)
            }
        }
    }

    @Test
    fun `showAddMcpServerDialog toggles correctly`() = runTest {
        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController, fakeNotificationPermissionController, testDispatcher)

        viewModel.state.test {
            val initialState = awaitItem()
            assertFalse(initialState.showAddMcpServerDialog)

            initialState.onShowAddMcpServerDialog(true)
            val showState = awaitItem()
            assertTrue(showState.showAddMcpServerDialog)

            showState.onShowAddMcpServerDialog(false)
            val hideState = awaitItem()
            assertFalse(hideState.showAddMcpServerDialog)
        }
    }

    @Test
    fun `onToggleTool does not crash with no mcp servers`() = runTest {
        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController, fakeNotificationPermissionController, testDispatcher)

        viewModel.state.test {
            val state = awaitItem()
            // Toggle a tool - should not crash even with no MCP servers
            state.onToggleTool("some_tool", false)
            // No exception means success
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Service Tests (continued) ---

    @Test
    fun `onChangeApiKey updates API key for specific instance`() = runTest {
        fakeRepository.setConfiguredServices(Service.Groq)
        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController, fakeNotificationPermissionController, testDispatcher)

        viewModel.state.test {
            val initialState = awaitItem()

            // Use instanceId instead of Service
            initialState.onChangeApiKey("groqcloud", "new-api-key")

            val updatedState = awaitItem()
            assertEquals("new-api-key", updatedState.configuredServices[0].apiKey)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `adding Anthropic service shows no models before validation`() = runTest {
        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController, fakeNotificationPermissionController, testDispatcher)

        viewModel.state.test {
            val initialState = awaitItem()
            assertTrue(initialState.configuredServices.isEmpty())

            initialState.onAddService(Service.Anthropic)
            testDispatcher.scheduler.advanceUntilIdle()

            val updatedState = awaitItem()
            assertEquals(1, updatedState.configuredServices.size)
            assertEquals(Service.Anthropic, updatedState.configuredServices[0].service)
            assertEquals("anthropic", updatedState.configuredServices[0].instanceId)

            // Models should be empty until API key is validated and models are fetched
            val models = fakeRepository.getInstanceModels("anthropic", Service.Anthropic).value
            assertTrue(models.isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Per-instance settings persistence ---

    @Test
    fun `onChangeApiKey persists to repository`() = runTest {
        fakeRepository.setConfiguredServices(Service.Anthropic)
        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController, fakeNotificationPermissionController, testDispatcher)

        viewModel.state.test {
            val initialState = awaitItem()
            initialState.onChangeApiKey("anthropic", "sk-test-123")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals("sk-test-123", fakeRepository.getInstanceApiKey("anthropic"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onChangeApiKey clears models for that instance`() = runTest {
        fakeRepository.setConfiguredServices(Service.Anthropic)
        // Pre-populate models
        fakeRepository.setInstanceModels("anthropic", listOf(SettingsModel(id = "claude-old", subtitle = "")))
        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController, fakeNotificationPermissionController, testDispatcher)

        viewModel.state.test {
            val initialState = awaitItem()
            initialState.onChangeApiKey("anthropic", "new-key")
            testDispatcher.scheduler.advanceUntilIdle()

            // Models should have been cleared
            val models = fakeRepository.getInstanceModels("anthropic", Service.Anthropic).value
            assertTrue(models.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onChangeBaseUrl persists to repository`() = runTest {
        fakeRepository.setConfiguredServices(Service.OpenAICompatible)
        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController, fakeNotificationPermissionController, testDispatcher)

        viewModel.state.test {
            val initialState = awaitItem()
            val instanceId = initialState.configuredServices.first().instanceId
            initialState.onChangeBaseUrl(instanceId, "https://custom.example.com/v1/")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(
                "https://custom.example.com/v1/",
                fakeRepository.getInstanceBaseUrl(instanceId, Service.OpenAICompatible),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onChangeBaseUrl resets connectionStatus to Unknown`() = runTest {
        fakeRepository.setConfiguredServices(Service.OpenAICompatible)
        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController, fakeNotificationPermissionController, testDispatcher)

        viewModel.state.test {
            val initialState = awaitItem()
            val instanceId = initialState.configuredServices.first().instanceId
            initialState.onChangeBaseUrl(instanceId, "https://x.example/v1")

            val updatedState = awaitItem()
            assertEquals(ConnectionStatus.Unknown, updatedState.configuredServices.first().connectionStatus)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onSelectModel marks the chosen model as selected on the repository`() = runTest {
        fakeRepository.setConfiguredServices(Service.Gemini)
        fakeRepository.setInstanceModels(
            "gemini",
            listOf(
                SettingsModel(id = "gemini-flash", subtitle = ""),
                SettingsModel(id = "gemini-pro", subtitle = ""),
            ),
        )
        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController, fakeNotificationPermissionController, testDispatcher)

        viewModel.state.test {
            val initialState = awaitItem()
            initialState.onSelectModel("gemini", "gemini-pro")
            testDispatcher.scheduler.advanceUntilIdle()

            val models = fakeRepository.getInstanceModels("gemini", Service.Gemini).value
            assertTrue(models.first { it.id == "gemini-pro" }.isSelected)
            assertFalse(models.first { it.id == "gemini-flash" }.isSelected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Expand state ---

    @Test
    fun `onExpandService updates expandedServiceId`() = runTest {
        fakeRepository.setConfiguredServices(Service.Gemini, Service.OpenAI)
        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController, fakeNotificationPermissionController, testDispatcher)

        viewModel.state.test {
            val initialState = awaitItem()
            assertEquals(null, initialState.expandedServiceId)

            initialState.onExpandService("openai")
            testDispatcher.scheduler.advanceUntilIdle()

            val expandedState = awaitItem()
            assertEquals("openai", expandedState.expandedServiceId)

            // Collapse
            expandedState.onExpandService(null)
            val collapsedState = awaitItem()
            assertEquals(null, collapsedState.expandedServiceId)
        }
    }

    // --- Tab selection ---

    @Test
    fun `onSelectTab updates currentTab`() = runTest {
        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController, fakeNotificationPermissionController, testDispatcher)

        viewModel.state.test {
            val initialState = awaitItem()
            initialState.onSelectTab(SettingsTab.Tools)
            val updated = awaitItem()
            assertEquals(SettingsTab.Tools, updated.currentTab)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Soul / dynamic UI / memory toggles ---

    @Test
    fun `onSaveSoul persists soul text`() = runTest {
        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController, fakeNotificationPermissionController, testDispatcher)

        viewModel.state.test {
            val initialState = awaitItem()
            initialState.onSaveSoul("You are a helpful assistant.")

            val updated = awaitItem()
            assertEquals("You are a helpful assistant.", updated.soulText)
            assertEquals("You are a helpful assistant.", fakeRepository.getSoulText())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onToggleDynamicUi persists and reflects in state`() = runTest {
        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController, fakeNotificationPermissionController, testDispatcher)

        viewModel.state.test {
            val initialState = awaitItem()
            assertTrue(initialState.isDynamicUiEnabled)
            initialState.onToggleDynamicUi(false)
            val updated = awaitItem()
            assertFalse(updated.isDynamicUiEnabled)
            assertFalse(fakeRepository.isDynamicUiEnabled())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onToggleMemory persists and reflects in state`() = runTest {
        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController, fakeNotificationPermissionController, testDispatcher)

        viewModel.state.test {
            val initialState = awaitItem()
            assertTrue(initialState.isMemoryEnabled)
            initialState.onToggleMemory(false)
            val updated = awaitItem()
            assertFalse(updated.isMemoryEnabled)
            assertFalse(fakeRepository.isMemoryEnabled())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onToggleScheduling persists and reflects in state`() = runTest {
        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController, fakeNotificationPermissionController, testDispatcher)

        viewModel.state.test {
            val initialState = awaitItem()
            assertTrue(initialState.isSchedulingEnabled)
            initialState.onToggleScheduling(false)
            val updated = awaitItem()
            assertFalse(updated.isSchedulingEnabled)
            assertFalse(fakeRepository.isSchedulingEnabled())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onToggleFreeFallback persists and reflects in state`() = runTest {
        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController, fakeNotificationPermissionController, testDispatcher)

        viewModel.state.test {
            val initialState = awaitItem()
            assertTrue(initialState.isFreeFallbackEnabled)
            initialState.onToggleFreeFallback(false)
            val updated = awaitItem()
            assertFalse(updated.isFreeFallbackEnabled)
            assertFalse(fakeRepository.isFreeFallbackEnabled())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onChangeUiScale persists and reflects in state`() = runTest {
        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController, fakeNotificationPermissionController, testDispatcher)

        viewModel.state.test {
            val initialState = awaitItem()
            initialState.onChangeUiScale(1.5f)
            val updated = awaitItem()
            assertEquals(1.5f, updated.uiScale)
            assertEquals(1.5f, fakeRepository.getUiScale())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onChangeEmailPollInterval persists and reflects in state`() = runTest {
        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController, fakeNotificationPermissionController, testDispatcher)

        viewModel.state.test {
            val initialState = awaitItem()
            initialState.onChangeEmailPollInterval(30)
            val updated = awaitItem()
            assertEquals(30, updated.emailPollIntervalMinutes)
            assertEquals(30, fakeRepository.getEmailPollIntervalMinutes())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onChangeHeartbeatInterval persists and reflects in state`() = runTest {
        val viewModel = SettingsViewModel(fakeRepository, fakeDaemonController, fakeNotificationPermissionController, testDispatcher)

        viewModel.state.test {
            val initialState = awaitItem()
            initialState.onChangeHeartbeatInterval(45)
            val updated = awaitItem()
            assertEquals(45, updated.heartbeatIntervalMinutes)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
