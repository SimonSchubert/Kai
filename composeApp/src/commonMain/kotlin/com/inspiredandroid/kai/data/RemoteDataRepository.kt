@file:OptIn(ExperimentalEncodingApi::class, ExperimentalTime::class, ExperimentalUuidApi::class)

package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.compressImageBytes
import com.inspiredandroid.kai.formatFileSize
import com.inspiredandroid.kai.getAvailableTools
import com.inspiredandroid.kai.getPlatformToolDefinitions
import com.inspiredandroid.kai.inference.DownloadError
import com.inspiredandroid.kai.inference.DownloadedModel
import com.inspiredandroid.kai.inference.EngineState
import com.inspiredandroid.kai.inference.InferenceMessage
import com.inspiredandroid.kai.inference.LocalInferenceEngine
import com.inspiredandroid.kai.inference.LocalModel
import com.inspiredandroid.kai.inference.LocalTool
import com.inspiredandroid.kai.inference.NoModelDownloadedException
import com.inspiredandroid.kai.inference.getTotalMemoryBytes
import com.inspiredandroid.kai.mcp.McpServerConfig
import com.inspiredandroid.kai.mcp.McpServerManager
import com.inspiredandroid.kai.network.AnthropicGenericException
import com.inspiredandroid.kai.network.AnthropicInsufficientCreditsException
import com.inspiredandroid.kai.network.ContextWindowExceededException
import com.inspiredandroid.kai.network.FileTooLargeException
import com.inspiredandroid.kai.network.OpenAICompatibleEmptyResponseException
import com.inspiredandroid.kai.network.OpenAICompatibleQuotaExhaustedException
import com.inspiredandroid.kai.network.Requests
import com.inspiredandroid.kai.network.ServiceCredentials
import com.inspiredandroid.kai.network.UnsupportedFileTypeException
import com.inspiredandroid.kai.network.dtos.anthropic.AnthropicChatRequestDto
import com.inspiredandroid.kai.network.dtos.anthropic.extractText
import com.inspiredandroid.kai.network.dtos.gemini.extractText
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.platformName
import com.inspiredandroid.kai.ui.chat.History
import com.inspiredandroid.kai.ui.chat.ToolCallInfo
import com.inspiredandroid.kai.ui.chat.toAnthropicContentBlocks
import com.inspiredandroid.kai.ui.chat.toGeminiMessageDto
import com.inspiredandroid.kai.ui.chat.toGroqMessageDto
import com.inspiredandroid.kai.ui.settings.SettingsModel
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.mimeType
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.default_soul
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.jetbrains.compose.resources.getString
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val limitedModels = listOf(
    "llama3.2:1b",
    "llama3.2:3b",
    "llama3.1:8b",
    "gemma2",
    "gemma:2b",
    "gemma:7b",
    "gemma-4-e2b",
    "gemma-4-e4b",
    "phi3:mini",
    "tinyllama",
    "stablelm",
    "codellama",
    "deepseek-coder:1.3b",
    "deepseek-coder:6.7b",
)

private const val MAX_TOOL_ITERATIONS = 15
private const val MIN_TOOL_DISPLAY_MS = 2000L
private const val MAX_REPEATED_TOOL_CALLS = 3
private const val MAX_API_RETRIES = 2
private const val MAX_HEARTBEAT_MESSAGES = 50
private const val ESTIMATED_CHARS_PER_TOKEN = 4
private const val DEFAULT_CONTEXT_WINDOW_TOKENS = 100_000
private const val COMPACTION_THRESHOLD = 0.7 // Compact when history exceeds 70% of context window
private const val COMPACTION_KEEP_RECENT = 4 // Number of recent user exchanges to keep verbatim

// Explicit allowlist of tools exposed to the on-device (LiteRT) model. We use a
// hardcoded name list rather than a structural filter because small Gemma models hit
// litert-lm's strict ANTLR function-call parser hard on anything more complex than
// a couple of string parameters. Excluded by design: memory_learn (4 params + enum),
// schedule_task / list_tasks / cancel_task (datetime + cron), the entire email family,
// the heartbeat config tools, and MCP tools.
internal val LOCAL_TOOL_ALLOWLIST = setOf(
    "get_local_time",
    "get_location_from_ip",
    "web_search",
    "open_url",
    "memory_store",
    "memory_forget",
    "memory_reinforce",
    "execute_shell_command",
)

/**
 * Returns the estimated context window size in tokens for a given model ID.
 * Uses substring matching on the model ID to cover versioned variants (e.g. "gpt-4o-2024-08-06").
 * Falls back to [DEFAULT_CONTEXT_WINDOW_TOKENS] for unknown models.
 */
private fun estimateContextWindowTokens(modelId: String): Int {
    val id = modelId.lowercase()
    return when {
        // Gemini
        "gemini-2.5" in id || "gemini-2.0" in id -> 1_000_000

        "gemini-1.5-pro" in id -> 2_000_000

        "gemini-1.5-flash" in id -> 1_000_000

        "gemini" in id -> 32_000

        // Anthropic
        "claude-opus" in id || "claude-sonnet" in id -> 200_000

        "claude-haiku" in id -> 200_000

        "claude-3" in id || "claude-3.5" in id -> 200_000

        "claude" in id -> 200_000

        // OpenAI
        "gpt-4o" in id -> 128_000

        "gpt-4-turbo" in id -> 128_000

        "gpt-4" in id -> 8_192

        "gpt-3.5" in id -> 16_385

        "o1" in id || "o3" in id || "o4" in id -> 200_000

        // DeepSeek
        "deepseek" in id -> 64_000

        // Mistral
        "mistral-large" in id -> 128_000

        "mistral" in id -> 32_000

        // xAI
        "grok" in id -> 131_072

        // Llama
        "llama-3.3" in id || "llama-3.1" in id -> 128_000

        "llama" in id -> 8_192

        // Qwen
        "qwen" in id -> 32_000

        // Small/local models
        "phi" in id -> 16_000

        "gemma" in id -> 8_192

        else -> DEFAULT_CONTEXT_WINDOW_TOKENS
    }
}

