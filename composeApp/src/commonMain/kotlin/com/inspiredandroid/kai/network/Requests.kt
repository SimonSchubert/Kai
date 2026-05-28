package com.inspiredandroid.kai.network

import com.inspiredandroid.kai.Version
import com.inspiredandroid.kai.currentPlatform
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.httpClient
import com.inspiredandroid.kai.isDebugBuild
import com.inspiredandroid.kai.network.dtos.anthropic.AnthropicChatRequestDto
import com.inspiredandroid.kai.network.dtos.anthropic.AnthropicChatResponseDto
import com.inspiredandroid.kai.network.dtos.anthropic.AnthropicModelsResponseDto
import com.inspiredandroid.kai.network.dtos.gemini.FunctionDeclaration
import com.inspiredandroid.kai.network.dtos.gemini.FunctionParameters
import com.inspiredandroid.kai.network.dtos.gemini.GeminiChatRequestDto
import com.inspiredandroid.kai.network.dtos.gemini.GeminiChatResponseDto
import com.inspiredandroid.kai.network.dtos.gemini.GeminiModelsResponseDto
import com.inspiredandroid.kai.network.dtos.gemini.GeminiTool
import com.inspiredandroid.kai.network.dtos.gemini.PropertySchema
import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto
import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatResponseDto
import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleModelResponseDto
import com.inspiredandroid.kai.network.tools.Tool
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.EMPTY
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.seconds

data class ServiceCredentials(
    val apiKey: String = "",
    val modelId: String = "",
    val baseUrl: String = "",
)

class Requests {

