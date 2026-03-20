package com.inspiredandroid.kai.network

import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.error_empty_response
import kai.composeapp.generated.resources.error_file_too_large
import kai.composeapp.generated.resources.error_image_too_large
import kai.composeapp.generated.resources.error_insufficient_credits
import kai.composeapp.generated.resources.error_invalid_api_key
import kai.composeapp.generated.resources.error_openai_compatible_connection
import kai.composeapp.generated.resources.error_openai_compatible_model_not_found
import kai.composeapp.generated.resources.error_quota_exhausted
import kai.composeapp.generated.resources.error_rate_limit_exceeded
import kai.composeapp.generated.resources.error_unknown
import kai.composeapp.generated.resources.error_unsupported_file_type
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
class AnthropicOverloadedException : AnthropicApiException()
class AnthropicInsufficientCreditsException : AnthropicApiException()

sealed class OpenAICompatibleApiException(message: String? = null, cause: Throwable? = null) : ApiException(message, cause)
class OpenAICompatibleGenericException(message: String, cause: Throwable? = null) : OpenAICompatibleApiException(message, cause)
class OpenAICompatibleInvalidApiKeyException : OpenAICompatibleApiException()
class OpenAICompatibleRateLimitExceededException : OpenAICompatibleApiException()
class OpenAICompatibleQuotaExhaustedException : OpenAICompatibleApiException()
class OpenAICompatibleConnectionException : OpenAICompatibleApiException()
class OpenAICompatibleModelNotFoundException : OpenAICompatibleApiException()
class OpenAICompatibleEmptyResponseException : OpenAICompatibleApiException()
class OpenAICompatibleRequestTooLargeException : OpenAICompatibleApiException()

class UnsupportedFileTypeException : ApiException(null)
class FileTooLargeException : ApiException(null)

sealed interface UiError {
    data class Resource(val resource: StringResource) : UiError
    data class Text(val message: String) : UiError
}

fun Exception.toUiError(): UiError = when (this) {
    is UnsupportedFileTypeException -> UiError.Resource(Res.string.error_unsupported_file_type)
    is FileTooLargeException -> UiError.Resource(Res.string.error_file_too_large)
    is OpenAICompatibleRequestTooLargeException -> UiError.Resource(Res.string.error_image_too_large)
    is GeminiInvalidApiKeyException, is OpenAICompatibleInvalidApiKeyException, is AnthropicInvalidApiKeyException -> UiError.Resource(Res.string.error_invalid_api_key)
    is GeminiRateLimitExceededException, is OpenAICompatibleRateLimitExceededException, is AnthropicRateLimitExceededException -> UiError.Resource(Res.string.error_rate_limit_exceeded)
    is AnthropicOverloadedException -> UiError.Resource(Res.string.error_rate_limit_exceeded)
    is AnthropicInsufficientCreditsException -> UiError.Resource(Res.string.error_insufficient_credits)
    is OpenAICompatibleQuotaExhaustedException -> UiError.Resource(Res.string.error_quota_exhausted)
    is OpenAICompatibleConnectionException -> UiError.Resource(Res.string.error_openai_compatible_connection)
    is OpenAICompatibleModelNotFoundException -> UiError.Resource(Res.string.error_openai_compatible_model_not_found)
    is OpenAICompatibleEmptyResponseException -> UiError.Resource(Res.string.error_empty_response)
    is GeminiGenericException, is OpenAICompatibleGenericException, is AnthropicGenericException, is GenericNetworkException -> UiError.Text(message ?: "An unexpected error occurred.")
    else -> UiError.Resource(Res.string.error_unknown)
}
