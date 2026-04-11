@file:OptIn(ExperimentalUuidApi::class, ExperimentalEncodingApi::class)

package com.inspiredandroid.kai.ui.chat

import androidx.compose.runtime.Immutable
import com.inspiredandroid.kai.data.Attachment
import com.inspiredandroid.kai.data.ServiceEntry
import com.inspiredandroid.kai.data.SharedJson
import com.inspiredandroid.kai.network.UiError
import com.inspiredandroid.kai.network.dtos.gemini.GeminiChatRequestDto
import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.StringResource
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private fun String.isTextMimeType(): Boolean = startsWith("text/") || this == "application/json" || this == "application/xml" ||
    this == "application/javascript" || this == "application/x-yaml" || this == "application/yaml"

/**
 * Splits attachments into the text that should be prepended to the user's message
 * (decoded text files with filename headers) and the remaining binary attachments
 * (images, PDFs) that become standalone content blocks in provider-specific formats.
 */
private data class AttachmentSplit(
    val textPrefix: String,
    val binaries: List<Attachment>,
)

private fun List<Attachment>.splitForMessage(): AttachmentSplit {
    if (isEmpty()) return AttachmentSplit("", emptyList())
    val prefix = StringBuilder()
    val binaries = mutableListOf<Attachment>()
    for (att in this) {
        if (att.mimeType.isTextMimeType()) {
            val decoded = Base64.decode(att.data).decodeToString()
            if (att.fileName != null) prefix.append("--- ${att.fileName} ---\n")
            prefix.append(decoded).append("\n\n")
        } else {
            binaries.add(att)
        }
    }
    return AttachmentSplit(prefix.toString(), binaries)
}

@Immutable
data class ConversationSummary(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val isHeartbeat: Boolean = false,
    val isInteractive: Boolean = false,
)

@Immutable
data class ChatUiState(
    val actions: ChatActions,
    val history: ImmutableList<History> = persistentListOf(),
    val isSpeechOutputEnabled: Boolean = false,
    val isLoading: Boolean = false,
    val error: UiError? = null,
    val warning: StringResource? = null,
    val showPrivacyInfo: Boolean = false,
    val supportedFileExtensions: ImmutableList<String> = persistentListOf(),
    val isSpeaking: Boolean = false,
    val isSpeakingContentId: String = "",
    val files: ImmutableList<PlatformFile> = persistentListOf(),
    val availableServices: ImmutableList<ServiceEntry> = persistentListOf(),
    val savedConversations: ImmutableList<ConversationSummary> = persistentListOf(),
    val currentConversationId: String? = null,
    val hasUnreadHeartbeat: Boolean = false,
    val snackbarMessage: StringResource? = null,
    val pendingConversationDeletion: String? = null,
    val isInteractiveMode: Boolean = false,
) {
    val heartbeatConversationId: String?
        get() = savedConversations.firstOrNull { it.isHeartbeat }?.id
}

@Immutable
data class History(
    val id: String = Uuid.random().toString(),
    val role: Role,
    val content: String,
    val attachments: ImmutableList<Attachment> = persistentListOf(),
    val toolCallId: String? = null,
    val toolName: String? = null,
    val toolCalls: ImmutableList<ToolCallInfo>? = null,
    val isThinking: Boolean = false,
    val isStatusMessage: Boolean = false,
    val fallbackServiceName: String? = null,
) {
    enum class Role {
        USER,
        ASSISTANT,
        TOOL_EXECUTING,
        TOOL,
    }
}

@Immutable
data class ToolCallInfo(
    val id: String,
    val name: String,
    val arguments: String,
    val thoughtSignature: String? = null,
)

fun History.toGroqMessageDto(): OpenAICompatibleChatRequestDto.Message = when (role) {
    History.Role.USER -> {
        val split = attachments.splitForMessage()
        // Images become image_url parts; PDFs are dropped (OpenAI-compatible has no native PDF
        // support, matching the prior behavior). Text files get merged into the text prefix.
        val imageAttachments = split.binaries.filter { it.mimeType.startsWith("image/") }
        val fullText = "${split.textPrefix}$content"
        val messageContent: JsonElement = if (imageAttachments.isEmpty()) {
            JsonPrimitive(fullText)
        } else {
            JsonArray(
                buildList {
                    add(
                        buildJsonObject {
                            put("type", "text")
                            put("text", fullText)
                        },
                    )
                    for (att in imageAttachments) {
                        add(
                            buildJsonObject {
                                put("type", "image_url")
                                put(
                                    "image_url",
                                    buildJsonObject {
                                        put("url", "data:${att.mimeType};base64,${att.data}")
                                    },
                                )
                            },
                        )
                    }
                },
            )
        }
        OpenAICompatibleChatRequestDto.Message(role = "user", content = messageContent)
    }

    History.Role.ASSISTANT -> {
        if (toolCalls != null) {
            OpenAICompatibleChatRequestDto.Message(
                role = "assistant",
                content = if (content.isEmpty()) null else JsonPrimitive(content),
                tool_calls = toolCalls.map { tc ->
                    OpenAICompatibleChatRequestDto.ToolCall(
                        id = tc.id,
                        function = OpenAICompatibleChatRequestDto.FunctionCall(
                            name = tc.name,
                            arguments = tc.arguments,
                        ),
                    )
                },
            )
        } else {
            OpenAICompatibleChatRequestDto.Message(role = "assistant", content = JsonPrimitive(content))
        }
    }

    History.Role.TOOL -> OpenAICompatibleChatRequestDto.Message(
        role = "tool",
        content = JsonPrimitive(content),
        tool_call_id = toolCallId,
    )

    History.Role.TOOL_EXECUTING -> OpenAICompatibleChatRequestDto.Message(role = "assistant", content = JsonPrimitive(content))
}

