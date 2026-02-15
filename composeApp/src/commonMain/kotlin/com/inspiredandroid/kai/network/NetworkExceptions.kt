package com.inspiredandroid.kai.network

import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.error_generic
import kai.composeapp.generated.resources.error_invalid_api_key
import kai.composeapp.generated.resources.error_openai_compatible_connection
import kai.composeapp.generated.resources.error_openai_compatible_model_not_found
import kai.composeapp.generated.resources.error_rate_limit_exceeded
import kai.composeapp.generated.resources.error_unknown
import org.jetbrains.compose.resources.getString

sealed class ApiException(message: String?, cause: Throwable? = null) : Exception(message, cause)

class GenericNetworkException(message: String, cause: Throwable? = null) : ApiException(message, cause)

sealed class GeminiApiException(message: String? = null, cause: Throwable? = null) : ApiException(message, cause)
class GeminiGenericException(message: String, cause: Throwable? = null) : GeminiApiException(message, cause)
class GeminiRateLimitExceededException : GeminiApiException()
class GeminiInvalidApiKeyException : GeminiApiException()

sealed class OpenAICompatibleApiException(message: String? = null, cause: Throwable? = null) : ApiException(message, cause)
class OpenAICompatibleGenericException(message: String, cause: Throwable? = null) : OpenAICompatibleApiException(message, cause)
class OpenAICompatibleInvalidApiKeyException : OpenAICompatibleApiException()
class OpenAICompatibleRateLimitExceededException : OpenAICompatibleApiException()
class OpenAICompatibleQuotaExhaustedException : OpenAICompatibleApiException("Quota exhausted")
class OpenAICompatibleConnectionException : OpenAICompatibleApiException("Cannot connect to server")
class OpenAICompatibleModelNotFoundException(model: String) : OpenAICompatibleApiException("Model not found: $model")

suspend fun Exception.toUserMessage(): String = try {
    when (this) {
        is GeminiInvalidApiKeyException, is OpenAICompatibleInvalidApiKeyException -> getString(Res.string.error_invalid_api_key)
        is GeminiRateLimitExceededException, is OpenAICompatibleRateLimitExceededException -> getString(Res.string.error_rate_limit_exceeded)
        is OpenAICompatibleConnectionException -> getString(Res.string.error_openai_compatible_connection)
        is OpenAICompatibleModelNotFoundException -> getString(Res.string.error_openai_compatible_model_not_found)
        is GeminiGenericException, is OpenAICompatibleGenericException, is GenericNetworkException -> message ?: getString(Res.string.error_generic)
        else -> getString(Res.string.error_unknown)
    }
} catch (_: Exception) {
    message ?: "An error occurred"
}
