@file:OptIn(ExperimentalUuidApi::class)

package com.inspiredandroid.kai.ui.chat

import androidx.compose.runtime.Immutable
import com.inspiredandroid.kai.network.dtos.gemini.GeminiChatRequestDto
import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Immutable
data class ChatUiState(
    val actions: ChatActions,
    val history: List<History> = emptyList(),
    val isSpeechOutputEnabled: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showPrivacyInfo: Boolean = false,
    val allowFileAttachment: Boolean = false,
    val isSpeaking: Boolean = false,
    val isSpeakingContentId: String = "",
    val file: PlatformFile? = null,
    val hasSavedConversations: Boolean = false,
    val shouldScrollToBottom: Boolean = false,
)

@Immutable
data class History(
    val id: String = Uuid.random().toString(),
    val role: Role,
    val content: String,
    val mimeType: String? = null,
    val data: String? = null,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val toolCalls: List<ToolCallInfo>? = null,
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
    History.Role.USER -> OpenAICompatibleChatRequestDto.Message(role = "user", content = content)

    History.Role.ASSISTANT -> {
        if (toolCalls != null) {
            OpenAICompatibleChatRequestDto.Message(
                role = "assistant",
                content = content.ifEmpty { null },
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
            OpenAICompatibleChatRequestDto.Message(role = "assistant", content = content)
        }
    }

    History.Role.TOOL -> OpenAICompatibleChatRequestDto.Message(
        role = "tool",
        content = content,
        tool_call_id = toolCallId,
    )

    History.Role.TOOL_EXECUTING -> OpenAICompatibleChatRequestDto.Message(role = "assistant", content = content)
}

private val geminiJsonParser = Json { ignoreUnknownKeys = true }

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
                    val inlineData = if (data != null && mimeType != null) {
                        GeminiChatRequestDto.InlineData(mime_type = mimeType, data = data)
                    } else {
                        null
                    }
                    if (inlineData != null) {
                        add(GeminiChatRequestDto.Part(inline_data = inlineData))
                    }
                    add(GeminiChatRequestDto.Part(text = content))
                }
            }
        },
        role = geminiRole,
    )
}
