package com.inspiredandroid.kai.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.DaemonController
import com.inspiredandroid.kai.data.DataRepository
import com.inspiredandroid.kai.data.ImportSection
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.getBackgroundDispatcher
import com.inspiredandroid.kai.httpClient
import com.inspiredandroid.kai.inference.LocalModel
import com.inspiredandroid.kai.isDesktopPlatform
import com.inspiredandroid.kai.isEmailSupported
import com.inspiredandroid.kai.mcp.PopularMcpServer
import com.inspiredandroid.kai.network.AnthropicInsufficientCreditsException
import com.inspiredandroid.kai.network.AnthropicInvalidApiKeyException
import com.inspiredandroid.kai.network.AnthropicOverloadedException
import com.inspiredandroid.kai.network.AnthropicRateLimitExceededException
import com.inspiredandroid.kai.network.GeminiInvalidApiKeyException
import com.inspiredandroid.kai.network.GeminiRateLimitExceededException
import com.inspiredandroid.kai.network.OpenAICompatibleConnectionException
import com.inspiredandroid.kai.network.OpenAICompatibleInvalidApiKeyException
import com.inspiredandroid.kai.network.OpenAICompatibleQuotaExhaustedException
import com.inspiredandroid.kai.network.OpenAICompatibleRateLimitExceededException
import com.inspiredandroid.kai.network.dtos.SponsorsResponseDto
import com.inspiredandroid.kai.platformName
import com.inspiredandroid.kai.tools.NotificationPermissionController
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext

