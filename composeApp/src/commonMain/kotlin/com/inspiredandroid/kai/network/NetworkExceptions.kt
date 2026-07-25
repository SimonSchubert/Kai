package com.inspiredandroid.kai.network

import com.inspiredandroid.kai.inference.InferenceTimeoutException
import com.inspiredandroid.kai.inference.InsufficientMemoryException
import com.inspiredandroid.kai.inference.NoModelDownloadedException
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.error_all_services_failed
import kai.composeapp.generated.resources.error_bad_request
import kai.composeapp.generated.resources.error_content_moderation
import kai.composeapp.generated.resources.error_context_window_exceeded
import kai.composeapp.generated.resources.error_empty_response
import kai.composeapp.generated.resources.error_file_too_large
import kai.composeapp.generated.resources.error_image_too_large
import kai.composeapp.generated.resources.error_insufficient_credits
import kai.composeapp.generated.resources.error_invalid_api_key
import kai.composeapp.generated.resources.error_openai_compatible_connection
import kai.composeapp.generated.resources.error_openai_compatible_model_not_found
import kai.composeapp.generated.resources.error_provider_error
import kai.composeapp.generated.resources.error_quota_exhausted
import kai.composeapp.generated.resources.error_rate_limit_exceeded
import kai.composeapp.generated.resources.error_service_unavailable
import kai.composeapp.generated.resources.error_unknown
import kai.composeapp.generated.resources.error_unsupported_file_type
import kai.composeapp.generated.resources.litert_error_inference_timeout
import kai.composeapp.generated.resources.litert_error_insufficient_memory
import kai.composeapp.generated.resources.litert_error_no_model
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
class OpenAICompatibleContentModerationException(detail: String? = null) : OpenAICompatibleApiException(detail)
class OpenAICompatibleProviderErrorException(detail: String? = null) : OpenAICompatibleApiException(detail)
class OpenAICompatibleServiceUnavailableException : OpenAICompatibleApiException()
class OpenAICompatibleTimeoutException : OpenAICompatibleApiException()
class OpenAICompatibleBadRequestException(detail: String? = null) : OpenAICompatibleApiException(detail)

class ContextWindowExceededException : ApiException(null)
class UnsupportedFileTypeException : ApiException(null)
class FileTooLargeException : ApiException(null)
class AllServicesFailedException : ApiException(null)

sealed interface UiError {
    data class Resource(val resource: StringResource) : UiError
    data class Text(val message: String) : UiError
    data class ResourceWithDetail(val resource: StringResource, val detail: String) : UiError
}

/**
 * Errors that mean the built-in Free path (or another free-capacity quota)
 * is exhausted — used to decide whether to show free-provider signup suggestions.
 *
 * The kai9000 Free proxy often returns HTTP 500 with a body like
 * "All free providers failed" rather than a pure 429, so message matching
 * is required in addition to typed rate-limit exceptions.
 */
fun Exception.isFreeCapacityError(): Boolean = when (this) {
    is OpenAICompatibleRateLimitExceededException,
    is OpenAICompatibleQuotaExhaustedException,
    is GeminiRateLimitExceededException,
    is AnthropicRateLimitExceededException,
    is AnthropicOverloadedException,
    -> true

    is OpenAICompatibleProviderErrorException,
    is OpenAICompatibleServiceUnavailableException,
    is OpenAICompatibleGenericException,
    is OpenAICompatibleBadRequestException,
    -> messageLooksLikeFreeCapacity(message)

    else -> messageLooksLikeFreeCapacity(message)
}

/** Message patterns used by Free / capacity exhaustion across providers. */
internal fun messageLooksLikeFreeCapacity(message: String?): Boolean {
    val m = message?.lowercase() ?: return false
    return m.contains("all free providers failed") ||
        m.contains("free providers failed") ||
        m.contains("all free providers") ||
        m.contains("rate limit") ||
        m.contains("rate_limit") ||
        m.contains("too many requests") ||
        m.contains("quota") ||
        m.contains("capacity") ||
        m.contains("overloaded")
}

/**
 * Show the free-provider upsell only when Free is the sole path (no user
 * services configured) and the failure is a free-capacity rate/quota limit.
 */
fun shouldShowFreeProviderSuggestions(
    noConfiguredServices: Boolean,
    exception: Exception,
): Boolean = noConfiguredServices && exception.isFreeCapacityError()

fun Exception.toUiError(): UiError = when (this) {
    is UnsupportedFileTypeException -> UiError.Resource(Res.string.error_unsupported_file_type)

    is FileTooLargeException -> UiError.Resource(Res.string.error_file_too_large)

    is ContextWindowExceededException -> UiError.Resource(Res.string.error_context_window_exceeded)

    is AllServicesFailedException -> UiError.Resource(Res.string.error_all_services_failed)

    is OpenAICompatibleRequestTooLargeException -> UiError.Resource(Res.string.error_image_too_large)

    is GeminiInvalidApiKeyException, is OpenAICompatibleInvalidApiKeyException, is AnthropicInvalidApiKeyException -> UiError.Resource(Res.string.error_invalid_api_key)

    is GeminiRateLimitExceededException, is OpenAICompatibleRateLimitExceededException, is AnthropicRateLimitExceededException -> UiError.Resource(Res.string.error_rate_limit_exceeded)

    is AnthropicOverloadedException -> UiError.Resource(Res.string.error_rate_limit_exceeded)

    is AnthropicInsufficientCreditsException -> UiError.Resource(Res.string.error_insufficient_credits)

    is OpenAICompatibleQuotaExhaustedException -> UiError.Resource(Res.string.error_quota_exhausted)

    is OpenAICompatibleConnectionException -> UiError.Resource(Res.string.error_openai_compatible_connection)

    is OpenAICompatibleModelNotFoundException -> UiError.Resource(Res.string.error_openai_compatible_model_not_found)

    is OpenAICompatibleEmptyResponseException -> UiError.Resource(Res.string.error_empty_response)

    is OpenAICompatibleTimeoutException -> UiError.Resource(Res.string.error_openai_compatible_connection)

    is OpenAICompatibleServiceUnavailableException -> UiError.Resource(Res.string.error_service_unavailable)

    is OpenAICompatibleContentModerationException -> message?.takeIf { it.isNotBlank() }
        ?.let { UiError.ResourceWithDetail(Res.string.error_content_moderation, it) }
        ?: UiError.Resource(Res.string.error_content_moderation)

    is OpenAICompatibleProviderErrorException -> message?.takeIf { it.isNotBlank() }
        ?.let { UiError.ResourceWithDetail(Res.string.error_provider_error, it) }
        ?: UiError.Resource(Res.string.error_provider_error)

    is OpenAICompatibleBadRequestException -> message?.takeIf { it.isNotBlank() }
        ?.let { UiError.ResourceWithDetail(Res.string.error_bad_request, it) }
        ?: UiError.Resource(Res.string.error_bad_request)

    is InsufficientMemoryException -> UiError.Resource(Res.string.litert_error_insufficient_memory)

    is InferenceTimeoutException -> UiError.Resource(Res.string.litert_error_inference_timeout)

    is NoModelDownloadedException -> UiError.Resource(Res.string.litert_error_no_model)

    is GeminiGenericException, is OpenAICompatibleGenericException, is AnthropicGenericException, is GenericNetworkException ->
        if (!message.isNullOrBlank()) UiError.Text(message!!) else UiError.Resource(Res.string.error_unknown)

    else -> if (!message.isNullOrBlank()) UiError.Text(message!!) else UiError.Resource(Res.string.error_unknown)
}
