package com.inspiredandroid.kai.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.mcp.PopularMcpServer
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.skills.RegistrySkillEntry
import com.inspiredandroid.kai.skills.SkillManifest
import com.inspiredandroid.kai.ui.handCursor
import com.inspiredandroid.kai.ui.kaiAdaptiveCardBorder
import com.inspiredandroid.kai.ui.kaiAdaptiveCardColors
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.settings_tools_description
import kai.composeapp.generated.resources.settings_tools_none_available
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ToolsContent(
    tools: ImmutableList<ToolInfo>,
    onToggleTool: (String, Boolean) -> Unit,
    mcpServers: ImmutableList<McpServerUiState>,
    onAddMcpServer: (String, String, Map<String, String>) -> Unit,
    onRemoveMcpServer: (String) -> Unit,
    onToggleMcpServer: (String, Boolean) -> Unit,
    onRefreshMcpServer: (String) -> Unit,
    showAddMcpServerDialog: Boolean,
    onShowAddMcpServerDialog: (Boolean) -> Unit,
    onAddPopularMcpServer: (PopularMcpServer) -> Unit,
    skills: ImmutableList<SkillManifest>,
    onUninstallSkill: (String) -> Unit,
    showAddSkillDialog: Boolean,
    onShowAddSkillDialog: (Boolean) -> Unit,
    onInstallGitHubSkill: (String) -> Unit,
    onInstallBrowsedSkill: (RegistrySkillEntry) -> Unit,
    isInstallingSkill: Boolean,
    skillInstallError: String?,
    browsableSkills: ImmutableList<RegistrySkillEntry>,
    isBrowsingSkills: Boolean,
    browseSkillsFailed: Boolean,
    showSkills: Boolean,
    isSandboxInstalled: Boolean,
    onNavigateToSandbox: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // MCP Servers section
        McpServersSection(
            mcpServers = mcpServers,
            onAddMcpServer = onAddMcpServer,
            onRemoveMcpServer = onRemoveMcpServer,
            onToggleMcpServer = onToggleMcpServer,
            onRefreshMcpServer = onRefreshMcpServer,
            onToggleTool = onToggleTool,
            showAddDialog = showAddMcpServerDialog,
            onShowAddDialog = onShowAddMcpServerDialog,
            onAddPopularMcpServer = onAddPopularMcpServer,
        )

        // Skills section — sandbox-backed, so Android only.
        if (showSkills) {
            Spacer(Modifier.height(24.dp))
            SkillsSection(
                skills = skills,
                onUninstallSkill = onUninstallSkill,
                showAddDialog = showAddSkillDialog,
                onShowAddDialog = onShowAddSkillDialog,
                onInstallGitHub = onInstallGitHubSkill,
                onInstallBrowsed = onInstallBrowsedSkill,
                isInstalling = isInstallingSkill,
                installError = skillInstallError,
                browsableSkills = browsableSkills,
                isBrowsing = isBrowsingSkills,
                browseFailed = browseSkillsFailed,
                isSandboxInstalled = isSandboxInstalled,
                onNavigateToSandbox = onNavigateToSandbox,
            )
        }

        Spacer(Modifier.height(24.dp))

        // Native tools section
        Text(
            text = stringResource(Res.string.settings_tools_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        if (tools.isEmpty()) {
            Text(
                text = stringResource(Res.string.settings_tools_none_available),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columns = when {
                    maxWidth >= 800.dp -> 3
                    maxWidth >= 500.dp -> 2
                    else -> 1
                }
                val rows = tools.chunked(columns)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { rowTools ->
                        Row(
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rowTools.forEach { tool ->
                                ToolItem(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    tool = tool,
                                    onToggle = { enabled -> onToggleTool(tool.id, enabled) },
                                )
                            }
                            // Fill empty slots so last row items don't stretch
                            repeat(columns - rowTools.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolItem(
    tool: ToolInfo,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .clip(CardDefaults.shape)
            .clickable { onToggle(!tool.isEnabled) }
            .handCursor(),
        colors = kaiAdaptiveCardColors(),
        border = kaiAdaptiveCardBorder(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tool.nameRes?.let { stringResource(it) } ?: tool.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = tool.descriptionRes?.let { stringResource(it) } ?: tool.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.width(16.dp))

            Switch(
                checked = tool.isEnabled,
                onCheckedChange = onToggle,
            )
        }
    }
}
