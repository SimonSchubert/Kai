package com.inspiredandroid.kai.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.skills.RegistrySkillEntry
import com.inspiredandroid.kai.skills.SkillManifest
import com.inspiredandroid.kai.ui.KaiOutlinedTextField
import com.inspiredandroid.kai.ui.components.VerticalScrollbarForScroll
import com.inspiredandroid.kai.ui.handCursor
import com.inspiredandroid.kai.ui.kaiAdaptiveCardBorder
import com.inspiredandroid.kai.ui.kaiAdaptiveCardColors
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.settings_skills
import kai.composeapp.generated.resources.settings_skills_add
import kai.composeapp.generated.resources.settings_skills_add_github
import kai.composeapp.generated.resources.settings_skills_browse
import kai.composeapp.generated.resources.settings_skills_browse_failed
import kai.composeapp.generated.resources.settings_skills_browse_loading
import kai.composeapp.generated.resources.settings_skills_builtin
import kai.composeapp.generated.resources.settings_skills_cancel
import kai.composeapp.generated.resources.settings_skills_description
import kai.composeapp.generated.resources.settings_skills_github_hint
import kai.composeapp.generated.resources.settings_skills_github_url
import kai.composeapp.generated.resources.settings_skills_install
import kai.composeapp.generated.resources.settings_skills_installing
import kai.composeapp.generated.resources.settings_skills_needs_sandbox
import kai.composeapp.generated.resources.settings_skills_none
import kai.composeapp.generated.resources.settings_skills_remove
import kai.composeapp.generated.resources.settings_skills_search
import kai.composeapp.generated.resources.settings_skills_search_empty
import kai.composeapp.generated.resources.settings_skills_setup_sandbox
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SkillsSection(
    skills: ImmutableList<SkillManifest>,
    onUninstallSkill: (String) -> Unit,
    showAddDialog: Boolean,
    onShowAddDialog: (Boolean) -> Unit,
    onInstallGitHub: (String) -> Unit,
    onInstallBrowsed: (RegistrySkillEntry) -> Unit,
    isInstalling: Boolean,
    installError: String?,
    browsableSkills: ImmutableList<RegistrySkillEntry>,
    isBrowsing: Boolean,
    browseFailed: Boolean,
    isSandboxInstalled: Boolean,
    onNavigateToSandbox: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.settings_skills),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.settings_skills_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        if (!isSandboxInstalled) {
            // Skills live in the Linux sandbox, so it must be installed first.
            Text(
                text = stringResource(Res.string.settings_skills_needs_sandbox),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onNavigateToSandbox,
                modifier = Modifier.align(Alignment.CenterHorizontally).handCursor(),
            ) {
                Text(stringResource(Res.string.settings_skills_setup_sandbox))
            }
        } else {
            if (skills.isEmpty()) {
                Text(
                    text = stringResource(Res.string.settings_skills_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                for (skill in skills) {
                    SkillCard(
                        skill = skill,
                        onRemove = { onUninstallSkill(skill.id) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { onShowAddDialog(true) },
                modifier = Modifier.align(Alignment.CenterHorizontally).handCursor(),
            ) {
                Text(stringResource(Res.string.settings_skills_add))
            }
        }
    }

    if (showAddDialog && isSandboxInstalled) {
        AddSkillDialog(
            onDismiss = { onShowAddDialog(false) },
            onInstallGitHub = onInstallGitHub,
            onInstallBrowsed = onInstallBrowsed,
            isInstalling = isInstalling,
            installError = installError,
            browsableSkills = browsableSkills,
            isBrowsing = isBrowsing,
            browseFailed = browseFailed,
            installedIds = remember(skills) { skills.map { it.id }.toSet() },
        )
    }
}

@Composable
private fun SkillCard(
    skill: SkillManifest,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().handCursor(),
        colors = kaiAdaptiveCardColors(),
        border = kaiAdaptiveCardBorder(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "/${skill.id}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (skill.isBuiltIn) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(Res.string.settings_skills_builtin),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = skill.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                )
            }

            // Built-in skills ship in the app and cannot be uninstalled — hide the remove action for them.
            if (expanded && !skill.isBuiltIn) {
                Spacer(Modifier.height(8.dp))

                TextButton(onClick = onRemove, modifier = Modifier.handCursor()) {
                    Text(
                        text = stringResource(Res.string.settings_skills_remove),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSkillDialog(
    onDismiss: () -> Unit,
    onInstallGitHub: (String) -> Unit,
    onInstallBrowsed: (RegistrySkillEntry) -> Unit,
    isInstalling: Boolean,
    installError: String?,
    browsableSkills: ImmutableList<RegistrySkillEntry>,
    isBrowsing: Boolean,
    browseFailed: Boolean,
    installedIds: Set<String>,
) {
    var url by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    // Which browse row the user tapped, so we can show a spinner on just that row.
    var installingId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(isInstalling) {
        if (!isInstalling) installingId = null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        val scrollState = rememberScrollState()
        Box {
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(16.dp),
            ) {
                Text(
                    text = stringResource(Res.string.settings_skills_add),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(16.dp))

                Text(
                    text = stringResource(Res.string.settings_skills_add_github),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                KaiOutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(Res.string.settings_skills_github_url)) },
                    singleLine = true,
                    enabled = !isInstalling,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.settings_skills_github_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (installError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = installError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isInstalling) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(Res.string.settings_skills_installing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.handCursor()) {
                        Text(stringResource(Res.string.settings_skills_cancel))
                    }
                    TextButton(
                        onClick = { onInstallGitHub(url) },
                        enabled = url.isNotBlank() && !isInstalling,
                        modifier = Modifier.handCursor(),
                    ) {
                        Text(stringResource(Res.string.settings_skills_install))
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.settings_skills_browse),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))

                when {
                    isBrowsing -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(Res.string.settings_skills_browse_loading),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    browseFailed -> {
                        Text(
                            text = stringResource(Res.string.settings_skills_browse_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    else -> {
                        KaiOutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            label = { Text(stringResource(Res.string.settings_skills_search)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))

                        val filtered = remember(browsableSkills, search) {
                            val q = search.trim().lowercase()
                            if (q.isEmpty()) {
                                browsableSkills
                            } else {
                                browsableSkills.filter {
                                    it.id.lowercase().contains(q) ||
                                        it.description.lowercase().contains(q) ||
                                        it.sourceName.lowercase().contains(q)
                                }
                            }
                        }

                        if (filtered.isEmpty()) {
                            Text(
                                text = stringResource(Res.string.settings_skills_search_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            for (entry in filtered) {
                                RegistrySkillRow(
                                    entry = entry,
                                    alreadyInstalled = entry.id in installedIds,
                                    enabled = !isInstalling,
                                    installing = isInstalling && installingId == entry.id,
                                    onInstall = {
                                        installingId = entry.id
                                        onInstallBrowsed(entry)
                                    },
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
            VerticalScrollbarForScroll(
                scrollState = scrollState,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun RegistrySkillRow(
    entry: RegistrySkillEntry,
    alreadyInstalled: Boolean,
    enabled: Boolean,
    installing: Boolean,
    onInstall: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardDefaults.shape)
            .then(if (enabled && !alreadyInstalled) Modifier.clickable { onInstall() }.handCursor() else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "/${entry.id}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = entry.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
                Text(
                    text = entry.sourceName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (installing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else if (alreadyInstalled) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
