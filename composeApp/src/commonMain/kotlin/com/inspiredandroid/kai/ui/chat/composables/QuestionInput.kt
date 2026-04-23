package com.inspiredandroid.kai.ui.chat.composables

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.Platform
import com.inspiredandroid.kai.currentPlatform
import com.inspiredandroid.kai.data.ServiceEntry
import com.inspiredandroid.kai.data.imageExtensions
import com.inspiredandroid.kai.ui.gradientBrush
import com.inspiredandroid.kai.ui.handCursor
import com.inspiredandroid.kai.ui.outlineTextFieldColors
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.name
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.ic_attach
import kai.composeapp.generated.resources.ic_file
import kai.composeapp.generated.resources.ic_image
import kai.composeapp.generated.resources.ic_stop
import kai.composeapp.generated.resources.ic_up
import kai.composeapp.generated.resources.prompt_ask_question
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuestionInput(
    files: ImmutableList<PlatformFile>,
    addFile: (PlatformFile) -> Unit,
    removeFile: (PlatformFile) -> Unit,
    ask: (String) -> Unit,
    supportedFileExtensions: ImmutableList<String>,
    isLoading: Boolean = false,
    cancel: () -> Unit = {},
    availableServices: ImmutableList<ServiceEntry> = persistentListOf(),
    onSelectService: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (files.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (file in files) {
                    val icon = if (file.extension.lowercase() in imageExtensions) {
                        Res.drawable.ic_image
                    } else {
                        Res.drawable.ic_file
                    }
                    SuggestionChip(
                        modifier = Modifier.handCursor(),
                        onClick = { removeFile(file) },
                        icon = {
                            Icon(
                                modifier = Modifier.size(16.dp),
                                painter = painterResource(icon),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        },
                        label = {
                            DisableSelection {
                                Text(
                                    modifier = Modifier.handCursor(),
                                    text = truncateFileName(file.name),
                                )
                            }
                        },
                    )
                }
            }
        }

        var textState by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }

        fun submitQuestion() {
            val text = textState.text
            if (text.isNotBlank()) {
                ask(text.trim())
                textState = TextFieldValue("")
            }
        }

        val allowFileAttachment = supportedFileExtensions.isNotEmpty()
        val filePickerLauncher = if (allowFileAttachment) {
            rememberFilePickerLauncher(
                type = FileKitType.File(extensions = supportedFileExtensions),
            ) { file ->
                if (file != null) addFile(file)
            }
        } else {
            null
        }

        val focusRequester = remember { FocusRequester() }
        TextField(
            value = textState,
            onValueChange = {
                textState = it
            },
            modifier = Modifier
                .focusRequester(focusRequester)
                .padding(16.dp)
                .heightIn(max = 120.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(
                    BorderStroke(width = 2.dp, brush = gradientBrush),
                    shape = RoundedCornerShape(28.dp),
                )
                .onPreviewKeyEvent { event ->
                    // Only handle hardware keyboard on desktop/web platforms
                    if (currentPlatform !is Platform.Mobile && event.key.keyCode == Key.Enter.keyCode && event.type == KeyEventType.KeyDown) {
                        if (event.isShiftPressed) {
                            // Shift+Enter -> manually insert newline
                            val currentText = textState.text
                            val selection = textState.selection
                            val start = minOf(selection.start, selection.end).coerceIn(0, currentText.length)
                            val end = maxOf(selection.start, selection.end).coerceIn(0, currentText.length)

                            val newText = currentText.replaceRange(start, end, "\n")
                            textState = TextFieldValue(
                                text = newText,
                                selection = TextRange(start + 1),
                            )
                            return@onPreviewKeyEvent true
                        } else {
                            // Enter without Shift -> send message and consume event
                            submitQuestion()
                            return@onPreviewKeyEvent true
                        }
                    }
                    return@onPreviewKeyEvent false
                },
            colors = outlineTextFieldColors(),
            placeholder = {
                Text(
                    stringResource(Res.string.prompt_ask_question),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            },
            trailingIcon = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (availableServices.size > 1) {
                        ServiceSelector(
                            services = availableServices,
                            onSelectService = onSelectService,
                        )
                    }
                    if (isLoading) {
                        TrailingIcon(icon = Res.drawable.ic_stop, onClick = cancel, isPulsing = true)
                    } else if (textState.text.isNotBlank()) {
                        TrailingIcon(icon = Res.drawable.ic_up, onClick = { submitQuestion() })
                    }
                }
            },
            keyboardActions = if (currentPlatform !is Platform.Mobile) {
                KeyboardActions(onSend = { submitQuestion() })
            } else {
                KeyboardActions() // No keyboard send action on mobile
            },
            leadingIcon = if (filePickerLauncher != null) {
                {
                    CircleIconButton(
                        icon = vectorResource(Res.drawable.ic_attach),
                        onClick = { filePickerLauncher.launch() },
                        modifier = Modifier.padding(start = 7.dp),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(
                imeAction = if (currentPlatform is Platform.Mobile) ImeAction.Default else ImeAction.Send,
            ),
        )
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}

/**
 * Shortens a filename that is too long to display in a chip. Returns the first [maxChars]
 * characters of the base name followed by `…` and the original extension, so the user still
 * recognizes the file type. Short names are returned unchanged.
 */
internal fun truncateFileName(name: String, maxChars: Int = 16): String {
    if (name.length <= maxChars) return name
    val dotIndex = name.lastIndexOf('.')
    return if (dotIndex > 0 && dotIndex < name.length - 1) {
        val base = name.substring(0, dotIndex)
        val ext = name.substring(dotIndex) // includes the dot
        val keep = (maxChars - ext.length - 1).coerceAtLeast(1)
        "${base.take(keep)}…$ext"
    } else {
        "${name.take(maxChars - 1)}…"
    }
}

@Composable
internal fun TrailingIcon(
    icon: org.jetbrains.compose.resources.DrawableResource = Res.drawable.ic_up,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPulsing: Boolean = false,
) {
    val pulseModifier = if (isPulsing) {
        val infiniteTransition = rememberInfiniteTransition()
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        )
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.7f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        )
        Modifier.graphicsLayer {
            scaleX = pulseScale
            scaleY = pulseScale
            alpha = pulseAlpha
        }
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(brush = gradientBrush, CircleShape)
            .handCursor()
            .clickable {
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            vectorResource(icon),
            modifier = Modifier.size(32.dp).then(pulseModifier),
            contentDescription = null,
            tint = Color.White,
        )
    }
}

@Composable
internal fun CircleIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable { onClick() }
            .handCursor(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            modifier = Modifier.size(24.dp),
            contentDescription = null,
            tint = tint,
        )
    }
}
