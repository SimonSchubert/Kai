package com.inspiredandroid.kai.ui.chat.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.ui.darkPurple
import com.inspiredandroid.kai.ui.lightPurple
import com.inspiredandroid.kai.ui.outlineTextFieldColors
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.name
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.ic_add
import kai.composeapp.generated.resources.ic_file
import kai.composeapp.generated.resources.ic_image
import kai.composeapp.generated.resources.ic_up
import kai.composeapp.generated.resources.prompt_ask_question
import kai.composeapp.generated.resources.prompt_pick_media
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
fun QuestionInput(
    file: PlatformFile?,
    setFile: (PlatformFile?) -> Unit,
    ask: (String) -> Unit,
    allowFileAttachment: Boolean,
) {
    if (file != null) {
        val icon = when (file.extension) {
            "jpg", "jpeg", "png", "gif" -> Res.drawable.ic_image
            else -> Res.drawable.ic_file
        }
        SuggestionChip(
            modifier = Modifier
                .padding(start = 16.dp)
                .pointerHoverIcon(PointerIcon.Hand),
            onClick = { setFile(null) },
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
                        modifier = Modifier
                            .pointerHoverIcon(PointerIcon.Hand),
                        text = file.name,
                    )
                }
            },
        )
    }

    var textState by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }

    fun submitQuestion() {
        val text = textState.text
        if (text.isNotBlank()) {
            ask(text.trim())
            textState = TextFieldValue("")
            setFile(null)
        }
    }

    val filePickerLauncher = rememberFilePickerLauncher(
        type = FileKitType.ImageAndVideo,
        mode = FileKitMode.Single,
        title = stringResource(Res.string.prompt_pick_media),
    ) {
        setFile(it)
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
            .border(
                BorderStroke(width = 2.dp, brush = Brush.horizontalGradient(listOf(darkPurple, lightPurple))),
                shape = RoundedCornerShape(28.dp),
            )
            .onKeyEvent { event ->
                // Explicitly name 'event' for clarity
                if (event.key.keyCode == Key.Enter.keyCode && event.type == KeyEventType.KeyUp) {
                    if (event.isShiftPressed) {
                        val currentText = textState.text
                        val selection = textState.selection
                        val textToInsert = "\n"

                        // Ensure selection is valid and ordered
                        val start = minOf(selection.start, selection.end).coerceIn(0, currentText.length)
                        val end = maxOf(selection.start, selection.end).coerceIn(0, currentText.length)

                        val newText = currentText.replaceRange(start, end, textToInsert)
                        textState = TextFieldValue(
                            text = newText,
                            selection = TextRange(start + textToInsert.length),
                        )
                    } else {
                        submitQuestion()
                    }
                    return@onKeyEvent true
                }
                return@onKeyEvent false
            },
        colors = outlineTextFieldColors(),
        placeholder = {
            Text(
                stringResource(Res.string.prompt_ask_question),
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        trailingIcon = if (textState.text.isNotBlank()) {
            { TrailingIcon(onClick = { submitQuestion() }) }
        } else {
            null
        },
        keyboardActions = KeyboardActions(onSend = {
            submitQuestion()
        }),
        leadingIcon = if (allowFileAttachment) {
            {
                LeadingIcon(onClick = {
                    filePickerLauncher.launch()
                })
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
    )
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
private fun TrailingIcon(
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(end = 6.dp)
            .size(42.dp)
            .clip(CircleShape)
            .background(brush = Brush.horizontalGradient(listOf(darkPurple, lightPurple)), CircleShape)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable {
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            vectorResource(Res.drawable.ic_up),
            modifier = Modifier.size(32.dp),
            contentDescription = null,
            tint = Color.White,
        )
    }
}

@Composable
private fun LeadingIcon(
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(start = 6.dp)
            .size(42.dp)
            .clip(CircleShape)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable {
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            vectorResource(Res.drawable.ic_add),
            modifier = Modifier.size(32.dp),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
        )
    }
}
