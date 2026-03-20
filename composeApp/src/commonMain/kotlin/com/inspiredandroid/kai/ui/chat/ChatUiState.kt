@file:OptIn(ExperimentalUuidApi::class, ExperimentalEncodingApi::class)

package com.inspiredandroid.kai.ui.chat

import androidx.compose.runtime.Immutable
import com.inspiredandroid.kai.data.ServiceEntry
import com.inspiredandroid.kai.data.SharedJson
import com.inspiredandroid.kai.network.UiError
import com.inspiredandroid.kai.network.dtos.anthropic.AnthropicChatRequestDto
import com.inspiredandroid.kai.network.dtos.gemini.GeminiChatRequestDto
import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Immutable
data class ConversationSummary(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val isHeartbeat: Boolean = false,
)

@Immutable
data class ChatUiState(
    val actions: ChatActions,
    val history: List<History> = emptyList(),
    val isSpeechOutputEnabled: Boolean = false,
    val isLoading: Boolean = false,
    val error: UiError? = null,
    val showPrivacyInfo: Boolean = false,
    val allowFileAttachment: Boolean = false,
    val isSpeaking: Boolean = false,
    val isSpeakingContentId: String = "",
    val file: PlatformFile? = null,
    val availableServices: List<ServiceEntry> = emptyList(),
    val savedConversations: List<ConversationSummary> = emptyList(),
    val currentConversationId: String? = null,
    val hasUnreadHeartbeat: Boolean = false,
    val snackbarMessage: String? = null,
    val pendingConversationDeletion: String? = null,
) {
    val heartbeatConversationId: String?
        get() = savedConversations.firstOrNull { it.isHeartbeat }?.id
}

@Immutable
data class History(
    val id: String = Uuid.random().toString(),
    val role: Role,
    val content: String,
    val mimeType: String? = null,
    val data: String? = null,
    val fileName: String? = null,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val toolCalls: List<ToolCallInfo>? = null,
    val isThinking: Boolean = false,
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
        val messageContent: JsonElement = if (data != null && mimeType != null) {
            when {
                mimeType.startsWith("text/") || mimeType == "application/json" || mimeType == "application/xml" || mimeType == "application/javascript" || mimeType == "application/x-yaml" || mimeType == "application/yaml" -> {
                    val decoded = Base64.decode(data).decodeToString()
                    val header = if (fileName != null) "--- $fileName ---\n" else ""
                    JsonPrimitive("$header$decoded\n\n$content")
                }

                mimeType == "application/pdf" -> {
                    JsonArray(
                        listOf(
                            buildJsonObject {
                                put("type", "file")
                                put(
                                    "file",
                                    buildJsonObject {
                                        put("file_data", "data:application/pdf;base64,$data")
                                    },
                                )
                            },
                            buildJsonObject {
                                put("type", "text")
                                put("text", content)
                            },
                        ),
                    )
                }

                else -> {
                    JsonArray(
                        listOf(
                            buildJsonObject {
                                put("type", "text")
                                put("text", content)
                            },
                            buildJsonObject {
                                put("type", "image_url")
                                put(
                                    "image_url",
                                    buildJsonObject {
                                        put("url", "data:$mimeType;base64,$data")
                                    },
                                )
                            },
                        ),
                    )
                }
            }
        } else {
            JsonPrimitive(content)
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
        if (data != null && mimeType != null) {
            when {
                mimeType.startsWith("text/") || mimeType == "application/json" || mimeType == "application/xml" || mimeType == "application/javascript" || mimeType == "application/x-yaml" || mimeType == "application/yaml" -> {
                    val decoded = Base64.decode(data).decodeToString()
                    val header = if (fileName != null) "--- $fileName ---\n" else ""
                    JsonPrimitive("$header$decoded\n\n$content")
                }

                mimeType == "application/pdf" -> {
                    JsonArray(
                        listOf(
                            buildJsonObject {
                                put("type", "document")
                                put(
                                    "source",
                                    buildJsonObject {
                                        put("type", "base64")
                                        put("media_type", "application/pdf")
                                        put("data", data)
                                    },
                                )
                            },
                            buildJsonObject {
                                put("type", "text")
                                put("text", content)
                            },
                        ),
                    )
                }

                else -> {
                    JsonArray(
                        listOf(
                            buildJsonObject {
                                put("type", "image")
                                put(
                                    "source",
                                    buildJsonObject {
                                        put("type", "base64")
                                        put("media_type", mimeType)
                                        put("data", data)
                                    },
                                )
                            },
                            buildJsonObject {
                                put("type", "text")
                                put("text", content)
                            },
                        ),
                    )
                }
            }
        } else {
            JsonPrimitive(content)
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
                    // Regular user message with potential inline data
                    if (data != null && mimeType != null) {
                        val isText = mimeType.startsWith("text/") || mimeType == "application/json" || mimeType == "application/xml" || mimeType == "application/javascript" || mimeType == "application/x-yaml" || mimeType == "application/yaml"
                        if (isText) {
                            val decoded = Base64.decode(data).decodeToString()
                            val header = if (fileName != null) "--- $fileName ---\n" else ""
                            add(GeminiChatRequestDto.Part(text = "$header$decoded\n\n$content"))
                        } else {
                            val inlineData = GeminiChatRequestDto.InlineData(mime_type = mimeType, data = data)
                            add(GeminiChatRequestDto.Part(inline_data = inlineData))
                            add(GeminiChatRequestDto.Part(text = content))
                        }
                    } else {
                        add(GeminiChatRequestDto.Part(text = content))
                    }
                }
            }
        },
        role = geminiRole,
    )
}
