package com.inspiredandroid.kai.network

import com.inspiredandroid.kai.Key
import com.inspiredandroid.kai.Value
import com.inspiredandroid.kai.httpClient
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
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

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
            requestTimeoutMillis = 30_000 // 30 seconds
        }
        install(Logging) {
            logger = DebugKtorLogger()
            level = LogLevel.BODY
        }
    }

    private val geminiClient = httpClient {
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
        groqClient.authProviders.filterIsInstance<BearerAuthProvider>().first().clearToken()
    }

    class DebugKtorLogger : Logger {
        override fun log(message: String) {
            println("[KTOR] $message")
        }
    }

    suspend fun geminiChat(messages: List<GeminiChatRequestDto.Content>): Result<GeminiChatResponseDto> {
        return try {
            val apiKey = settings.getStringOrNull(Key.GEMINI_API_KEY)
                ?: return Result.failure(UnauthorizedException)
            val selectedModelId = settings.getString(Key.GEMINI_MODEL_ID, Value.DEFAULT_GEMINI_MODEL)

            val response: HttpResponse =
                geminiClient.post("https://generativelanguage.googleapis.com/v1beta/models/$selectedModelId:generateContent?key=$apiKey") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        GeminiChatRequestDto(
                            contents = messages,
                        ),
                    )
                }
            if (response.status.value == 403) {
                Result.failure(UnauthorizedException)
            } else {
                Result.success(response.body())
            }
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    suspend fun groqChat(messages: List<GroqChatRequestDto.Message>): Result<GroqChatResponseDto> {
        val url = if (settings.getString(Key.GROQ_API_KEY, "").isEmpty()) {
            "https://proxy-api-amber.vercel.app/chat"
        } else {
            "https://api.groq.com/openai/v1/chat/completions"
        }
        val selectedModelId = settings.getString(Key.GROQ_MODEL_ID, Value.DEFAULT_GROQ_MODEL)
        return try {
            val response: HttpResponse =
                groqClient.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(
                        GroqChatRequestDto(
                            messages = messages,
                            model = selectedModelId,
                        ),
                    )
                }
            if (response.status.value == 401) {
                Result.failure(UnauthorizedException)
            } else {
                Result.success(response.body())
            }
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    suspend fun getGroqModels(): Result<GroqModelResponseDto> = try {
        val response: HttpResponse =
            groqClient.get("https://api.groq.com/openai/v1/models")
        Result.success(response.body())
    } catch (exception: Throwable) {
        Result.failure(exception)
    }
}

object UnauthorizedException : Exception()