private fun supportsTools(modelId: String): Boolean {
    val lower = modelId.lowercase()
    return limitedModels.none { lower.startsWith(it) }
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
    private val mcpServerManager: McpServerManager,
    private val localInferenceEngine: LocalInferenceEngine? = null,
) : DataRepository {

    private val prettyJson = Json { prettyPrint = true }

    /**
     * Returns the tools exposed to the on-device (LiteRT) model. Filtered by name against
     * [LOCAL_TOOL_ALLOWLIST]. Tools the user has disabled in settings (e.g. shell command,
     * which is gated behind `isToolEnabled("execute_shell_command")`) won't appear in
     * `getAvailableTools()` in the first place, so they're naturally excluded.
     */
    private fun getLocalSafeTools(): List<Tool> = getAvailableTools()
        .filter { it.schema.name in LOCAL_TOOL_ALLOWLIST }

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

    override val savedConversations: StateFlow<List<Conversation>> = conversationStorage.conversations

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

    override fun getServiceEntries(): List<ServiceEntry> = getConfiguredServiceInstances().map { instance ->
        val service = Service.fromId(instance.serviceId)
        val modelId = appSettings.getInstanceModelId(instance.instanceId).ifEmpty {
            appSettings.getSelectedModelId(service)
        }
        ServiceEntry(
            instanceId = instance.instanceId,
            serviceId = service.id,
            serviceName = service.displayName,
            modelId = modelId,
            icon = service.icon,
        )
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
        val defaultSettingsModels = service.defaultModels.map {
            SettingsModel(
                id = it.id,
                subtitle = it.subtitle,
                descriptionRes = it.descriptionRes,
                isSelected = it.id == selectedModelId,
            )
        }
        val models = if (selectedModelId.isNotEmpty() && defaultSettingsModels.none { it.id == selectedModelId }) {
            listOf(SettingsModel(id = selectedModelId, subtitle = "", isSelected = true)) + defaultSettingsModels
        } else {
            defaultSettingsModels
        }
        MutableStateFlow(models)
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
        if (service.isOnDevice) {
            fetchInstanceModels(service, instanceId)
            return
        }
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

            Service.Anthropic -> fetchAnthropicModelsForInstance(instanceId)

            Service.Free -> { /* No model listing */ }

            Service.LiteRT -> {
                val engine = localInferenceEngine ?: return
                val selectedModelId = appSettings.getInstanceModelId(instanceId)
                val downloaded = engine.getDownloadedModels()
                val models = downloaded.map {
                    SettingsModel(
                        id = it.id,
                        subtitle = "${it.displayName} (${formatFileSize(it.sizeBytes)})",
                        isSelected = it.id == selectedModelId,
                    )
                }
                updateModelsForInstance(instanceId, models, service)
            }

            else -> {
                if (service.modelsUrl != null) {
                    fetchOpenAICompatibleModelsForInstance(service, instanceId)
                } else if (service.defaultModels.isNotEmpty()) {
                    val selectedModelId = appSettings.getInstanceModelId(instanceId)
                    val models = service.defaultModels.map {
                        SettingsModel(
                            id = it.id,
                            subtitle = it.subtitle,
                            descriptionRes = it.descriptionRes,
                            isSelected = it.id == selectedModelId,
                        )
                    }
                    updateModelsForInstance(instanceId, models, service)
                }
            }
        }
    }

    private suspend fun fetchAnthropicModelsForInstance(instanceId: String) {
        val creds = instanceCredentials(instanceId, Service.Anthropic)
        val response = requests.getAnthropicModels(creds).getOrThrow()
        val selectedModelId = appSettings.getInstanceModelId(instanceId)
        val models = mapAnthropicModels(response.data, selectedModelId)
        updateModelsForInstance(instanceId, models)
    }

    private suspend fun fetchGeminiModelsForInstance(instanceId: String) {
        val creds = instanceCredentials(instanceId, Service.Gemini)
        val response = requests.getGeminiModels(creds).getOrThrow()
        val selectedModelId = appSettings.getInstanceModelId(instanceId)
        val models = mapGeminiModels(response.models, selectedModelId)
        updateModelsForInstance(instanceId, models)
    }

    private suspend fun fetchOpenAICompatibleModelsForInstance(service: Service, instanceId: String) {
        val creds = instanceCredentials(instanceId, service)
        val response = requests.getOpenAICompatibleModels(service, creds).getOrThrow()
        val selectedModelId = appSettings.getInstanceModelId(instanceId)
        val models = mapOpenAICompatibleModels(response.data, service, selectedModelId)
        updateModelsForInstance(instanceId, models)
    }

    private fun updateModelsForInstance(instanceId: String, models: List<SettingsModel>, service: Service? = null) {
        val flow = modelsByInstance.getOrPut(instanceId) { MutableStateFlow(emptyList()) }
        flow.update { models }
        if (models.isNotEmpty() && models.none { it.isSelected }) {
            val default = pickDefaultModel(models, service)
            if (default != null) {
                appSettings.setInstanceModelId(instanceId, default.id)
                flow.update { m -> m.map { it.copy(isSelected = it.id == default.id) } }
            }
        }
    }

    private fun pickDefaultModel(models: List<SettingsModel>, service: Service? = null): SettingsModel? {
        val defaultModel = service?.defaultModel
        if (defaultModel != null) {
            models.firstOrNull { it.id == defaultModel }?.let { return it }
        }
        return models.firstOrNull { it.id.contains("kimi-k2.5", ignoreCase = true) }
            ?: models.firstOrNull()
    }

    private suspend fun askWithLocalEngine(
        messages: List<History>,
        systemPrompt: String?,
        instanceId: String,
        history: MutableStateFlow<List<History>> = chatHistory,
    ): String {
        val engine = localInferenceEngine
            ?: throw IllegalStateException("On-device inference not available on this platform")

        val modelId = appSettings.getInstanceModelId(instanceId)
        val downloadedModels = engine.getDownloadedModels()
        val model = downloadedModels.find { it.id == modelId }
            ?: downloadedModels.firstOrNull()
            ?: throw NoModelDownloadedException()

        val catalogModel = engine.getAvailableModels().find { it.id == model.id }
        val storedContext = appSettings.getModelContextTokens(model.id)
        val contextTokens = if (storedContext > 0) storedContext else catalogModel?.defaultContextTokens ?: 0

        val needsInit = engine.engineState.value != EngineState.READY
        if (needsInit) {
            val statusEntry = History(
                role = History.Role.TOOL_EXECUTING,
                content = "",
                toolName = "Initializing ${model.displayName}",
                isStatusMessage = true,
            )
            history.update { it + statusEntry }
            try {
                engine.initialize(model, contextTokens)
            } finally {
                history.update { h -> h.filter { it.id != statusEntry.id } }
            }
        } else {
            engine.initialize(model, contextTokens)
        }

        // Callers pass either a CHAT_LOCAL system prompt (chat + silent paths) or null
        // (Splinterlands via `askSilentlyWithInstance`, where the caller owns the full
        // prompt shape). We hand whichever one through to the engine unchanged.
        // Native litert-lm `automaticToolCalling` owns the tool loop — our allowlisted
        // tools are passed once via [localToolDescriptionJson] and the engine drives them.
        val localTools: List<LocalTool> = getLocalSafeTools().map { tool ->
            LocalTool(
                name = tool.schema.name,
                descriptionJsonString = localToolDescriptionJson(tool),
                execute = { jsonArgs -> runLocalToolWithUiFeedback(tool.schema.name, jsonArgs, history) },
            )
        }

        val inferenceMessages = messages.mapNotNull { msg ->
            when (msg.role) {
                History.Role.USER -> InferenceMessage(role = "user", content = msg.content)
                History.Role.ASSISTANT -> InferenceMessage(role = "assistant", content = msg.content)
                else -> null
            }
        }

        return try {
            engine.chat(messages = inferenceMessages, systemPrompt = systemPrompt, tools = localTools)
        } catch (e: RuntimeException) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // litert-lm's strict ANTLR function-call parser sometimes rejects malformed
            // tool-call output from small Gemma models, throwing INVALID_ARGUMENT from JNI.
            // Retry once without tools so the user gets *some* answer rather than a hard
            // error in the UI. With an empty tool list, LiteRTInferenceEngine sets
            // automaticToolCalling = false, so the parser is bypassed entirely on the retry.
            println("LiteRT: tool-call parser failed (${e.message?.take(200)}). Falling back to plain chat.")
            engine.chat(messages = inferenceMessages, systemPrompt = systemPrompt, tools = emptyList())
        }
    }

    /**
     * Cached OpenAPI/OpenAI-style JSON descriptions for local tools, keyed by tool name.
     * Schemas are static for allowlisted tools, so serializing them once per tool avoids
     * re-running the JSON builder on every message.
     */
    private val localToolDescriptionJsonCache = mutableMapOf<String, String>()

    /**
     * Returns the cached OpenAPI/OpenAI-style JSON description for [tool], building it on
     * first request. Shape mirrors `Tool.toRequestTool()` in `Requests.kt` without the
     * OpenAI `{type: "function", function: {…}}` wrapper, so litert-lm's `OpenApiTool`
     * adapter can forward it straight to the model. If a parameter has a `rawSchema`,
     * it's passed through verbatim — that preserves array/enum/nested-object shapes the
     * simple `{type, description}` form would lose.
     */
    private fun localToolDescriptionJson(tool: Tool): String = localToolDescriptionJsonCache.getOrPut(tool.schema.name) {
        buildJsonObject {
            put("name", tool.schema.name)
            put("description", tool.schema.description)
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    for ((paramName, param) in tool.schema.parameters) {
                        val raw = param.rawSchema
                        if (raw != null) {
                            put(paramName, raw)
                        } else {
                            putJsonObject(paramName) {
                                put("type", param.type)
                                put("description", param.description)
                            }
                        }
                    }
                }
                putJsonArray("required") {
                    tool.schema.parameters.filter { it.value.required }.keys.forEach { add(it) }
                }
            }
        }.toString()
    }

    /**
     * Runs a single tool invocation requested by the on-device engine, mirroring the UI
     * flow used by [executeToolCallsInParallel]: write the assistant tool-call row, show a
     * TOOL_EXECUTING indicator (with a 2 s minimum so it's visible), execute the tool, then
     * replace the indicator with a TOOL result row. Returns the raw result string for the
     * engine to feed back to the model.
     */
    private suspend fun runLocalToolWithUiFeedback(
        name: String,
        arguments: String,
        history: MutableStateFlow<List<History>>,
    ): String {
        val callId = "local-${Uuid.random()}"
        val executingId = Uuid.random().toString()
        val displayName = toolExecutor.getToolDisplayName(name)
        // Append the assistant tool-call row and the executing indicator in a single
        // StateFlow update so the UI doesn't flash twice before the tool even starts.
        history.update {
            it.toMutableList().apply {
                add(
                    History(
                        role = History.Role.ASSISTANT,
                        content = "",
                        toolCalls = persistentListOf(
                            ToolCallInfo(id = callId, name = name, arguments = arguments),
                        ),
                    ),
                )
                add(
                    History(
                        id = executingId,
                        role = History.Role.TOOL_EXECUTING,
                        content = name,
                        toolName = displayName,
                    ),
                )
            }
        }
        val startTime = Clock.System.now().toEpochMilliseconds()
        val result = try {
            toolExecutor.executeTool(name, arguments)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            """{"success": false, "error": "${e.message ?: "Tool execution failed"}"}"""
        }
        val elapsed = Clock.System.now().toEpochMilliseconds() - startTime
        if (elapsed < MIN_TOOL_DISPLAY_MS) {
            delay(MIN_TOOL_DISPLAY_MS - elapsed)
        }
        history.update { h ->
            buildList(h.size) {
                for (entry in h) {
                    if (entry.id != executingId) add(entry)
                }
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
        return result
    }

    private suspend fun askWithService(
        service: Service,
        messages: List<History>,
        systemPrompt: String?,
        instanceId: String,
        history: MutableStateFlow<List<History>> = chatHistory,
    ): String {
        if (service.isOnDevice) {
            // Re-fetch the system prompt with the CHAT_LOCAL variant — the caller
            // (`ask()`/`askWithTools()`) pre-fetched a CHAT_REMOTE prompt, but on-device
            // needs the trimmed variant.
            val localPrompt = getActiveSystemPrompt(SystemPromptVariant.CHAT_LOCAL)
            return askWithLocalEngine(messages, localPrompt, instanceId, history)
        }

        val creds = instanceCredentials(instanceId, service)
        val tools = if (supportsTools(creds.modelId)) getAvailableTools() else emptyList()

        return when (service) {
            Service.Gemini -> {
                if (tools.isNotEmpty()) {
                    handleGeminiChatWithTools(creds, messages, tools, systemPrompt, history)
                } else {
                    val geminiMessages = messages.map { it.toGeminiMessageDto() }
                    val response = requests.geminiChat(creds, geminiMessages, systemInstruction = systemPrompt).getOrThrow()
                    response.extractText()
                }
            }

            Service.Anthropic -> {
                if (tools.isNotEmpty()) {
                    handleAnthropicChatWithTools(creds, messages, tools, systemPrompt, history)
                } else {
                    val anthropicMessages = buildAnthropicMessages(messages)
                    val response = requests.anthropicChat(creds, anthropicMessages, systemInstruction = systemPrompt).getOrThrow()
                    response.extractText()
                }
            }

            else -> {
                if (tools.isNotEmpty()) {
                    handleOpenAICompatibleChatWithTools(service, creds, messages, tools, systemPrompt, history)
                } else {
                    val openAIMessages = buildOpenAIMessages(messages, systemPrompt)
                    val response = requests.openAICompatibleChat(service, creds, openAIMessages).getOrThrow()
                    response.choices.firstOrNull()?.message?.effectiveContent ?: throw OpenAICompatibleEmptyResponseException()
                }
            }
        }
    }

    private fun hasValidInstanceApiKey(instanceId: String, service: Service): Boolean {
        if (service == Service.Free) return true
        if (service.isOnDevice) return true
        if (!service.requiresApiKey && !service.supportsOptionalApiKey) return true
        if (service.requiresApiKey) return appSettings.getInstanceApiKey(instanceId).isNotBlank()
        return true // Optional API key services are always valid
    }

    private data class FallbackEntry(val instanceId: String, val service: Service)

    private fun getOrderedFallbackEntries(): List<FallbackEntry> {
        val instances = getConfiguredServiceInstances()
        val entries = instances.map { FallbackEntry(instanceId = it.instanceId, service = Service.fromId(it.serviceId)) }
            .filter { it.service != Service.Free }
            .filter { !it.service.isOnDevice || localInferenceEngine != null }
        return if (entries.isEmpty()) {
            listOf(FallbackEntry(instanceId = "free", service = Service.Free))
        } else if (appSettings.isFreeFallbackEnabled()) {
            entries + FallbackEntry(instanceId = "free", service = Service.Free)
        } else {
            entries
        }
    }

    override suspend fun ask(question: String?, files: List<PlatformFile>) {
        // Process every attached file: classify, compress/encode, and build an Attachment.
        // readBytes() is suspend, so this happens before the StateFlow.update block.
        val attachments = files.map { file ->
            val rawBytes = file.readBytes()
            val fileMimeType = file.mimeType()?.toString()
            val fileName = file.name

            val category = classifyFile(fileMimeType, fileName)
            if (category == FileCategory.UNSUPPORTED) throw UnsupportedFileTypeException()
            if (category == FileCategory.TEXT && rawBytes.size > MAX_TEXT_FILE_BYTES) throw FileTooLargeException()

            when (category) {
                FileCategory.IMAGE -> {
                    val compressed = compressImageBytes(rawBytes, fileMimeType ?: "image/jpeg")
                    Attachment(
                        data = Base64.encode(compressed),
                        mimeType = "image/jpeg",
                        fileName = null,
                    )
                }

                FileCategory.TEXT -> Attachment(
                    data = Base64.encode(rawBytes),
                    mimeType = fileMimeType ?: "text/plain",
                    fileName = fileName,
                )

                FileCategory.PDF -> Attachment(
                    data = Base64.encode(rawBytes),
                    mimeType = "application/pdf",
                    fileName = fileName,
                )

                FileCategory.UNSUPPORTED -> throw UnsupportedFileTypeException()
            }
        }.toImmutableList()

        if (question != null) {
            chatHistory.update {
                it.toMutableList().apply {
                    add(
                        History(
                            role = History.Role.USER,
                            content = question,
                            attachments = attachments,
                        ),
                    )
                }
            }
        }

        compactHistoryIfNeeded()

        val messages = chatHistory.value
        val systemPrompt = getActiveSystemPrompt()

        val fallbackEntries = getOrderedFallbackEntries().filter { hasValidInstanceApiKey(it.instanceId, it.service) }

        val historyChars = messages.sumOf { it.content.length } + (systemPrompt?.length ?: 0)

        var lastException: Exception? = null
        var fallbackServiceName: String? = null

        for ((index, entry) in fallbackEntries.withIndex()) {
            // Skip fallback services whose context window is too small for the current history
            // On-device models handle their own context limits, so skip this check for them
            if (!entry.service.isOnDevice) {
                val creds = instanceCredentials(entry.instanceId, entry.service)
                val entryWindowChars = estimateContextWindowTokens(creds.modelId) * ESTIMATED_CHARS_PER_TOKEN
                if (historyChars > entryWindowChars) {
                    lastException = ContextWindowExceededException()
                    continue
                }
            }

            val responseText = try {
                retryApiCall {
                    askWithService(entry.service, messages, systemPrompt, entry.instanceId)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (isNonRetryableException(e)) throw e
                // On-device services should not silently fall back — surface the error
                if (entry.service.isOnDevice) throw e
                lastException = e
                continue
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
        }

        throw lastException ?: OpenAICompatibleEmptyResponseException()
    }

    private suspend fun handleOpenAICompatibleChatWithTools(
        service: Service,
        credentials: ServiceCredentials,
        messages: List<History>,
        tools: List<Tool>,
        systemPrompt: String? = null,
        history: MutableStateFlow<List<History>> = chatHistory,
    ): String {
        val contextWindowTokens = estimateContextWindowTokens(credentials.modelId)
        var currentMessages = trimMessagesForContext(
            buildOpenAIMessages(
                messages.filter { it.role != History.Role.TOOL_EXECUTING },
                systemPrompt,
            ),
            contextWindowTokens,
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
                return message.effectiveContent ?: ""
            }

            // Check for repetition
            val signatures = toolCalls.map { "${it.function.name}:${it.function.arguments.hashCode()}" }
            if (isRepeatingToolCalls(recentSignatures, signatures)) {
                return makeFinalCallWithoutTools(service, credentials, currentMessages)
            }
            recentSignatures.addAll(signatures)

            // Add assistant message with tool calls to history
            history.update {
                it.toMutableList().apply {
                    add(
                        History(
                            role = History.Role.ASSISTANT,
                            content = message.effectiveContent ?: "",
                            isThinking = message.isContentFromReasoning,
                            toolCalls = toolCalls.map { tc ->
                                ToolCallInfo(id = tc.id, name = tc.function.name, arguments = tc.function.arguments)
                            }.toImmutableList(),
                        ),
                    )
                }
            }

            // Execute all tool calls in parallel
            val toolResults = executeToolCallsInParallel(toolCalls.map { Triple(it.id, it.function.name, it.function.arguments) })

            // Add all tool results to history
            history.update { h ->
                buildList(h.size + toolResults.size) {
                    // Remove any TOOL_EXECUTING entries
                    for (entry in h) {
                        if (entry.role != History.Role.TOOL_EXECUTING) add(entry)
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
                    history.value.filter { it.role != History.Role.TOOL_EXECUTING },
                    systemPrompt,
                ),
                contextWindowTokens,
            )
        }
    }

    private suspend fun handleGeminiChatWithTools(credentials: ServiceCredentials, messages: List<History>, tools: List<Tool>, systemPrompt: String? = null, history: MutableStateFlow<List<History>> = chatHistory): String {
        val contextWindowTokens = estimateContextWindowTokens(credentials.modelId)
        var iteration = 0
        val recentSignatures = mutableListOf<String>()

        // Loop until AI returns a final response (no more function calls)
        while (true) {
            iteration++

            if (iteration > MAX_TOOL_ITERATIONS) {
                // Bail out: make a final Gemini call without tools
                val currentMessages = history.value.filter { it.role != History.Role.TOOL_EXECUTING }
                val geminiMessages = currentMessages.map { it.toGeminiMessageDto() }
                val bailoutResponse = retryApiCall {
                    requests.geminiChat(
                        credentials = credentials,
                        messages = geminiMessages,
                        systemInstruction = "You have reached the tool call limit. Please respond with the best answer you have so far based on the information gathered. $systemPrompt",
                    ).getOrThrow()
                }
                return bailoutResponse.extractText()
            }

            val currentMessages = history.value.filter { it.role != History.Role.TOOL_EXECUTING }
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
                return bailoutResponse.extractText()
            }
            recentSignatures.addAll(signatures)

            // Add assistant message with tool calls to history
            val textContent = parts.mapNotNull { it.text }.joinToString("\n")
            history.update {
                it.toMutableList().apply {
                    add(
                        History(
                            role = History.Role.ASSISTANT,
                            content = textContent,
                            toolCalls = toolCallInfos.toImmutableList(),
                        ),
                    )
                }
            }

            // Execute all tool calls in parallel
            val toolResults = executeToolCallsInParallel(toolCallInfos.map { Triple(it.id, it.name, it.arguments) })

            // Add all tool results to history and trim to fit context window
            history.update { h ->
                val updated = buildList(h.size + toolResults.size) {
                    for (entry in h) {
                        if (entry.role != History.Role.TOOL_EXECUTING) add(entry)
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
                trimHistoryForContext(updated, systemPrompt?.length ?: 0, contextWindowTokens)
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

    private fun buildAnthropicMessages(
        messages: List<History>,
    ): List<AnthropicChatRequestDto.Message> = buildList {
        var pendingToolResults = mutableListOf<JsonElement>()

        for (msg in messages) {
            when (msg.role) {
                History.Role.TOOL_EXECUTING -> { /* skip */ }

                History.Role.TOOL -> {
                    // Accumulate tool results; they'll be merged into a single user message
                    val blocks = msg.toAnthropicContentBlocks()
                    if (blocks is JsonArray) {
                        pendingToolResults.addAll(blocks)
                    }
                }

                else -> {
                    // Flush any pending tool results as a single user message before the next message
                    if (pendingToolResults.isNotEmpty()) {
                        add(
                            AnthropicChatRequestDto.Message(
                                role = "user",
                                content = JsonArray(pendingToolResults),
                            ),
                        )
                        pendingToolResults = mutableListOf()
                    }
                    add(
                        AnthropicChatRequestDto.Message(
                            role = if (msg.role == History.Role.ASSISTANT) "assistant" else "user",
                            content = msg.toAnthropicContentBlocks(),
                        ),
                    )
                }
            }
        }
        // Flush any trailing tool results
        if (pendingToolResults.isNotEmpty()) {
            add(
                AnthropicChatRequestDto.Message(
                    role = "user",
                    content = JsonArray(pendingToolResults),
                ),
            )
        }
    }

    private suspend fun handleAnthropicChatWithTools(
        credentials: ServiceCredentials,
        messages: List<History>,
        tools: List<Tool>,
        systemPrompt: String? = null,
        history: MutableStateFlow<List<History>> = chatHistory,
    ): String {
        val contextWindowTokens = estimateContextWindowTokens(credentials.modelId)
        var iteration = 0
        val recentSignatures = mutableListOf<String>()

        while (true) {
            iteration++

            val currentMessages = buildAnthropicMessages(
                history.value.filter { it.role != History.Role.TOOL_EXECUTING },
            )

            if (iteration > MAX_TOOL_ITERATIONS) {
                val bailoutResponse = retryApiCall {
                    requests.anthropicChat(
                        credentials = credentials,
                        messages = currentMessages,
                        systemInstruction = "You have reached the tool call limit. Please respond with the best answer you have so far based on the information gathered. $systemPrompt",
                    ).getOrThrow()
                }
                return bailoutResponse.extractText()
            }

            val response = retryApiCall {
                requests.anthropicChat(
                    credentials = credentials,
                    messages = currentMessages,
                    tools = tools,
                    systemInstruction = systemPrompt,
                ).getOrThrow()
            }

            val toolUseBlocks = response.content.filter { it.type == "tool_use" }
            if (toolUseBlocks.isEmpty()) {
                return response.extractText()
            }

            val toolCallInfos = toolUseBlocks.map { block ->
                val argsJson = block.input?.toString() ?: "{}"
                ToolCallInfo(
                    id = block.id ?: "anthropic-${Uuid.random()}",
                    name = block.name ?: "unknown",
                    arguments = argsJson,
                )
            }

            // Check for repetition
            val signatures = toolCallInfos.map { "${it.name}:${it.arguments.hashCode()}" }
            if (isRepeatingToolCalls(recentSignatures, signatures)) {
                val bailoutResponse = retryApiCall {
                    requests.anthropicChat(
                        credentials = credentials,
                        messages = currentMessages,
                        systemInstruction = "You are repeating the same tool calls. Please respond with the best answer you have so far. $systemPrompt",
                    ).getOrThrow()
                }
                return bailoutResponse.extractText()
            }
            recentSignatures.addAll(signatures)

            // Add assistant message with tool calls to history
            val textContent = response.content.filter { it.type == "text" }.mapNotNull { it.text }.joinToString("\n")
            history.update {
                it.toMutableList().apply {
                    add(
                        History(
                            role = History.Role.ASSISTANT,
                            content = textContent,
                            toolCalls = toolCallInfos.toImmutableList(),
                        ),
                    )
                }
            }

            // Execute all tool calls in parallel
            val toolResults = executeToolCallsInParallel(toolCallInfos.map { Triple(it.id, it.name, it.arguments) })

            // Add all tool results to history and trim to fit context window
            history.update { h ->
                val updated = buildList(h.size + toolResults.size) {
                    for (entry in h) {
                        if (entry.role != History.Role.TOOL_EXECUTING) add(entry)
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
                trimHistoryForContext(updated, systemPrompt?.length ?: 0, contextWindowTokens)
            }
        }
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
        return response.choices.firstOrNull()?.message?.effectiveContent ?: ""
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

        // Execute all tools concurrently, ensuring indicators show for at least 2 seconds
        val startTime = Clock.System.now().toEpochMilliseconds()
        val results = coroutineScope {
            toolCalls.map { (callId, name, arguments) ->
                async {
                    val result = toolExecutor.executeTool(name, arguments)
                    Triple(callId, name, result)
                }
            }.awaitAll()
        }
        val elapsed = Clock.System.now().toEpochMilliseconds() - startTime
        if (elapsed < MIN_TOOL_DISPLAY_MS) {
            delay(MIN_TOOL_DISPLAY_MS - elapsed)
        }

        // Remove all TOOL_EXECUTING indicators
        chatHistory.update { history ->
            history.filter { h -> h.id !in executingIds }
        }

        return results
    }

    private fun isNonRetryableException(e: Exception): Boolean = e is AnthropicInsufficientCreditsException || e is OpenAICompatibleQuotaExhaustedException

    /**
     * Retries an API call with simple exponential backoff.
     */
    private suspend fun <T> retryApiCall(block: suspend () -> T): T {
        var lastException: Exception? = null
        for (attempt in 0..MAX_API_RETRIES) {
            try {
                return block()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (isNonRetryableException(e)) throw e
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
        contextWindowTokens: Int = DEFAULT_CONTEXT_WINDOW_TOKENS,
    ): List<com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto.Message> {
        val maxChars = contextWindowTokens * ESTIMATED_CHARS_PER_TOKEN
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

    /**
     * Trims History entries to fit within the estimated context window by dropping oldest messages
     * (keeping the most recent). Used by Gemini and Anthropic tool loops where the system prompt
     * is sent separately (not as a message).
     */
    private fun trimHistoryForContext(
        history: List<History>,
        systemPromptChars: Int = 0,
        contextWindowTokens: Int = DEFAULT_CONTEXT_WINDOW_TOKENS,
    ): List<History> {
        val maxChars = contextWindowTokens * ESTIMATED_CHARS_PER_TOKEN
        val totalChars = history.sumOf { it.content.length } + systemPromptChars
        if (totalChars <= maxChars) return history

        val availableChars = maxChars - systemPromptChars

        // Keep messages from the end until we exceed the budget
        val kept = mutableListOf<History>()
        var usedChars = 0
        for (msg in history.reversed()) {
            val msgChars = msg.content.length
            if (usedChars + msgChars > availableChars) break
            kept.add(0, msg)
            usedChars += msgChars
        }

        return kept
    }

    /**
     * Compacts chat history by summarizing older messages via an LLM call when the history
     * exceeds a percentage of the context window. Keeps recent exchanges verbatim and replaces
     * older ones with a single summary. Falls back to simple drop-oldest trimming on failure.
     */
    private suspend fun compactHistoryIfNeeded() {
        // Use primary service's context window for compaction decisions
        val firstInstance = getConfiguredServiceInstances().firstOrNull() ?: return
        val service = Service.fromId(firstInstance.serviceId)
        val modelId = appSettings.getSelectedModelId(service)
        val contextWindowTokens = estimateContextWindowTokens(modelId)

        val history = chatHistory.value.filter { it.role != History.Role.TOOL_EXECUTING }
        val systemPromptChars = getActiveSystemPrompt()?.length ?: 0
        val totalChars = history.sumOf { it.content.length } + systemPromptChars
        val maxChars = contextWindowTokens * ESTIMATED_CHARS_PER_TOKEN
        if (totalChars <= (maxChars * COMPACTION_THRESHOLD).toInt()) return

        // Split history: older messages to summarize, recent to keep verbatim
        val userIndices = history.mapIndexedNotNull { index, h ->
            if (h.role == History.Role.USER) index else null
        }
        if (userIndices.size <= COMPACTION_KEEP_RECENT) return
        val cutoffIndex = userIndices[userIndices.size - COMPACTION_KEEP_RECENT]
        val olderMessages = history.subList(0, cutoffIndex)
        val recentMessages = history.subList(cutoffIndex, history.size)

        if (olderMessages.isEmpty()) return

        // Build a transcript of the older messages for summarization
        val transcript = buildString {
            for (msg in olderMessages) {
                if (msg.role == History.Role.USER || msg.role == History.Role.ASSISTANT) {
                    val role = if (msg.role == History.Role.USER) "User" else "Assistant"
                    appendLine("$role: ${msg.content}")
                }
            }
        }

        val summaryPrompt = "Summarize this conversation concisely, preserving key facts, decisions, and any information the assistant would need to continue helping. Be brief but complete:\n\n$transcript"

        val summary = try {
            askSilently(summaryPrompt)
        } catch (_: Exception) {
            // Summarization failed — fall back to dropping old messages
            chatHistory.value = recentMessages
            return
        }

        val summaryEntry = History(
            role = History.Role.ASSISTANT,
            content = "[Conversation summary: $summary]",
        )

        chatHistory.value = listOf(summaryEntry) + recentMessages
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
            setCurrentConversationId(it)
        }

        val existingConversation = savedConversations.value.find { it.id == conversationId }

        val title = existingConversation?.title?.ifEmpty { null }
            ?: deriveTitle(history)
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
                        attachments = h.attachments,
                    )
                },
            createdAt = existingConversation?.createdAt ?: now,
            updatedAt = now,
            title = title,
            type = existingConversation?.type ?: if (interactiveModeFlag) Conversation.TYPE_INTERACTIVE else Conversation.TYPE_CHAT,
        )

        conversationStorage.saveConversation(conversation)
    }

    override fun clearHistory() {
        chatHistory.update {
            emptyList()
        }
    }

    override fun isUsingSharedKey(): Boolean = currentService() == Service.Free

    override fun supportedFileExtensions(): List<String> {
        val service = currentService()
        if (service.isOnDevice) return emptyList()
        return if (service.supportsPdf) supportedFileExtensions + "pdf" else supportedFileExtensions
    }

    override fun currentService(): Service {
        val instances = getConfiguredServiceInstances()
        return instances.firstOrNull()?.let { Service.fromId(it.serviceId) } ?: Service.Free
    }

    private fun setCurrentConversationId(id: String?) {
        _currentConversationId.value = id
        appSettings.setCurrentConversationId(id)
    }

    // Conversation management
    override fun loadConversations() {
        conversationStorage.loadConversations()
    }

    override fun loadConversation(id: String) {
        val conversation = savedConversations.value.find { it.id == id } ?: return

        setCurrentConversationId(id)
        chatHistory.value = conversation.messages.map { m ->
            // Prefer the modern `attachments` field. Fall back to the legacy single-file
            // fields for conversations saved before multi-attachment support.
            val attachments = when {
                m.attachments.isNotEmpty() -> m.attachments.toImmutableList()

                m.data != null && m.mimeType != null ->
                    persistentListOf(Attachment(data = m.data, mimeType = m.mimeType, fileName = m.fileName))

                else -> persistentListOf()
            }
            History(
                id = m.id,
                role = when (m.role) {
                    "user" -> History.Role.USER
                    "tool" -> History.Role.TOOL
                    else -> History.Role.ASSISTANT
                },
                content = m.content,
                attachments = attachments,
            )
        }
    }

    override suspend fun deleteConversation(id: String) {
        if (_currentConversationId.value == id) {
            setCurrentConversationId(null)
            chatHistory.value = emptyList()
        }
        conversationStorage.deleteConversation(id)
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
        setCurrentConversationId(null)
        chatHistory.value = emptyList()
    }

    override fun popLastExchange() {
        chatHistory.update { history ->
            val lastUserIndex = history.indexOfLast { it.role == History.Role.USER }
            if (lastUserIndex >= 0) history.take(lastUserIndex) else history
        }
    }

    override fun restoreCurrentConversation() {
        // One-time migration for existing users: pin the latest conversation as the new
        // "current" pointer so the upgrade is non-disruptive.
        if (!appSettings.isCurrentConversationMigrated()) {
            val latest = savedConversations.value.maxByOrNull { it.updatedAt }
            if (latest != null) {
                loadConversation(latest.id)
            }
            appSettings.markCurrentConversationMigrated()
            return
        }

        // Already-loaded guard (covers re-entry from refreshSettings)
        val currentId = _currentConversationId.value
        if (currentId != null && chatHistory.value.isNotEmpty() &&
            savedConversations.value.any { it.id == currentId }
        ) {
            return
        }

        val persistedId = appSettings.getCurrentConversationId()
        if (persistedId != null && savedConversations.value.any { it.id == persistedId }) {
            loadConversation(persistedId)
        }
        // else: null id or stale id → leave history empty (this is the new-empty-chat state)
    }

    // Tool management
    override fun getToolDefinitions(): List<ToolInfo> = getPlatformToolDefinitions().map { it.copy(isEnabled = appSettings.isToolEnabled(it.id, defaultEnabled = it.isEnabled)) }

    override fun setToolEnabled(toolId: String, enabled: Boolean) {
        appSettings.setToolEnabled(toolId, enabled)
    }

    // MCP servers
    override fun getMcpServers(): List<McpServerConfig> = mcpServerManager.getServers()

    override suspend fun addMcpServer(name: String, url: String, headers: Map<String, String>): McpServerConfig = mcpServerManager.addServer(name, url, headers)

    override fun removeMcpServer(serverId: String) {
        mcpServerManager.removeServer(serverId)
    }

    override fun setMcpServerEnabled(serverId: String, enabled: Boolean) {
        mcpServerManager.setServerEnabled(serverId, enabled)
    }

    override suspend fun connectMcpServer(serverId: String): Result<List<ToolInfo>> {
        val result = mcpServerManager.connectAndDiscoverTools(serverId)
        return result.map { mcpServerManager.getToolsForServer(serverId) }
    }

    override fun getMcpToolsForServer(serverId: String): List<ToolInfo> = mcpServerManager.getToolsForServer(serverId)

    override fun isMcpServerConnected(serverId: String): Boolean = mcpServerManager.isConnected(serverId)

    override suspend fun connectEnabledMcpServers() {
        mcpServerManager.connectEnabledServers()
    }

    // Soul (system prompt)
    override fun getSoulText(): String = appSettings.getSoulText()

    override fun setSoulText(text: String) {
        appSettings.setSoulText(text)
    }

    override suspend fun getActiveSystemPrompt(variant: SystemPromptVariant): String? {
        val soul = appSettings.getSoulText().ifEmpty { getString(Res.string.default_soul) }
        val memoryEnabled = appSettings.isMemoryEnabled()
        val schedulingEnabled = appSettings.isSchedulingEnabled()

        val memoryInstructions = if (memoryEnabled) {
            appSettings.getMemoryInstructions().ifEmpty { null }
        } else {
            null
        }

        val memories = if (memoryEnabled) memoryStore.getAllMemories() else emptyList()
        val byCategory = memories.groupBy { it.category }

        val pendingTasks = if (schedulingEnabled) taskStore.getPendingTasks() else emptyList()

        val service = currentService()
        // On-device services store the active model ID per-instance, not globally, so
        // `getSelectedModelId` comes back blank for LiteRT. Fall back to the first
        // configured on-device instance's model ID in that case.
        val modelId = appSettings.getSelectedModelId(service).ifBlank {
            if (service.isOnDevice) {
                getConfiguredServiceInstances()
                    .firstOrNull { Service.fromId(it.serviceId).isOnDevice }
                    ?.let { appSettings.getInstanceModelId(it.instanceId) }
                    .orEmpty()
            } else {
                ""
            }
        }
        val runtime = ChatPromptRuntimeContext(
            nowIsoString = Clock.System.now().toString(),
            platform = platformName,
            modelId = modelId,
            providerName = service.displayName,
        )

        val isLimited = !supportsTools(modelId)
        val uiMode = when {
            interactiveModeFlag -> ChatPromptUiMode.INTERACTIVE_UI
            appSettings.isDynamicUiEnabled() && !isLimited -> ChatPromptUiMode.DYNAMIC_UI
            else -> ChatPromptUiMode.NONE
        }

        return buildChatSystemPrompt(
            variant = variant,
            soul = soul,
            memoryInstructions = memoryInstructions,
            generalMemories = byCategory[MemoryCategory.GENERAL].orEmpty(),
            preferenceMemories = byCategory[MemoryCategory.PREFERENCE].orEmpty(),
            learningMemories = byCategory[MemoryCategory.LEARNING].orEmpty(),
            errorMemories = byCategory[MemoryCategory.ERROR].orEmpty(),
            pendingTasks = pendingTasks,
            runtime = runtime,
            uiMode = uiMode,
        ).ifEmpty { null }
    }

    override fun isDynamicUiEnabled(): Boolean = appSettings.isDynamicUiEnabled()

    override fun setDynamicUiEnabled(enabled: Boolean) {
        appSettings.setDynamicUiEnabled(enabled)
    }

    private var interactiveModeFlag = appSettings.getCurrentInteractiveMode()

    override fun setInteractiveMode(enabled: Boolean) {
        interactiveModeFlag = enabled
        appSettings.setCurrentInteractiveMode(enabled)
    }

    override fun isInteractiveModeActive(): Boolean = interactiveModeFlag

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

    override fun isSandboxEnabled(): Boolean = appSettings.isSandboxEnabled()

    override fun setSandboxEnabled(enabled: Boolean) {
        appSettings.setSandboxEnabled(enabled)
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

    override fun getHeartbeatInstanceId(): String? = heartbeatManager.getConfig().heartbeatInstanceId

    override fun setHeartbeatInstanceId(instanceId: String?) {
        val config = heartbeatManager.getConfig()
        heartbeatManager.saveConfig(config.copy(heartbeatInstanceId = instanceId))
    }

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

    override fun exportSettingsToJson(): String {
        val toolIds = getPlatformToolDefinitions().map { it.id }
        val jsonObject = appSettings.exportToJson(toolIds)
        return prettyJson.encodeToString(JsonObject.serializer(), jsonObject)
    }

    override fun importSettingsFromJson(json: String, sections: Set<ImportSection>, replace: Boolean): Int {
        val jsonObject = SharedJson.parseToJsonElement(json).jsonObject
        val toolIds = getPlatformToolDefinitions().map { it.id }
        return appSettings.importFromJson(jsonObject, toolIds, sections, replace)
    }

    override suspend fun askWithTools(prompt: String, instanceId: String?): String {
        // Selection: explicit instance > first remote > first on-device. The simple-tool
        // allowlist works at any context size, so on-device is always eligible for fallback.
        val instances = getConfiguredServiceInstances()
        val targetInstance = instanceId?.let { id -> instances.find { it.instanceId == id } }
            ?: instances.firstOrNull { !Service.fromId(it.serviceId).isOnDevice }
            ?: instances.firstOrNull { Service.fromId(it.serviceId).isOnDevice }
            ?: return ""
        val service = Service.fromId(targetInstance.serviceId)
        val messages = listOf(History(role = History.Role.USER, content = prompt))
        val systemPrompt = getActiveSystemPrompt()
        // Use a local history to avoid polluting the current conversation's chatHistory
        val localHistory = MutableStateFlow(messages)
        return askWithService(service, messages, systemPrompt, targetInstance.instanceId, localHistory)
    }

    override suspend fun askSilently(question: String): String {
        val service = currentService()
        val firstInstance = getConfiguredServiceInstances().firstOrNull() ?: return ""
        val messages = listOf(History(role = History.Role.USER, content = question))

        if (service.isOnDevice) {
            // Throwaway history — we don't want tool-execution rows leaking into the
            // visible chatHistory for a "silent" call. LOCAL variant of the system
            // prompt so small on-device models get the right section set.
            val localPrompt = getActiveSystemPrompt(SystemPromptVariant.CHAT_LOCAL)
            return askWithLocalEngine(messages, localPrompt, firstInstance.instanceId, MutableStateFlow(messages))
        }

        val systemPrompt = getActiveSystemPrompt()

        val creds = instanceCredentials(firstInstance.instanceId, service)

        val responseText = when (service) {
            Service.Gemini -> {
                val geminiMessages = messages.map { it.toGeminiMessageDto() }
                val response = requests.geminiChat(creds, geminiMessages, systemInstruction = systemPrompt).getOrThrow()
                response.extractText()
            }

            Service.Anthropic -> {
                val anthropicMessages = buildAnthropicMessages(messages)
                val response = requests.anthropicChat(creds, anthropicMessages, systemInstruction = systemPrompt).getOrThrow()
                response.extractText()
            }

            else -> {
                val openAIMessages = buildOpenAIMessages(messages, systemPrompt)
                val response = requests.openAICompatibleChat(service, creds, openAIMessages).getOrThrow()
                response.choices.firstOrNull()?.message?.effectiveContent ?: ""
            }
        }

        return responseText
    }

    override suspend fun askSilentlyWithInstance(instanceId: String, prompt: String, timeoutMs: Long): String {
        val instance = getConfiguredServiceInstances().find { it.instanceId == instanceId }
            ?: return askSilently(prompt)
        val service = Service.fromId(instance.serviceId)
        val messages = listOf(History(role = History.Role.USER, content = prompt))

        if (service.isOnDevice) {
            return askWithLocalEngine(messages, null, instanceId, MutableStateFlow(messages))
        }

        val creds = instanceCredentials(instanceId, service)
        val reqTimeout = if (timeoutMs > 0) timeoutMs else null

        return when (service) {
            Service.Gemini -> {
                val geminiMessages = messages.map { it.toGeminiMessageDto() }
                val response = requests.geminiChat(creds, geminiMessages, requestTimeoutMs = reqTimeout).getOrThrow()
                response.extractText()
            }

            Service.Anthropic -> {
                val anthropicMessages = buildAnthropicMessages(messages)
                val response = requests.anthropicChat(creds, anthropicMessages, requestTimeoutMs = reqTimeout).getOrThrow()
                response.extractText()
            }

            else -> {
                val openAIMessages = buildOpenAIMessages(messages, null)
                val response = requests.openAICompatibleChat(service, creds, openAIMessages, requestTimeoutMs = reqTimeout).getOrThrow()
                response.choices.firstOrNull()?.message?.effectiveContent ?: ""
            }
        }
    }

    private val _hasUnreadHeartbeat = MutableStateFlow(false)
    override val hasUnreadHeartbeat: StateFlow<Boolean> = _hasUnreadHeartbeat

    override fun clearUnreadHeartbeat() {
        _hasUnreadHeartbeat.value = false
    }

    override suspend fun addAssistantMessage(content: String) {
        val now = Clock.System.now().toEpochMilliseconds()

        val existing = savedConversations.value.find { it.type == Conversation.TYPE_HEARTBEAT }
        val heartbeatId = existing?.id ?: Uuid.random().toString()

        val newMessage = Conversation.Message(
            id = Uuid.random().toString(),
            role = "assistant",
            content = content,
        )

        val messages = ((existing?.messages ?: emptyList()) + newMessage).takeLast(MAX_HEARTBEAT_MESSAGES)

        val conversation = Conversation(
            id = heartbeatId,
            messages = messages,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            type = Conversation.TYPE_HEARTBEAT,
        )

        _hasUnreadHeartbeat.value = true
        conversationStorage.saveConversation(conversation)
    }

    private fun deriveTitle(history: List<History>): String {
        val firstUserMessage = history.firstOrNull { it.role == History.Role.USER }?.content ?: return ""
        return if (firstUserMessage.length <= 50) {
            firstUserMessage
        } else {
            val truncated = firstUserMessage.take(50)
            val lastSpace = truncated.lastIndexOf(' ')
            if (lastSpace > 20) truncated.substring(0, lastSpace) + "..." else truncated + "..."
        }
    }

    // On-device inference (LiteRT)

    override fun isLocalInferenceAvailable(): Boolean = localInferenceEngine != null

    override fun getLocalEngineState(): StateFlow<EngineState>? = localInferenceEngine?.engineState

    override fun getLocalDownloadingModelId(): StateFlow<String?>? = localInferenceEngine?.downloadingModelId

    override fun getLocalDownloadProgress(): StateFlow<Float?>? = localInferenceEngine?.downloadProgress

    override fun getLocalDownloadError(): StateFlow<DownloadError?>? = localInferenceEngine?.downloadError

    override fun getLocalDownloadedModels(): List<DownloadedModel> = localInferenceEngine?.getDownloadedModels() ?: emptyList()

    override fun getLocalAvailableModels(): List<LocalModel> = localInferenceEngine?.getAvailableModels() ?: emptyList()

    override fun getLocalFreeSpaceBytes(): Long = localInferenceEngine?.getFreeSpaceBytes() ?: 0L

    override fun getTotalDeviceMemoryBytes(): Long = getTotalMemoryBytes()

    override fun getModelContextTokens(modelId: String): Int = appSettings.getModelContextTokens(modelId)

    override fun setModelContextTokens(modelId: String, contextTokens: Int) {
        appSettings.setModelContextTokens(modelId, contextTokens)
    }

    override suspend fun releaseLocalEngine() {
        localInferenceEngine?.release()
    }

    override fun startLocalModelDownload(model: LocalModel) {
        localInferenceEngine?.startDownload(model)
    }

    override fun cancelLocalModelDownload() {
        localInferenceEngine?.cancelDownload()
    }

    override suspend fun deleteLocalModel(modelId: String) {
        localInferenceEngine?.deleteModel(modelId)
    }
}
