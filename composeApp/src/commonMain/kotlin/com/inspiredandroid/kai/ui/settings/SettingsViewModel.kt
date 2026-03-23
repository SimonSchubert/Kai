package com.inspiredandroid.kai.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.DaemonController
import com.inspiredandroid.kai.data.DataRepository
import com.inspiredandroid.kai.data.ImportSection
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.getBackgroundDispatcher
import com.inspiredandroid.kai.httpClient
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
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class SettingsViewModel(
    private val dataRepository: DataRepository,
    private val daemonController: DaemonController,
) : ViewModel() {

    private var connectionCheckJobs: MutableMap<String, Job> = mutableMapOf()
    private var hasCheckedInitialConnection = false
    private var pendingDeleteJob: Job? = null

    private fun buildFullState(): SettingsUiState = SettingsUiState(
        configuredServices = buildConfiguredServiceEntries().toImmutableList(),
        availableServicesToAdd = computeAvailableServices().toImmutableList(),
        tools = dataRepository.getToolDefinitions().toImmutableList(),
        onSelectTab = ::onSelectTab,
        onAddService = ::onAddService,
        onRemoveService = ::onRemoveService,
        onReorderServices = ::onReorderServices,
        onExpandService = ::onExpandService,
        onChangeApiKey = ::onChangeApiKey,
        onChangeBaseUrl = ::onChangeBaseUrl,
        onSelectModel = ::onSelectModel,
        onToggleTool = ::onToggleTool,
        soulText = dataRepository.getSoulText(),
        onSaveSoul = ::onSaveSoul,
        isMemoryEnabled = dataRepository.isMemoryEnabled(),
        onToggleMemory = ::onToggleMemory,
        memories = dataRepository.getMemories().toImmutableList(),
        onDeleteMemory = ::onDeleteMemory,
        isSchedulingEnabled = dataRepository.isSchedulingEnabled(),
        onToggleScheduling = ::onToggleScheduling,
        scheduledTasks = dataRepository.getScheduledTasks().toImmutableList(),
        onCancelTask = ::onCancelTask,
        isDaemonEnabled = dataRepository.isDaemonEnabled(),
        onToggleDaemon = ::onToggleDaemon,
        showDaemonToggle = platformName == "Android",
        isHeartbeatEnabled = dataRepository.getHeartbeatConfig().enabled,
        heartbeatIntervalMinutes = dataRepository.getHeartbeatConfig().intervalMinutes,
        heartbeatActiveHoursStart = dataRepository.getHeartbeatConfig().activeHoursStart,
        heartbeatActiveHoursEnd = dataRepository.getHeartbeatConfig().activeHoursEnd,
        heartbeatPrompt = dataRepository.getHeartbeatPrompt(),
        heartbeatLog = dataRepository.getHeartbeatLog().toImmutableList(),
        onToggleHeartbeat = ::onToggleHeartbeat,
        onChangeHeartbeatInterval = ::onChangeHeartbeatInterval,
        onChangeHeartbeatActiveHours = ::onChangeHeartbeatActiveHours,
        onSaveHeartbeatPrompt = ::onSaveHeartbeatPrompt,
        isEmailEnabled = dataRepository.isEmailEnabled(),
        showEmailToggle = isEmailSupported,
        emailAccounts = dataRepository.getEmailAccounts().toImmutableList(),
        emailPollIntervalMinutes = dataRepository.getEmailPollIntervalMinutes(),
        onToggleEmail = ::onToggleEmail,
        onRemoveEmailAccount = ::onRemoveEmailAccount,
        onChangeEmailPollInterval = ::onChangeEmailPollInterval,
        isSkillsEnabled = dataRepository.isSkillsEnabled(),
        onToggleSkills = ::onToggleSkills,
        skills = dataRepository.getSkills().toImmutableList(),
        onDeleteSkill = ::onDeleteSkill,
        onExecuteSkill = ::onExecuteSkill,
        isFreeFallbackEnabled = dataRepository.isFreeFallbackEnabled(),
        onToggleFreeFallback = ::onToggleFreeFallback,
        uiScale = dataRepository.getUiScale(),
        onChangeUiScale = ::onChangeUiScale,
        showUiScale = isDesktopPlatform,
        mcpServers = buildMcpServerEntries().toImmutableList(),
        onAddMcpServer = ::onAddMcpServer,
        onRemoveMcpServer = ::onRemoveMcpServer,
        onToggleMcpServer = ::onToggleMcpServer,
        onRefreshMcpServer = ::onRefreshMcpServer,
        onShowAddMcpServerDialog = ::onShowAddMcpServerDialog,
        onAddPopularMcpServer = ::onAddPopularMcpServer,
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

    fun onScreenVisible() {
        if (!hasCheckedInitialConnection) {
            hasCheckedInitialConnection = true
            checkAllConnections()
            connectEnabledMcpServers()
            fetchSponsors()
        }
    }

    private fun fetchSponsors() {
        viewModelScope.launch(context = getBackgroundDispatcher()) {
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
        // Sort alphabetically, but keep OpenAI-Compatible at the end
        return Service.all
            .filter { it != Service.Free }
            .sortedWith(compareBy<Service> { it is Service.OpenAICompatible }.thenBy { it.displayName })
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
        pendingDeleteJob = viewModelScope.launch {
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

    private fun onToggleMemory(enabled: Boolean) {
        dataRepository.setMemoryEnabled(enabled)
        _state.update { it.copy(isMemoryEnabled = enabled) }
    }

    private fun onDeleteMemory(key: String) {
        commitPendingDeletion()
        _state.update { it.copy(pendingDeletion = PendingDeletion.Memory(key)) }
        pendingDeleteJob = viewModelScope.launch {
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
        pendingDeleteJob = viewModelScope.launch {
            delay(4000)
            executeDeletion(PendingDeletion.Task(id))
        }
    }

    private fun onToggleSkills(enabled: Boolean) {
        dataRepository.setSkillsEnabled(enabled)
        _state.update { it.copy(isSkillsEnabled = enabled) }
    }

    private fun onDeleteSkill(name: String) {
        commitPendingDeletion()
        _state.update { it.copy(pendingDeletion = PendingDeletion.Skill(name)) }
        pendingDeleteJob = viewModelScope.launch {
            delay(4000)
            executeDeletion(PendingDeletion.Skill(name))
        }
    }

    private fun onExecuteSkill(name: String, input: String?) {
        _state.update { it.copy(executingSkillName = name, skillExecutionResult = null) }
        viewModelScope.launch {
            val result = dataRepository.executeSkill(name, input)
            _state.update {
                it.copy(
                    executingSkillName = null,
                    skillExecutionResult = name to result,
                    skills = dataRepository.getSkills().toImmutableList(),
                )
            }
        }
    }

    private fun onToggleDaemon(enabled: Boolean) {
        dataRepository.setDaemonEnabled(enabled)
        if (enabled) {
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

    private fun onToggleEmail(enabled: Boolean) {
        dataRepository.setEmailEnabled(enabled)
        _state.update { it.copy(isEmailEnabled = enabled) }
    }

    private fun onRemoveEmailAccount(id: String) {
        commitPendingDeletion()
        _state.update { it.copy(pendingDeletion = PendingDeletion.EmailAccount(id)) }
        pendingDeleteJob = viewModelScope.launch {
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
        viewModelScope.launch(context = getBackgroundDispatcher()) {
            val config = dataRepository.addMcpServer(name, url, headers)
            refreshMcpServers()
            connectMcpServerWithStatus(config.id)
        }
        _state.update { it.copy(showAddMcpServerDialog = false) }
    }

    private fun onRemoveMcpServer(serverId: String) {
        commitPendingDeletion()
        _state.update { it.copy(pendingDeletion = PendingDeletion.McpServer(serverId)) }
        pendingDeleteJob = viewModelScope.launch {
            delay(4000)
            executeDeletion(PendingDeletion.McpServer(serverId))
        }
    }

    private fun onToggleMcpServer(serverId: String, enabled: Boolean) {
        dataRepository.setMcpServerEnabled(serverId, enabled)
        refreshMcpServers()
        if (enabled) {
            viewModelScope.launch(context = getBackgroundDispatcher()) {
                connectMcpServerWithStatus(serverId)
            }
        }
    }

    private fun onRefreshMcpServer(serverId: String) {
        viewModelScope.launch(context = getBackgroundDispatcher()) {
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
            viewModelScope.launch(context = getBackgroundDispatcher()) {
                connectMcpServerWithStatus(server.id)
            }
        }
    }

    private fun commitPendingDeletion() {
        pendingDeleteJob?.cancel()
        pendingDeleteJob = null
        val deletion = _state.value.pendingDeletion ?: return
        _state.update { it.copy(pendingDeletion = null) }
        viewModelScope.launch {
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
                dataRepository.removeConfiguredService(deletion.instanceId)
                _state.update { it.copy(pendingDeletion = null) }
                refreshServiceList()
            }

            is PendingDeletion.McpServer -> {
                dataRepository.removeMcpServer(deletion.serverId)
                _state.update { it.copy(pendingDeletion = null) }
                refreshMcpServers()
            }

            is PendingDeletion.Skill -> {
                dataRepository.deleteSkill(deletion.name)
                _state.update { it.copy(skills = dataRepository.getSkills().toImmutableList(), pendingDeletion = null, skillExecutionResult = null) }
            }
        }
    }

    private fun onUndoDelete() {
        pendingDeleteJob?.cancel()
        pendingDeleteJob = null
        _state.update { it.copy(pendingDeletion = null) }
    }

    override fun onCleared() {
        commitPendingDeletion()
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
        viewModelScope.launch(context = getBackgroundDispatcher()) {
            try {
                dataRepository.validateConnection(service, instanceId)
                updateConnectionStatus(instanceId, ConnectionStatus.Connected)
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
