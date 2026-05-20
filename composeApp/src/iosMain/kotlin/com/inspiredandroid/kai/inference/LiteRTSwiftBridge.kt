package com.inspiredandroid.kai.inference

/**
 * Bridge to the LiteRT-LM Swift SDK (SPM package `github.com/google-ai-edge/LiteRT-LM`).
 *
 * Kotlin/Native exposes this interface to the iOS app target as an Objective-C protocol.
 * The Swift class `KaiLiteRTBridge` (in iosApp/) implements it and registers itself
 * into [LiteRTBridgeRegistry] at app startup.
 *
 * All async Swift operations (`Engine.initialize`, `Conversation.sendMessage`) are
 * wrapped in completion handlers here so the Kotlin caller can use
 * `suspendCancellableCoroutine` for natural suspend semantics.
 */
interface LiteRTSwiftBridge {
    fun initializeEngine(
        modelPath: String,
        cacheDir: String,
        maxNumTokens: Int,
        onComplete: (errorMessage: String?) -> Unit,
    )

    fun releaseEngine()

    fun isEngineReady(): Boolean

    /**
     * @param messagesJson JSON array of `{"role": "user"|"model", "content": "..."}`. The
     *        last entry is treated as the prompt; preceding entries become conversation history.
     * @param systemPrompt nullable system instruction.
     * @param onResult callback receiving either the model's text response or an error message.
     */
    fun chat(
        messagesJson: String,
        systemPrompt: String?,
        onResult: (response: String?, errorMessage: String?) -> Unit,
    )
}

/**
 * Single registration point for the Swift-side bridge. The iOS app target assigns
 * [bridge] before any Kotlin code touches the local inference engine.
 */
object LiteRTBridgeRegistry {
    var bridge: LiteRTSwiftBridge? = null
}
