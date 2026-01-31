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
    val setFile: (PlatformFile?) -> Unit,
    val startNewChat: () -> Unit,
    val resetScrollFlag: () -> Unit,
)
