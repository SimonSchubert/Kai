package com.inspiredandroid.kai.ui.chat.composables

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.getBackgroundDispatcher
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.components.MarkdownComponent
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.rememberMarkdownState
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.bot_message_copy_content_description
import kai.composeapp.generated.resources.bot_message_flag_content_description
import kai.composeapp.generated.resources.bot_message_regenerate_content_description
import kai.composeapp.generated.resources.bot_message_speech_content_description
import kai.composeapp.generated.resources.ic_copy
import kai.composeapp.generated.resources.ic_flag
import kai.composeapp.generated.resources.ic_refresh
import kai.composeapp.generated.resources.ic_stop
import kai.composeapp.generated.resources.ic_volume_up
import kotlinx.coroutines.launch
import nl.marc_apps.tts.TextToSpeechInstance
import nl.marc_apps.tts.errors.TextToSpeechSynthesisInterruptedError
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun BotMessage(
    message: String,
    textToSpeech: TextToSpeechInstance?,
    isSpeaking: Boolean,
    setIsSpeaking: (Boolean) -> Unit,
    isOpenClaw: Boolean = false,
    onRegenerate: (() -> Unit)? = null,
) {
    val isInspectionMode = LocalInspectionMode.current
    val markdownState = rememberMarkdownState(message, immediate = isInspectionMode)
    Markdown(
        markdownState,
        imageTransformer = Coil3ImageTransformerImpl,
        components = markdownComponents(
            codeBlock = highlightedCodeBlock,
            codeFence = highlightedCodeFence,
        ),
        typography = smallerMarkdownTypography(),
        modifier = Modifier.fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
    )
    Row(Modifier.padding(horizontal = 8.dp)) {
        if (textToSpeech != null) {
            val componentScope = rememberCoroutineScope()
            SmallIconButton( // This is now imported from the composables directory
                iconResource = if (isSpeaking) Res.drawable.ic_stop else Res.drawable.ic_volume_up,
                contentDescription = stringResource(Res.string.bot_message_speech_content_description),
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
                            } catch (ignore: TextToSpeechSynthesisInterruptedError) {
                                // Expected interruption - no action needed
                            } catch (e: Exception) {
                                // Handle TTS errors gracefully (service failure, audio issues, etc.)
                            }
                            setIsSpeaking(false)
                        }
                    }
                },
            )
        }
        val clipboardManager = LocalClipboardManager.current
        SmallIconButton(
            iconResource = Res.drawable.ic_copy,
            contentDescription = stringResource(Res.string.bot_message_copy_content_description),
            onClick = {
                clipboardManager.setText(
                    annotatedString = buildAnnotatedString {
                        append(message)
                    },
                )
            },
        )
        if (!isOpenClaw) {
            val uriHandler = LocalUriHandler.current
            SmallIconButton(
                iconResource = Res.drawable.ic_flag,
                contentDescription = stringResource(Res.string.bot_message_flag_content_description),
                onClick = {
                    uriHandler.openUri("https://form.jotform.com/250014908169355")
                },
            )
        }
        if (onRegenerate != null) {
            SmallIconButton(
                iconResource = Res.drawable.ic_refresh,
                contentDescription = stringResource(Res.string.bot_message_regenerate_content_description),
                onClick = onRegenerate,
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun smallerMarkdownTypography() = markdownTypography(
    h1 = MaterialTheme.typography.headlineSmall,
    h2 = MaterialTheme.typography.titleLarge,
    h3 = MaterialTheme.typography.titleMedium,
    h4 = MaterialTheme.typography.titleSmall,
    h5 = MaterialTheme.typography.bodyLarge,
    h6 = MaterialTheme.typography.bodyMedium,
)

val highlightedCodeFence: MarkdownComponent = {
    MarkdownHighlightedCodeFence(content = it.content, node = it.node, style = it.typography.code, showHeader = true)
}

val highlightedCodeBlock: MarkdownComponent = {
    MarkdownHighlightedCodeBlock(content = it.content, node = it.node, style = it.typography.code, showHeader = true)
}
