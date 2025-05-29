package com.inspiredandroid.kai.network

/**
 * Base class for API specific exceptions.
 */sealed class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exceptions related to the Gemini API.
 */sealed class GeminiApiException(message: String, cause: Throwable? = null) : ApiException(message, cause)

class GeminiInvalidApiKeyException(message: String, cause: Throwable? = null) : GeminiApiException(message, cause)
class GeminiRateLimitExceededException(message: String, cause: Throwable? = null) : GeminiApiException(message, cause)
class GeminiGenericException(message: String, cause: Throwable? = null) : GeminiApiException(message, cause)

/**
 * Exceptions related to the Groq API.
 */sealed class GroqApiException(message: String, cause: Throwable? = null) : ApiException(message, cause)

class GroqInvalidApiKeyException(message: String, cause: Throwable? = null) : GroqApiException(message, cause) // Corresponds to 401
class GroqRateLimitExceededException(message: String, cause: Throwable? = null) : GroqApiException(message, cause) // Potentially 429
class GroqGenericException(message: String, cause: Throwable? = null) : GroqApiException(message, cause)

/**
 * A generic network exception when the API is not specified or the error is not API-specific.
 */class GenericNetworkException(message: String, cause: Throwable? = null) : ApiException(message, cause)
