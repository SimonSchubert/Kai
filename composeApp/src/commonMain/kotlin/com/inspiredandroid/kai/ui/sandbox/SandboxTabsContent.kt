package com.inspiredandroid.kai.ui.sandbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inspiredandroid.kai.SandboxController
import com.inspiredandroid.kai.ui.handCursor
import com.inspiredandroid.kai.ui.settings.SandboxUiState
import com.inspiredandroid.kai.ui.settings.SettingsCard
import com.inspiredandroid.kai.ui.settings.TerminalContent
import com.inspiredandroid.kai.ui.settings.TerminalDarkBg
import com.inspiredandroid.kai.TerminalLine
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.settings_sandbox_cancel
import kai.composeapp.generated.resources.settings_sandbox_description
import kai.composeapp.generated.resources.settings_sandbox_install
import kai.composeapp.generated.resources.settings_sandbox_subtab_files
import kai.composeapp.generated.resources.settings_sandbox_subtab_packages
import kai.composeapp.generated.resources.settings_sandbox_subtab_terminal
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

internal enum class SandboxSubTab { Terminal, Files, Packages }

@Composable
internal fun SandboxTabsContent(
    sandboxState: SandboxUiState,
    onSetupSandbox: () -> Unit = {},
    onCancelSandbox: () -> Unit = {},
    previewLines: List<TerminalLine> = emptyList(),
    modifier: Modifier = Modifier,
) {
    if (sandboxState.sandboxReady) {
        val isPreview = LocalInspectionMode.current
        val sandboxController: SandboxController? = if (!isPreview) koinInject() else null
        val sessionViewModel: SandboxSessionViewModel? = if (!isPreview) koinViewModel() else null
        var localSubTab by remember { mutableStateOf(SandboxSubTab.Terminal) }
        val subTab = sessionViewModel?.selectedTab?.collectAsStateWithLifecycle()?.value ?: localSubTab
        val onSelectTab: (SandboxSubTab) -> Unit = sessionViewModel?.let { vm ->
            { vm.selectTab(it) }
        } ?: { localSubTab = it }
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SandboxSubTabSelector(currentTab = subTab, onSelectTab = onSelectTab)

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when (subTab) {
                    SandboxSubTab.Terminal -> Column(modifier = Modifier.fillMaxSize()) {
                        if (sessionViewModel != null) {
                            SessionChipRow(viewModel = sessionViewModel)
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = TerminalDarkBg,
                            tonalElevation = 2.dp,
                        ) {
                            TerminalContent(
                                sandboxController = sandboxController,
                                modifier = Modifier.fillMaxSize(),
                                darkBackground = true,
                                initialLines = previewLines,
                                sessionViewModel = sessionViewModel,
                            )
                        }
                    }

                    SandboxSubTab.Files -> SandboxFilesContent(
                        modifier = Modifier.fillMaxSize(),
                    )

                    SandboxSubTab.Packages -> SandboxPackagesContent(
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    } else {
        Column(modifier = modifier.fillMaxWidth()) {
            SettingsCard {
                Text(
                    text = "Alpine Linux",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = stringResource(Res.string.settings_sandbox_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (sandboxState.sandboxProgress != null) {
                    SandboxProgressRow(sandboxState.sandboxProgress, sandboxState.sandboxStatusText, onCancelSandbox)
                } else if (sandboxState.isWorking) {
                    SandboxProgressRow(null, sandboxState.sandboxStatusText, onCancelSandbox)
                } else if (sandboxState.hasError) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = sandboxState.sandboxStatusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (!sandboxState.isWorking) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onSetupSandbox, modifier = Modifier.handCursor()) {
                        Text(stringResource(Res.string.settings_sandbox_install))
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionChipRow(viewModel: SandboxSessionViewModel) {
    val tabs = viewModel.visibleSessions.collectAsStateWithLifecycle().value
    val selectedId = viewModel.selectedSessionId.collectAsStateWithLifecycle().value
    if (tabs.size <= 1) return // Nothing to switch between — keep the UI quiet.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tabs.forEach { tab ->
            val isSelected = tab.id == selectedId
            Surface(
                modifier = Modifier
                    .handCursor()
                    .clip(RoundedCornerShape(50))
                    .clickable { viewModel.selectSession(tab.id) },
                shape = RoundedCornerShape(50),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                } else {
                    Color.Transparent
                },
            ) {
                Text(
                    text = tab.label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SandboxSubTabSelector(
    currentTab: SandboxSubTab,
    onSelectTab: (SandboxSubTab) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(4.dp),
    ) {
        SandboxSubTab.entries.forEach { tab ->
            val isSelected = currentTab == tab
            Surface(
                modifier = Modifier
                    .handCursor()
                    .clip(RoundedCornerShape(50))
                    .clickable { onSelectTab(tab) },
                shape = RoundedCornerShape(50),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                } else {
                    Color.Transparent
                },
            ) {
                Text(
                    text = when (tab) {
                        SandboxSubTab.Terminal -> stringResource(Res.string.settings_sandbox_subtab_terminal)
                        SandboxSubTab.Files -> stringResource(Res.string.settings_sandbox_subtab_files)
                        SandboxSubTab.Packages -> stringResource(Res.string.settings_sandbox_subtab_packages)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun SandboxProgressRow(progress: Float?, statusText: String, onCancel: () -> Unit) {
    Spacer(Modifier.height(8.dp))
    if (progress != null) {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onCancel, modifier = Modifier.handCursor()) {
            Text(stringResource(Res.string.settings_sandbox_cancel))
        }
    }
}
