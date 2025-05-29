package com.inspiredandroid.kai.ui.chat.composables

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.ic_delete_forever
import kai.composeapp.generated.resources.ic_settings
import kai.composeapp.generated.resources.ic_volume_off
import kai.composeapp.generated.resources.ic_volume_up
import nl.marc_apps.tts.TextToSpeechInstance
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun TopBar(
    textToSpeech: TextToSpeechInstance? = null,
    isLoading: Boolean,
    isSpeechOutputEnabled: Boolean,
    isSpeaking: Boolean,
    setIsSpeaking: (Boolean, String) -> Unit,
    isChatHistoryEmpty: Boolean,
    clearHistory: () -> Unit,
    toggleSpeechOutput: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    Row {
        if (!isChatHistoryEmpty) {
            IconButton(
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                onClick = {
                    if (isSpeechOutputEnabled && isSpeaking) {
                        setIsSpeaking(false, "")
                        textToSpeech?.stop()
                    }
                    clearHistory()
                },
                enabled = !isLoading,
            ) {
                Icon(
                    imageVector = vectorResource(Res.drawable.ic_delete_forever),
                    contentDescription = "Clear chat",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        if (textToSpeech != null) {
            IconButton(
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                onClick = {
                    if (isSpeechOutputEnabled && isSpeaking) {
                        setIsSpeaking(false, "")
                        textToSpeech.stop()
                    }
                    toggleSpeechOutput()
                },
            ) {
                Icon(
                    imageVector =
                    if (isSpeechOutputEnabled) {
                        vectorResource(Res.drawable.ic_volume_up)
                    } else {
                        vectorResource(Res.drawable.ic_volume_off)
                    },
                    contentDescription = "Toggle speech output",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        IconButton(
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            onClick = onNavigateToSettings,
        ) {
            Icon(
                imageVector = vectorResource(Res.drawable.ic_settings),
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
