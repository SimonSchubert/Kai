package com.inspiredandroid.kai.ui.chat

import androidx.compose.runtime.Immutable
import io.github.vinceglb.filekit.PlatformFile

@Immutable
data class ChatActions(
    val ask: (String) -> Unit,
    val toggleSpeechOutput: () -> Unit,
    val retry: () -> Unit,
    val clearHistory: () -> Unit,
    val setIsSpeaking: (Boolean, String) -> Unit,
    val addFile: (PlatformFile) -> Unit,
    val removeFile: (PlatformFile) -> Unit,
    val startNewChat: () -> Unit,
    val regenerate: () -> Unit,
    val cancel: () -> Unit,
    val selectService: (String) -> Unit,
    val loadConversation: (String) -> Unit,
    val deleteConversation: (String) -> Unit,
    val clearUnreadHeartbeat: () -> Unit,
    val clearSnackbar: () -> Unit,
    val undoDeleteConversation: () -> Unit,
    val submitUiCallback: (event: String, data: Map<String, String>) -> Unit,
    val resubmit: (messageId: String, event: String, data: Map<String, String>) -> Unit,
    val enterInteractiveMode: () -> Unit,
    val exitInteractiveMode: () -> Unit,
    val goBackInteractiveMode: () -> Unit,
    val sendSmsDraft: (String) -> Unit,
    val discardSmsDraft: (String) -> Unit,
)
