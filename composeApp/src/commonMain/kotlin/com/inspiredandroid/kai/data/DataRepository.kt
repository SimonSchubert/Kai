package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.inference.DownloadError
import com.inspiredandroid.kai.inference.DownloadedModel
import com.inspiredandroid.kai.inference.EngineState
import com.inspiredandroid.kai.inference.LocalModel
import com.inspiredandroid.kai.mcp.McpServerConfig
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.ui.chat.History
import com.inspiredandroid.kai.ui.settings.SettingsModel
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.StateFlow

interface DataRepository {
    val chatHistory: StateFlow<List<History>>
    val currentConversationId: StateFlow<String?>
    val fallbackStatus: StateFlow<FallbackStatus?>

    // Configured services management
    fun getConfiguredServiceInstances(): List<ServiceInstance>
    fun addConfiguredService(serviceId: String): ServiceInstance
    fun removeConfiguredService(instanceId: String)
    fun reorderConfiguredServices(orderedInstanceIds: List<String>)
    fun getServiceEntries(): List<ServiceEntry>
    fun isFreeFallbackEnabled(): Boolean
    fun setFreeFallbackEnabled(enabled: Boolean)
    fun getFreeMode(): FreeMode
    fun setFreeMode(mode: FreeMode)
    fun isFreeServicePrimary(): Boolean
    fun setFreeServicePrimary(primary: Boolean)

    // Per-instance settings
    fun getInstanceApiKey(instanceId: String): String
    fun updateInstanceApiKey(instanceId: String, apiKey: String)
    fun getInstanceBaseUrl(instanceId: String, service: Service): String
    fun updateInstanceBaseUrl(instanceId: String, baseUrl: String)
    fun getInstanceModels(instanceId: String, service: Service): StateFlow<List<SettingsModel>>
    fun updateInstanceSelectedModel(instanceId: String, service: Service, modelId: String)
    fun clearInstanceModels(instanceId: String, service: Service)
    suspend fun validateConnection(service: Service, instanceId: String)

    suspend fun ask(question: String?, files: List<PlatformFile>, uiSubmission: UiSubmission? = null)
    fun clearHistory()
    fun currentService(): Service
    fun isUsingSharedKey(): Boolean
    fun supportedFileExtensions(): List<String>

    // Conversation management
    val savedConversations: StateFlow<List<Conversation>>
    fun loadConversations()
    fun loadConversation(id: String)
    suspend fun deleteConversation(id: String)
    fun startNewChat()
    fun regenerate()
    fun popLastExchange()
    fun truncateFrom(messageId: String)
    fun restoreCurrentConversation()

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
    suspend fun getActiveSystemPrompt(variant: SystemPromptVariant = SystemPromptVariant.CHAT_REMOTE): String?

    // Memory management
    fun isMemoryEnabled(): Boolean
    fun setMemoryEnabled(enabled: Boolean)
    fun getMemories(): List<MemoryEntry>
    suspend fun deleteMemory(key: String)
    suspend fun updateMemoryContent(key: String, content: String)

    // Scheduling management
    fun isSchedulingEnabled(): Boolean
    fun setSchedulingEnabled(enabled: Boolean)
    fun getScheduledTasks(): List<ScheduledTask>
    suspend fun cancelScheduledTask(id: String)

    // Dynamic UI
    fun isDynamicUiEnabled(): Boolean
    fun setDynamicUiEnabled(enabled: Boolean)

    // OLED mode
    fun isOledModeEnabled(): Boolean
    fun setOledModeEnabled(enabled: Boolean)

    // Interactive mode
    fun setInteractiveMode(enabled: Boolean)
    fun isInteractiveModeActive(): Boolean

    // Daemon mode
    fun isDaemonEnabled(): Boolean
    fun setDaemonEnabled(enabled: Boolean)

    // Linux Sandbox
    fun isSandboxEnabled(): Boolean
    fun setSandboxEnabled(enabled: Boolean)

    // Heartbeat
    fun getHeartbeatConfig(): HeartbeatConfig
    fun setHeartbeatEnabled(enabled: Boolean)
    fun setHeartbeatIntervalMinutes(minutes: Int)
    fun setHeartbeatActiveHours(start: Int, end: Int)
    fun getHeartbeatPrompt(): String
    fun setHeartbeatPrompt(text: String)
    fun getHeartbeatLog(): List<HeartbeatLogEntry>
    fun getHeartbeatInstanceId(): String?
    fun setHeartbeatInstanceId(instanceId: String?)

