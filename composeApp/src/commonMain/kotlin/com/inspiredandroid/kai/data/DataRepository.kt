package com.inspiredandroid.kai.data

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
    fun getOrderedServicesForFallback(): List<Service>
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
    suspend fun loadConversations()
    fun startNewChat()
    fun regenerate()
    suspend fun restoreLatestConversation()

    // Tool management
    fun getToolDefinitions(): List<ToolInfo>
    fun setToolEnabled(toolId: String, enabled: Boolean)

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

    // Silent ask (no chat history update, used for heartbeats)
    suspend fun askSilently(question: String): String
    fun addAssistantMessage(content: String)
}
