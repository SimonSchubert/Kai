package com.inspiredandroid.kai.sms

/**
 * Multiplatform SMS sender. Only the Android FOSS build actually sends — the
 * Play Store flavor doesn't declare `SEND_SMS`, so [hasPermission] returns
 * false there and [send] no-ops with a failure result. iOS/desktop/wasm stub
 * the same way.
 */
expect class SmsSender() {
    fun hasPermission(): Boolean

    /**
     * Fires the message via the system's default SMS stack. Long bodies are
     * split into multiple parts. Returns [SmsSendResult.Success] on accepted
     * submission (delivery is best-effort and may complete asynchronously), or
     * [SmsSendResult.Failure] on a precondition violation (missing permission,
     * bad address, platform unsupported).
     */
    suspend fun send(address: String, body: String): SmsSendResult
}

sealed class SmsSendResult {
    data object Success : SmsSendResult()
    data class Failure(val message: String) : SmsSendResult()
}
