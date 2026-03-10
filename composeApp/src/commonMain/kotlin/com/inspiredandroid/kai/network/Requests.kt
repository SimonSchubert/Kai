package com.inspiredandroid.kai.network

import com.inspiredandroid.kai.Version
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
import com.inspiredandroid.kai.platformName
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.EMPTY
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
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
            agent = "Kai/${Version.appVersion} ($platformName)"
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
            defaultClient.get("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
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
    ): Result<GeminiChatResponseDto> = try {
        val apiKey = credentials.apiKey.ifEmpty { throw GeminiInvalidApiKeyException() }
        val selectedModelId = credentials.modelId

        val systemContent = systemInstruction?.let {
            GeminiChatRequestDto.Content(
                parts = listOf(GeminiChatRequestDto.Part(text = it)),
            )
        }

        val response: HttpResponse =
            defaultClient.post("${Service.Gemini.chatUrl}$selectedModelId:generateContent?key=$apiKey") {
                contentType(ContentType.Application.Json)
                setBody(
                    GeminiChatRequestDto(
                        contents = messages,
                        tools = tools.map { it.toGeminiTool() }.ifEmpty { null },
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
    ): Result<OpenAICompatibleChatResponseDto> = try {
        val apiKey = getApiKeyOrThrow(service, credentials)
        val model = if (service == Service.Free) null else credentials.modelId.ifEmpty { null }
        val url = resolveUrl(service, credentials, service.chatUrl)
        val response: HttpResponse =
            defaultClient.post(url) {
                contentType(ContentType.Application.Json)
                apiKey?.let { bearerAuth(it) }
                customHeaders.forEach { (k, v) -> header(k, v) }
                setBody(
                    OpenAICompatibleChatRequestDto(
                        messages = messages,
                        model = model,
                        tools = tools.map { it.toRequestTool() }.ifEmpty { null },
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
    } catch (e: Exception) {
        Result.failure(OpenAICompatibleConnectionException())
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
            Result.success(response.body())
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
    ): Result<AnthropicChatResponseDto> = try {
        val apiKey = credentials.apiKey.ifEmpty { throw AnthropicInvalidApiKeyException() }
        val response: HttpResponse =
            defaultClient.post(Service.Anthropic.chatUrl) {
                contentType(ContentType.Application.Json)
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                setBody(
                    AnthropicChatRequestDto(
                        model = credentials.modelId,
                        messages = messages,
                        max_tokens = 8192,
                        system = systemInstruction,
                        tools = tools.map { it.toAnthropicTool() }.ifEmpty { null },
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
        "${credentials.baseUrl.ifEmpty { Service.DEFAULT_OPENAI_COMPATIBLE_BASE_URL }}$path"
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
        when (response.status.value) {
            401 -> throw OpenAICompatibleInvalidApiKeyException()

            402 -> throw OpenAICompatibleQuotaExhaustedException()

            404 -> throw OpenAICompatibleModelNotFoundException(credentials.modelId)

            413 -> throw OpenAICompatibleRequestTooLargeException()

            429 -> throw OpenAICompatibleRateLimitExceededException()

            else -> {
                if (service == Service.XAI) {
                    val responseBody = response.bodyAsText()
                    if (responseBody.contains("exhausted", ignoreCase = true) ||
                        responseBody.contains("credits", ignoreCase = true) ||
                        responseBody.contains("spending limit", ignoreCase = true)
                    ) {
                        throw OpenAICompatibleQuotaExhaustedException()
                    }
                }
                throw OpenAICompatibleGenericException("${service.displayName} request failed: ${response.status}")
            }
        }
    }

    private fun Tool.toRequestTool(): OpenAICompatibleChatRequestDto.Tool = OpenAICompatibleChatRequestDto.Tool(
        function = OpenAICompatibleChatRequestDto.Function(
            name = schema.name,
            description = schema.description,
            parameters = OpenAICompatibleChatRequestDto.Parameters(
                properties = schema.parameters.mapValues { (_, param) ->
                    OpenAICompatibleChatRequestDto.PropertySchema(
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
                AnthropicChatRequestDto.PropertySchema(
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
                        PropertySchema(
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