class SettingsViewModel(
    private val dataRepository: DataRepository,
    private val daemonController: DaemonController,
    private val notificationPermissionController: NotificationPermissionController,
    private val backgroundDispatcher: CoroutineContext = getBackgroundDispatcher(),
) : ViewModel() {

    private var connectionCheckJobs: MutableMap<String, Job> = mutableMapOf()
    private var hasCheckedInitialConnection = false
    private var pendingDeleteJob: Job? = null

    private fun buildFullState(): SettingsUiState = SettingsUiState(
        configuredServices = buildConfiguredServiceEntries().toImmutableList(),
        availableServicesToAdd = computeAvailableServices().toImmutableList(),
        tools = dataRepository.getToolDefinitions().toImmutableList(),
        soulText = dataRepository.getSoulText(),
        isDynamicUiEnabled = dataRepository.isDynamicUiEnabled(),
        isMemoryEnabled = dataRepository.isMemoryEnabled(),
        memories = dataRepository.getMemories().toImmutableList(),
        isSchedulingEnabled = dataRepository.isSchedulingEnabled(),
        scheduledTasks = dataRepository.getScheduledTasks().toImmutableList(),
        isDaemonEnabled = dataRepository.isDaemonEnabled(),
        showDaemonToggle = platformName == "Android",
        isHeartbeatEnabled = dataRepository.getHeartbeatConfig().enabled,
        heartbeatIntervalMinutes = dataRepository.getHeartbeatConfig().intervalMinutes,
        heartbeatActiveHoursStart = dataRepository.getHeartbeatConfig().activeHoursStart,
        heartbeatActiveHoursEnd = dataRepository.getHeartbeatConfig().activeHoursEnd,
        heartbeatPrompt = dataRepository.getHeartbeatPrompt(),
        heartbeatLog = dataRepository.getHeartbeatLog().toImmutableList(),
        heartbeatServiceEntries = dataRepository.getServiceEntries().filter { !Service.fromId(it.serviceId).isOnDevice }.toImmutableList(),
        heartbeatSelectedInstanceId = dataRepository.getHeartbeatInstanceId()?.takeIf { id ->
            dataRepository.getServiceEntries().any { it.instanceId == id }
        }.also { validId ->
            val savedId = dataRepository.getHeartbeatInstanceId()
            if (savedId != null && validId == null) dataRepository.setHeartbeatInstanceId(null)
        },
        isEmailEnabled = dataRepository.isEmailEnabled(),
        showEmailToggle = isEmailSupported,
        emailAccounts = dataRepository.getEmailAccounts().toImmutableList(),
        emailPollIntervalMinutes = dataRepository.getEmailPollIntervalMinutes(),
        isFreeFallbackEnabled = dataRepository.isFreeFallbackEnabled(),
        uiScale = dataRepository.getUiScale(),
        showUiScale = isDesktopPlatform,
        mcpServers = buildMcpServerEntries().toImmutableList(),
        localAvailableModels = dataRepository.getLocalAvailableModels().toImmutableList(),
        totalDeviceMemoryBytes = dataRepository.getTotalDeviceMemoryBytes(),
        localFreeSpaceBytes = dataRepository.getLocalFreeSpaceBytes(),
        localDownloadingModelId = dataRepository.getLocalDownloadingModelId()?.value,
        localDownloadProgress = dataRepository.getLocalDownloadProgress()?.value,
        modelContextTokens = buildModelContextTokensMap(),
    )

    // Bound once so downstream Compose skipping works — a new SettingsActions
    // instance on every state emission would defeat it.
    val actions: SettingsActions = SettingsActions(
        onSelectTab = ::onSelectTab,
        onAddService = ::onAddService,
        onRemoveService = ::onRemoveService,
        onReorderServices = ::onReorderServices,
        onExpandService = ::onExpandService,
        onChangeApiKey = ::onChangeApiKey,
        onChangeBaseUrl = ::onChangeBaseUrl,
        onSelectModel = ::onSelectModel,
        onToggleTool = ::onToggleTool,
        onSaveSoul = ::onSaveSoul,
        onToggleDynamicUi = ::onToggleDynamicUi,
        onToggleMemory = ::onToggleMemory,
        onDeleteMemory = ::onDeleteMemory,
        onToggleScheduling = ::onToggleScheduling,
        onCancelTask = ::onCancelTask,
        onToggleDaemon = ::onToggleDaemon,
        onToggleHeartbeat = ::onToggleHeartbeat,
        onChangeHeartbeatInterval = ::onChangeHeartbeatInterval,
        onChangeHeartbeatActiveHours = ::onChangeHeartbeatActiveHours,
        onSaveHeartbeatPrompt = ::onSaveHeartbeatPrompt,
        onChangeHeartbeatService = ::onChangeHeartbeatService,
        onToggleEmail = ::onToggleEmail,
        onRemoveEmailAccount = ::onRemoveEmailAccount,
        onChangeEmailPollInterval = ::onChangeEmailPollInterval,
        onToggleFreeFallback = ::onToggleFreeFallback,
        onChangeUiScale = ::onChangeUiScale,
        onAddMcpServer = ::onAddMcpServer,
        onRemoveMcpServer = ::onRemoveMcpServer,
        onToggleMcpServer = ::onToggleMcpServer,
        onRefreshMcpServer = ::onRefreshMcpServer,
        onShowAddMcpServerDialog = ::onShowAddMcpServerDialog,
        onAddPopularMcpServer = ::onAddPopularMcpServer,
        onDownloadLocalModel = ::onDownloadLocalModel,
        onCancelLocalModelDownload = ::onCancelLocalModelDownload,
        onDeleteLocalModel = ::onDeleteLocalModel,
        onChangeModelContextTokens = ::onChangeModelContextTokens,
        onExportSettings = ::onExportSettings,
        onImportSettings = ::onImportSettings,
        onUndoDelete = ::onUndoDelete,
    )

    private val _state = MutableStateFlow(buildFullState())

    val state = _state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _state.value,
    )

    init {
        // Observe download state from the engine singleton (survives activity recreation)
        val downloadingFlow = dataRepository.getLocalDownloadingModelId() ?: flowOf(null)
        val progressFlow = dataRepository.getLocalDownloadProgress() ?: flowOf(null)
        val errorFlow = dataRepository.getLocalDownloadError() ?: flowOf(null)
        viewModelScope.launch {
            combine(downloadingFlow, progressFlow, errorFlow) { modelId, progress, error ->
                Triple(modelId, progress, error)
            }.collect { (modelId, progress, error) ->
                val wasDownloading = _state.value.localDownloadingModelId != null
                _state.update {
                    it.copy(
                        localDownloadingModelId = modelId,
                        localDownloadProgress = progress,
                        localDownloadError = error,
                    )
                }
                if (modelId == null && wasDownloading) {
                    // Download finished or cancelled — refresh
                    _state.update { it.copy(localFreeSpaceBytes = dataRepository.getLocalFreeSpaceBytes()) }
                    refreshServiceList()
                    _state.value.configuredServices
                        .filter { it.service.isOnDevice }
                        .forEach { checkConnection(it.instanceId, it.service) }
                }
            }
        }
    }

    fun onScreenVisible() {
        if (!hasCheckedInitialConnection) {
            hasCheckedInitialConnection = true
            checkAllConnections()
            connectEnabledMcpServers()
            fetchSponsors()
        }
    }

    private fun fetchSponsors() {
        viewModelScope.launch(backgroundDispatcher) {
            try {
                val client = httpClient {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true })
                    }
                }
                val response = client.get("https://ghs.vercel.app/v3/sponsors/SimonSchubert")
                if (response.status.isSuccess()) {
                    val dto = response.body<SponsorsResponseDto>()
                    _state.update {
                        it.copy(
                            currentSponsors = dto.sponsors.current.toImmutableList(),
                            pastSponsors = dto.sponsors.past.toImmutableList(),
                        )
                    }
                }
            } catch (_: Exception) {
                // Silently ignore - sponsors are non-critical
            }
        }
    }

    private fun buildConfiguredServiceEntries(): List<ConfiguredServiceEntry> = dataRepository.getConfiguredServiceInstances().map { instance ->
        val service = Service.fromId(instance.serviceId)
        val models = dataRepository.getInstanceModels(instance.instanceId, service).value
        ConfiguredServiceEntry(
            instanceId = instance.instanceId,
            service = service,
            apiKey = dataRepository.getInstanceApiKey(instance.instanceId),
            baseUrl = dataRepository.getInstanceBaseUrl(instance.instanceId, service),
            selectedModel = models.firstOrNull { it.isSelected },
            models = models.toImmutableList(),
        )
    }

    private fun computeAvailableServices(): List<Service> {
        // Allow all non-Free services (multiple instances of same type are allowed)
        // Pin OpenAI-Compatible and LiteRT (Local Model) to the top, then sort the rest alphabetically
        // Hide on-device services on platforms that don't support them
        return Service.all
            .filter { it != Service.Free }
            .filter { !it.isOnDevice || dataRepository.isLocalInferenceAvailable() }
            .sortedWith(compareBy<Service> { !(it is Service.OpenAICompatible || it.isOnDevice) }.thenBy { it.displayName })
    }

    private fun refreshServiceList() {
        _state.update { current ->
            val existingStatuses = current.configuredServices.associate { it.instanceId to it.connectionStatus }
            val newEntries = buildConfiguredServiceEntries().map { entry ->
                val preservedStatus = existingStatuses[entry.instanceId]
                if (preservedStatus != null) entry.copy(connectionStatus = preservedStatus) else entry
            }
            current.copy(
                configuredServices = newEntries.toImmutableList(),
                availableServicesToAdd = computeAvailableServices().toImmutableList(),
            )
        }
    }

    private fun onSelectTab(tab: SettingsTab) {
        _state.update { it.copy(currentTab = tab) }
    }

    private fun onAddService(service: Service) {
        val instance = dataRepository.addConfiguredService(service.id)
        refreshServiceList()
        _state.update { it.copy(expandedServiceId = instance.instanceId) }
        checkConnection(instance.instanceId, service)
    }

    private fun onRemoveService(instanceId: String) {
        commitPendingDeletion()
        _state.update {
            it.copy(
                expandedServiceId = if (it.expandedServiceId == instanceId) null else it.expandedServiceId,
                pendingDeletion = PendingDeletion.Service(instanceId),
            )
        }
        pendingDeleteJob = viewModelScope.launch(backgroundDispatcher) {
            delay(4000)
            executeDeletion(PendingDeletion.Service(instanceId))
        }
    }

    private fun onReorderServices(orderedIds: List<String>) {
        dataRepository.reorderConfiguredServices(orderedIds)
        refreshServiceList()
    }

    private fun onExpandService(instanceId: String?) {
        _state.update { it.copy(expandedServiceId = instanceId) }
        if (instanceId != null) {
            refreshInstanceModels(instanceId)
        }
    }

    private fun refreshInstanceModels(instanceId: String) {
        val entry = _state.value.configuredServices.find { it.instanceId == instanceId } ?: return
        val models = dataRepository.getInstanceModels(instanceId, entry.service).value
        _state.update { state ->
            state.copy(
                configuredServices = state.configuredServices.map { e ->
                    if (e.instanceId == instanceId) {
                        e.copy(
                            models = models.toImmutableList(),
                            selectedModel = models.firstOrNull { it.isSelected },
                        )
                    } else {
                        e
                    }
                }.toImmutableList(),
            )
        }
    }

    private fun onChangeApiKey(instanceId: String, apiKey: String) {
        val entry = _state.value.configuredServices.find { it.instanceId == instanceId } ?: return
        dataRepository.updateInstanceApiKey(instanceId, apiKey)
        dataRepository.clearInstanceModels(instanceId, entry.service)
        _state.update { state ->
            state.copy(
                configuredServices = state.configuredServices.map { e ->
                    if (e.instanceId == instanceId) {
                        e.copy(apiKey = apiKey, connectionStatus = ConnectionStatus.Unknown)
                    } else {
                        e
                    }
                }.toImmutableList(),
            )
        }
        checkConnectionDebounced(instanceId, entry.service)
    }

    private fun onChangeBaseUrl(instanceId: String, baseUrl: String) {
        val entry = _state.value.configuredServices.find { it.instanceId == instanceId } ?: return
        dataRepository.updateInstanceBaseUrl(instanceId, baseUrl)
        dataRepository.clearInstanceModels(instanceId, entry.service)
        _state.update { state ->
            state.copy(
                configuredServices = state.configuredServices.map { e ->
                    if (e.instanceId == instanceId) {
                        e.copy(baseUrl = baseUrl, connectionStatus = ConnectionStatus.Unknown)
                    } else {
                        e
                    }
                }.toImmutableList(),
            )
        }
        checkConnectionDebounced(instanceId, entry.service)
    }

    private fun onSelectModel(instanceId: String, modelId: String) {
        val entry = _state.value.configuredServices.find { it.instanceId == instanceId } ?: return
        dataRepository.updateInstanceSelectedModel(instanceId, entry.service, modelId)
        refreshInstanceModels(instanceId)
    }

    private fun onSaveSoul(text: String) {
        dataRepository.setSoulText(text)
        _state.update { it.copy(soulText = text) }
    }

    private fun onToggleDynamicUi(enabled: Boolean) {
        dataRepository.setDynamicUiEnabled(enabled)
        _state.update { it.copy(isDynamicUiEnabled = enabled) }
    }

    private fun onToggleMemory(enabled: Boolean) {
        dataRepository.setMemoryEnabled(enabled)
        _state.update { it.copy(isMemoryEnabled = enabled) }
    }

    private fun onDeleteMemory(key: String) {
        commitPendingDeletion()
        _state.update { it.copy(pendingDeletion = PendingDeletion.Memory(key)) }
        pendingDeleteJob = viewModelScope.launch(backgroundDispatcher) {
            delay(4000)
            executeDeletion(PendingDeletion.Memory(key))
        }
    }

    private fun onToggleScheduling(enabled: Boolean) {
        dataRepository.setSchedulingEnabled(enabled)
        _state.update { it.copy(isSchedulingEnabled = enabled) }
    }

    private fun onCancelTask(id: String) {
        commitPendingDeletion()
        _state.update { it.copy(pendingDeletion = PendingDeletion.Task(id)) }
        pendingDeleteJob = viewModelScope.launch(backgroundDispatcher) {
            delay(4000)
            executeDeletion(PendingDeletion.Task(id))
        }
    }

    private fun onToggleDaemon(enabled: Boolean) {
        dataRepository.setDaemonEnabled(enabled)
        if (enabled) {
            viewModelScope.launch { notificationPermissionController.requestPermission() }
            daemonController.start()
        } else {
            daemonController.stop()
        }
        _state.update { it.copy(isDaemonEnabled = enabled) }
    }

    private fun onToggleHeartbeat(enabled: Boolean) {
        dataRepository.setHeartbeatEnabled(enabled)
        _state.update { it.copy(isHeartbeatEnabled = enabled) }
    }

    private fun onChangeHeartbeatInterval(minutes: Int) {
        dataRepository.setHeartbeatIntervalMinutes(minutes)
        _state.update { it.copy(heartbeatIntervalMinutes = minutes) }
    }

    private fun onChangeHeartbeatActiveHours(start: Int, end: Int) {
        dataRepository.setHeartbeatActiveHours(start, end)
        _state.update { it.copy(heartbeatActiveHoursStart = start, heartbeatActiveHoursEnd = end) }
    }

    private fun onSaveHeartbeatPrompt(text: String) {
        dataRepository.setHeartbeatPrompt(text)
        _state.update { it.copy(heartbeatPrompt = text) }
    }

    private fun onChangeHeartbeatService(instanceId: String?) {
        dataRepository.setHeartbeatInstanceId(instanceId)
        _state.update { it.copy(heartbeatSelectedInstanceId = instanceId) }
    }

    private fun onToggleEmail(enabled: Boolean) {
        dataRepository.setEmailEnabled(enabled)
        _state.update { it.copy(isEmailEnabled = enabled) }
    }

    private fun onRemoveEmailAccount(id: String) {
        commitPendingDeletion()
        _state.update { it.copy(pendingDeletion = PendingDeletion.EmailAccount(id)) }
        pendingDeleteJob = viewModelScope.launch(backgroundDispatcher) {
            delay(4000)
            executeDeletion(PendingDeletion.EmailAccount(id))
        }
    }

    private fun onChangeEmailPollInterval(minutes: Int) {
        dataRepository.setEmailPollIntervalMinutes(minutes)
        _state.update { it.copy(emailPollIntervalMinutes = minutes) }
    }

    private fun onToggleFreeFallback(enabled: Boolean) {
        dataRepository.setFreeFallbackEnabled(enabled)
        _state.update { it.copy(isFreeFallbackEnabled = enabled) }
    }

    private fun onDownloadLocalModel(model: LocalModel) {
        dataRepository.startLocalModelDownload(model)
    }

    private fun onCancelLocalModelDownload() {
        dataRepository.cancelLocalModelDownload()
    }

    private fun onChangeModelContextTokens(modelId: String, contextTokens: Int) {
        if (_state.value.modelContextTokens[modelId] == contextTokens) return
        dataRepository.setModelContextTokens(modelId, contextTokens)
        _state.update {
            it.copy(modelContextTokens = it.modelContextTokens.toMutableMap().apply { put(modelId, contextTokens) }.toImmutableMap())
        }
        // Release engine so the next message re-initializes with the new context size
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.releaseLocalEngine()
        }
    }

    private fun buildModelContextTokensMap() = dataRepository.getLocalAvailableModels().associate { model ->
        val stored = dataRepository.getModelContextTokens(model.id)
        model.id to if (stored > 0) stored else model.defaultContextTokens
    }.toImmutableMap()

    private fun onDeleteLocalModel(modelId: String) {
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.deleteLocalModel(modelId)
            _state.update { it.copy(localFreeSpaceBytes = dataRepository.getLocalFreeSpaceBytes()) }
            refreshServiceList()
            _state.value.configuredServices
                .filter { it.service.isOnDevice }
                .forEach { checkConnection(it.instanceId, it.service) }
        }
    }

    private fun onChangeUiScale(scale: Float) {
        dataRepository.setUiScale(scale)
        _state.update { it.copy(uiScale = scale) }
    }

    private fun onExportSettings(): String = dataRepository.exportSettingsToJson()

    private fun onImportSettings(bytes: ByteArray, sections: Set<ImportSection>, replace: Boolean): ImportResult = try {
        val currentTab = _state.value.currentTab
        val errors = dataRepository.importSettingsFromJson(bytes.decodeToString(), sections, replace)
        _state.value = buildFullState().copy(currentTab = currentTab)
        checkAllConnections()
        connectEnabledMcpServers()
        if (errors == 0) ImportResult.Success else ImportResult.PartialSuccess(errors)
    } catch (_: Exception) {
        ImportResult.Failure
    }

    private fun onToggleTool(toolId: String, enabled: Boolean) {
        dataRepository.setToolEnabled(toolId, enabled)
        _state.update { state ->
            state.copy(
                tools = state.tools.map { tool ->
                    if (tool.id == toolId) tool.copy(isEnabled = enabled) else tool
                }.toImmutableList(),
                mcpServers = state.mcpServers.map { server ->
                    server.copy(
                        tools = server.tools.map { tool ->
                            if (tool.id == toolId) tool.copy(isEnabled = enabled) else tool
                        }.toImmutableList(),
                    )
                }.toImmutableList(),
            )
        }
    }

    // MCP server management
    private fun buildMcpServerEntries(): List<McpServerUiState> = dataRepository.getMcpServers().map { config ->
        McpServerUiState(
            id = config.id,
            name = config.name,
            url = config.url,
            isEnabled = config.isEnabled,
            connectionStatus = if (dataRepository.isMcpServerConnected(config.id)) {
                McpConnectionStatus.Connected
            } else {
                McpConnectionStatus.Unknown
            },
            tools = dataRepository.getMcpToolsForServer(config.id).toImmutableList(),
        )
    }

    private fun refreshMcpServers() {
        _state.update { current ->
            val existingStatuses = current.mcpServers.associate { it.id to it.connectionStatus }
            current.copy(
                mcpServers = buildMcpServerEntries().map { entry ->
                    val preservedStatus = existingStatuses[entry.id]
                    // Only preserve transient statuses (Connecting/Error) — derive Connected/Unknown from actual state
                    if (preservedStatus == McpConnectionStatus.Connecting || preservedStatus == McpConnectionStatus.Error) {
                        entry.copy(connectionStatus = preservedStatus)
                    } else {
                        entry
                    }
                }.toImmutableList(),
            )
        }
    }

    private fun onAddMcpServer(name: String, url: String, headers: Map<String, String>) {
        viewModelScope.launch(backgroundDispatcher) {
            val config = dataRepository.addMcpServer(name, url, headers)
            refreshMcpServers()
            connectMcpServerWithStatus(config.id)
        }
        _state.update { it.copy(showAddMcpServerDialog = false) }
    }

    private fun onRemoveMcpServer(serverId: String) {
        commitPendingDeletion()
        _state.update { it.copy(pendingDeletion = PendingDeletion.McpServer(serverId)) }
        pendingDeleteJob = viewModelScope.launch(backgroundDispatcher) {
            delay(4000)
            executeDeletion(PendingDeletion.McpServer(serverId))
        }
    }

    private fun onToggleMcpServer(serverId: String, enabled: Boolean) {
        dataRepository.setMcpServerEnabled(serverId, enabled)
        refreshMcpServers()
        if (enabled) {
            viewModelScope.launch(backgroundDispatcher) {
                connectMcpServerWithStatus(serverId)
            }
        }
    }

    private fun onRefreshMcpServer(serverId: String) {
        viewModelScope.launch(backgroundDispatcher) {
            connectMcpServerWithStatus(serverId)
        }
    }

    private fun onShowAddMcpServerDialog(show: Boolean) {
        _state.update { it.copy(showAddMcpServerDialog = show) }
    }

    private fun onAddPopularMcpServer(server: PopularMcpServer) {
        onAddMcpServer(server.name, server.url, emptyMap())
    }

    private suspend fun connectMcpServerWithStatus(serverId: String) {
        updateMcpConnectionStatus(serverId, McpConnectionStatus.Connecting)
        val result = dataRepository.connectMcpServer(serverId)
        if (result.isSuccess) {
            updateMcpConnectionStatus(serverId, McpConnectionStatus.Connected)
            refreshMcpServers()
        } else {
            updateMcpConnectionStatus(serverId, McpConnectionStatus.Error)
        }
    }

    private fun updateMcpConnectionStatus(serverId: String, status: McpConnectionStatus) {
        _state.update { state ->
            state.copy(
                mcpServers = state.mcpServers.map { entry ->
                    if (entry.id == serverId) entry.copy(connectionStatus = status) else entry
                }.toImmutableList(),
            )
        }
    }

    private fun connectEnabledMcpServers() {
        val enabledServers = _state.value.mcpServers.filter { it.isEnabled && it.connectionStatus != McpConnectionStatus.Connected }
        for (server in enabledServers) {
            viewModelScope.launch(backgroundDispatcher) {
                connectMcpServerWithStatus(server.id)
            }
        }
    }

    private fun commitPendingDeletion() {
        pendingDeleteJob?.cancel()
        pendingDeleteJob = null
        val deletion = _state.value.pendingDeletion ?: return
        _state.update { it.copy(pendingDeletion = null) }
        viewModelScope.launch(backgroundDispatcher) {
            executeDeletion(deletion)
        }
    }

    private suspend fun executeDeletion(deletion: PendingDeletion) {
        when (deletion) {
            is PendingDeletion.Memory -> {
                dataRepository.deleteMemory(deletion.key)
                _state.update { it.copy(memories = dataRepository.getMemories().toImmutableList(), pendingDeletion = null) }
            }

            is PendingDeletion.Task -> {
                dataRepository.cancelScheduledTask(deletion.id)
                _state.update { it.copy(scheduledTasks = dataRepository.getScheduledTasks().toImmutableList(), pendingDeletion = null) }
            }

            is PendingDeletion.EmailAccount -> {
                dataRepository.removeEmailAccount(deletion.id)
                _state.update { it.copy(emailAccounts = dataRepository.getEmailAccounts().toImmutableList(), pendingDeletion = null) }
            }

            is PendingDeletion.Service -> {
                val service = _state.value.configuredServices.find { it.instanceId == deletion.instanceId }?.service
                dataRepository.removeConfiguredService(deletion.instanceId)
                // If removing the last on-device service, delete all downloaded models
                if (service?.isOnDevice == true) {
                    val hasOtherOnDevice = dataRepository.getConfiguredServiceInstances().any {
                        Service.fromId(it.serviceId).isOnDevice
                    }
                    if (!hasOtherOnDevice) {
                        dataRepository.getLocalDownloadedModels().forEach {
                            dataRepository.deleteLocalModel(it.id)
                        }
                        _state.update { it.copy(localFreeSpaceBytes = dataRepository.getLocalFreeSpaceBytes()) }
                    }
                }
                _state.update { it.copy(pendingDeletion = null) }
                refreshServiceList()
            }

            is PendingDeletion.McpServer -> {
                dataRepository.removeMcpServer(deletion.serverId)
                _state.update { it.copy(pendingDeletion = null) }
                refreshMcpServers()
            }
        }
    }

    private fun onUndoDelete() {
        pendingDeleteJob?.cancel()
        pendingDeleteJob = null
        _state.update { it.copy(pendingDeletion = null) }
    }

    override fun onCleared() {
        pendingDeleteJob?.cancel()
        pendingDeleteJob = null
        val deletion = _state.value.pendingDeletion ?: run {
            super.onCleared()
            return
        }
        _state.update { it.copy(pendingDeletion = null) }
        CoroutineScope(backgroundDispatcher).launch {
            executeDeletion(deletion)
        }
        super.onCleared()
    }

    private fun checkAllConnections() {
        for (entry in _state.value.configuredServices) {
            checkConnection(entry.instanceId, entry.service)
        }
    }

    private fun checkConnectionDebounced(instanceId: String, service: Service) {
        connectionCheckJobs[instanceId]?.cancel()
        connectionCheckJobs[instanceId] = viewModelScope.launch {
            delay(800)
            checkConnection(instanceId, service)
        }
    }

    private fun checkConnection(instanceId: String, service: Service) {
        if (service == Service.Free) {
            updateConnectionStatus(instanceId, ConnectionStatus.Connected)
            return
        }
        if (service.isOnDevice) {
            validateConnectionWithStatus(instanceId, service)
            return
        }
        if (service.requiresApiKey && dataRepository.getInstanceApiKey(instanceId).isBlank()) {
            updateConnectionStatus(instanceId, ConnectionStatus.Unknown)
            return
        }
        validateConnectionWithStatus(instanceId, service)
    }

    private fun updateConnectionStatus(instanceId: String, status: ConnectionStatus) {
        _state.update { state ->
            state.copy(
                configuredServices = state.configuredServices.map { entry ->
                    if (entry.instanceId == instanceId) {
                        entry.copy(connectionStatus = status)
                    } else {
                        entry
                    }
                }.toImmutableList(),
            )
        }
    }

    private fun validateConnectionWithStatus(instanceId: String, service: Service) {
        updateConnectionStatus(instanceId, ConnectionStatus.Checking)
        viewModelScope.launch(backgroundDispatcher) {
            try {
                dataRepository.validateConnection(service, instanceId)
                if (service.isOnDevice && dataRepository.getLocalDownloadedModels().isEmpty()) {
                    updateConnectionStatus(instanceId, ConnectionStatus.Unknown)
                } else {
                    updateConnectionStatus(instanceId, ConnectionStatus.Connected)
                }
                refreshInstanceModels(instanceId)
            } catch (e: Exception) {
                val status = when (e) {
                    is OpenAICompatibleInvalidApiKeyException, is GeminiInvalidApiKeyException, is AnthropicInvalidApiKeyException ->
                        ConnectionStatus.ErrorInvalidKey

                    is OpenAICompatibleQuotaExhaustedException, is AnthropicInsufficientCreditsException ->
                        ConnectionStatus.ErrorQuotaExhausted

                    is OpenAICompatibleRateLimitExceededException, is GeminiRateLimitExceededException, is AnthropicRateLimitExceededException ->
                        ConnectionStatus.ErrorRateLimited

                    is AnthropicOverloadedException ->
                        ConnectionStatus.Error

                    is OpenAICompatibleConnectionException ->
                        ConnectionStatus.ErrorConnectionFailed

                    else -> ConnectionStatus.Error
                }
                updateConnectionStatus(instanceId, status)
            }
        }
    }
}
