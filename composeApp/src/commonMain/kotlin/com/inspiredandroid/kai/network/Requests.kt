package com.inspiredandroid.kai.network

import com.inspiredandroid.kai.Key
import com.inspiredandroid.kai.Value
import com.inspiredandroid.kai.httpClient
import com.inspiredandroid.kai.isDebugBuild
import com.inspiredandroid.kai.network.NetworkConstants.GEMINI_BASE_URL
import com.inspiredandroid.kai.network.NetworkConstants.GEMINI_GENERATE_CONTENT_PATH
import com.inspiredandroid.kai.network.NetworkConstants.GROQ_BASE_URL
import com.inspiredandroid.kai.network.NetworkConstants.GROQ_CHAT_COMPLETIONS_PATH
import com.inspiredandroid.kai.network.NetworkConstants.GROQ_MODELS_PATH
import com.inspiredandroid.kai.network.NetworkConstants.PROXY_BASE_URL
import com.inspiredandroid.kai.network.dtos.gemini.GeminiChatRequestDto
import com.inspiredandroid.kai.network.dtos.gemini.GeminiChatResponseDto
import com.inspiredandroid.kai.network.dtos.groq.GroqChatRequestDto
import com.inspiredandroid.kai.network.dtos.groq.GroqChatResponseDto
import com.inspiredandroid.kai.network.dtos.groq.GroqModelResponseDto
import com.russhwolf.settings.Settings
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

class Requests(private val settings: Settings) {

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
                    BearerTokens(settings.getString(Key.GROQ_API_KEY, ""), null)
                }
                refreshTokens {
                    BearerTokens(settings.getString(Key.GROQ_API_KEY, ""), null)
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

    suspend fun geminiChat(messages: List<GeminiChatRequestDto.Content>): Result<GeminiChatResponseDto> = try {
        val apiKey = settings.getStringOrNull(Key.GEMINI_API_KEY)
            ?: throw GeminiInvalidApiKeyException()

        val selectedModelId = settings.getString(Key.GEMINI_MODEL_ID, Value.DEFAULT_GEMINI_MODEL)

        val response: HttpResponse =
            defaultClient.post("$GEMINI_BASE_URL$selectedModelId$GEMINI_GENERATE_CONTENT_PATH?key=$apiKey") {
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
            defaultClient.post(PROXY_BASE_URL) {
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
        val model = settings.getString(Key.GROQ_MODEL_ID, Value.DEFAULT_GROQ_MODEL)
        val response: HttpResponse =
            groqClient.post("$GROQ_BASE_URL$GROQ_CHAT_COMPLETIONS_PATH") {
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
            groqClient.get("$GROQ_BASE_URL$GROQ_MODELS_PATH")
        if (response.status.isSuccess()) {
            Result.success(response.body())
        } else {
            throw Exception()
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
