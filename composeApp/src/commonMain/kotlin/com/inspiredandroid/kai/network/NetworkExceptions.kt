package com.inspiredandroid.kai.network

sealed class ApiException(message: String?, cause: Throwable? = null) : Exception(message, cause)

class GenericNetworkException(message: String, cause: Throwable? = null) : ApiException(message, cause)

sealed class GeminiApiException(message: String? = null, cause: Throwable? = null) : ApiException(message, cause)
class GeminiGenericException(message: String, cause: Throwable? = null) : GeminiApiException(message, cause)
class GeminiRateLimitExceededException : GeminiApiException()
class GeminiInvalidApiKeyException : GeminiApiException()

sealed class GroqApiException(message: String? = null, cause: Throwable? = null) : ApiException(message, cause)
class GroqGenericException(message: String, cause: Throwable? = null) : GroqApiException(message, cause)
class GroqInvalidApiKeyException : GroqApiException()
class GroqRateLimitExceededException : GroqApiException()

sealed class OllamaApiException(message: String? = null, cause: Throwable? = null) : ApiException(message, cause)
class OllamaGenericException(message: String, cause: Throwable? = null) : OllamaApiException(message, cause)
class OllamaConnectionException : OllamaApiException("Cannot connect to Ollama server")
class OllamaModelNotFoundException(model: String) : OllamaApiException("Model not found: $model")
