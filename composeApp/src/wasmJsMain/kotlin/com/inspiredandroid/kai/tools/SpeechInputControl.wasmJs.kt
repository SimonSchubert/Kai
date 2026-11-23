package com.inspiredandroid.kai.tools

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun SpeechInputControl(
    onTranscript: (String) -> Unit,
    target: VoiceInputTarget,
    modifier: Modifier,
) = Unit
