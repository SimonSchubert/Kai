package com.inspiredandroid.kai.data

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

class ConversationIdElement(val conversationId: String) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ConversationIdElement>
}

suspend fun currentConversationIdOrNull(): String? = coroutineContext[ConversationIdElement]?.conversationId

/**
 * Marks the coroutine of a heartbeat run. Tools that rewrite durable agent state (e.g. promoting
 * a memory into the system prompt) check for it, so text injected into a normal chat — from an
 * email, notification or fetched page — can't trigger them.
 */
object HeartbeatRunElement : AbstractCoroutineContextElement(HeartbeatRunElement), CoroutineContext.Key<HeartbeatRunElement>

suspend fun isHeartbeatRun(): Boolean = coroutineContext[HeartbeatRunElement] != null
