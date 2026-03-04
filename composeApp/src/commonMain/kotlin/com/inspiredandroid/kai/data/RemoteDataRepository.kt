@file:OptIn(ExperimentalEncodingApi::class, ExperimentalTime::class, ExperimentalUuidApi::class)

package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.getAvailableTools
import com.inspiredandroid.kai.getPlatformToolDefinitions
import com.inspiredandroid.kai.network.OpenAICompatibleEmptyResponseException
import com.inspiredandroid.kai.network.Requests
import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatResponseDto
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.toHumanReadableDate
import com.inspiredandroid.kai.ui.chat.History
import com.inspiredandroid.kai.ui.chat.ToolCallInfo
import com.inspiredandroid.kai.ui.chat.toGeminiMessageDto
import com.inspiredandroid.kai.ui.chat.toGroqMessageDto
import com.inspiredandroid.kai.ui.settings.SettingsModel
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.mimeType
import io.github.vinceglb.filekit.readBytes
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.default_soul
import kai.composeapp.generated.resources.new_conversation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.compose.resources.getString
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val modelsWithoutToolSupport = listOf(
    "llama3.2:1b",
    "llama3.2:3b",
    "llama3.1:8b",
    "gemma2",
    "gemma:2b",
    "gemma:7b",
    "phi3:mini",
    "tinyllama",
    "stablelm",
    "codellama",
    "deepseek-coder:1.3b",
    "deepseek-coder:6.7b",
)

private fun supportsTools(modelId: String): Boolean {
    val lower = modelId.lowercase()
    return modelsWithoutToolSupport.none { lower.startsWith(it) }
}

