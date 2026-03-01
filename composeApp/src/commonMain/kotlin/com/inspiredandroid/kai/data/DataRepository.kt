package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.ui.chat.History
import com.inspiredandroid.kai.ui.settings.SettingsModel
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.StateFlow

interface DataRepository {
    val chatHistory: StateFlow<List<History>>
    val currentConversationId: StateFlow<String?>
    val savedConversations: StateFlow<List<Conversation>>

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
    suspend fun loadConversation(id: String)
    suspend fun deleteConversation(id: String)
    suspend fun deleteAllConversations()
    fun startNewChat()
    fun regenerate()
    suspend fun restoreLatestConversation()

    // Tool management
    fun getToolDefinitions(): List<ToolInfo>
    fun setToolEnabled(toolId: String, enabled: Boolean)

    // Identity management
    fun getIdentities(): List<Identity>
    fun getSelectedIdentity(): Identity
    fun setSelectedIdentity(id: String)
    fun saveIdentity(identity: Identity)
    fun deleteIdentity(id: String)
    fun getActiveSystemPrompt(): String?
    fun resetIdentityToDefault(id: String)
}
