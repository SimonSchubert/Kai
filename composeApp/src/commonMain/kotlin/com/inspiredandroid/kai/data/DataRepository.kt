package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.ui.chat.History
import com.inspiredandroid.kai.ui.settings.SettingsModel
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.StateFlow

interface DataRepository {
    val chatHistory: StateFlow<List<History>>
    val currentConversationId: StateFlow<String?>

    fun selectService(service: Service)
    fun updateApiKey(service: Service, apiKey: String)
    fun getApiKey(service: Service): String
    fun updateSelectedModel(service: Service, modelId: String)
    fun getModels(service: Service): StateFlow<List<SettingsModel>>
    fun clearModels(service: Service)
    suspend fun fetchModels(service: Service)
    suspend fun validateConnection(service: Service)
    fun updateBaseUrl(service: Service, baseUrl: String)
    fun getBaseUrl(service: Service): String

    suspend fun ask(question: String?, file: PlatformFile?)
    fun clearHistory()
    fun currentService(): Service
    fun isUsingSharedKey(): Boolean

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

    // Silent ask (no chat history update, used for heartbeats)
    suspend fun askSilently(question: String): String
    fun addAssistantMessage(content: String)
}
