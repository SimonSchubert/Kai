package com.inspiredandroid.kai.splinterlands

actual fun signMessage(message: String, postingKeyWif: String): String = throw UnsupportedOperationException("Splinterlands is not supported on iOS")

actual suspend fun buildSignedCustomJson(
    username: String,
    postingKeyWif: String,
    opId: String,
    jsonPayload: String,
): String = throw UnsupportedOperationException("Splinterlands is not supported on iOS")
