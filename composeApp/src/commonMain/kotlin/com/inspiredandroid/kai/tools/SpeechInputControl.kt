package com.inspiredandroid.kai.tools

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform speech-input control. Android provides local recognition; other targets
 * intentionally render nothing until they have a native, privacy-equivalent backend.
 */
@Composable
expect fun SpeechInputControl(
    onTranscript: (String) -> Unit,
    target: VoiceInputTarget,
    modifier: Modifier,
)