    private val defaultClient = httpClient {
        install(ContentNegotiation) {
            json(
                Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                    explicitNulls = false
                },
            )
        }
        install(UserAgent) {
            agent = "Kai/${Version.appVersion} (${currentPlatform.displayName})"
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60.seconds.inWholeMilliseconds
        }
        install(Logging) {
            if (isDebugBuild) {
                logger = DebugKtorLogger()
                level = LogLevel.BODY
            } else {
                logger = Logger.EMPTY
                level = LogLevel.NONE
            }
        }
    }

    class DebugKtorLogger : Logger {
        override fun log(message: String) {
            println("[KTOR] $message")
        }
    }

    // region Gemini

    suspend fun getGeminiModels(credentials: ServiceCredentials): Result<GeminiModelsResponseDto> = try {
        val apiKey = credentials.apiKey.ifEmpty { throw GeminiInvalidApiKeyException() }
        val response: HttpResponse =
            defaultClient.get("https://generativelanguage.googleapis.com/v1beta/models") {
                header("x-goog-api-key", apiKey)
            }
        if (response.status.isSuccess()) {
            Result.success(response.body())
        } else {
            when (response.status.value) {
                400, 403 -> throw GeminiInvalidApiKeyException()
                else -> throw GeminiGenericException("Failed to fetch models: ${response.status}")
            }
        }
    } catch (e: GeminiApiException) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(GeminiGenericException("Connection failed", e))
    }

    suspend fun geminiChat(
        credentials: ServiceCredentials,
        messages: List<GeminiChatRequestDto.Content>,
        tools: List<Tool> = emptyList(),
        systemInstruction: String? = null,
        requestTimeoutMs: Long? = null,
    ): Result<GeminiChatResponseDto> = try {
        val apiKey = credentials.apiKey.ifEmpty { throw GeminiInvalidApiKeyException() }
        val selectedModelId = credentials.modelId

        val systemContent = systemInstruction?.let {
            GeminiChatRequestDto.Content(
                parts = listOf(GeminiChatRequestDto.Part(text = it)),
            )
        }

        val response: HttpResponse =
            defaultClient.post("${Service.Gemini.chatUrl}$selectedModelId:generateContent") {
                header("x-goog-api-key", apiKey)
                requestTimeoutMs?.let {
                    timeout {
                        requestTimeoutMillis = it
                        socketTimeoutMillis = it
                    }
                }
                contentType(ContentType.Application.Json)
                setBody(
                    GeminiChatRequestDto(
                        contents = messages,
                        tools = tools.mapNotNull { runCatching { it.toGeminiTool() }.getOrNull() }
                            .ifEmpty { null },
                        systemInstruction = systemContent,
                    ),
                )
            }
        if (response.status.isSuccess()) {
            Result.success(response.body())
        } else {
            when (response.status.value) {
                429 -> throw GeminiRateLimitExceededException()

                403 -> throw GeminiInvalidApiKeyException()

                else -> {
                    val responseBody = response.bodyAsText()
                    if (responseBody.contains("API_KEY_INVALID", ignoreCase = true)) {
                        throw GeminiInvalidApiKeyException()
                    } else {
                        throw GeminiGenericException("Chat request failed: ${response.status}")
                    }
                }
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    // endregion

    // region OpenAI-compatible (unified)

    suspend fun openAICompatibleChat(
        service: Service,
        credentials: ServiceCredentials,
        messages: List<OpenAICompatibleChatRequestDto.Message>,
        tools: List<Tool> = emptyList(),
        customHeaders: Map<String, String> = emptyMap(),
        requestTimeoutMs: Long? = null,
    ): Result<OpenAICompatibleChatResponseDto> = try {
        val apiKey = getApiKeyOrThrow(service, credentials)
        val model = credentials.modelId.ifEmpty { null }
        val url = resolveUrl(service, credentials, service.chatUrl)
        val response: HttpResponse =
            defaultClient.post(url) {
                requestTimeoutMs?.let {
                    timeout {
                        requestTimeoutMillis = it
                        socketTimeoutMillis = it
                    }
                }
                contentType(ContentType.Application.Json)
                apiKey?.let { bearerAuth(it) }
                customHeaders.forEach { (k, v) -> header(k, v) }
                setBody(
                    OpenAICompatibleChatRequestDto(
                        messages = messages,
                        model = model,
                        tools = tools.mapNotNull { runCatching { it.toRequestTool() }.getOrNull() }
                            .ifEmpty { null },
                    ),
                )
            }
        if (response.status.isSuccess()) {
            Result.success(response.body())
        } else {
            handleOpenAICompatibleError(service, credentials, response)
        }
    } catch (e: OpenAICompatibleApiException) {
        Result.failure(e)
    } catch (e: io.ktor.client.plugins.HttpRequestTimeoutException) {
        Result.failure(OpenAICompatibleConnectionException())
    } catch (e: Exception) {
        Result.failure(mapOpenAICompatibleException(e))
    }

    suspend fun getOpenAICompatibleModels(
        service: Service,
        credentials: ServiceCredentials,
    ): Result<OpenAICompatibleModelResponseDto> = try {
        val modelsUrl = service.modelsUrl
            ?: return Result.failure(OpenAICompatibleGenericException("Models URL not configured for ${service.displayName}"))
        val url = resolveUrl(service, credentials, modelsUrl)
        val apiKey = getOptionalApiKey(service, credentials)
        val response: HttpResponse = defaultClient.get(url) {
            apiKey?.let { bearerAuth(it) }
        }
        if (response.status.isSuccess()) {
            if (service.modelsResponseIsArray) {
                val models: List<OpenAICompatibleModelResponseDto.Model> = response.body()
                Result.success(OpenAICompatibleModelResponseDto(data = models))
            } else {
                Result.success(response.body())
            }
        } else {
            handleOpenAICompatibleError(service, credentials, response)
        }
    } catch (e: OpenAICompatibleApiException) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(OpenAICompatibleConnectionException())
    }

    suspend fun validateOpenRouterApiKey(credentials: ServiceCredentials): Result<Unit> = try {
        val apiKey = credentials.apiKey.ifEmpty { throw OpenAICompatibleInvalidApiKeyException() }
        val response: HttpResponse = defaultClient.get("https://openrouter.ai/api/v1/auth/key") {
            bearerAuth(apiKey)
        }
        if (response.status.isSuccess()) {
            Result.success(Unit)
        } else {
            when (response.status.value) {
                401, 403 -> throw OpenAICompatibleInvalidApiKeyException()
                else -> throw OpenAICompatibleGenericException("Failed to validate OpenRouter API key: ${response.status}")
            }
        }
    } catch (e: OpenAICompatibleApiException) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(OpenAICompatibleConnectionException())
    }

    // endregion

    private val anthropicJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    // region Anthropic

    suspend fun getAnthropicModels(credentials: ServiceCredentials): Result<AnthropicModelsResponseDto> = try {
        val apiKey = credentials.apiKey.ifEmpty { throw AnthropicInvalidApiKeyException() }
        val response: HttpResponse =
            defaultClient.get("https://api.anthropic.com/v1/models") {
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
            }
        val responseBody = response.bodyAsText()
        if (response.status.isSuccess()) {
            val dto = anthropicJson.decodeFromString(AnthropicModelsResponseDto.serializer(), responseBody)
            Result.success(dto)
        } else {
            throwAnthropicError(response.status.value, responseBody)
        }
    } catch (e: AnthropicApiException) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(AnthropicGenericException("Anthropic: ${e.message}", e))
    }

    suspend fun anthropicChat(
        credentials: ServiceCredentials,
        messages: List<AnthropicChatRequestDto.Message>,
        tools: List<Tool> = emptyList(),
        systemInstruction: String? = null,
        requestTimeoutMs: Long? = null,
    ): Result<AnthropicChatResponseDto> = try {
        val apiKey = credentials.apiKey.ifEmpty { throw AnthropicInvalidApiKeyException() }
        val response: HttpResponse =
            defaultClient.post(Service.Anthropic.chatUrl) {
                requestTimeoutMs?.let {
                    timeout {
                        requestTimeoutMillis = it
                        socketTimeoutMillis = it
                    }
                }
                contentType(ContentType.Application.Json)
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                setBody(
                    AnthropicChatRequestDto(
                        model = credentials.modelId,
                        messages = messages,
                        max_tokens = 8192,
                        system = systemInstruction,
                        tools = tools.mapNotNull { runCatching { it.toAnthropicTool() }.getOrNull() }
                            .ifEmpty { null },
                    ),
                )
            }
        val responseBody = response.bodyAsText()
        if (response.status.isSuccess()) {
            val dto = anthropicJson.decodeFromString(AnthropicChatResponseDto.serializer(), responseBody)
            Result.success(dto)
        } else {
            throwAnthropicError(response.status.value, responseBody)
        }
    } catch (e: AnthropicApiException) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(AnthropicGenericException("Anthropic: ${e.message}", e))
    }

    private fun throwAnthropicError(statusCode: Int, responseBody: String): Nothing {
        when (statusCode) {
            401, 403 -> throw AnthropicInvalidApiKeyException()
            429 -> throw AnthropicRateLimitExceededException()
            529 -> throw AnthropicOverloadedException()
        }
        val errorMessage = parseAnthropicErrorMessage(responseBody)
        if (errorMessage != null && errorMessage.contains("credit balance", ignoreCase = true)) {
            throw AnthropicInsufficientCreditsException()
        }
        throw AnthropicGenericException(errorMessage ?: "Anthropic: $statusCode $responseBody")
    }

    private fun parseAnthropicErrorMessage(responseBody: String): String? = try {
        val json = anthropicJson.parseToJsonElement(responseBody)
        val errorObj = json.jsonObject["error"]?.jsonObject
        errorObj?.get("message")?.jsonPrimitive?.content
    } catch (_: Exception) {
        null
    }

    // endregion

    // region Helpers

    private fun resolveUrl(service: Service, credentials: ServiceCredentials, path: String): String = if (service == Service.OpenAICompatible) {
        "${credentials.baseUrl.ifEmpty { Service.DEFAULT_OPENAI_COMPATIBLE_BASE_URL }.trimEnd('/')}$path"
    } else {
        path
    }

    private fun getApiKeyOrThrow(service: Service, credentials: ServiceCredentials): String? {
        if (!service.requiresApiKey && !service.supportsOptionalApiKey) return null
        val key = credentials.apiKey
        if (service.requiresApiKey && key.isEmpty()) throw OpenAICompatibleInvalidApiKeyException()
        return key.ifEmpty { null }
    }

    private fun getOptionalApiKey(service: Service, credentials: ServiceCredentials): String? {
        if (!service.requiresApiKey && !service.supportsOptionalApiKey) return null
        return credentials.apiKey.ifEmpty { null }
    }

    private suspend fun handleOpenAICompatibleError(
        service: Service,
        credentials: ServiceCredentials,
        response: HttpResponse,
    ): Nothing {
        val responseBody = response.bodyAsText()
        val parsed = parseOpenAICompatibleErrorDetail(responseBody)
        val moderationDetail = parsed.moderationDetail()
        when (response.status.value) {
            400 -> {
                if (parsed.looksLikeContentPolicyViolation()) {
                    throw OpenAICompatibleContentModerationException(moderationDetail)
                }
                throw OpenAICompatibleBadRequestException(parsed.message)
            }

            401 -> throw OpenAICompatibleInvalidApiKeyException()

            402 -> throw OpenAICompatibleQuotaExhaustedException()

            403 -> throw OpenAICompatibleContentModerationException(moderationDetail)

            404 -> throw OpenAICompatibleModelNotFoundException()

            408, 504 -> throw OpenAICompatibleTimeoutException()

            413 -> throw OpenAICompatibleRequestTooLargeException()

            429 -> throw OpenAICompatibleRateLimitExceededException()

            500, 502 -> throw OpenAICompatibleProviderErrorException(parsed.message)

            503 -> throw OpenAICompatibleServiceUnavailableException()

            else -> {
                val haystack = parsed.message ?: responseBody
                if (haystack.contains("credit", ignoreCase = true) ||
                    haystack.contains("exhausted", ignoreCase = true) ||
                    haystack.contains("spending limit", ignoreCase = true) ||
                    haystack.contains("quota", ignoreCase = true) ||
                    haystack.contains("subscription", ignoreCase = true) ||
                    haystack.contains("upgrade", ignoreCase = true)
                ) {
                    throw OpenAICompatibleQuotaExhaustedException()
                }
                val detail = parsed.message ?: "${response.status}"
                throw OpenAICompatibleGenericException("${service.displayName}: $detail")
            }
        }
    }

    // Distinguish genuine network/I/O failures (preserve the "Cannot connect to
    // server" UX and the settings-screen ErrorConnectionFailed status) from
    // client-side exceptions (schema/serialization bugs, etc.) which would
    // otherwise be silently misclassified as connection failures.
    private fun mapOpenAICompatibleException(e: Exception): OpenAICompatibleApiException {
        val name = e::class.simpleName.orEmpty()
        val looksLikeNetworkFailure = name.endsWith("IOException") ||
            name.contains("Timeout", ignoreCase = true) ||
            name.contains("ConnectException", ignoreCase = true) ||
            name.contains("UnknownHost", ignoreCase = true) ||
            name.contains("NoRoute", ignoreCase = true) ||
            name.contains("SocketException", ignoreCase = true) ||
            name.contains("Unresolved", ignoreCase = true)
        return if (looksLikeNetworkFailure) {
            OpenAICompatibleConnectionException()
        } else {
            OpenAICompatibleGenericException(
                e.message?.takeIf { it.isNotBlank() } ?: "Unexpected error: $name",
                e,
            )
        }
    }

    private data class OpenAICompatibleErrorDetail(
        val message: String?,
        val code: String?,
        val type: String?,
        val reasons: List<String>,
    ) {
        fun looksLikeContentPolicyViolation(): Boolean {
            if (code?.equals("content_policy_violation", ignoreCase = true) == true) return true
            if (type?.contains("content_filter", ignoreCase = true) == true) return true
            if (type?.contains("content_policy", ignoreCase = true) == true) return true
            val msg = message ?: return false
            return msg.contains("content policy", ignoreCase = true) ||
                msg.contains("moderation", ignoreCase = true) ||
                msg.contains("flagged", ignoreCase = true)
        }

        fun moderationDetail(): String? {
            val reasonText = reasons.takeIf { it.isNotEmpty() }
                ?.joinToString(", ")
                ?.let { "flagged for '$it'" }
            return reasonText ?: message
        }
    }

    private fun parseOpenAICompatibleErrorDetail(responseBody: String): OpenAICompatibleErrorDetail = try {
        val error = anthropicJson.parseToJsonElement(responseBody).jsonObject["error"]
        when {
            error == null -> OpenAICompatibleErrorDetail(null, null, null, emptyList())

            error is JsonPrimitive -> OpenAICompatibleErrorDetail(
                message = error.content.takeIf { it.isNotBlank() },
                code = null,
                type = null,
                reasons = emptyList(),
            )

            else -> {
                val obj = error.jsonObject
                val message = obj["message"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                val code = (obj["code"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                val type = (obj["type"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                val reasons = (obj["metadata"] as? JsonObject)?.get("reasons")?.let { it as? JsonArray }
                    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.isNotBlank() } }
                    .orEmpty()
                OpenAICompatibleErrorDetail(message, code, type, reasons)
            }
        }
    } catch (_: Exception) {
        OpenAICompatibleErrorDetail(null, null, null, emptyList())
    }

    private fun Tool.toRequestTool(): OpenAICompatibleChatRequestDto.Tool = OpenAICompatibleChatRequestDto.Tool(
        function = OpenAICompatibleChatRequestDto.Function(
            name = schema.name,
            description = schema.description,
            parameters = OpenAICompatibleChatRequestDto.Parameters(
                properties = schema.parameters.mapValues { (_, param) ->
                    param.rawSchema?.toOpenAIPropertySchema()
                        ?: OpenAICompatibleChatRequestDto.PropertySchema(
                            type = param.type,
                            description = param.description,
                        )
                },
                required = schema.parameters.filter { it.value.required }.keys.toList(),
            ),
        ),
    )

    private fun Tool.toAnthropicTool(): AnthropicChatRequestDto.Tool = AnthropicChatRequestDto.Tool(
        name = schema.name,
        description = schema.description,
        input_schema = AnthropicChatRequestDto.InputSchema(
            properties = schema.parameters.mapValues { (_, param) ->
                param.rawSchema?.toAnthropicPropertySchema()
                    ?: AnthropicChatRequestDto.PropertySchema(
                        type = param.type,
                        description = param.description,
                    )
            },
            required = schema.parameters.filter { it.value.required }.keys.toList(),
        ),
    )

    private fun Tool.toGeminiTool(): GeminiTool = GeminiTool(
        functionDeclarations = listOf(
            FunctionDeclaration(
                name = schema.name,
                description = schema.description,
                parameters = FunctionParameters(
                    properties = schema.parameters.mapValues { (_, param) ->
                        param.rawSchema?.toGeminiPropertySchema()
                            ?: PropertySchema(
                                type = param.type,
                                description = param.description,
                            )
                    },
                    required = schema.parameters.filter { it.value.required }.keys.toList(),
                ),
            ),
        ),
    )

    // endregion
}

private val DEFAULT_OPENAI_STRING_ITEMS = OpenAICompatibleChatRequestDto.PropertySchema(type = "string")
private val DEFAULT_ANTHROPIC_STRING_ITEMS = AnthropicChatRequestDto.PropertySchema(type = "string")
private val DEFAULT_GEMINI_STRING_ITEMS = PropertySchema(type = "string")

// JSON Schema dialects vary: `type` may be a string or an array (e.g.
// ["string","null"]); enum/required entries may be non-string primitives;
// nested objects may be malformed. MCP tool servers (especially proxies like
// litellm /toolset/<category>/mcp) emit any of these shapes, so the converters
// fall back to safe defaults instead of throwing — a throw here would bubble
// to setBody() and surface as a misleading "Cannot connect to server" error.

private fun JsonObject.schemaType(): String = when (val t = this["type"]) {
    is JsonPrimitive -> t.contentOrNull ?: "string"

    is JsonArray -> t.asSequence()
        .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        .firstOrNull { it != "null" }
        ?: "string"

    else -> "string"
}

private fun JsonObject.schemaString(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.schemaStringList(key: String): List<String>? = (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    ?.takeIf { it.isNotEmpty() }

private fun JsonObject.schemaObjectMap(key: String): Map<String, JsonObject>? = (this[key] as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonObject)?.let { k to it } }
    ?.toMap()
    ?.takeIf { it.isNotEmpty() }

private fun JsonObject.toOpenAIPropertySchema(): OpenAICompatibleChatRequestDto.PropertySchema {
    val type = schemaType()
    val items = (this["items"] as? JsonObject)?.toOpenAIPropertySchema()
    val properties = schemaObjectMap("properties")?.mapValues { it.value.toOpenAIPropertySchema() }
    val additionalProperties = (this["additionalProperties"] as? JsonPrimitive)
        ?.contentOrNull?.toBooleanStrictOrNull()
    return OpenAICompatibleChatRequestDto.PropertySchema(
        type = type,
        description = schemaString("description"),
        enum = schemaStringList("enum"),
        items = items ?: if (type == "array") DEFAULT_OPENAI_STRING_ITEMS else null,
        properties = properties,
        required = schemaStringList("required"),
        additionalProperties = additionalProperties,
    )
}

private fun JsonObject.toAnthropicPropertySchema(): AnthropicChatRequestDto.PropertySchema {
    val type = schemaType()
    val items = (this["items"] as? JsonObject)?.toAnthropicPropertySchema()
    val properties = schemaObjectMap("properties")?.mapValues { it.value.toAnthropicPropertySchema() }
    return AnthropicChatRequestDto.PropertySchema(
        type = type,
        description = schemaString("description"),
        enum = schemaStringList("enum"),
        items = items ?: if (type == "array") DEFAULT_ANTHROPIC_STRING_ITEMS else null,
        properties = properties,
        required = schemaStringList("required"),
    )
}

private fun JsonObject.toGeminiPropertySchema(): PropertySchema {
    val type = schemaType()
    val items = (this["items"] as? JsonObject)?.toGeminiPropertySchema()
    val properties = schemaObjectMap("properties")?.mapValues { it.value.toGeminiPropertySchema() }
    return PropertySchema(
        type = type,
        description = schemaString("description"),
        enum = schemaStringList("enum"),
        items = items ?: if (type == "array") DEFAULT_GEMINI_STRING_ITEMS else null,
        properties = properties,
        required = schemaStringList("required"),
    )
}
