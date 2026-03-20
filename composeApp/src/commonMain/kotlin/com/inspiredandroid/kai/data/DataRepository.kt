package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.mcp.McpServerConfig
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.ui.chat.History
import com.inspiredandroid.kai.ui.settings.SettingsModel
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.StateFlow

interface DataRepository {
    val chatHistory: StateFlow<List<History>>
    val currentConversationId: StateFlow<String?>

    // Configured services management
    fun getConfiguredServiceInstances(): List<ServiceInstance>
    fun addConfiguredService(serviceId: String): ServiceInstance
    fun removeConfiguredService(instanceId: String)
    fun reorderConfiguredServices(orderedInstanceIds: List<String>)
    fun getServiceEntries(): List<ServiceEntry>
    fun isFreeFallbackEnabled(): Boolean
    fun setFreeFallbackEnabled(enabled: Boolean)

    // Per-instance settings
    fun getInstanceApiKey(instanceId: String): String
    fun updateInstanceApiKey(instanceId: String, apiKey: String)
    fun getInstanceBaseUrl(instanceId: String, service: Service): String
    fun updateInstanceBaseUrl(instanceId: String, baseUrl: String)
    fun getInstanceModels(instanceId: String, service: Service): StateFlow<List<SettingsModel>>
    fun updateInstanceSelectedModel(instanceId: String, service: Service, modelId: String)
    fun clearInstanceModels(instanceId: String, service: Service)
    suspend fun validateConnection(service: Service, instanceId: String)

    suspend fun ask(question: String?, file: PlatformFile?)
    fun clearHistory()
    fun currentService(): Service
    fun isUsingSharedKey(): Boolean
    fun supportsFileAttachment(): Boolean

    // Conversation management
    val savedConversations: StateFlow<List<Conversation>>
    suspend fun loadConversations()
    fun loadConversation(id: String)
    suspend fun deleteConversation(id: String)
    fun startNewChat()
    fun regenerate()
    suspend fun restoreLatestConversation()

    // Tool management
    fun getToolDefinitions(): List<ToolInfo>
    fun setToolEnabled(toolId: String, enabled: Boolean)

    // MCP servers
    fun getMcpServers(): List<McpServerConfig>
    suspend fun addMcpServer(name: String, url: String, headers: Map<String, String>): McpServerConfig
    fun removeMcpServer(serverId: String)
    fun setMcpServerEnabled(serverId: String, enabled: Boolean)
    suspend fun connectMcpServer(serverId: String): Result<List<ToolInfo>>
    fun getMcpToolsForServer(serverId: String): List<ToolInfo>
    fun isMcpServerConnected(serverId: String): Boolean
    suspend fun connectEnabledMcpServers()

    // Soul (system prompt)
    fun getSoulText(): String
    fun setSoulText(text: String)
    suspend fun getActiveSystemPrompt(): String?

    // Memory management
    fun isMemoryEnabled(): Boolean
    fun setMemoryEnabled(enabled: Boolean)
    fun getMemories(): List<MemoryEntry>
    suspend fun deleteMemory(key: String)

    // Scheduling management
    fun isSchedulingEnabled(): Boolean
    fun setSchedulingEnabled(enabled: Boolean)
    fun getScheduledTasks(): List<ScheduledTask>
    suspend fun cancelScheduledTask(id: String)

    // Daemon mode
    fun isDaemonEnabled(): Boolean
    fun setDaemonEnabled(enabled: Boolean)

    // Heartbeat
    fun getHeartbeatConfig(): HeartbeatConfig
    fun setHeartbeatEnabled(enabled: Boolean)
    fun setHeartbeatIntervalMinutes(minutes: Int)
    fun setHeartbeatActiveHours(start: Int, end: Int)
    fun getHeartbeatPrompt(): String
    fun setHeartbeatPrompt(text: String)
    fun getHeartbeatLog(): List<HeartbeatLogEntry>

    // Email
    fun isEmailEnabled(): Boolean
    fun setEmailEnabled(enabled: Boolean)
    fun getEmailAccounts(): List<EmailAccount>
    suspend fun removeEmailAccount(id: String)
    fun getEmailPollIntervalMinutes(): Int
    fun setEmailPollIntervalMinutes(minutes: Int)

    // UI Scale
    fun getUiScale(): Float
    fun setUiScale(scale: Float)

    // Export/Import
    fun exportSettingsToJson(): String
    fun importSettingsFromJson(json: String, sections: Set<ImportSection>, replace: Boolean): Int

    // Background ask with tools (no chat history update, supports tool-calling loop)
    suspend fun askWithTools(prompt: String): String

    // Silent ask (no tools, no chat history update)
    suspend fun askSilently(question: String): String
    suspend fun askSilentlyWithInstance(instanceId: String, prompt: String, timeoutMs: Long = 0L): String
    suspend fun addAssistantMessage(content: String)

    // Heartbeat notification
    val hasUnreadHeartbeat: StateFlow<Boolean>
    fun clearUnreadHeartbeat()
}
