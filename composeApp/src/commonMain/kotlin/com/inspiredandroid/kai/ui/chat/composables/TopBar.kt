package com.inspiredandroid.kai.ui.chat.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.ui.chat.ChatActions
import com.inspiredandroid.kai.ui.handCursor
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.chat_history_content_description
import kai.composeapp.generated.resources.ic_add
import kai.composeapp.generated.resources.ic_history
import kai.composeapp.generated.resources.ic_settings
import kai.composeapp.generated.resources.ic_volume_off
import kai.composeapp.generated.resources.ic_volume_up
import kai.composeapp.generated.resources.new_chat_content_description
import kai.composeapp.generated.resources.settings_content_description
import kai.composeapp.generated.resources.toggle_speech_output_content_description
import nl.marc_apps.tts.TextToSpeechInstance
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun TopBar(
    textToSpeech: TextToSpeechInstance? = null,
    isSpeechOutputEnabled: Boolean,
    isSpeaking: Boolean,
    actions: ChatActions,
    isChatHistoryEmpty: Boolean,
    hasSavedConversations: Boolean,
    onNavigateToSettings: () -> Unit,
    onShowHistory: () -> Unit,
    navigationTabBar: (@Composable () -> Unit)? = null,
) {
    if (navigationTabBar != null) {
        Box(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp),
        ) {
            Row(modifier = Modifier.align(Alignment.CenterStart)) {
                LeadingButtons(textToSpeech, isSpeechOutputEnabled, isSpeaking, actions, isChatHistoryEmpty, hasSavedConversations, onShowHistory)
            }
            Box(modifier = Modifier.align(Alignment.Center)) {
                navigationTabBar()
            }
            Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                if (textToSpeech != null) {
                    SpeechToggleButton(textToSpeech, isSpeechOutputEnabled, isSpeaking, actions)
                }
            }
        }
    } else {
        Row {
            LeadingButtons(textToSpeech, isSpeechOutputEnabled, isSpeaking, actions, isChatHistoryEmpty, hasSavedConversations, onShowHistory)
            Spacer(Modifier.weight(1f))
            if (textToSpeech != null) {
                SpeechToggleButton(textToSpeech, isSpeechOutputEnabled, isSpeaking, actions)
            }
            IconButton(
                modifier = Modifier.handCursor(),
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
}

@Composable
private fun LeadingButtons(
    textToSpeech: TextToSpeechInstance?,
    isSpeechOutputEnabled: Boolean,
    isSpeaking: Boolean,
    actions: ChatActions,
    isChatHistoryEmpty: Boolean,
    hasSavedConversations: Boolean,
    onShowHistory: () -> Unit,
) {
    if (hasSavedConversations) {
        IconButton(
            modifier = Modifier.handCursor(),
            onClick = onShowHistory,
        ) {
            Icon(
                imageVector = vectorResource(Res.drawable.ic_history),
                contentDescription = stringResource(Res.string.chat_history_content_description),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
    if (!isChatHistoryEmpty) {
        IconButton(
            modifier = Modifier.handCursor(),
            onClick = {
                if (isSpeechOutputEnabled && isSpeaking) {
                    actions.setIsSpeaking(false, "")
                    textToSpeech?.stop()
                }
                actions.startNewChat()
            },
        ) {
            Icon(
                imageVector = vectorResource(Res.drawable.ic_add),
                contentDescription = stringResource(Res.string.new_chat_content_description),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun SpeechToggleButton(
    textToSpeech: TextToSpeechInstance,
    isSpeechOutputEnabled: Boolean,
    isSpeaking: Boolean,
    actions: ChatActions,
) {
    IconButton(
        modifier = Modifier.handCursor(),
        onClick = {
            if (isSpeechOutputEnabled && isSpeaking) {
                actions.setIsSpeaking(false, "")
                textToSpeech.stop()
            }
            actions.toggleSpeechOutput()
        },
    ) {
        Icon(
            imageVector = if (isSpeechOutputEnabled) {
                vectorResource(Res.drawable.ic_volume_up)
            } else {
                vectorResource(Res.drawable.ic_volume_off)
            },
            contentDescription = stringResource(Res.string.toggle_speech_output_content_description),
            tint = MaterialTheme.colorScheme.onBackground,
        )
    }
}
