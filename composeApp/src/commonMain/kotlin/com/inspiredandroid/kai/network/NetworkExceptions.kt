package com.inspiredandroid.kai.network

import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.error_empty_response
import kai.composeapp.generated.resources.error_generic
import kai.composeapp.generated.resources.error_invalid_api_key
import kai.composeapp.generated.resources.error_openai_compatible_connection
import kai.composeapp.generated.resources.error_openai_compatible_model_not_found
import kai.composeapp.generated.resources.error_rate_limit_exceeded
import kai.composeapp.generated.resources.error_unknown
import org.jetbrains.compose.resources.StringResource

sealed class ApiException(message: String?, cause: Throwable? = null) : Exception(message, cause)

class GenericNetworkException(message: String, cause: Throwable? = null) : ApiException(message, cause)

sealed class GeminiApiException(message: String? = null, cause: Throwable? = null) : ApiException(message, cause)
class GeminiGenericException(message: String, cause: Throwable? = null) : GeminiApiException(message, cause)
class GeminiRateLimitExceededException : GeminiApiException()
class GeminiInvalidApiKeyException : GeminiApiException()

sealed class AnthropicApiException(message: String? = null, cause: Throwable? = null) : ApiException(message, cause)
class AnthropicGenericException(message: String, cause: Throwable? = null) : AnthropicApiException(message, cause)
class AnthropicInvalidApiKeyException : AnthropicApiException()
class AnthropicRateLimitExceededException : AnthropicApiException()
class AnthropicOverloadedException : AnthropicApiException("Anthropic API is overloaded")
class AnthropicInsufficientCreditsException : AnthropicApiException("Insufficient credits")

sealed class OpenAICompatibleApiException(message: String? = null, cause: Throwable? = null) : ApiException(message, cause)
class OpenAICompatibleGenericException(message: String, cause: Throwable? = null) : OpenAICompatibleApiException(message, cause)
class OpenAICompatibleInvalidApiKeyException : OpenAICompatibleApiException()
class OpenAICompatibleRateLimitExceededException : OpenAICompatibleApiException()
class OpenAICompatibleQuotaExhaustedException : OpenAICompatibleApiException("Quota exhausted")
class OpenAICompatibleConnectionException(message: String = "Cannot connect to server") : OpenAICompatibleApiException(message)
class OpenAICompatibleModelNotFoundException(model: String) : OpenAICompatibleApiException("Model not found: $model")
class OpenAICompatibleEmptyResponseException : OpenAICompatibleApiException("Empty response")
class OpenAICompatibleRequestTooLargeException : OpenAICompatibleApiException("Image is too large. Try a smaller image.")

class UnsupportedFileTypeException(message: String = "This file type is not supported. You can attach images, text files, and PDFs.") : ApiException(message)
class FileTooLargeException(message: String = "File is too large. Text files must be under 100 KB.") : ApiException(message)

sealed interface UiError {
    data class Resource(val resource: StringResource) : UiError
    data class Text(val message: String) : UiError
}

fun Exception.toUiError(): UiError = when (this) {
    is UnsupportedFileTypeException, is FileTooLargeException -> UiError.Text(message ?: "File error")
    is GeminiInvalidApiKeyException, is OpenAICompatibleInvalidApiKeyException, is AnthropicInvalidApiKeyException -> UiError.Resource(Res.string.error_invalid_api_key)
    is GeminiRateLimitExceededException, is OpenAICompatibleRateLimitExceededException, is AnthropicRateLimitExceededException -> UiError.Resource(Res.string.error_rate_limit_exceeded)
    is AnthropicOverloadedException -> UiError.Resource(Res.string.error_rate_limit_exceeded)
    is AnthropicInsufficientCreditsException, is OpenAICompatibleQuotaExhaustedException -> UiError.Text(message ?: "An unexpected error occurred.")
    is OpenAICompatibleConnectionException -> UiError.Resource(Res.string.error_openai_compatible_connection)
    is OpenAICompatibleModelNotFoundException -> UiError.Resource(Res.string.error_openai_compatible_model_not_found)
    is OpenAICompatibleEmptyResponseException -> UiError.Resource(Res.string.error_empty_response)
    is GeminiGenericException, is OpenAICompatibleGenericException, is AnthropicGenericException, is GenericNetworkException -> UiError.Text(message ?: "An unexpected error occurred.")
    else -> UiError.Resource(Res.string.error_unknown)
}
