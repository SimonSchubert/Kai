@file:OptIn(InternalCoilApi::class, ExperimentalEncodingApi::class, ExperimentalTime::class, ExperimentalUuidApi::class)

package com.inspiredandroid.kai.data

import coil3.annotation.InternalCoilApi
import coil3.util.MimeTypeMap
import com.inspiredandroid.kai.currentTimeMillis
import com.inspiredandroid.kai.getAvailableTools
import com.inspiredandroid.kai.getPlatformToolDefinitions
import com.inspiredandroid.kai.network.Requests
import com.inspiredandroid.kai.network.dtos.gemini.GeminiChatResponseDto
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
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class RemoteDataRepository(
    private val requests: Requests,
    private val appSettings: AppSettings,
    private val conversationStorage: ConversationStorage,
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

    override val savedConversations: StateFlow<List<Conversation>> = conversationStorage.conversations

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
            Service.Groq -> fetchGroqModels()
            Service.XAI -> fetchXaiModels()
            Service.OpenRouter -> fetchOpenRouterModels()
            Service.OpenAICompatible -> fetchOpenAICompatibleModels()
            Service.Gemini -> fetchGeminiModels()
            Service.Free -> { /* Free has no models */ }
        }
    }

    override suspend fun validateConnection(service: Service) {
        when (service) {
            Service.Gemini -> fetchGeminiModels()

            Service.Groq -> fetchGroqModels()

            Service.XAI -> fetchXaiModels()

            Service.OpenRouter -> {
                // Validate API key first, then fetch models
                requests.validateOpenRouterApiKey().getOrThrow()
                fetchOpenRouterModels()
            }

            Service.OpenAICompatible -> fetchOpenAICompatibleModels()

            Service.Free -> { /* Always valid */ }
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

    private suspend fun fetchGroqModels() {
        val response = requests.getGroqModels().getOrThrow()
        val selectedModelId = appSettings.getSelectedModelId(Service.Groq)
        val models = response.data
            .filter { it.isActive == true }
            .sortedByDescending { it.context_window }
            .map {
                SettingsModel(
                    id = it.id,
                    subtitle = it.owned_by ?: "",
                    description = it.created?.toHumanReadableDate(),
                    createdAt = it.created ?: 0L,
                    isSelected = it.id == selectedModelId,
                )
            }
        modelsByService[Service.Groq]?.update { models }
        // Auto-select first model if none selected or selected model not in list
        if (models.isNotEmpty() && models.none { it.isSelected }) {
            appSettings.setSelectedModelId(Service.Groq, models.first().id)
            updateModelsSelection(Service.Groq)
        }
    }

    private suspend fun fetchXaiModels() {
        val response = requests.getXaiModels().getOrThrow()
        val selectedModelId = appSettings.getSelectedModelId(Service.XAI)
        val models = response.data
            .filter { it.isActive != false }
            .sortedByDescending { it.context_window }
            .map {
                SettingsModel(
                    id = it.id,
                    subtitle = it.owned_by ?: "",
                    description = it.created?.toHumanReadableDate(),
                    createdAt = it.created ?: 0L,
                    isSelected = it.id == selectedModelId,
                )
            }
        modelsByService[Service.XAI]?.update { models }
        // Auto-select first model if none selected or selected model not in list
        if (models.isNotEmpty() && models.none { it.isSelected }) {
            appSettings.setSelectedModelId(Service.XAI, models.first().id)
            updateModelsSelection(Service.XAI)
        }
    }

    private suspend fun fetchOpenRouterModels() {
        val response = requests.getOpenRouterModels().getOrThrow()
        val selectedModelId = appSettings.getSelectedModelId(Service.OpenRouter)
        val models = response.data
            .filter { it.isActive != false }
            .sortedByDescending { it.context_window }
            .map {
                SettingsModel(
                    id = it.id,
                    subtitle = it.owned_by ?: "",
                    description = it.created?.toHumanReadableDate(),
                    createdAt = it.created ?: 0L,
                    isSelected = it.id == selectedModelId,
                )
            }
        modelsByService[Service.OpenRouter]?.update { models }
        // Auto-select first model if none selected or selected model not in list
        if (models.isNotEmpty() && models.none { it.isSelected }) {
            appSettings.setSelectedModelId(Service.OpenRouter, models.first().id)
            updateModelsSelection(Service.OpenRouter)
        }
    }

    private suspend fun fetchOpenAICompatibleModels() {
        val baseUrl = appSettings.getBaseUrl(Service.OpenAICompatible)
        val response = requests.getOpenAICompatibleModels(baseUrl).getOrThrow()
        val selectedModelId = appSettings.getSelectedModelId(Service.OpenAICompatible)
        val models = response.models
            .sortedBy { it.name }
            .map {
                SettingsModel(
                    id = it.name,
                    subtitle = it.details?.family ?: "",
                    description = it.details?.parameter_size,
                    isSelected = it.name == selectedModelId,
                )
            }
        modelsByService[Service.OpenAICompatible]?.update { models }
        // Auto-select first model if none selected or selected model not in list
        if (models.isNotEmpty() && models.none { it.isSelected }) {
            appSettings.setSelectedModelId(Service.OpenAICompatible, models.first().id)
            updateModelsSelection(Service.OpenAICompatible)
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
                it + History(
                    role = History.Role.USER,
                    content = question,
                    mimeType = file?.extension?.let { MimeTypeMap.getMimeTypeFromExtension(it) },
                    data = file?.readBytes()?.let { Base64.encode(it) },
                )
            }
        }
        val service = currentService()
        val messages = chatHistory.value
        val tools = getAvailableTools()

        val responseText = when (service) {
            Service.Gemini -> {
                if (tools.isNotEmpty()) {
                    handleGeminiChatWithTools(messages, tools)
                } else {
                    val geminiMessages = messages.map { it.toGeminiMessageDto() }
                    val response = requests.geminiChat(geminiMessages).getOrThrow()
                    response.candidates.firstOrNull()?.content?.parts?.joinToString("\n") { part ->
                        part.text ?: ""
                    } ?: ""
                }
            }

            else -> {
                // All OpenAI-compatible services (Free, Groq, XAI, OpenRouter, OpenAICompatible)
                if (tools.isNotEmpty()) {
                    handleOpenAICompatibleChatWithTools(service, messages, tools)
                } else {
                    val openAIMessages = messages.map { it.toGroqMessageDto() }
                    val response = sendOpenAICompatibleRequest(service, openAIMessages, emptyList()).getOrThrow()
                    response.choices.firstOrNull()?.message?.content ?: ""
                }
            }
        }

        chatHistory.update {
            it + History(role = History.Role.ASSISTANT, content = responseText)
        }

        // Auto-save conversation after each message
        saveCurrentConversation()
    }

    private suspend fun sendOpenAICompatibleRequest(
        service: Service,
        messages: List<com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto.Message>,
        tools: List<Tool>,
    ): Result<OpenAICompatibleChatResponseDto> = when (service) {
        Service.Free -> requests.freeChat(messages = messages, tools = tools)

        Service.Groq -> requests.groqChat(messages = messages, tools = tools)

        Service.XAI -> requests.xaiChat(messages = messages, tools = tools)

        Service.OpenRouter -> requests.openRouterChat(messages = messages, tools = tools)

        Service.OpenAICompatible -> {
            val baseUrl = appSettings.getBaseUrl(Service.OpenAICompatible)
            requests.openAICompatibleChat(messages = messages, baseUrl = baseUrl, tools = tools)
        }

        Service.Gemini -> throw IllegalArgumentException("Gemini should not use OpenAI-compatible request")
    }

    private suspend fun handleOpenAICompatibleChatWithTools(
        service: Service,
        messages: List<History>,
        tools: List<Tool>,
    ): String {
        var currentMessages = messages.filter { it.role != History.Role.TOOL_EXECUTING }.map { it.toGroqMessageDto() }

        // Loop until AI returns a final response (no more tool calls)
        while (true) {
            val response = sendOpenAICompatibleRequest(service, currentMessages, tools).getOrThrow()
            val message = response.choices.firstOrNull()?.message ?: return ""

            val toolCalls = message.toolCalls
            if (toolCalls.isNullOrEmpty()) {
                // No more tool calls - return the final response
                return message.content ?: ""
            }

            // Add assistant message with tool calls to history
            chatHistory.update {
                it + History(
                    role = History.Role.ASSISTANT,
                    content = message.content ?: "",
                    toolCalls = toolCalls.map { tc ->
                        ToolCallInfo(id = tc.id, name = tc.function.name, arguments = tc.function.arguments)
                    },
                )
            }

            // Process each tool call
            for (toolCall in toolCalls) {
                val toolExecutingId = Uuid.random().toString()
                val toolDisplayName = getToolDisplayName(toolCall.function.name)

                // Add tool executing message to show in UI
                chatHistory.update {
                    it + History(
                        id = toolExecutingId,
                        role = History.Role.TOOL_EXECUTING,
                        content = toolCall.function.name,
                        toolName = toolDisplayName,
                    )
                }

                // Execute the tool
                val toolResult = executeTool(toolCall.function.name, toolCall.function.arguments)

                // Remove tool executing message and add tool result
                chatHistory.update { history ->
                    history.filter { it.id != toolExecutingId } + History(
                        role = History.Role.TOOL,
                        content = toolResult,
                        toolCallId = toolCall.id,
                        toolName = toolCall.function.name,
                    )
                }
            }

            // Update messages for next iteration
            currentMessages = chatHistory.value.filter { it.role != History.Role.TOOL_EXECUTING }.map { it.toGroqMessageDto() }
        }
    }

    private suspend fun handleGeminiChatWithTools(messages: List<History>, tools: List<Tool>): String {
        // Loop until AI returns a final response (no more function calls)
        while (true) {
            val currentMessages = chatHistory.value.filter { it.role != History.Role.TOOL_EXECUTING }
            val geminiMessages = currentMessages.map { it.toGeminiMessageDto() }

            val response = requests.geminiChat(messages = geminiMessages, tools = tools).getOrThrow()
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
                        "\"$k\": ${formatJsonElement(v)}"
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
                it + History(
                    role = History.Role.ASSISTANT,
                    content = textContent,
                    toolCalls = toolCallInfos,
                )
            }

            // Process each function call
            for (toolCallInfo in toolCallInfos) {
                val toolExecutingId = Uuid.random().toString()
                val toolDisplayName = getToolDisplayName(toolCallInfo.name)

                // Add tool executing message to show in UI
                chatHistory.update {
                    it + History(
                        id = toolExecutingId,
                        role = History.Role.TOOL_EXECUTING,
                        content = toolCallInfo.name,
                        toolName = toolDisplayName,
                    )
                }

                // Execute the tool
                val toolResult = executeTool(toolCallInfo.name, toolCallInfo.arguments)

                // Remove tool executing message and add tool result
                chatHistory.update { history ->
                    history.filter { it.id != toolExecutingId } + History(
                        role = History.Role.TOOL,
                        content = toolResult,
                        toolCallId = toolCallInfo.id,
                        toolName = toolCallInfo.name,
                    )
                }
            }
        }
    }

    private fun formatJsonElement(element: JsonElement): String = when {
        element is JsonNull -> "null"
        element is kotlinx.serialization.json.JsonPrimitive && element.isString -> "\"${element.content}\""
        element is kotlinx.serialization.json.JsonPrimitive -> element.content
        else -> element.toString()
    }

    private val jsonParser = Json { ignoreUnknownKeys = true }

    private suspend fun executeTool(name: String, arguments: String): String {
        println("[Tool] executeTool called: name=$name, arguments=$arguments")

        // Find the tool in available tools
        val tools = getAvailableTools()
        println("[Tool] Available tools: ${tools.map { it.schema.name }}")
        val tool = tools.find { it.schema.name == name }
        if (tool == null) {
            println("[Tool] Tool not found: $name")
            return """{"success": false, "error": "Unknown tool: $name"}"""
        }

        // Parse JSON arguments to Map
        val args = try {
            parseJsonToMap(arguments)
        } catch (e: Exception) {
            println("[Tool] Failed to parse arguments: ${e.message}")
            return """{"success": false, "error": "Failed to parse arguments: ${e.message}"}"""
        }
        println("[Tool] Parsed arguments: $args")

        // Execute the tool
        return try {
            println("[Tool] Executing tool...")
            val result = tool.execute(args)
            println("[Tool] Tool execution result: $result")
            when (result) {
                is Map<*, *> -> {
                    // Convert map to JSON string
                    val jsonEntries = result.entries.joinToString(", ") { (k, v) ->
                        val valueStr = when (v) {
                            is String -> "\"$v\""
                            is Boolean, is Number -> v.toString()
                            else -> "\"$v\""
                        }
                        "\"$k\": $valueStr"
                    }
                    "{$jsonEntries}"
                }

                is String -> result

                else -> """{"result": "$result"}"""
            }
        } catch (e: Exception) {
            println("[Tool] Tool execution failed: ${e.message}")
            e.printStackTrace()
            """{"success": false, "error": "Tool execution failed: ${e.message}"}"""
        }
    }

    private fun parseJsonToMap(json: String): Map<String, Any> {
        val jsonObject = jsonParser.parseToJsonElement(json).jsonObject
        return jsonObject.toMap()
    }

    private fun JsonObject.toMap(): Map<String, Any> = entries.associate { (key, value) ->
        key to when {
            value is JsonPrimitive && value.isString -> value.content
            value is JsonPrimitive && value.booleanOrNull != null -> value.boolean
            value is JsonPrimitive && value.intOrNull != null -> value.int
            value is JsonPrimitive && value.doubleOrNull != null -> value.double
            value is JsonObject -> value.toMap()
            else -> value.toString()
        }
    }

    private suspend fun saveCurrentConversation() {
        val history = chatHistory.value
        if (history.isEmpty()) return

        val now = currentTimeMillis()
        val conversationId = _currentConversationId.value ?: Uuid.random().toString().also {
            _currentConversationId.value = it
        }

        val firstUserMessage = history.firstOrNull { it.role == History.Role.USER }
        val title = firstUserMessage?.content?.take(50) ?: "New conversation"

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

    override suspend fun loadConversation(id: String) {
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

    override suspend fun deleteConversation(id: String) {
        conversationStorage.deleteConversation(id)
        if (_currentConversationId.value == id) {
            startNewChat()
        }
    }

    override suspend fun deleteAllConversations() {
        conversationStorage.deleteAllConversations()
        startNewChat()
    }

    override fun startNewChat() {
        _currentConversationId.value = null
        chatHistory.value = emptyList()
    }

    // Tool management
    override fun getToolDefinitions(): List<ToolInfo> = getPlatformToolDefinitions().map { it.copy(isEnabled = appSettings.isToolEnabled(it.id)) }

    /**
     * Gets the human-readable display name for a tool given its ID.
     * Falls back to the ID if not found.
     */
    private fun getToolDisplayName(toolId: String): String = getPlatformToolDefinitions().find { it.id == toolId }?.name ?: toolId

    override fun setToolEnabled(toolId: String, enabled: Boolean) {
        appSettings.setToolEnabled(toolId, enabled)
    }
}
