package com.inspiredandroid.kai.ui.build

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.build.BuildAgents
import com.inspiredandroid.kai.build.BuildEnvironmentState
import com.inspiredandroid.kai.build.BuildStep
import com.inspiredandroid.kai.build.KaiBuildState
import com.inspiredandroid.kai.ui.handCursor
import com.inspiredandroid.kai.ui.sandbox.SandboxProgressRow
import com.inspiredandroid.kai.ui.settings.SettingsCard
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.kai_build_setup_agents_description
import kai.composeapp.generated.resources.kai_build_setup_agents_title
import kai.composeapp.generated.resources.kai_build_setup_description
import kai.composeapp.generated.resources.kai_build_setup_install
import kai.composeapp.generated.resources.kai_build_setup_title
import kai.composeapp.generated.resources.kai_build_step_base_packages
import kai.composeapp.generated.resources.kai_build_step_configure
import kai.composeapp.generated.resources.kai_build_step_download
import kai.composeapp.generated.resources.kai_build_step_extract
import kai.composeapp.generated.resources.kai_build_step_install_agent
import kotlinx.collections.immutable.ImmutableSet
import org.jetbrains.compose.resources.stringResource

/**
 * First screen a user lands on: install Debian, optionally with coding agents.
 * Doubles as the progress surface for later agent installs.
 */
@Composable
internal fun BuildSetupContent(
    state: KaiBuildState,
    selectedAgents: ImmutableSet<String>,
    onToggleAgent: (String) -> Unit,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val installing = state.environment as? BuildEnvironmentState.Installing

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.kai_build_setup_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(Res.string.kai_build_setup_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SettingsCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(Res.string.kai_build_setup_agents_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.kai_build_setup_agents_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            BuildAgents.all.forEach { agent ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = installing == null) { onToggleAgent(agent.id) }
                        .handCursor(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = agent.id in selectedAgents || agent.id in state.installedAgents,
                        onCheckedChange = { onToggleAgent(agent.id) },
                        enabled = installing == null && agent.id !in state.installedAgents,
                    )
                    Text(
                        text = agent.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        if (installing != null) {
            SandboxProgressRow(
                progress = installing.progress,
                statusText = stepLabel(installing),
                onCancel = onCancel,
            )
        } else {
            Button(onClick = onInstall, modifier = Modifier.handCursor()) {
                Text(stringResource(Res.string.kai_build_setup_install))
            }
        }

        state.lastError?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun stepLabel(state: BuildEnvironmentState.Installing): String = when (state.step) {
    BuildStep.Download -> stringResource(
        Res.string.kai_build_step_download,
        ((state.progress ?: 0f) * 100).toInt(),
    )
    BuildStep.Extract -> stringResource(Res.string.kai_build_step_extract)
    BuildStep.Configure -> stringResource(Res.string.kai_build_step_configure)
    BuildStep.BasePackages -> stringResource(Res.string.kai_build_step_base_packages)
    BuildStep.Agent -> stringResource(
        Res.string.kai_build_step_install_agent,
        BuildAgents.get(state.agentId)?.title.orEmpty(),
    )
}
