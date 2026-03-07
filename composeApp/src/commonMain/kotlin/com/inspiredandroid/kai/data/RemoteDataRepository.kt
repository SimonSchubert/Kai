@file:OptIn(ExperimentalEncodingApi::class, ExperimentalTime::class, ExperimentalUuidApi::class)

package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.compressImageBytes
import com.inspiredandroid.kai.getAvailableTools
import com.inspiredandroid.kai.getPlatformToolDefinitions
import com.inspiredandroid.kai.network.OpenAICompatibleEmptyResponseException
import com.inspiredandroid.kai.network.Requests
import com.inspiredandroid.kai.network.ServiceCredentials
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.platformName
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.compose.resources.getString
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val modelsWithoutImageSupport = listOf(
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

private const val MAX_TOOL_ITERATIONS = 15
private const val MAX_REPEATED_TOOL_CALLS = 3
private const val MAX_API_RETRIES = 2
private const val ESTIMATED_CHARS_PER_TOKEN = 4
private const val DEFAULT_CONTEXT_WINDOW_TOKENS = 100_000

private fun supportsTools(modelId: String): Boolean {
    val lower = modelId.lowercase()
    return modelsWithoutToolSupport.none { lower.startsWith(it) }
}

private fun supportsImageAttachment(modelId: String): Boolean {
    val lower = modelId.lowercase()
    return modelsWithoutImageSupport.none { lower.startsWith(it) }
}

class RemoteDataRepository(
    private val requests: Requests,
    private val appSettings: AppSettings,
    private val conversationStorage: ConversationStorage,
    private val toolExecutor: ToolExecutor,
    private val memoryStore: MemoryStore,
    private val taskStore: TaskStore,
    private val heartbeatManager: HeartbeatManager,
    private val emailStore: EmailStore,
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

    // Per-instance model storage: instanceId -> models flow
    private val modelsByInstance: MutableMap<String, MutableStateFlow<List<SettingsModel>>> = mutableMapOf()

    /** Build credentials from per-instance settings */
    private fun instanceCredentials(instanceId: String, service: Service): ServiceCredentials = ServiceCredentials(
        apiKey = appSettings.getInstanceApiKey(instanceId),
        modelId = appSettings.getInstanceModelId(instanceId).ifEmpty { appSettings.getSelectedModelId(service) },
        baseUrl = appSettings.getInstanceBaseUrl(instanceId).ifEmpty { appSettings.getBaseUrl(service) },
    )

    override val chatHistory: MutableStateFlow<List<History>> = MutableStateFlow(emptyList())

    private val _currentConversationId = MutableStateFlow<String?>(null)
    override val currentConversationId: StateFlow<String?> = _currentConversationId

    private val savedConversations: StateFlow<List<Conversation>> = conversationStorage.conversations

    override fun getConfiguredServiceInstances(): List<ServiceInstance> = appSettings.getConfiguredServiceInstances().filter { Service.fromId(it.serviceId) != Service.Free }

    override fun addConfiguredService(serviceId: String): ServiceInstance {
        val instanceId = appSettings.generateInstanceId(serviceId)
        val instance = ServiceInstance(instanceId = instanceId, serviceId = serviceId)
        val current = appSettings.getConfiguredServiceInstances().toMutableList()
        current.add(instance)
        appSettings.setConfiguredServiceInstances(current)
        return instance
    }

    override fun removeConfiguredService(instanceId: String) {
        val current = appSettings.getConfiguredServiceInstances().toMutableList()
        current.removeAll { it.instanceId == instanceId }
        appSettings.setConfiguredServiceInstances(current)
        appSettings.removeInstanceSettings(instanceId)
        modelsByInstance.remove(instanceId)
    }

    override fun reorderConfiguredServices(orderedInstanceIds: List<String>) {
        val current = appSettings.getConfiguredServiceInstances()
        val byId = current.associateBy { it.instanceId }
        val reordered = orderedInstanceIds.mapNotNull { byId[it] }
        appSettings.setConfiguredServiceInstances(reordered)
    }

    override fun getOrderedServicesForFallback(): List<Service> {
        val instances = getConfiguredServiceInstances()
        val services = instances.map { Service.fromId(it.serviceId) }.filter { it != Service.Free }
        return if (services.isEmpty()) {
            listOf(Service.Free)
        } else if (appSettings.isFreeFallbackEnabled()) {
            services + Service.Free
        } else {
            services
        }
    }

    override fun isFreeFallbackEnabled(): Boolean = appSettings.isFreeFallbackEnabled()

    override fun setFreeFallbackEnabled(enabled: Boolean) {
        appSettings.setFreeFallbackEnabled(enabled)
    }

    // Per-instance settings
    override fun getInstanceApiKey(instanceId: String): String = appSettings.getInstanceApiKey(instanceId)

    override fun updateInstanceApiKey(instanceId: String, apiKey: String) {
        appSettings.setInstanceApiKey(instanceId, apiKey)
    }

    override fun getInstanceBaseUrl(instanceId: String, service: Service): String {
        val url = appSettings.getInstanceBaseUrl(instanceId)
        return url.ifBlank { if (service is Service.OpenAICompatible) Service.DEFAULT_OPENAI_COMPATIBLE_BASE_URL else "" }
    }

    override fun updateInstanceBaseUrl(instanceId: String, baseUrl: String) {
        appSettings.setInstanceBaseUrl(instanceId, baseUrl)
    }

    override fun getInstanceModels(instanceId: String, service: Service): StateFlow<List<SettingsModel>> = modelsByInstance.getOrPut(instanceId) {
        val selectedModelId = appSettings.getInstanceModelId(instanceId)
        MutableStateFlow(
            service.defaultModels.map {
                SettingsModel(
                    id = it.id,
                    subtitle = it.subtitle,
                    descriptionRes = it.descriptionRes,
                    isSelected = it.id == selectedModelId,
                )
            },
        )
    }

    override fun updateInstanceSelectedModel(instanceId: String, service: Service, modelId: String) {
        appSettings.setInstanceModelId(instanceId, modelId)
        modelsByInstance[instanceId]?.update { models ->
            models.map { it.copy(isSelected = it.id == modelId) }
        }
    }

    override fun clearInstanceModels(instanceId: String, service: Service) {
        modelsByInstance[instanceId]?.update { emptyList() }
    }

    override suspend fun validateConnection(service: Service, instanceId: String) {
        val creds = instanceCredentials(instanceId, service)
        when (service) {
            Service.Free -> { /* Always valid */ }

            Service.OpenRouter -> {
                requests.validateOpenRouterApiKey(creds).getOrThrow()
                fetchInstanceModels(service, instanceId)
            }

            else -> fetchInstanceModels(service, instanceId)
        }
    }

    private suspend fun fetchInstanceModels(service: Service, instanceId: String) {
        when (service) {
            Service.Gemini -> fetchGeminiModelsForInstance(instanceId)
            Service.Free -> { /* No model listing */ }
            else -> fetchOpenAICompatibleModelsForInstance(service, instanceId)
        }
    }

    private suspend fun fetchGeminiModelsForInstance(instanceId: String) {
        val creds = instanceCredentials(instanceId, Service.Gemini)
        val response = requests.getGeminiModels(creds).getOrThrow()
        val selectedModelId = appSettings.getInstanceModelId(instanceId)
        val models = response.models
            .filter { it.supportedGenerationMethods?.contains("generateContent") == true }
            .map {
                val modelId = it.name.removePrefix("models/")
                SettingsModel(
                    id = modelId,
                    subtitle = it.displayName ?: modelId,
                    description = it.description,
                    isSelected = modelId == selectedModelId,
                )
            }
            .sortedWith(geminiModelComparator)
        val flow = modelsByInstance.getOrPut(instanceId) { MutableStateFlow(emptyList()) }
        flow.update { models }
        if (models.isNotEmpty() && models.none { it.isSelected }) {
            val default = pickDefaultModel(models)
            if (default != null) {
                appSettings.setInstanceModelId(instanceId, default.id)
                flow.update { m -> m.map { it.copy(isSelected = it.id == default.id) } }
            }
        }
    }

    private suspend fun fetchOpenAICompatibleModelsForInstance(service: Service, instanceId: String) {
        val creds = instanceCredentials(instanceId, service)
        val response = requests.getOpenAICompatibleModels(service, creds).getOrThrow()
        val selectedModelId = appSettings.getInstanceModelId(instanceId)
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
        val flow = modelsByInstance.getOrPut(instanceId) { MutableStateFlow(emptyList()) }
        flow.update { models }
        if (models.isNotEmpty() && models.none { it.isSelected }) {
            val default = pickDefaultModel(models)
            if (default != null) {
                appSettings.setInstanceModelId(instanceId, default.id)
                flow.update { m -> m.map { it.copy(isSelected = it.id == default.id) } }
            }
        }
    }

    private fun pickDefaultModel(models: List<SettingsModel>): SettingsModel? = models.firstOrNull { it.id.contains("kimi-k2.5", ignoreCase = true) }
        ?: models.firstOrNull()

    private suspend fun askWithService(
        service: Service,
        messages: List<History>,
        systemPrompt: String?,
        instanceId: String,
    ): String {
        val creds = instanceCredentials(instanceId, service)
        val tools = if (supportsTools(creds.modelId)) getAvailableTools() else emptyList()

        return when (service) {
            Service.Gemini -> {
                if (tools.isNotEmpty()) {
                    handleGeminiChatWithTools(creds, messages, tools, systemPrompt)
                } else {
                    val geminiMessages = messages.map { it.toGeminiMessageDto() }
                    val response = requests.geminiChat(creds, geminiMessages, systemInstruction = systemPrompt).getOrThrow()
                    response.candidates.firstOrNull()?.content?.parts?.joinToString("\n") { part ->
                        part.text ?: ""
                    } ?: ""
                }
            }

            else -> {
                if (tools.isNotEmpty()) {
                    handleOpenAICompatibleChatWithTools(service, creds, messages, tools, systemPrompt)
                } else {
                    val openAIMessages = buildOpenAIMessages(messages, systemPrompt)
                    val response = requests.openAICompatibleChat(service, creds, openAIMessages).getOrThrow()
                    response.choices.firstOrNull()?.message?.content ?: throw OpenAICompatibleEmptyResponseException()
                }
            }
        }
    }

    private fun hasValidInstanceApiKey(instanceId: String, service: Service): Boolean {
        if (service == Service.Free) return true
        if (!service.requiresApiKey && !service.supportsOptionalApiKey) return true
        if (service.requiresApiKey) return appSettings.getInstanceApiKey(instanceId).isNotBlank()
        return true // Optional API key services are always valid
    }

    private data class FallbackEntry(val instanceId: String, val service: Service)

    private fun getOrderedFallbackEntries(): List<FallbackEntry> {
        val instances = getConfiguredServiceInstances()
        val entries = instances.map { FallbackEntry(instanceId = it.instanceId, service = Service.fromId(it.serviceId)) }
            .filter { it.service != Service.Free }
        return if (entries.isEmpty()) {
            listOf(FallbackEntry(instanceId = "free", service = Service.Free))
        } else if (appSettings.isFreeFallbackEnabled()) {
            entries + FallbackEntry(instanceId = "free", service = Service.Free)
        } else {
            entries
        }
    }

    override suspend fun ask(question: String?, file: PlatformFile?) {
        // Read file bytes outside of StateFlow.update (readBytes is suspend)
        val rawBytes = file?.readBytes()
        val fileMimeType = file?.mimeType()?.toString()
        val fileData = rawBytes?.let { bytes ->
            val compressed = compressImageBytes(bytes, fileMimeType ?: "image/jpeg")
            Base64.encode(compressed)
        }
        val effectiveMimeType = if (rawBytes != null && fileMimeType?.startsWith("image/") == true) "image/jpeg" else fileMimeType

        if (question != null) {
            chatHistory.update {
                it.toMutableList().apply {
                    add(
                        History(
                            role = History.Role.USER,
                            content = question,
                            mimeType = effectiveMimeType,
                            data = fileData,
                        ),
                    )
                }
            }
        }

        val messages = chatHistory.value
        val systemPrompt = getActiveSystemPrompt()

        val fallbackEntries = getOrderedFallbackEntries().filter { hasValidInstanceApiKey(it.instanceId, it.service) }

        var lastException: Exception? = null
        var fallbackServiceName: String? = null

        for ((index, entry) in fallbackEntries.withIndex()) {
            try {
                val responseText = retryApiCall {
                    askWithService(entry.service, messages, systemPrompt, entry.instanceId)
                }
                if (index > 0) {
                    fallbackServiceName = entry.service.displayName
                }
                chatHistory.update {
                    it.toMutableList().apply {
                        add(History(role = History.Role.ASSISTANT, content = responseText, fallbackServiceName = fallbackServiceName))
                    }
                }
                saveCurrentConversation()
                return
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastException = e
                // Try next service
            }
        }

        throw lastException ?: OpenAICompatibleEmptyResponseException()
    }

    private suspend fun handleOpenAICompatibleChatWithTools(
        service: Service,
        credentials: ServiceCredentials,
        messages: List<History>,
        tools: List<Tool>,
        systemPrompt: String? = null,
    ): String {
        var currentMessages = trimMessagesForContext(
            buildOpenAIMessages(
                messages.filter { it.role != History.Role.TOOL_EXECUTING },
                systemPrompt,
            ),
        )

        var iteration = 0
        val recentSignatures = mutableListOf<String>()

        // Loop until AI returns a final response (no more tool calls)
        while (true) {
            iteration++

            // Bail out if too many iterations
            if (iteration > MAX_TOOL_ITERATIONS) {
                return makeFinalCallWithoutTools(service, credentials, currentMessages)
            }

            val response = retryApiCall {
                requests.openAICompatibleChat(service, credentials, currentMessages, tools).getOrThrow()
            }
            val message = response.choices.firstOrNull()?.message ?: throw OpenAICompatibleEmptyResponseException()

            val toolCalls = message.toolCalls
            if (toolCalls.isNullOrEmpty()) {
                // No more tool calls - return the final response
                return message.content ?: ""
            }

            // Check for repetition
            val signatures = toolCalls.map { "${it.function.name}:${it.function.arguments.hashCode()}" }
            if (isRepeatingToolCalls(recentSignatures, signatures)) {
                return makeFinalCallWithoutTools(service, credentials, currentMessages)
            }
            recentSignatures.addAll(signatures)

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

            // Execute all tool calls in parallel
            val toolResults = executeToolCallsInParallel(toolCalls.map { Triple(it.id, it.function.name, it.function.arguments) })

            // Add all tool results to history
            chatHistory.update { history ->
                buildList(history.size + toolResults.size) {
                    // Remove any TOOL_EXECUTING entries
                    for (h in history) {
                        if (h.role != History.Role.TOOL_EXECUTING) add(h)
                    }
                    for ((callId, name, result) in toolResults) {
                        add(
                            History(
                                role = History.Role.TOOL,
                                content = result,
                                toolCallId = callId,
                                toolName = name,
                            ),
                        )
                    }
                }
            }

            // Update messages for next iteration with context trimming
            currentMessages = trimMessagesForContext(
                buildOpenAIMessages(
                    chatHistory.value.filter { it.role != History.Role.TOOL_EXECUTING },
                    systemPrompt,
                ),
            )
        }
    }

    private suspend fun handleGeminiChatWithTools(credentials: ServiceCredentials, messages: List<History>, tools: List<Tool>, systemPrompt: String? = null): String {
        var iteration = 0
        val recentSignatures = mutableListOf<String>()

        // Loop until AI returns a final response (no more function calls)
        while (true) {
            iteration++

            if (iteration > MAX_TOOL_ITERATIONS) {
                // Bail out: make a final Gemini call without tools
                val currentMessages = chatHistory.value.filter { it.role != History.Role.TOOL_EXECUTING }
                val geminiMessages = currentMessages.map { it.toGeminiMessageDto() }
                val bailoutResponse = retryApiCall {
                    requests.geminiChat(
                        credentials = credentials,
                        messages = geminiMessages,
                        systemInstruction = "You have reached the tool call limit. Please respond with the best answer you have so far based on the information gathered. $systemPrompt",
                    ).getOrThrow()
                }
                return bailoutResponse.candidates.firstOrNull()?.content?.parts?.joinToString("\n") { it.text ?: "" } ?: ""
            }

            val currentMessages = chatHistory.value.filter { it.role != History.Role.TOOL_EXECUTING }
            val geminiMessages = currentMessages.map { it.toGeminiMessageDto() }

            val response = retryApiCall {
                requests.geminiChat(credentials = credentials, messages = geminiMessages, tools = tools, systemInstruction = systemPrompt).getOrThrow()
            }
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

            // Check for repetition
            val signatures = toolCallInfos.map { "${it.name}:${it.arguments.hashCode()}" }
            if (isRepeatingToolCalls(recentSignatures, signatures)) {
                val bailoutMessages = currentMessages.map { it.toGeminiMessageDto() }
                val bailoutResponse = retryApiCall {
                    requests.geminiChat(
                        credentials = credentials,
                        messages = bailoutMessages,
                        systemInstruction = "You are repeating the same tool calls. Please respond with the best answer you have so far. $systemPrompt",
                    ).getOrThrow()
                }
                return bailoutResponse.candidates.firstOrNull()?.content?.parts?.joinToString("\n") { it.text ?: "" } ?: ""
            }
            recentSignatures.addAll(signatures)

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

            // Execute all tool calls in parallel
            val toolResults = executeToolCallsInParallel(toolCallInfos.map { Triple(it.id, it.name, it.arguments) })

            // Add all tool results to history
            chatHistory.update { history ->
                buildList(history.size + toolResults.size) {
                    for (h in history) {
                        if (h.role != History.Role.TOOL_EXECUTING) add(h)
                    }
                    for ((callId, name, result) in toolResults) {
                        add(
                            History(
                                role = History.Role.TOOL,
                                content = result,
                                toolCallId = callId,
                                toolName = name,
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
                    content = JsonPrimitive(systemPrompt),
                ),
            )
        }
        addAll(
            messages.map { it.toGroqMessageDto() }
                .filter { msg ->
                    // Drop tool messages that lost their tool_call_id (from conversation reload)
                    if (msg.role == "tool" && msg.tool_call_id == null) return@filter false
                    // Drop assistant messages that had tool_calls but lost them (empty content, no tool_calls)
                    if (msg.role == "assistant" && msg.content == null && msg.tool_calls.isNullOrEmpty()) return@filter false
                    true
                },
        )
    }

    /**
     * Detects if the current batch of tool calls is repeating a recent pattern.
     */
    private fun isRepeatingToolCalls(recentSignatures: List<String>, currentSignatures: List<String>): Boolean {
        if (currentSignatures.isEmpty()) return false
        // Count how many consecutive times the same signature set appeared at the tail
        val batchSize = currentSignatures.size
        var consecutiveCount = 0
        var i = recentSignatures.size - batchSize
        while (i >= 0) {
            val slice = recentSignatures.subList(i, i + batchSize)
            if (slice == currentSignatures) {
                consecutiveCount++
                i -= batchSize
            } else {
                break
            }
        }
        // +1 for the current batch that's about to be executed
        return consecutiveCount + 1 >= MAX_REPEATED_TOOL_CALLS
    }

    /**
     * Makes a final OpenAI-compatible API call without tools, asking the model to summarize.
     */
    private suspend fun makeFinalCallWithoutTools(
        service: Service,
        credentials: ServiceCredentials,
        messages: List<com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto.Message>,
    ): String {
        val bailoutMessages = messages.toMutableList().apply {
            add(
                com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto.Message(
                    role = "user",
                    content = JsonPrimitive("You have reached the tool call limit. Please respond with the best answer you have so far based on the information gathered."),
                ),
            )
        }
        val response = retryApiCall {
            requests.openAICompatibleChat(service, credentials, bailoutMessages).getOrThrow()
        }
        return response.choices.firstOrNull()?.message?.content ?: ""
    }

    /**
     * Executes tool calls in parallel, showing TOOL_EXECUTING indicators in the UI.
     * Returns a list of (callId, toolName, result).
     */
    private suspend fun executeToolCallsInParallel(
        toolCalls: List<Triple<String, String, String>>,
    ): List<Triple<String, String, String>> {
        // Add all TOOL_EXECUTING indicators first
        val executingIds = toolCalls.map { Uuid.random().toString() }
        for ((index, toolCall) in toolCalls.withIndex()) {
            val (_, name, _) = toolCall
            val toolDisplayName = toolExecutor.getToolDisplayName(name)
            chatHistory.update {
                it.toMutableList().apply {
                    add(
                        History(
                            id = executingIds[index],
                            role = History.Role.TOOL_EXECUTING,
                            content = name,
                            toolName = toolDisplayName,
                        ),
                    )
                }
            }
        }

        // Execute all tools concurrently
        val results = coroutineScope {
            toolCalls.map { (callId, name, arguments) ->
                async {
                    val result = toolExecutor.executeTool(name, arguments)
                    Triple(callId, name, result)
                }
            }.map { it.await() }
        }

        // Remove all TOOL_EXECUTING indicators
        chatHistory.update { history ->
            history.filter { h -> h.id !in executingIds }
        }

        return results
    }

    /**
     * Retries an API call with simple exponential backoff.
     */
    private suspend fun <T> retryApiCall(block: suspend () -> T): T {
        var lastException: Exception? = null
        for (attempt in 0..MAX_API_RETRIES) {
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                if (attempt < MAX_API_RETRIES) {
                    delay(1000L * (attempt + 1))
                }
            }
        }
        throw lastException!!
    }

    private fun estimateMessageChars(msg: com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto.Message): Int {
        val contentChars = when (val content = msg.content) {
            is JsonArray -> {
                // Vision messages: only count text parts, not base64 image data
                content.sumOf { element ->
                    val obj = element as? JsonObject
                    val type = (obj?.get("type") as? JsonPrimitive)?.content
                    if (type == "text") {
                        (obj["text"] as? JsonPrimitive)?.content?.length ?: 0
                    } else {
                        100 // Fixed small cost for image references
                    }
                }
            }

            is JsonPrimitive -> content.content.length

            else -> content?.toString()?.length ?: 0
        }
        return contentChars + msg.role.length
    }

    /**
     * Trims messages to fit within the estimated context window by dropping oldest messages
     * (keeping the system prompt and most recent messages).
     */
    private fun trimMessagesForContext(
        messages: List<com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto.Message>,
    ): List<com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto.Message> {
        val maxChars = DEFAULT_CONTEXT_WINDOW_TOKENS * ESTIMATED_CHARS_PER_TOKEN
        val totalChars = messages.sumOf { estimateMessageChars(it) }
        if (totalChars <= maxChars) return messages

        // Keep system prompt (first message if role is "system") and trim from oldest non-system
        val systemMessages = messages.takeWhile { it.role == "system" }
        val nonSystemMessages = messages.drop(systemMessages.size)

        val systemChars = systemMessages.sumOf { estimateMessageChars(it) }
        val availableChars = maxChars - systemChars

        // Keep messages from the end until we exceed the budget
        val kept = mutableListOf<com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto.Message>()
        var usedChars = 0
        for (msg in nonSystemMessages.reversed()) {
            val msgChars = estimateMessageChars(msg)
            if (usedChars + msgChars > availableChars) break
            kept.add(0, msg)
            usedChars += msgChars
        }

        return systemMessages + kept
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

        val existingConversation = savedConversations.value.find { it.id == conversationId }

        val conversation = Conversation(
            id = conversationId,
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
        )

        conversationStorage.saveConversation(conversation)
    }

    override fun clearHistory() {
        chatHistory.update {
            emptyList()
        }
    }

    override fun isUsingSharedKey(): Boolean = currentService() == Service.Free

    override fun supportsFileAttachment(): Boolean {
        val service = currentService()
        if (service == Service.Free) return true
        val modelId = appSettings.getSelectedModelId(service)
        return supportsImageAttachment(modelId)
    }

    override fun currentService(): Service {
        val instances = getConfiguredServiceInstances()
        return instances.firstOrNull()?.let { Service.fromId(it.serviceId) } ?: Service.Free
    }

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
        // Already have a loaded conversation with messages — nothing to do
        val currentId = _currentConversationId.value
        if (currentId != null && chatHistory.value.isNotEmpty() &&
            savedConversations.value.any { it.id == currentId }
        ) {
            return
        }

        val latest = savedConversations.value
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
            // Runtime context
            val service = currentService()
            val modelId = appSettings.getSelectedModelId(service)
            append("\n\n## Context\n")
            append("- Date: ${Clock.System.now()}\n")
            append("- Platform: $platformName\n")
            append("- Model: $modelId\n")
            append("- Provider: ${service.displayName}\n")
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

    override fun setHeartbeatIntervalMinutes(minutes: Int) {
        val config = heartbeatManager.getConfig()
        heartbeatManager.saveConfig(config.copy(intervalMinutes = minutes))
    }

    override fun setHeartbeatActiveHours(start: Int, end: Int) {
        val config = heartbeatManager.getConfig()
        heartbeatManager.saveConfig(config.copy(activeHoursStart = start, activeHoursEnd = end))
    }

    override fun getHeartbeatPrompt(): String = appSettings.getHeartbeatPrompt()

    override fun setHeartbeatPrompt(text: String) {
        appSettings.setHeartbeatPrompt(text)
    }

    override fun getHeartbeatLog(): List<HeartbeatLogEntry> = heartbeatManager.getHeartbeatLog()

    override fun isEmailEnabled(): Boolean = appSettings.isEmailEnabled()

    override fun setEmailEnabled(enabled: Boolean) {
        appSettings.setEmailEnabled(enabled)
    }

    override fun getEmailAccounts(): List<EmailAccount> = emailStore.getAccounts()

    override suspend fun removeEmailAccount(id: String) {
        emailStore.removeAccount(id)
    }

    override fun getEmailPollIntervalMinutes(): Int = appSettings.getEmailPollIntervalMinutes()

    override fun setEmailPollIntervalMinutes(minutes: Int) {
        appSettings.setEmailPollIntervalMinutes(minutes)
    }

    override fun getUiScale(): Float = appSettings.getUiScale()

    override fun setUiScale(scale: Float) {
        appSettings.setUiScale(scale)
    }

    override suspend fun askSilently(question: String): String {
        val service = currentService()
        val firstInstance = getConfiguredServiceInstances().firstOrNull() ?: return ""
        val creds = instanceCredentials(firstInstance.instanceId, service)
        val messages = chatHistory.value + History(role = History.Role.USER, content = question)
        val systemPrompt = getActiveSystemPrompt()

        val responseText = when (service) {
            Service.Gemini -> {
                val geminiMessages = messages.map { it.toGeminiMessageDto() }
                val response = requests.geminiChat(creds, geminiMessages, systemInstruction = systemPrompt).getOrThrow()
                response.candidates.firstOrNull()?.content?.parts?.joinToString("\n") { part ->
                    part.text ?: ""
                } ?: ""
            }

            else -> {
                val openAIMessages = buildOpenAIMessages(messages, systemPrompt)
                val response = requests.openAICompatibleChat(service, creds, openAIMessages).getOrThrow()
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