fun History.toAnthropicContentBlocks(): JsonElement = when (role) {
    History.Role.USER -> {
        val split = attachments.splitForMessage()
        val fullText = "${split.textPrefix}$content"
        if (split.binaries.isEmpty()) {
            JsonPrimitive(fullText)
        } else {
            JsonArray(
                buildList {
                    for (att in split.binaries) {
                        if (att.mimeType == "application/pdf") {
                            add(
                                buildJsonObject {
                                    put("type", "document")
                                    put(
                                        "source",
                                        buildJsonObject {
                                            put("type", "base64")
                                            put("media_type", "application/pdf")
                                            put("data", att.data)
                                        },
                                    )
                                },
                            )
                        } else {
                            add(
                                buildJsonObject {
                                    put("type", "image")
                                    put(
                                        "source",
                                        buildJsonObject {
                                            put("type", "base64")
                                            put("media_type", att.mimeType)
                                            put("data", att.data)
                                        },
                                    )
                                },
                            )
                        }
                    }
                    add(
                        buildJsonObject {
                            put("type", "text")
                            put("text", fullText)
                        },
                    )
                },
            )
        }
    }

    History.Role.ASSISTANT -> {
        if (toolCalls != null) {
            JsonArray(
                buildList {
                    if (content.isNotEmpty()) {
                        add(
                            buildJsonObject {
                                put("type", "text")
                                put("text", content)
                            },
                        )
                    }
                    for (tc in toolCalls) {
                        add(
                            buildJsonObject {
                                put("type", "tool_use")
                                put("id", tc.id)
                                put("name", tc.name)
                                put("input", SharedJson.parseToJsonElement(tc.arguments))
                            },
                        )
                    }
                },
            )
        } else {
            JsonPrimitive(content)
        }
    }

    History.Role.TOOL -> {
        JsonArray(
            listOf(
                buildJsonObject {
                    put("type", "tool_result")
                    put("tool_use_id", toolCallId ?: "")
                    put("content", content)
                },
            ),
        )
    }

    History.Role.TOOL_EXECUTING -> JsonPrimitive(content)
}

private val geminiJsonParser = SharedJson

fun History.toGeminiMessageDto(): GeminiChatRequestDto.Content {
    // Gemini uses "user" for tool responses (functionResponse), not "tool"
    val geminiRole = when (role) {
        History.Role.USER -> "user"

        History.Role.TOOL -> "user"

        // Tool results are sent as user role with functionResponse
        History.Role.ASSISTANT, History.Role.TOOL_EXECUTING -> "model"
    }
    return GeminiChatRequestDto.Content(
        parts = buildList {
            when (role) {
                History.Role.TOOL -> {
                    // Send tool result as functionResponse
                    // Explicitly convert to LinkedHashMap to avoid serialization issues with JsonObject
                    val responseContent: Map<String, JsonElement> = try {
                        val parsed = geminiJsonParser.parseToJsonElement(content)
                        if (parsed is JsonObject) {
                            LinkedHashMap(parsed)
                        } else {
                            mapOf("result" to JsonPrimitive(content))
                        }
                    } catch (e: Exception) {
                        mapOf("result" to JsonPrimitive(content))
                    }
                    add(
                        GeminiChatRequestDto.Part(
                            functionResponse = GeminiChatRequestDto.FunctionResponse(
                                name = toolName ?: "unknown",
                                response = responseContent,
                            ),
                        ),
                    )
                }

                History.Role.ASSISTANT -> {
                    // Handle assistant messages with tool calls
                    if (toolCalls != null) {
                        for (tc in toolCalls) {
                            // Explicitly convert to LinkedHashMap to avoid serialization issues with JsonObject
                            val args: Map<String, JsonElement>? = try {
                                val parsed = geminiJsonParser.parseToJsonElement(tc.arguments)
                                if (parsed is JsonObject) LinkedHashMap(parsed) else null
                            } catch (e: Exception) {
                                null
                            }
                            add(
                                GeminiChatRequestDto.Part(
                                    functionCall = GeminiChatRequestDto.FunctionCall(
                                        name = tc.name,
                                        args = args,
                                    ),
                                    thoughtSignature = tc.thoughtSignature,
                                ),
                            )
                        }
                    }
                    if (content.isNotEmpty()) {
                        add(GeminiChatRequestDto.Part(text = content))
                    }
                }

                else -> {
                    // Regular user message with potential inline data (images / PDFs)
                    val split = attachments.splitForMessage()
                    for (att in split.binaries) {
                        add(
                            GeminiChatRequestDto.Part(
                                inline_data = GeminiChatRequestDto.InlineData(
                                    mime_type = att.mimeType,
                                    data = att.data,
                                ),
                            ),
                        )
                    }
                    add(GeminiChatRequestDto.Part(text = "${split.textPrefix}$content"))
                }
            }
        },
        role = geminiRole,
    )
}
