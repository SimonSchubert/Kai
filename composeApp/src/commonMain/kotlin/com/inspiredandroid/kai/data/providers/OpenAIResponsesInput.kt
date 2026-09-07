package com.inspiredandroid.kai.data.providers

import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Rewrites chat-completions messages as Responses API `input` items.
 *
 * Deliberately a translation of the existing message list rather than a second builder off
 * [com.inspiredandroid.kai.ui.chat.History]: everything upstream — system prompt placement,
 * attachment splitting, [sanitizeToolMessages] pairing, context trimming — stays shared with the
 * chat-completions path, and only the wire shape differs.
 *
 * The mapping is:
 *
 * | Chat completions | Responses |
 * |---|---|
 * | `{role, content: "..."}` | same (an "easy input message") |
 * | user `content` parts `text` / `image_url` | `input_text` / `input_image` (flat `image_url` string) |
 * | assistant `tool_calls[]` | one `function_call` item each, `id` → `call_id` |
 * | `{role: "tool", tool_call_id, content}` | `{type: "function_call_output", call_id, output}` |
 *
 * Reasoning is not replayed. OpenAI recommends echoing back the `reasoning` items that preceded a
 * `function_call`, but replaying one whose following item was trimmed away is a hard 400
 * (`Item 'rs_…' of type 'reasoning' was provided without its required following item`), so the
 * trade is a re-reasoned tool round-trip instead of a request that can fail outright.
 */
internal fun toResponsesInput(messages: List<OpenAICompatibleChatRequestDto.Message>): List<JsonObject> = buildList {
    for (message in messages) {
        when {
            message.role == "tool" -> add(
                buildJsonObject {
                    put("type", "function_call_output")
                    put("call_id", message.tool_call_id.orEmpty())
                    put("output", message.content.asPlainText())
                },
            )

            message.role == "assistant" && !message.tool_calls.isNullOrEmpty() -> {
                val text = message.content.asPlainText()
                if (text.isNotBlank()) add(easyMessage("assistant", text))
                for (call in message.tool_calls) {
                    add(
                        buildJsonObject {
                            put("type", "function_call")
                            put("call_id", call.id)
                            put("name", call.function.name)
                            put("arguments", call.function.arguments)
                        },
                    )
                }
            }

            // Only user turns carry content parts. Any other role with an array falls through to
            // the flattened form below: assistant parts would have to be `output_text`, and
            // emitting `input_text` for them is a 400.
            message.role == "user" && message.content is JsonArray -> add(
                buildJsonObject {
                    put("role", message.role)
                    put("content", JsonArray((message.content as JsonArray).map { it.toResponsesContentPart() }))
                },
            )

            else -> add(easyMessage(message.role, message.content.asPlainText()))
        }
    }
}

private fun easyMessage(role: String, content: String): JsonObject = buildJsonObject {
    put("role", role)
    put("content", content)
}

/**
 * Flattens a chat-completions content value to text. Content-part arrays only reach here for
 * roles that can't carry attachments, so concatenating their `text` fields matches how
 * `FlexibleContentSerializer` reads the same shape coming back.
 */
private fun JsonElement?.asPlainText(): String = when (this) {
    null -> ""

    is JsonPrimitive -> contentOrNull.orEmpty()

    is JsonArray -> mapNotNull { (it as? JsonObject)?.get("text")?.let { text -> (text as? JsonPrimitive)?.contentOrNull } }
        .joinToString("")

    else -> ""
}

/**
 * Converts one user content part. `image_url` carries a nested `{url}` object in chat completions
 * but a flat string in the Responses API. Unknown part types degrade to `input_text` so a shape we
 * don't model still reaches the model as text instead of failing validation.
 */
private fun JsonElement.toResponsesContentPart(): JsonObject {
    val part = this as? JsonObject ?: return buildJsonObject {
        put("type", "input_text")
        put("text", (this@toResponsesContentPart as? JsonPrimitive)?.contentOrNull.orEmpty())
    }
    val url = (part["image_url"] as? JsonObject)?.get("url")?.let { (it as? JsonPrimitive)?.contentOrNull }
    return if (url != null) {
        buildJsonObject {
            put("type", "input_image")
            put("image_url", url)
        }
    } else {
        buildJsonObject {
            put("type", "input_text")
            put("text", (part["text"] as? JsonPrimitive)?.contentOrNull.orEmpty())
        }
    }
}
