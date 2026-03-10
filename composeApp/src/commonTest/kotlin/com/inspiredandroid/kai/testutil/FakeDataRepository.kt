package com.inspiredandroid.kai.testutil

import com.inspiredandroid.kai.data.DataRepository
import com.inspiredandroid.kai.data.EmailAccount
import com.inspiredandroid.kai.data.HeartbeatConfig
import com.inspiredandroid.kai.data.HeartbeatLogEntry
import com.inspiredandroid.kai.data.MemoryEntry
import com.inspiredandroid.kai.data.ScheduledTask
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.data.ServiceInstance
import com.inspiredandroid.kai.mcp.McpServerConfig
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.tools.CommonTools
import com.inspiredandroid.kai.ui.chat.History
import com.inspiredandroid.kai.ui.settings.SettingsModel
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class FakeDataRepository : DataRepository {

    private var currentService: Service = Service.Free

    override val chatHistory: MutableStateFlow<List<History>> = MutableStateFlow(emptyList())
    override val currentConversationId: MutableStateFlow<String?> = MutableStateFlow(null)

    val askCalls = mutableListOf<Pair<String?, PlatformFile?>>()
    var clearHistoryCalls = 0
    var askException: Exception? = null

    fun setCurrentService(service: Service) {
        currentService = service
    }

    // Configured services management (instance-based)
    private val configuredInstances = mutableListOf<ServiceInstance>()
    private val instanceApiKeys = mutableMapOf<String, String>()
    private val instanceBaseUrls = mutableMapOf<String, String>()
    private val instanceModels = mutableMapOf<String, MutableStateFlow<List<SettingsModel>>>()
    private var instanceCounter = 0

    override fun getConfiguredServiceInstances(): List<ServiceInstance> = configuredInstances.toList()

    override fun addConfiguredService(serviceId: String): ServiceInstance {
        val existingIds = configuredInstances.map { it.instanceId }.toSet()
        val instanceId = if (serviceId !in existingIds) {
            serviceId
        } else {
            var counter = 2
            while ("${serviceId}_$counter" in existingIds) counter++
            "${serviceId}_$counter"
        }
        val instance = ServiceInstance(instanceId = instanceId, serviceId = serviceId)
        configuredInstances.add(instance)
        return instance
    }

    override fun removeConfiguredService(instanceId: String) {
        configuredInstances.removeAll { it.instanceId == instanceId }
        instanceApiKeys.remove(instanceId)
        instanceBaseUrls.remove(instanceId)
        instanceModels.remove(instanceId)
    }

    override fun reorderConfiguredServices(orderedInstanceIds: List<String>) {
        val byId = configuredInstances.associateBy { it.instanceId }
        val reordered = orderedInstanceIds.mapNotNull { byId[it] }
        configuredInstances.clear()
        configuredInstances.addAll(reordered)
    }

    override fun getOrderedServicesForFallback(): List<Service> {
        val services = configuredInstances.map { Service.fromId(it.serviceId) }.filter { it != Service.Free }
        return if (services.isEmpty()) {
            listOf(Service.Free)
        } else if (freeFallbackEnabled) {
            services + Service.Free
        } else {
            services
        }
    }

    private var freeFallbackEnabled = true

    override fun isFreeFallbackEnabled(): Boolean = freeFallbackEnabled

    override fun setFreeFallbackEnabled(enabled: Boolean) {
        freeFallbackEnabled = enabled
    }

    // Per-instance settings
    override fun getInstanceApiKey(instanceId: String): String = instanceApiKeys[instanceId] ?: ""

    override fun updateInstanceApiKey(instanceId: String, apiKey: String) {
        instanceApiKeys[instanceId] = apiKey
    }

    override fun getInstanceBaseUrl(instanceId: String, service: Service): String = instanceBaseUrls[instanceId] ?: if (service is Service.OpenAICompatible) Service.DEFAULT_OPENAI_COMPATIBLE_BASE_URL else ""

    override fun updateInstanceBaseUrl(instanceId: String, baseUrl: String) {
        instanceBaseUrls[instanceId] = baseUrl
    }

    override fun getInstanceModels(instanceId: String, service: Service): StateFlow<List<SettingsModel>> = instanceModels.getOrPut(instanceId) { MutableStateFlow(emptyList()) }

    override fun updateInstanceSelectedModel(instanceId: String, service: Service, modelId: String) {
        instanceModels[instanceId]?.update { models ->
            models.map { it.copy(isSelected = it.id == modelId) }
        }
    }

    override fun clearInstanceModels(instanceId: String, service: Service) {
        instanceModels[instanceId]?.value = emptyList()
    }

    override suspend fun validateConnection(service: Service, instanceId: String) {
        // No-op in tests
    }

    fun setConfiguredServices(vararg services: Service) {
        configuredInstances.clear()
        val usedIds = mutableSetOf<String>()
        for (service in services) {
            val instanceId = if (service.id !in usedIds) {
                service.id
            } else {
                var counter = 2
                while ("${service.id}_$counter" in usedIds) counter++
                "${service.id}_$counter"
            }
            usedIds.add(instanceId)
            configuredInstances.add(ServiceInstance(instanceId = instanceId, serviceId = service.id))
        }
    }

    fun setInstanceApiKey(instanceId: String, apiKey: String) {
        instanceApiKeys[instanceId] = apiKey
    }

    fun setInstanceModels(instanceId: String, models: List<SettingsModel>) {
        instanceModels.getOrPut(instanceId) { MutableStateFlow(emptyList()) }.value = models
    }

    override suspend fun ask(question: String?, file: PlatformFile?) {
        askCalls.add(question to file)
        askException?.let { throw it }
        if (question != null) {
            chatHistory.update { history ->
                history + History(role = History.Role.USER, content = question)
            }
        }
        chatHistory.update { history ->
            history + History(role = History.Role.ASSISTANT, content = "Test response")
        }
    }

    override fun clearHistory() {
        clearHistoryCalls++
        chatHistory.value = emptyList()
    }

    override fun currentService(): Service = currentService

    override fun isUsingSharedKey(): Boolean = currentService == Service.Free

    var fileAttachmentSupported = true

    override fun supportsFileAttachment(): Boolean = fileAttachmentSupported

    // Conversation management
    override suspend fun loadConversations() {
        // No-op in tests
    }

    override fun regenerate() {
        chatHistory.update { history ->
            val lastUserIndex = history.indexOfLast { it.role == History.Role.USER }
            if (lastUserIndex >= 0) {
                history.subList(0, lastUserIndex + 1)
            } else {
                history
            }
        }
    }

    override fun startNewChat() {
        currentConversationId.value = null
        chatHistory.value = emptyList()
    }

    override suspend fun restoreLatestConversation() {
        // No-op in tests
    }

    override fun getToolDefinitions(): List<ToolInfo> = CommonTools.commonToolDefinitions

    override fun setToolEnabled(toolId: String, enabled: Boolean) {
    }

    // MCP servers
    private val mcpServers = mutableListOf<McpServerConfig>()
    private val mcpConnected = mutableSetOf<String>()
    private val mcpTools = mutableMapOf<String, List<ToolInfo>>()

    override fun getMcpServers(): List<McpServerConfig> = mcpServers.toList()

    override suspend fun addMcpServer(name: String, url: String, headers: Map<String, String>): McpServerConfig {
        val id = name.lowercase().replace(Regex("[^a-z0-9]"), "_").take(30)
        val config = McpServerConfig(id = id, name = name, url = url, headers = headers)
        mcpServers.add(config)
        return config
    }

    override fun removeMcpServer(serverId: String) {
        mcpServers.removeAll { it.id == serverId }
        mcpConnected.remove(serverId)
        mcpTools.remove(serverId)
    }

    override fun setMcpServerEnabled(serverId: String, enabled: Boolean) {
        val index = mcpServers.indexOfFirst { it.id == serverId }
        if (index >= 0) {
            mcpServers[index] = mcpServers[index].copy(isEnabled = enabled)
        }
        if (!enabled) {
            mcpConnected.remove(serverId)
            mcpTools.remove(serverId)
        }
    }

    override suspend fun connectMcpServer(serverId: String): Result<List<ToolInfo>> {
        mcpConnected.add(serverId)
        return Result.success(mcpTools[serverId] ?: emptyList())
    }

    override fun getMcpToolDefinitions(): List<ToolInfo> = mcpTools.values.flatten()

    override fun getMcpToolsForServer(serverId: String): List<ToolInfo> = mcpTools[serverId] ?: emptyList()

    override fun isMcpServerConnected(serverId: String): Boolean = serverId in mcpConnected

    override suspend fun connectEnabledMcpServers() {
        mcpServers.filter { it.isEnabled }.forEach { mcpConnected.add(it.id) }
    }

    // Soul (system prompt)
    private var soulText = ""

    override fun getSoulText(): String = soulText

    override fun setSoulText(text: String) {
        soulText = text
    }

    override suspend fun getActiveSystemPrompt(): String? = soulText.ifEmpty { null }

    // Memory management
    private var memoryEnabled = true
    private val memories = mutableListOf<MemoryEntry>()

    override fun isMemoryEnabled(): Boolean = memoryEnabled

    override fun setMemoryEnabled(enabled: Boolean) {
        memoryEnabled = enabled
    }

    override fun getMemories(): List<MemoryEntry> = memories.toList()

    override suspend fun deleteMemory(key: String) {
        memories.removeAll { it.key == key }
    }

    // Scheduling management
    private var schedulingEnabled = true
    private val scheduledTasks = mutableListOf<ScheduledTask>()

    override fun isSchedulingEnabled(): Boolean = schedulingEnabled

    override fun setSchedulingEnabled(enabled: Boolean) {
        schedulingEnabled = enabled
    }

    override fun getScheduledTasks(): List<ScheduledTask> = scheduledTasks.toList()

    override suspend fun cancelScheduledTask(id: String) {
        scheduledTasks.removeAll { it.id == id }
    }

    // Daemon mode
    private var daemonEnabled = false

    override fun isDaemonEnabled(): Boolean = daemonEnabled

    override fun setDaemonEnabled(enabled: Boolean) {
        daemonEnabled = enabled
    }

    override fun getHeartbeatConfig(): HeartbeatConfig = HeartbeatConfig()

    override fun setHeartbeatEnabled(enabled: Boolean) {
    }

    override fun setHeartbeatIntervalMinutes(minutes: Int) {
    }

    override fun setHeartbeatActiveHours(start: Int, end: Int) {
    }

    override fun getHeartbeatPrompt(): String = ""

    override fun setHeartbeatPrompt(text: String) {
    }

    override fun getHeartbeatLog(): List<HeartbeatLogEntry> = emptyList()

    override suspend fun askSilently(question: String): String = ""
    override fun addAssistantMessage(content: String) {}

    // Email management
    private var emailEnabled = true
    private val emailAccounts = mutableListOf<EmailAccount>()
    private var emailPollIntervalMinutes = 15

    override fun isEmailEnabled(): Boolean = emailEnabled

    override fun setEmailEnabled(enabled: Boolean) {
        emailEnabled = enabled
    }

    override fun getEmailAccounts(): List<EmailAccount> = emailAccounts.toList()

    override suspend fun removeEmailAccount(id: String) {
        emailAccounts.removeAll { it.id == id }
    }

    override fun getEmailPollIntervalMinutes(): Int = emailPollIntervalMinutes

    override fun setEmailPollIntervalMinutes(minutes: Int) {
        emailPollIntervalMinutes = minutes
    }

    private var uiScale: Float = 1.0f

    override fun getUiScale(): Float = uiScale

    override fun setUiScale(scale: Float) {
        uiScale = scale
    }

    override fun exportSettingsToJson(): String = "{}"

    override fun importSettingsFromJson(json: String) {
    }
}
