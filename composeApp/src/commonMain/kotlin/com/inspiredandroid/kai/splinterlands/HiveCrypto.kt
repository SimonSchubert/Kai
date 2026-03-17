package com.inspiredandroid.kai.splinterlands

/**
 * Signs a message (username + timestamp) with the Hive posting key.
 * Returns the signature as a hex string (compact recovery format).
 */
expect fun signMessage(message: String, postingKeyWif: String): String

/**
 * Build and sign a Hive custom_json operation, returning the signed transaction as JSON.
 * The transaction is NOT broadcast — just signed for submission to the Splinterlands battle_tx endpoint.
 */
expect suspend fun buildSignedCustomJson(
    username: String,
    postingKeyWif: String,
    opId: String,
    jsonPayload: String,
): String
