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
import com.inspiredandroid.kai.Version
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.clear_chat_content_description
import kai.composeapp.generated.resources.ic_delete_forever
import kai.composeapp.generated.resources.ic_settings
import kai.composeapp.generated.resources.ic_volume_off
import kai.composeapp.generated.resources.ic_volume_up
import kai.composeapp.generated.resources.settings_content_description
import kai.composeapp.generated.resources.toggle_speech_output_content_description
import nl.marc_apps.tts.TextToSpeechInstance
import org.jetbrains.compose.resources.stringResource
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
                    contentDescription = stringResource(Res.string.clear_chat_content_description),
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
                    contentDescription = stringResource(Res.string.toggle_speech_output_content_description),
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
                contentDescription = stringResource(Res.string.settings_content_description),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
