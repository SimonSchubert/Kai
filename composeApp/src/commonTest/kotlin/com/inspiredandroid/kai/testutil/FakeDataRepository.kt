package com.inspiredandroid.kai.testutil

import com.inspiredandroid.kai.data.DataRepository
import com.inspiredandroid.kai.data.EmailAccount
import com.inspiredandroid.kai.data.HeartbeatConfig
import com.inspiredandroid.kai.data.HeartbeatLogEntry
import com.inspiredandroid.kai.data.MemoryEntry
import com.inspiredandroid.kai.data.ScheduledTask
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.data.TaskStatus
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
    private val apiKeys = mutableMapOf<Service, String>()
    private val baseUrls = mutableMapOf<Service, String>()
    private val modelsByService: Map<Service, MutableStateFlow<List<SettingsModel>>> =
        Service.all.associateWith { MutableStateFlow(emptyList()) }

    override val chatHistory: MutableStateFlow<List<History>> = MutableStateFlow(emptyList())
    override val currentConversationId: MutableStateFlow<String?> = MutableStateFlow(null)

    val selectServiceCalls = mutableListOf<Service>()
    val updateApiKeyCalls = mutableListOf<Pair<Service, String>>()
    val updateSelectedModelCalls = mutableListOf<Pair<Service, String>>()
    val fetchModelsCalls = mutableListOf<Service>()
    val askCalls = mutableListOf<Pair<String?, PlatformFile?>>()
    var clearHistoryCalls = 0
    var askException: Exception? = null

    fun setCurrentService(service: Service) {
        currentService = service
    }

    fun setApiKey(service: Service, apiKey: String) {
        apiKeys[service] = apiKey
    }

    fun setModels(service: Service, models: List<SettingsModel>) {
        modelsByService[service]?.value = models
    }

    override fun selectService(service: Service) {
        selectServiceCalls.add(service)
        currentService = service
    }

    override fun updateApiKey(service: Service, apiKey: String) {
        updateApiKeyCalls.add(service to apiKey)
        apiKeys[service] = apiKey
    }

    override fun getApiKey(service: Service): String = apiKeys[service] ?: ""

    override fun updateSelectedModel(service: Service, modelId: String) {
        updateSelectedModelCalls.add(service to modelId)
        modelsByService[service]?.update { models ->
            models.map { it.copy(isSelected = it.id == modelId) }
        }
    }

    override fun getModels(service: Service): StateFlow<List<SettingsModel>> = modelsByService[service] ?: MutableStateFlow(emptyList())

    override fun clearModels(service: Service) {
        modelsByService[service]?.value = emptyList()
    }

    override suspend fun fetchModels(service: Service) {
        fetchModelsCalls.add(service)
    }

    override suspend fun validateConnection(service: Service) {
        // No-op in tests
    }

    override fun updateBaseUrl(service: Service, baseUrl: String) {
        baseUrls[service] = baseUrl
    }

    override fun getBaseUrl(service: Service): String = baseUrls[service] ?: Service.DEFAULT_OPENAI_COMPATIBLE_BASE_URL

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
}
