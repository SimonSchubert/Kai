package com.inspiredandroid.kai.network

import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.httpClient
import com.inspiredandroid.kai.isDebugBuild
import com.inspiredandroid.kai.network.dtos.gemini.FunctionDeclaration
import com.inspiredandroid.kai.network.dtos.gemini.FunctionParameters
import com.inspiredandroid.kai.network.dtos.gemini.GeminiChatRequestDto
import com.inspiredandroid.kai.network.dtos.gemini.GeminiChatResponseDto
import com.inspiredandroid.kai.network.dtos.gemini.GeminiModelsResponseDto
import com.inspiredandroid.kai.network.dtos.gemini.GeminiTool
import com.inspiredandroid.kai.network.dtos.gemini.PropertySchema
import com.inspiredandroid.kai.network.dtos.openai.OpenAICompatibleModelsResponseDto
import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto
import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatResponseDto
import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleModelResponseDto
import com.inspiredandroid.kai.network.tools.Tool
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.EMPTY
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

class Requests(private val appSettings: AppSettings) {

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

    suspend fun getGeminiModels(): Result<GeminiModelsResponseDto> = try {
        val apiKey = appSettings.getApiKey(Service.Gemini).ifEmpty { throw GeminiInvalidApiKeyException() }
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
        messages: List<GeminiChatRequestDto.Content>,
        tools: List<Tool> = emptyList(),
    ): Result<GeminiChatResponseDto> = try {
        val apiKey = appSettings.getApiKey(Service.Gemini).ifEmpty { throw GeminiInvalidApiKeyException() }
        val selectedModelId = appSettings.getSelectedModelId(Service.Gemini)

        val response: HttpResponse =
            defaultClient.post("${Service.Gemini.chatUrl}$selectedModelId:generateContent?key=$apiKey") {
                contentType(ContentType.Application.Json)
                setBody(
                    GeminiChatRequestDto(
                        contents = messages,
                        tools = tools.map { it.toGeminiTool() }.ifEmpty { null },
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

    suspend fun freeChat(
        messages: List<OpenAICompatibleChatRequestDto.Message>,
        tools: List<Tool>,
    ): Result<OpenAICompatibleChatResponseDto> = try {
        val response: HttpResponse =
            defaultClient.post(Service.Free.chatUrl) {
                contentType(ContentType.Application.Json)
                setBody(
                    OpenAICompatibleChatRequestDto(
                        messages = messages,
                        tools = tools.map { it.toRequestTool() }.ifEmpty { null },
                    ),
                )
            }
        if (response.status.isSuccess()) {
            Result.success(response.body())
        } else {
            when (response.status.value) {
                401 -> throw OpenAICompatibleInvalidApiKeyException()

                429 -> throw OpenAICompatibleRateLimitExceededException()

                else -> {
                    throw GenericNetworkException("Free tier request failed: ${response.status}")
                }
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
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

    suspend fun groqChat(
        messages: List<OpenAICompatibleChatRequestDto.Message>,
        tools: List<Tool> = emptyList(),
    ): Result<OpenAICompatibleChatResponseDto> = try {
        val apiKey = appSettings.getApiKey(Service.Groq).ifEmpty { throw OpenAICompatibleInvalidApiKeyException() }
        val model = appSettings.getSelectedModelId(Service.Groq)
        val response: HttpResponse =
            defaultClient.post(Service.Groq.chatUrl) {
                contentType(ContentType.Application.Json)
                bearerAuth(apiKey)
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
            when (response.status.value) {
                401 -> throw OpenAICompatibleInvalidApiKeyException()
                429 -> throw OpenAICompatibleRateLimitExceededException()
                else -> throw OpenAICompatibleGenericException("Groq request failed: ${response.status}")
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getGroqModels(): Result<OpenAICompatibleModelResponseDto> = try {
        val apiKey = appSettings.getApiKey(Service.Groq).ifEmpty { throw OpenAICompatibleInvalidApiKeyException() }
        val modelsUrl = Service.Groq.modelsUrl
            ?: return Result.failure(OpenAICompatibleGenericException("Models URL not configured for Groq"))
        val response: HttpResponse = defaultClient.get(modelsUrl) {
            bearerAuth(apiKey)
        }
        if (response.status.isSuccess()) {
            Result.success(response.body())
        } else {
            when (response.status.value) {
                401 -> throw OpenAICompatibleInvalidApiKeyException()
                else -> throw OpenAICompatibleGenericException("Failed to fetch Groq models: ${response.status}")
            }
        }
    } catch (e: OpenAICompatibleApiException) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(OpenAICompatibleConnectionException())
    }

    suspend fun xaiChat(
        messages: List<OpenAICompatibleChatRequestDto.Message>,
        tools: List<Tool> = emptyList(),
    ): Result<OpenAICompatibleChatResponseDto> = try {
        val apiKey = appSettings.getApiKey(Service.XAI).ifEmpty { throw OpenAICompatibleInvalidApiKeyException() }
        val model = appSettings.getSelectedModelId(Service.XAI)
        val response: HttpResponse =
            defaultClient.post(Service.XAI.chatUrl) {
                contentType(ContentType.Application.Json)
                bearerAuth(apiKey)
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
            when (response.status.value) {
                401 -> throw OpenAICompatibleInvalidApiKeyException()
                429 -> throw OpenAICompatibleRateLimitExceededException()
                else -> throw OpenAICompatibleGenericException("xAI request failed: ${response.status}")
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getXaiModels(): Result<OpenAICompatibleModelResponseDto> = try {
        val apiKey = appSettings.getApiKey(Service.XAI).ifEmpty { throw OpenAICompatibleInvalidApiKeyException() }
        val modelsUrl = Service.XAI.modelsUrl
            ?: return Result.failure(OpenAICompatibleGenericException("Models URL not configured for xAI"))
        val response: HttpResponse = defaultClient.get(modelsUrl) {
            bearerAuth(apiKey)
        }
        if (response.status.isSuccess()) {
            Result.success(response.body())
        } else {
            when (response.status.value) {
                401 -> throw OpenAICompatibleInvalidApiKeyException()

                402 -> throw OpenAICompatibleQuotaExhaustedException()

                else -> {
                    val responseBody = response.bodyAsText()
                    if (responseBody.contains("exhausted", ignoreCase = true) ||
                        responseBody.contains("credits", ignoreCase = true) ||
                        responseBody.contains("spending limit", ignoreCase = true)
                    ) {
                        throw OpenAICompatibleQuotaExhaustedException()
                    }
                    throw OpenAICompatibleGenericException("Failed to fetch xAI models: ${response.status}")
                }
            }
        }
    } catch (e: OpenAICompatibleApiException) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(OpenAICompatibleConnectionException())
    }

    suspend fun openRouterChat(
        messages: List<OpenAICompatibleChatRequestDto.Message>,
        tools: List<Tool> = emptyList(),
    ): Result<OpenAICompatibleChatResponseDto> = try {
        val apiKey = appSettings.getApiKey(Service.OpenRouter).ifEmpty { throw OpenAICompatibleInvalidApiKeyException() }
        val model = appSettings.getSelectedModelId(Service.OpenRouter)
        val response: HttpResponse =
            defaultClient.post(Service.OpenRouter.chatUrl) {
                contentType(ContentType.Application.Json)
                bearerAuth(apiKey)
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
            when (response.status.value) {
                401 -> throw OpenAICompatibleInvalidApiKeyException()
                429 -> throw OpenAICompatibleRateLimitExceededException()
                else -> throw OpenAICompatibleGenericException("OpenRouter request failed: ${response.status}")
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun nvidiaChat(
        messages: List<OpenAICompatibleChatRequestDto.Message>,
        tools: List<Tool> = emptyList(),
    ): Result<OpenAICompatibleChatResponseDto> = try {
        val apiKey = appSettings.getApiKey(Service.Nvidia).ifEmpty { throw OpenAICompatibleInvalidApiKeyException() }
        val model = appSettings.getSelectedModelId(Service.Nvidia)
        val response: HttpResponse =
            defaultClient.post(Service.Nvidia.chatUrl) {
                contentType(ContentType.Application.Json)
                bearerAuth(apiKey)
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
            when (response.status.value) {
                401 -> throw OpenAICompatibleInvalidApiKeyException()
                429 -> throw OpenAICompatibleRateLimitExceededException()
                else -> throw OpenAICompatibleGenericException("NVIDIA request failed: ${response.status}")
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getNvidiaModels(): Result<OpenAICompatibleModelResponseDto> = try {
        val apiKey = appSettings.getApiKey(Service.Nvidia).ifEmpty { throw OpenAICompatibleInvalidApiKeyException() }
        val modelsUrl = Service.Nvidia.modelsUrl
            ?: return Result.failure(OpenAICompatibleGenericException("Models URL not configured for NVIDIA"))
        val response: HttpResponse = defaultClient.get(modelsUrl) {
            bearerAuth(apiKey)
        }
        if (response.status.isSuccess()) {
            Result.success(response.body())
        } else {
            when (response.status.value) {
                401 -> throw OpenAICompatibleInvalidApiKeyException()
                else -> throw OpenAICompatibleGenericException("Failed to fetch NVIDIA models: ${response.status}")
            }
        }
    } catch (e: OpenAICompatibleApiException) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(OpenAICompatibleConnectionException())
    }

    suspend fun getOpenRouterModels(): Result<OpenAICompatibleModelResponseDto> = try {
        val modelsUrl = Service.OpenRouter.modelsUrl
            ?: return Result.failure(OpenAICompatibleGenericException("Models URL not configured for OpenRouter"))
        // OpenRouter's models endpoint is public, no auth needed
        val response: HttpResponse = defaultClient.get(modelsUrl)
        if (response.status.isSuccess()) {
            Result.success(response.body())
        } else {
            throw OpenAICompatibleGenericException("Failed to fetch OpenRouter models: ${response.status}")
        }
    } catch (e: OpenAICompatibleApiException) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(OpenAICompatibleConnectionException())
    }

    suspend fun validateOpenRouterApiKey(): Result<Unit> = try {
        val apiKey = appSettings.getApiKey(Service.OpenRouter).ifEmpty { throw OpenAICompatibleInvalidApiKeyException() }
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

    suspend fun openAICompatibleChat(
        messages: List<OpenAICompatibleChatRequestDto.Message>,
        baseUrl: String,
        tools: List<Tool> = emptyList(),
    ): Result<OpenAICompatibleChatResponseDto> = try {
        val model = appSettings.getSelectedModelId(Service.OpenAICompatible)
        if (model.isEmpty()) {
            throw OpenAICompatibleModelNotFoundException("No model selected")
        }
        val apiKey = appSettings.getApiKey(Service.OpenAICompatible)
        val response: HttpResponse =
            defaultClient.post("$baseUrl${Service.OpenAICompatible.chatUrl}") {
                contentType(ContentType.Application.Json)
                if (apiKey.isNotBlank()) {
                    bearerAuth(apiKey)
                }
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
            when (response.status.value) {
                401 -> throw OpenAICompatibleInvalidApiKeyException()
                404 -> throw OpenAICompatibleModelNotFoundException(model)
                else -> throw OpenAICompatibleGenericException("Request failed: ${response.status}")
            }
        }
    } catch (e: OpenAICompatibleApiException) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(OpenAICompatibleConnectionException())
    }

    suspend fun getOpenAICompatibleModels(baseUrl: String): Result<OpenAICompatibleModelsResponseDto> = try {
        val apiKey = appSettings.getApiKey(Service.OpenAICompatible)
        val response: HttpResponse =
            defaultClient.get("$baseUrl${Service.OpenAICompatible.modelsUrl}") {
                if (apiKey.isNotBlank()) {
                    bearerAuth(apiKey)
                }
            }
        if (response.status.isSuccess()) {
            Result.success(response.body())
        } else {
            when (response.status.value) {
                401 -> throw OpenAICompatibleInvalidApiKeyException()
                else -> throw OpenAICompatibleGenericException("Failed to fetch models: ${response.status}")
            }
        }
    } catch (e: OpenAICompatibleApiException) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(OpenAICompatibleConnectionException())
    }
}