    // Email
    fun isEmailEnabled(): Boolean
    fun setEmailEnabled(enabled: Boolean)
    fun getEmailAccounts(): List<EmailAccount>
    suspend fun removeEmailAccount(id: String)
    fun getEmailPollIntervalMinutes(): Int
    fun setEmailPollIntervalMinutes(minutes: Int)
    fun getPendingEmailCount(): Int
    fun getEmailSyncStates(): Map<String, EmailSyncState>
    suspend fun pollEmailAccount(accountId: String)

    // SMS (FOSS-only on Android; other platforms return stub values).
    // Read and send are independent opt-ins with separate runtime permissions.
    fun isSmsEnabled(): Boolean
    fun setSmsEnabled(enabled: Boolean)
    fun getSmsPollIntervalMinutes(): Int
    fun setSmsPollIntervalMinutes(minutes: Int)
    fun getPendingSmsCount(): Int
    fun getSmsSyncState(): SmsSyncState
    fun hasSmsPermission(): Boolean
    suspend fun requestSmsPermission(): Boolean
    suspend fun pollSms()

    fun isSmsSendEnabled(): Boolean
    fun setSmsSendEnabled(enabled: Boolean)
    fun hasSmsSendPermission(): Boolean
    suspend fun requestSmsSendPermission(): Boolean
    val smsDrafts: StateFlow<List<SmsDraft>>
    suspend fun sendSmsDraft(draftId: String): Boolean
    suspend fun discardSmsDraft(draftId: String)

    // Notifications (FOSS-only on Android; other platforms return stub values).
    // Per-app filtering is delegated to the system Notification Access "Apps" picker.
    fun isNotificationsEnabled(): Boolean
    fun setNotificationsEnabled(enabled: Boolean)
    fun isNotificationListenerAccessGranted(): Boolean
    fun openNotificationListenerSettings()
    fun getPendingNotificationCount(): Int
    fun getNotificationSyncState(): NotificationSyncState
    suspend fun clearPendingNotifications()

    // UI Scale
    fun getUiScale(): Float
    fun setUiScale(scale: Float)

    // Export/Import
    fun exportSettingsToJson(sections: Set<ImportSection> = ImportSection.entries.toSet()): String
    fun getExportPreview(): Map<ImportSection, String?>
    fun importSettingsFromJson(json: String, sections: Set<ImportSection>, replace: Boolean): Int

    // Background ask with tools (no chat history update, supports tool-calling loop)
    suspend fun askWithTools(prompt: String, instanceId: String? = null): String

    // Silent ask (no tools, no chat history update)
    suspend fun askSilently(question: String): String
    suspend fun askSilentlyWithInstance(instanceId: String, prompt: String, timeoutMs: Long = 0L): String
    suspend fun addAssistantMessage(content: String)

    // Heartbeat notification
    val hasUnreadHeartbeat: StateFlow<Boolean>
    fun clearUnreadHeartbeat()

    /**
     * Pulse that fires when the user taps a heartbeat push notification while the app is
     * not already on the heartbeat conversation. `true` means "load the heartbeat
     * conversation now, then call [consumeOpenHeartbeatRequest]". Collected by
     * `ChatViewModel` in its init block.
     */
    val openHeartbeatRequested: StateFlow<Boolean>
    fun requestOpenHeartbeat()
    fun consumeOpenHeartbeatRequest()

    // On-device inference (LiteRT)
    fun isLocalInferenceAvailable(): Boolean
    fun getLocalEngineState(): StateFlow<EngineState>?
    fun getLocalDownloadedModels(): List<DownloadedModel>
    fun getLocalAvailableModels(): List<LocalModel>
    fun getLocalFreeSpaceBytes(): Long
    fun getTotalDeviceMemoryBytes(): Long
    fun getModelContextTokens(modelId: String): Int
    fun setModelContextTokens(modelId: String, contextTokens: Int)
    suspend fun releaseLocalEngine()
    fun getLocalDownloadingModelId(): StateFlow<String?>?
    fun getLocalDownloadProgress(): StateFlow<Float?>?
    fun getLocalDownloadError(): StateFlow<DownloadError?>?
    fun startLocalModelDownload(model: LocalModel)
    fun cancelLocalModelDownload()
    suspend fun deleteLocalModel(modelId: String)
}
