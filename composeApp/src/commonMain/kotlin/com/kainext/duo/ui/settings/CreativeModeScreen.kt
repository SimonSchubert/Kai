package com.kainext.duo.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kainext.duo.skills.CreativeModeManager
import com.kainext.duo.skills.CreativeModeManager.Stage
import com.kainext.duo.ui.KaiOutlinedTextField
import com.kainext.duo.ui.components.VerticalScrollbarForScroll
import com.kainext.duo.ui.handCursor
import com.kainext.duo.ui.kaiAdaptiveCardBorder
import com.kainext.duo.ui.kaiAdaptiveCardColors
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.settings_creative_mode
import kai.composeapp.generated.resources.settings_creative_mode_cancel
import kai.composeapp.generated.resources.settings_creative_mode_collecting
import kai.composeapp.generated.resources.settings_creative_mode_continue
import kai.composeapp.generated.resources.settings_creative_mode_draft_name
import kai.composeapp.generated.resources.settings_creative_mode_draft_desc
import kai.composeapp.generated.resources.settings_creative_mode_draft_body
import kai.composeapp.generated.resources.settings_creative_mode_idle_prompt
import kai.composeapp.generated.resources.settings_creative_mode_preview
import kai.composeapp.generated.resources.settings_creative_mode_ready
import kai.composeapp.generated.resources.settings_creative_mode_saved
import kai.composeapp.generated.resources.settings_creative_mode_save
import kai.composeapp.generated.resources.settings_creative_mode_start
import kai.composeapp.generated.resources.settings_creative_mode_body_hint

/**
 * 对话式“创造模式”底部弹窗：用户与子智能共同定义新 skill，
 * 逐步拼装名称/描述/正文，预览后写入 ~/skills/<id>/SKILL.md。
 */
@Composable
internal fun CreativeModeScreen(
    manager: CreativeModeManager,
    onDismiss: () -> Unit,
    onSkillCreated: (String) -> Unit,
) {
    val stage by manager.stage.collectAsState()
    val draft by manager.draft.collectAsState()
    val message by manager.message.collectAsState()
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var name by remember { mutableStateOf(draft?.name ?: "") }
    var description by remember { mutableStateOf(draft?.description ?: "") }
    var body by remember { mutableStateOf(draft?.body ?: "") }

    val canSave = stage == Stage.ReadyToSave || stage == Stage.Collecting
    // The skill id is derived from what the user typed instead of being demanded
    // verbatim: "Code Review" becomes "code-review". Requiring the typed name to
    // already be a slug left Save permanently disabled for any capital letter or
    // space, which is most names a person would write.
    val skillId = name.trim().lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(64)
        .trim('-')
    val nameValid = skillId.isNotEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = stringResource(Res.string.settings_creative_mode),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))

            when (stage) {
                Stage.Idle -> {
                    Text(
                        text = stringResource(Res.string.settings_creative_mode_idle_prompt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { manager.start(); name = ""; description = ""; body = "" },
                        enabled = true,
                        modifier = Modifier.fillMaxWidth().handCursor(),
                    ) {
                        Text(stringResource(Res.string.settings_creative_mode_start))
                    }
                }

                Stage.Collecting -> {
                    Text(
                        text = stringResource(Res.string.settings_creative_mode_collecting),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    message?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    KaiOutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(Res.string.settings_creative_mode_draft_name)) },
                        singleLine = true,
                        enabled = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    KaiOutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(Res.string.settings_creative_mode_draft_desc)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    KaiOutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text(stringResource(Res.string.settings_creative_mode_draft_body)) },
                        singleLine = false,
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    if (!nameValid) {
                        Text(
                            text = "名称里至少要有一个字母或数字。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(8.dp))
                    } else if (skillId != name.trim()) {
                        // The id is generated, so show what it will become instead
                        // of leaving the user to guess why it differs from the name.
                        Text(
                            text = "ID：$skillId",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                    ) {
                        TextButton(onClick = onDismiss, modifier = Modifier.handCursor()) {
                            Text(stringResource(Res.string.settings_creative_mode_cancel))
                        }
                        Button(
                            onClick = {
                                manager.setDraft(skillId, description, body)
                            },
                            enabled = nameValid && description.isNotBlank(),
                            modifier = Modifier.handCursor(),
                        ) {
                            Text(stringResource(Res.string.settings_creative_mode_ready))
                        }
                    }
                }

                Stage.ReadyToSave -> {
                    Text(
                        text = stringResource(Res.string.settings_creative_mode_ready),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    message?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(Res.string.settings_creative_mode_preview),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    val preview = manager.preview() ?: ""
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                        maxLines = Int.MAX_VALUE,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                    ) {
                        TextButton(onClick = { manager.cancel(); onDismiss() }, modifier = Modifier.handCursor()) {
                            Text(stringResource(Res.string.settings_creative_mode_cancel))
                        }
                        IconButton(onClick = { manager.setDraft(skillId, description, body) }, modifier = Modifier.handCursor()) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val result = manager.save()
                                    if (result.isSuccess) {
                                        result.getOrNull()?.let { onSkillCreated(it.id) }
                                        onDismiss()
                                    }
                                }
                            },
                            enabled = nameValid && description.isNotBlank(),
                            modifier = Modifier.handCursor(),
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Filled.Save,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(Res.string.settings_creative_mode_save))
                        }
                    }
                }

                Stage.Saved -> {
                    Text(
                        text = stringResource(Res.string.settings_creative_mode_saved),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    message?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { manager.cancel(); onDismiss() },
                        modifier = Modifier.fillMaxWidth().handCursor(),
                    ) {
                        Text(stringResource(Res.string.settings_creative_mode_continue))
                    }
                }
            }

        }
        VerticalScrollbarForScroll(
            scrollState = scrollState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}