class RemoteDataRepository(
    private val requests: Requests,
    private val appSettings: AppSettings,
    private val conversationStorage: ConversationStorage,
    private val toolExecutor: ToolExecutor,
    private val memoryStore: MemoryStore,
    private val taskStore: TaskStore,
    private val heartbeatManager: HeartbeatManager,
) : DataRepository {

    /**
     * Comparator for Gemini models that sorts by:
     * 1. Version number (descending) - e.g., 2.5 > 2.0 > 1.5
     * 2. Model type priority: pro > flash > others
     */
    private val geminiModelComparator = Comparator<SettingsModel> { a, b ->
        val versionA = extractGeminiVersion(a.id)
        val versionB = extractGeminiVersion(b.id)

        // Compare versions (descending - higher versions first)
        val versionCompare = versionB.compareTo(versionA)
        if (versionCompare != 0) return@Comparator versionCompare

        // Same version, compare by model type priority
        val priorityA = getGeminiModelPriority(a.id)
        val priorityB = getGeminiModelPriority(b.id)
        priorityA.compareTo(priorityB)
    }

    private fun extractGeminiVersion(modelId: String): Double {
        // Match patterns like "gemini-2.5-pro", "gemini-1.5-flash-8b"
        val versionRegex = Regex("""gemini-(\d+\.?\d*)""")
        val match = versionRegex.find(modelId)
        return match?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    }

    private fun getGeminiModelPriority(modelId: String): Int {
        val lowerId = modelId.lowercase()
        return when {
            lowerId.contains("pro") && !lowerId.contains("flash") -> 0
            lowerId.contains("flash") -> 1
            else -> 2
        }
    }

    private val modelsByService: Map<Service, MutableStateFlow<List<SettingsModel>>> =
        Service.all.associateWith { service ->
            MutableStateFlow(service.defaultModels.toSettingsModels(service))
        }

    override val chatHistory: MutableStateFlow<List<History>> = MutableStateFlow(emptyList())

    private val _currentConversationId = MutableStateFlow<String?>(null)
    override val currentConversationId: StateFlow<String?> = _currentConversationId

    private val savedConversations: StateFlow<List<Conversation>> = conversationStorage.conversations

    override fun selectService(service: Service) {
        appSettings.selectService(service)
    }

    override fun updateApiKey(service: Service, apiKey: String) {
        if (service.requiresApiKey || service.supportsOptionalApiKey) {
            appSettings.setApiKey(service, apiKey)
        }
    }

    override fun getApiKey(service: Service): String = appSettings.getApiKey(service)

    override fun updateSelectedModel(service: Service, modelId: String) {
        if (service.modelIdKey.isNotEmpty()) {
            appSettings.setSelectedModelId(service, modelId)
            updateModelsSelection(service)
        }
    }

    override fun updateBaseUrl(service: Service, baseUrl: String) {
        appSettings.setBaseUrl(service, baseUrl)
    }

    override fun getBaseUrl(service: Service): String = appSettings.getBaseUrl(service)

    override fun getModels(service: Service): StateFlow<List<SettingsModel>> = modelsByService[service] ?: MutableStateFlow(emptyList())

    override fun clearModels(service: Service) {
        modelsByService[service]?.update { emptyList() }
    }

    override suspend fun fetchModels(service: Service) {
        when (service) {
            Service.Gemini -> fetchGeminiModels()
            Service.Free -> { /* No model listing */ }
            else -> fetchOpenAICompatibleModels(service)
        }
    }

    override suspend fun validateConnection(service: Service) {
        when (service) {
            Service.Free -> { /* Always valid */ }

            Service.OpenRouter -> {
                requests.validateOpenRouterApiKey().getOrThrow()
                fetchModels(service)
            }

            else -> fetchModels(service)
        }
    }

    private suspend fun fetchGeminiModels() {
        val response = requests.getGeminiModels().getOrThrow()
        val selectedModelId = appSettings.getSelectedModelId(Service.Gemini)
        val models = response.models
            .filter { it.supportedGenerationMethods?.contains("generateContent") == true }
            .map {
                // Convert "models/gemini-1.5-pro" to "gemini-1.5-pro"
                val modelId = it.name.removePrefix("models/")
                SettingsModel(
                    id = modelId,
                    subtitle = it.displayName ?: modelId,
                    description = it.description,
                    isSelected = modelId == selectedModelId,
                )
            }
            .sortedWith(geminiModelComparator)
        modelsByService[Service.Gemini]?.update { models }
        // Auto-select first model if none selected or selected model not in list
        // The list is already sorted with the best models at the top
        if (models.isNotEmpty() && models.none { it.isSelected }) {
            appSettings.setSelectedModelId(Service.Gemini, models.first().id)
            updateModelsSelection(Service.Gemini)
        }
    }

    private suspend fun fetchOpenAICompatibleModels(service: Service) {
        val response = requests.getOpenAICompatibleModels(service).getOrThrow()
        val selectedModelId = appSettings.getSelectedModelId(service)
        val activeFiltered = if (service.filterActiveStrictly) {
            response.data.filter { it.isActive == true }
        } else {
            response.data.filter { it.isActive != false }
        }
        val filtered = if (service is Service.OpenAI) {
            val chatPrefixes = listOf("gpt-", "o1", "o3", "o4", "chatgpt-")
            activeFiltered.filter { model -> chatPrefixes.any { model.id.startsWith(it) } }
        } else {
            activeFiltered
        }
        val sorted = if (service.sortModelsById) {
            filtered.sortedBy { it.id }
        } else {
            filtered.sortedByDescending { it.context_window }
        }
        val models = sorted.map {
            SettingsModel(
                id = it.id,
                subtitle = it.owned_by ?: "",
                description = if (service.includeModelDate) it.created?.toHumanReadableDate() else null,
                isSelected = it.id == selectedModelId,
            )
        }
        modelsByService[service]?.update { models }
        // Auto-select first model if none selected or selected model not in list
        if (models.isNotEmpty() && models.none { it.isSelected }) {
            appSettings.setSelectedModelId(service, models.first().id)
            updateModelsSelection(service)
        }
    }

    private fun List<ModelDefinition>.toSettingsModels(service: Service): List<SettingsModel> {
        val selectedModelId = appSettings.getSelectedModelId(service)
        return map {
            SettingsModel(
                id = it.id,
                subtitle = it.subtitle,
                descriptionRes = it.descriptionRes,
                isSelected = it.id == selectedModelId,
            )
        }
    }

    private fun updateModelsSelection(service: Service) {
        val selectedModelId = appSettings.getSelectedModelId(service)
        modelsByService[service]?.update { models ->
            models.map { it.copy(isSelected = it.id == selectedModelId) }
        }
    }

    override suspend fun ask(question: String?, file: PlatformFile?) {
        if (question != null) {
            chatHistory.update {
                it.toMutableList().apply {
                    add(
                        History(
                            role = History.Role.USER,
                            content = question,
                            mimeType = file?.mimeType()?.toString(),
                            data = file?.readBytes()?.let { Base64.encode(it) },
                        ),
                    )
                }
            }
        }
        val service = currentService()
        val messages = chatHistory.value
        val modelId = appSettings.getSelectedModelId(service)
        val tools = if (supportsTools(modelId)) getAvailableTools() else emptyList()

        val systemPrompt = getActiveSystemPrompt()

        val responseText = when (service) {
            Service.Gemini -> {
                if (tools.isNotEmpty()) {
                    handleGeminiChatWithTools(messages, tools, systemPrompt)
                } else {
                    val geminiMessages = messages.map { it.toGeminiMessageDto() }
                    val response = requests.geminiChat(geminiMessages, systemInstruction = systemPrompt).getOrThrow()
                    response.candidates.firstOrNull()?.content?.parts?.joinToString("\n") { part ->
                        part.text ?: ""
                    } ?: ""
                }
            }

            else -> {
                // All OpenAI-compatible services (Free, Groq, XAI, OpenRouter, Nvidia, OpenAICompatible)
                if (tools.isNotEmpty()) {
                    handleOpenAICompatibleChatWithTools(service, messages, tools, systemPrompt)
                } else {
                    val openAIMessages = buildOpenAIMessages(messages, systemPrompt)
                    val response = requests.openAICompatibleChat(service, openAIMessages).getOrThrow()
                    response.choices.firstOrNull()?.message?.content ?: throw OpenAICompatibleEmptyResponseException()
                }
            }
        }

        chatHistory.update {
            it.toMutableList().apply {
                add(History(role = History.Role.ASSISTANT, content = responseText))
            }
        }

        // Auto-save conversation after each message
        saveCurrentConversation()
    }

    private suspend fun handleOpenAICompatibleChatWithTools(
        service: Service,
        messages: List<History>,
        tools: List<Tool>,
        systemPrompt: String? = null,
    ): String {
        var currentMessages = buildOpenAIMessages(
            messages.filter { it.role != History.Role.TOOL_EXECUTING },
            systemPrompt,
        )

        // Loop until AI returns a final response (no more tool calls)
        while (true) {
            val response = requests.openAICompatibleChat(service, currentMessages, tools).getOrThrow()
            val message = response.choices.firstOrNull()?.message ?: throw OpenAICompatibleEmptyResponseException()

            val toolCalls = message.toolCalls
            if (toolCalls.isNullOrEmpty()) {
                // No more tool calls - return the final response
                return message.content ?: ""
            }

            // Add assistant message with tool calls to history
            chatHistory.update {
                it.toMutableList().apply {
                    add(
                        History(
                            role = History.Role.ASSISTANT,
                            content = message.content ?: "",
                            toolCalls = toolCalls.map { tc ->
                                ToolCallInfo(id = tc.id, name = tc.function.name, arguments = tc.function.arguments)
                            },
                        ),
                    )
                }
            }

            // Process each tool call
            for (toolCall in toolCalls) {
                val toolExecutingId = Uuid.random().toString()
                val toolDisplayName = toolExecutor.getToolDisplayName(toolCall.function.name)

                // Add tool executing message to show in UI
                chatHistory.update {
                    it.toMutableList().apply {
                        add(
                            History(
                                id = toolExecutingId,
                                role = History.Role.TOOL_EXECUTING,
                                content = toolCall.function.name,
                                toolName = toolDisplayName,
                            ),
                        )
                    }
                }

                // Execute the tool
                val toolResult = toolExecutor.executeTool(toolCall.function.name, toolCall.function.arguments)

                // Remove tool executing message and add tool result
                chatHistory.update { history ->
                    buildList(history.size) {
                        for (h in history) {
                            if (h.id != toolExecutingId) add(h)
                        }
                        add(
                            History(
                                role = History.Role.TOOL,
                                content = toolResult,
                                toolCallId = toolCall.id,
                                toolName = toolCall.function.name,
                            ),
                        )
                    }
                }
            }

            // Update messages for next iteration
            currentMessages = buildOpenAIMessages(
                chatHistory.value.filter { it.role != History.Role.TOOL_EXECUTING },
                systemPrompt,
            )
        }
    }

    private suspend fun handleGeminiChatWithTools(messages: List<History>, tools: List<Tool>, systemPrompt: String? = null): String {
        // Loop until AI returns a final response (no more function calls)
        while (true) {
            val currentMessages = chatHistory.value.filter { it.role != History.Role.TOOL_EXECUTING }
            val geminiMessages = currentMessages.map { it.toGeminiMessageDto() }

            val response = requests.geminiChat(messages = geminiMessages, tools = tools, systemInstruction = systemPrompt).getOrThrow()
            val parts = response.candidates.firstOrNull()?.content?.parts ?: return ""

            // Check for function calls in the response (parts that have functionCall)
            val partsWithFunctionCalls = parts.filter { it.functionCall != null }
            if (partsWithFunctionCalls.isEmpty()) {
                // No function calls - return the text response
                return parts.joinToString("\n") { it.text ?: "" }
            }

            // Convert Gemini function calls to ToolCallInfo with synthetic IDs
            // Include thoughtSignature from the Part (required for Gemini 3 models)
            val toolCallInfos = partsWithFunctionCalls.map { part ->
                val fc = part.functionCall!!
                val argsJson = fc.args?.let { args ->
                    args.entries.joinToString(", ", "{", "}") { (k, v) ->
                        "\"$k\": ${toolExecutor.formatJsonElement(v)}"
                    }
                } ?: "{}"
                ToolCallInfo(
                    id = "gemini-${Uuid.random()}",
                    name = fc.name,
                    arguments = argsJson,
                    thoughtSignature = part.thoughtSignature,
                )
            }

            // Add assistant message with tool calls to history
            val textContent = parts.mapNotNull { it.text }.joinToString("\n")
            chatHistory.update {
                it.toMutableList().apply {
                    add(
                        History(
                            role = History.Role.ASSISTANT,
                            content = textContent,
                            toolCalls = toolCallInfos,
                        ),
                    )
                }
            }

            // Process each function call
            for (toolCallInfo in toolCallInfos) {
                val toolExecutingId = Uuid.random().toString()
                val toolDisplayName = toolExecutor.getToolDisplayName(toolCallInfo.name)

                // Add tool executing message to show in UI
                chatHistory.update {
                    it.toMutableList().apply {
                        add(
                            History(
                                id = toolExecutingId,
                                role = History.Role.TOOL_EXECUTING,
                                content = toolCallInfo.name,
                                toolName = toolDisplayName,
                            ),
                        )
                    }
                }

                // Execute the tool
                val toolResult = toolExecutor.executeTool(toolCallInfo.name, toolCallInfo.arguments)

                // Remove tool executing message and add tool result
                chatHistory.update { history ->
                    buildList(history.size) {
                        for (h in history) {
                            if (h.id != toolExecutingId) add(h)
                        }
                        add(
                            History(
                                role = History.Role.TOOL,
                                content = toolResult,
                                toolCallId = toolCallInfo.id,
                                toolName = toolCallInfo.name,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun buildOpenAIMessages(
        messages: List<History>,
        systemPrompt: String?,
    ): List<com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto.Message> = buildList {
        if (!systemPrompt.isNullOrEmpty()) {
            add(
                com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto.Message(
                    role = "system",
                    content = systemPrompt,
                ),
            )
        }
        addAll(
            messages.map { it.toGroqMessageDto() }
                .filter { msg ->
                    // Drop tool messages that lost their tool_call_id (from conversation reload)
                    if (msg.role == "tool" && msg.tool_call_id == null) return@filter false
                    // Drop assistant messages that had tool_calls but lost them (empty content, no tool_calls)
                    if (msg.role == "assistant" && msg.content.isNullOrEmpty() && msg.tool_calls.isNullOrEmpty()) return@filter false
                    true
                },
        )
    }

    private fun trimToRecentExchanges(history: List<History>, maxExchanges: Int): List<History> {
        val userIndices = history.mapIndexedNotNull { index, h ->
            if (h.role == History.Role.USER) index else null
        }
        if (userIndices.size <= maxExchanges) return history
        val cutoffIndex = userIndices[userIndices.size - maxExchanges]
        return history.subList(cutoffIndex, history.size)
    }

    private suspend fun saveCurrentConversation() {
        val history = trimToRecentExchanges(chatHistory.value, 20)
        if (history.isEmpty()) return

        val now = Clock.System.now().toEpochMilliseconds()
        val conversationId = _currentConversationId.value ?: Uuid.random().toString().also {
            _currentConversationId.value = it
        }

        val firstUserMessage = history.firstOrNull { it.role == History.Role.USER }
        val title = firstUserMessage?.content?.take(50) ?: getString(Res.string.new_conversation)

        val existingConversation = savedConversations.value.find { it.id == conversationId }

        val conversation = Conversation(
            id = conversationId,
            title = title,
            messages = history
                .filter { it.role != History.Role.TOOL_EXECUTING }
                .map { h ->
                    Conversation.Message(
                        id = h.id,
                        role = when (h.role) {
                            History.Role.USER -> "user"
                            History.Role.ASSISTANT -> "assistant"
                            History.Role.TOOL -> "tool"
                            History.Role.TOOL_EXECUTING -> "tool" // Should not happen due to filter
                        },
                        content = h.content,
                        mimeType = h.mimeType,
                        data = h.data,
                    )
                },
            createdAt = existingConversation?.createdAt ?: now,
            updatedAt = now,
            serviceId = currentService().id,
        )

        conversationStorage.saveConversation(conversation)
    }

    override fun clearHistory() {
        chatHistory.update {
            emptyList()
        }
    }

    override fun isUsingSharedKey(): Boolean = currentService() == Service.Free

    override fun currentService(): Service = appSettings.currentService()

    // Conversation management
    override suspend fun loadConversations() {
        conversationStorage.loadConversations()
    }

    private suspend fun loadConversation(id: String) {
        val conversation = savedConversations.value.find { it.id == id } ?: return

        _currentConversationId.value = id
        chatHistory.value = conversation.messages.map { m ->
            History(
                id = m.id,
                role = when (m.role) {
                    "user" -> History.Role.USER
                    "tool" -> History.Role.TOOL
                    else -> History.Role.ASSISTANT
                },
                content = m.content,
                mimeType = m.mimeType,
                data = m.data,
            )
        }
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
        _currentConversationId.value = null
        chatHistory.value = emptyList()
    }

    override suspend fun restoreLatestConversation() {
        val service = currentService()

        // Already have a loaded conversation with messages — nothing to do
        val currentId = _currentConversationId.value
        if (currentId != null && chatHistory.value.isNotEmpty() &&
            savedConversations.value.any { it.id == currentId && it.serviceId == service.id }
        ) {
            return
        }

        val latest = savedConversations.value
            .filter { it.serviceId == service.id }
            .maxByOrNull { it.updatedAt }
            ?: return

        loadConversation(latest.id)
    }

    // Tool management
    override fun getToolDefinitions(): List<ToolInfo> = getPlatformToolDefinitions().map { it.copy(isEnabled = appSettings.isToolEnabled(it.id, defaultEnabled = it.isEnabled)) }

    override fun setToolEnabled(toolId: String, enabled: Boolean) {
        appSettings.setToolEnabled(toolId, enabled)
    }

    // Soul (system prompt)
    override fun getSoulText(): String = appSettings.getSoulText()

    override fun setSoulText(text: String) {
        appSettings.setSoulText(text)
    }

    override suspend fun getActiveSystemPrompt(): String? {
        val soul = appSettings.getSoulText().ifEmpty { getString(Res.string.default_soul) }
        val memoryEnabled = appSettings.isMemoryEnabled()
        val schedulingEnabled = appSettings.isSchedulingEnabled()
        return buildString {
            append(soul)
            if (memoryEnabled) {
                val memoryInstructions = appSettings.getMemoryInstructions()
                if (memoryInstructions.isNotEmpty()) {
                    if (isNotEmpty()) append("\n\n")
                    append(memoryInstructions)
                }
                val memories = memoryStore.getAllMemories()
                if (memories.isNotEmpty()) {
                    val general = memories.filter { it.category == MemoryCategory.GENERAL }
                    val preferences = memories.filter { it.category == MemoryCategory.PREFERENCE }
                    val learnings = memories.filter { it.category == MemoryCategory.LEARNING }
                    val errors = memories.filter { it.category == MemoryCategory.ERROR }

                    if (general.isNotEmpty()) {
                        append("\n\n## Your Memories\n")
                        for (m in general) {
                            append("- **${m.key}**: ${m.content}\n")
                        }
                    }
                    if (preferences.isNotEmpty()) {
                        append("\n\n## User Preferences\n")
                        for (m in preferences) {
                            append("- **${m.key}**: ${m.content}\n")
                        }
                    }
                    if (learnings.isNotEmpty()) {
                        append("\n\n## Learnings\n")
                        for (m in learnings) {
                            append("- **${m.key}** (reinforced ${m.hitCount}x): ${m.content}\n")
                        }
                    }
                    if (errors.isNotEmpty()) {
                        append("\n\n## Known Issues & Resolutions\n")
                        for (m in errors) {
                            append("- **${m.key}**: ${m.content}\n")
                        }
                    }
                }
            }
            if (schedulingEnabled) {
                val pendingTasks = taskStore.getAllTasks().filter { it.status == TaskStatus.PENDING }
                if (pendingTasks.isNotEmpty()) {
                    append("\n\n## Scheduled Tasks\n")
                    for (t in pendingTasks) {
                        append("- **${t.description}** (id: ${t.id}, scheduled: ${Instant.fromEpochMilliseconds(t.scheduledAtEpochMs)})")
                        if (t.cron != null) append(" [cron: ${t.cron}]")
                        append("\n")
                    }
                }
            }
        }.ifEmpty { null }
    }

    override fun isMemoryEnabled(): Boolean = appSettings.isMemoryEnabled()

    override fun setMemoryEnabled(enabled: Boolean) {
        appSettings.setMemoryEnabled(enabled)
    }

    override fun getMemories(): List<MemoryEntry> = memoryStore.getAllMemories()

    override suspend fun deleteMemory(key: String) {
        memoryStore.forget(key)
    }

    override fun isSchedulingEnabled(): Boolean = appSettings.isSchedulingEnabled()

    override fun setSchedulingEnabled(enabled: Boolean) {
        appSettings.setSchedulingEnabled(enabled)
    }

    override fun getScheduledTasks(): List<ScheduledTask> = taskStore.getAllTasks()

    override suspend fun cancelScheduledTask(id: String) {
        taskStore.removeTask(id)
    }

    override fun isDaemonEnabled(): Boolean = appSettings.isDaemonEnabled()

    override fun setDaemonEnabled(enabled: Boolean) {
        appSettings.setDaemonEnabled(enabled)
    }

    override fun getHeartbeatConfig(): HeartbeatConfig = heartbeatManager.getConfig()

    override fun setHeartbeatEnabled(enabled: Boolean) {
        val config = heartbeatManager.getConfig()
        heartbeatManager.saveConfig(config.copy(enabled = enabled))
    }

    override fun getHeartbeatPrompt(): String = appSettings.getHeartbeatPrompt()

    override fun setHeartbeatPrompt(text: String) {
        appSettings.setHeartbeatPrompt(text)
    }

    override fun getHeartbeatLog(): List<HeartbeatLogEntry> = heartbeatManager.getHeartbeatLog()

    override suspend fun askSilently(question: String): String {
        val service = currentService()
        val messages = chatHistory.value + History(role = History.Role.USER, content = question)
        val modelId = appSettings.getSelectedModelId(service)
        val systemPrompt = getActiveSystemPrompt()

        val responseText = when (service) {
            Service.Gemini -> {
                val geminiMessages = messages.map { it.toGeminiMessageDto() }
                val response = requests.geminiChat(geminiMessages, systemInstruction = systemPrompt).getOrThrow()
                response.candidates.firstOrNull()?.content?.parts?.joinToString("\n") { part ->
                    part.text ?: ""
                } ?: ""
            }

            else -> {
                val openAIMessages = buildOpenAIMessages(messages, systemPrompt)
                val response = requests.openAICompatibleChat(service, openAIMessages).getOrThrow()
                response.choices.firstOrNull()?.message?.content ?: ""
            }
        }

        return responseText
    }

    override fun addAssistantMessage(content: String) {
        chatHistory.update {
            it.toMutableList().apply {
                add(History(role = History.Role.ASSISTANT, content = content))
            }
        }
    }
}
