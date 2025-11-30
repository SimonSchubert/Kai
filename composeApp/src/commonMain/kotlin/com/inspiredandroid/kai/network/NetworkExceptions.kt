package com.inspiredandroid.kai.network

sealed class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

class GenericNetworkException(message: String, cause: Throwable? = null) : ApiException(message, cause)

sealed class GeminiApiException(message: String, cause: Throwable? = null) : ApiException(message, cause)
class GeminiGenericException(message: String, cause: Throwable? = null) : GeminiApiException(message, cause)
class GeminiRateLimitExceededException(message: String, cause: Throwable? = null) : GeminiApiException(message, cause)
class GeminiInvalidApiKeyException(message: String, cause: Throwable? = null) : GeminiApiException(message, cause)

sealed class GroqApiException(message: String, cause: Throwable? = null) : ApiException(message, cause)
class GroqInvalidApiKeyException(message: String, cause: Throwable? = null) : GroqApiException(message, cause) // Corresponds to 401
class GroqRateLimitExceededException(message: String, cause: Throwable? = null) : GroqApiException(message, cause) // Potentially 429
class GroqGenericException(message: String, cause: Throwable? = null) : GroqApiException(message, cause)
