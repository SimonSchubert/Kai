package com.inspiredandroid.kai.network

import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.httpClient
import com.inspiredandroid.kai.isDebugBuild
import com.inspiredandroid.kai.network.dtos.gemini.GeminiChatRequestDto
import com.inspiredandroid.kai.network.dtos.gemini.GeminiChatResponseDto
import com.inspiredandroid.kai.network.dtos.gemini.GeminiModelsResponseDto
import com.inspiredandroid.kai.network.dtos.groq.GroqChatRequestDto
import com.inspiredandroid.kai.network.dtos.groq.GroqChatResponseDto
import com.inspiredandroid.kai.network.dtos.groq.GroqModelResponseDto
import com.inspiredandroid.kai.network.dtos.ollama.OllamaModelsResponseDto
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.authProviders
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.EMPTY
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
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

    private fun <T : HttpClientEngineConfig> HttpClientConfig<T>.commonConfig() {
        install(ContentNegotiation) {
            json(
                Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                },
            )
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30.seconds.inWholeMilliseconds
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

    private val defaultClient = httpClient {
        commonConfig()
    }

    private val groqClient = httpClient {
        commonConfig()
        install(Auth) {
            bearer {
                loadTokens {
                    BearerTokens(appSettings.getApiKey(Service.Groq), null)
                }
                refreshTokens {
                    BearerTokens(appSettings.getApiKey(Service.Groq), null)
                }
            }
        }
    }

    fun clearBearerToken() {
        groqClient.authProviders.filterIsInstance<BearerAuthProvider>().firstOrNull()?.clearToken()
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

    suspend fun geminiChat(messages: List<GeminiChatRequestDto.Content>): Result<GeminiChatResponseDto> = try {
        val apiKey = appSettings.getApiKey(Service.Gemini).ifEmpty { throw GeminiInvalidApiKeyException() }
        val selectedModelId = appSettings.getSelectedModelId(Service.Gemini)

        val response: HttpResponse =
            defaultClient.post("${Service.Gemini.chatUrl}$selectedModelId:generateContent?key=$apiKey") {
                contentType(ContentType.Application.Json)
                setBody(
                    GeminiChatRequestDto(
                        contents = messages,
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
                        throw Exception()
                    }
                }
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun freeChat(messages: List<GroqChatRequestDto.Message>): Result<GroqChatResponseDto> = try {
        val response: HttpResponse =
            defaultClient.post(Service.Free.chatUrl) {
                contentType(ContentType.Application.Json)
                setBody(
                    GroqChatRequestDto(
                        messages = messages,
                        model = "",
                    ),
                )
            }
        if (response.status.isSuccess()) {
            Result.success(response.body())
        } else {
            when (response.status.value) {
                401 -> throw GroqInvalidApiKeyException()
                429 -> throw GroqRateLimitExceededException()
                else -> throw Exception()
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun groqChat(messages: List<GroqChatRequestDto.Message>): Result<GroqChatResponseDto> = try {
        val model = appSettings.getSelectedModelId(Service.Groq)
        val response: HttpResponse =
            groqClient.post(Service.Groq.chatUrl) {
                contentType(ContentType.Application.Json)
                setBody(
                    GroqChatRequestDto(
                        messages = messages,
                        model = model,
                    ),
                )
            }
        if (response.status.isSuccess()) {
            Result.success(response.body())
        } else {
            when (response.status.value) {
                401 -> throw GroqInvalidApiKeyException()
                429 -> throw GroqRateLimitExceededException()
                else -> throw Exception()
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getGroqModels(): Result<GroqModelResponseDto> = try {
        val response: HttpResponse =
            groqClient.get(Service.Groq.modelsUrl!!)
        if (response.status.isSuccess()) {
            Result.success(response.body())
        } else {
            throw Exception()
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun ollamaChat(
        messages: List<GroqChatRequestDto.Message>,
        baseUrl: String,
    ): Result<GroqChatResponseDto> = try {
        val model = appSettings.getSelectedModelId(Service.Ollama)
        if (model.isEmpty()) {
            throw OllamaModelNotFoundException("No model selected")
        }
        val response: HttpResponse =
            defaultClient.post("$baseUrl${Service.Ollama.chatUrl}") {
                contentType(ContentType.Application.Json)
                setBody(
                    GroqChatRequestDto(
                        messages = messages,
                        model = model,
                    ),
                )
            }
        if (response.status.isSuccess()) {
            Result.success(response.body())
        } else {
            when (response.status.value) {
                404 -> throw OllamaModelNotFoundException(model)
                else -> throw OllamaGenericException("Ollama request failed: ${response.status}")
            }
        }
    } catch (e: OllamaApiException) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(OllamaConnectionException())
    }

    suspend fun getOllamaModels(baseUrl: String): Result<OllamaModelsResponseDto> = try {
        val response: HttpResponse =
            defaultClient.get("$baseUrl${Service.Ollama.modelsUrl}")
        if (response.status.isSuccess()) {
            Result.success(response.body())
        } else {
            throw OllamaGenericException("Failed to fetch models: ${response.status}")
        }
    } catch (e: OllamaApiException) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(OllamaConnectionException())
    }
}
