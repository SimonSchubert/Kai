package com.inspiredandroid.kai.ui.chat.composables

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.getBackgroundDispatcher
import com.inspiredandroid.kai.openUrl
import com.mikepenz.markdown.m3.Markdown
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.ic_copy
import kai.composeapp.generated.resources.ic_flag
import kai.composeapp.generated.resources.ic_stop
import kai.composeapp.generated.resources.ic_volume_up
import kotlinx.coroutines.launch
// import com.inspiredandroid.kai.ui.chat.SmallIconButton // This line is removed
import nl.marc_apps.tts.TextToSpeechInstance
import nl.marc_apps.tts.errors.TextToSpeechSynthesisInterruptedError

@Composable
internal fun BotMessage(message: String, textToSpeech: TextToSpeechInstance?, isSpeaking: Boolean, setIsSpeaking: (Boolean) -> Unit) {
    Markdown(
        message,
        modifier = Modifier.fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
    )
    Row(Modifier.padding(horizontal = 8.dp)) {
        if (textToSpeech != null) {
            val componentScope = rememberCoroutineScope()
            SmallIconButton( // This is now imported from the composables directory
                iconResource = if (isSpeaking) Res.drawable.ic_stop else Res.drawable.ic_volume_up,
                contentDescription = "Speech",
                onClick = {
                    componentScope.launch(getBackgroundDispatcher()) {
                        textToSpeech.stop()
                        if (isSpeaking) {
                            setIsSpeaking(false)
                        } else {
                            setIsSpeaking(true)
                            try {
                                textToSpeech.say(
                                    text = message,
                                )
                            } catch (ignore: TextToSpeechSynthesisInterruptedError) { }
                            setIsSpeaking(false)
                        }
                    }
                },
            )
        }
        val clipboardManager = LocalClipboardManager.current
        SmallIconButton( // This is now imported from the composables directory
            iconResource = Res.drawable.ic_copy,
            contentDescription = "Copy",
            onClick = {
                clipboardManager.setText(
                    annotatedString = buildAnnotatedString {
                        append(message)
                    },
                )
            },
        )
        SmallIconButton( // This is now imported from the composables directory
            iconResource = Res.drawable.ic_flag,
            contentDescription = "Flag content",
            onClick = {
                openUrl("https://form.jotform.com/250014908169355")
            },
        )
        Spacer(Modifier.weight(1f))
    }
}
