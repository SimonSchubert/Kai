package com.kainext.duo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kainext.duo.DeviceControlStatus
import com.kainext.duo.ui.handCursor
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.settings_device_checking
import kai.composeapp.generated.resources.settings_device_control_description
import kai.composeapp.generated.resources.settings_device_control_title
import kai.composeapp.generated.resources.settings_device_grant_shizuku
import kai.composeapp.generated.resources.settings_device_mode_auto
import kai.composeapp.generated.resources.settings_device_mode_root
import kai.composeapp.generated.resources.settings_device_mode_shizuku
import kai.composeapp.generated.resources.settings_device_overlay_title
import kai.composeapp.generated.resources.settings_device_overlay_description
import kai.composeapp.generated.resources.settings_device_overlay_permission_needed
import kai.composeapp.generated.resources.settings_device_overlay_grant
import kai.composeapp.generated.resources.settings_device_privileged_mode
import kai.composeapp.generated.resources.settings_device_refresh
import kai.composeapp.generated.resources.settings_device_sdcard_description
import kai.composeapp.generated.resources.settings_device_sdcard_title
import kai.composeapp.generated.resources.settings_device_shell_description
import kai.composeapp.generated.resources.settings_device_shell_title
import kai.composeapp.generated.resources.settings_device_status_none
import kai.composeapp.generated.resources.settings_device_status_root
import kai.composeapp.generated.resources.settings_device_status_shizuku
import kai.composeapp.generated.resources.settings_device_ui_description
import kai.composeapp.generated.resources.settings_device_ui_title
import org.jetbrains.compose.resources.stringResource

/**
 * Settings → Device control. Master switch plus three independently gated capabilities:
 * privileged shell, /sdcard file access, and UI automation. The status card shows which
 * backend (root / Shizuku) the next command would actually use.
 */
@Composable
internal fun DeviceControlContent(
    uiState: SettingsUiState,
    actions: SettingsActions,
) {
    // Probe once when the section becomes visible so the status row is populated without a tap.
    LaunchedEffect(uiState.isDeviceControlEnabled) {
        if (uiState.isDeviceControlEnabled && uiState.deviceControlStatus == null) {
            actions.onProbeDeviceControl()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsCard {
            ToggleableHeadline(
                title = stringResource(Res.string.settings_device_control_title),
                description = stringResource(Res.string.settings_device_control_description),
                checked = uiState.isDeviceControlEnabled,
                onCheckedChange = actions.onToggleDeviceControl,
            )
        }

        if (uiState.isDeviceControlEnabled) {
            SettingsCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(Res.string.settings_device_privileged_mode),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PrivilegedModeOption(
                            label = stringResource(Res.string.settings_device_mode_auto),
                            selected = uiState.privilegedMode == "auto",
                            onClick = { actions.onChangePrivilegedMode("auto") },
                        )
                        PrivilegedModeOption(
                            label = stringResource(Res.string.settings_device_mode_root),
                            selected = uiState.privilegedMode == "root",
                            onClick = { actions.onChangePrivilegedMode("root") },
                        )
                        PrivilegedModeOption(
                            label = stringResource(Res.string.settings_device_mode_shizuku),
                            selected = uiState.privilegedMode == "shizuku",
                            onClick = { actions.onChangePrivilegedMode("shizuku") },
                        )
                        PrivilegedModeOption(
                            label = "Accessibility",
                            selected = uiState.privilegedMode == "accessibility",
                            onClick = { actions.onChangePrivilegedMode("accessibility") },
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    DeviceControlStatusRow(
                        status = uiState.deviceControlStatus,
                        isProbing = uiState.isProbingDeviceControl,
                        onRefresh = actions.onProbeDeviceControl,
                        onGrantShizuku = actions.onRequestShizukuPermission,
                    )
                }
            }

            SettingsCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Accessibility service",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Reads the live screen and taps, swipes and types on your behalf. Needs no root, and taps land on the intended control instead of guessed coordinates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (uiState.deviceControlStatus?.accessibilityEnabled == true) {
                            "Enabled"
                        } else {
                            "Not enabled"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (uiState.deviceControlStatus?.accessibilityEnabled == true) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = actions.onOpenAccessibilitySettings,
                            modifier = Modifier.handCursor(),
                        ) {
                            Text("Open accessibility settings")
                        }
                        OutlinedButton(
                            onClick = actions.onKeepAccessibilityAlive,
                            modifier = Modifier.handCursor(),
                        ) {
                            Text("Keep alive via root/Shizuku")
                        }
                    }
                }
            }

            SettingsCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ToggleableHeadline(
                        title = stringResource(Res.string.settings_device_overlay_title),
                        description = stringResource(Res.string.settings_device_overlay_description),
                        checked = uiState.isOverlayEnabled,
                        onCheckedChange = actions.onToggleOverlay,
                    )
                    if (!uiState.canShowOverlay) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(Res.string.settings_device_overlay_permission_needed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = actions.onOpenOverlaySettings,
                            modifier = Modifier.handCursor(),
                        ) {
                            Text(stringResource(Res.string.settings_device_overlay_grant))
                        }
                    }
                }
            }

            SettingsCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ToggleableHeadline(
                        title = stringResource(Res.string.settings_device_shell_title),
                        description = stringResource(Res.string.settings_device_shell_description),
                        checked = uiState.isDeviceShellEnabled,
                        onCheckedChange = actions.onToggleDeviceShell,
                    )
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    ToggleableHeadline(
                        title = stringResource(Res.string.settings_device_sdcard_title),
                        description = stringResource(Res.string.settings_device_sdcard_description),
                        checked = uiState.isSdcardAccessEnabled,
                        onCheckedChange = actions.onToggleSdcardAccess,
                    )
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    ToggleableHeadline(
                        title = stringResource(Res.string.settings_device_ui_title),
                        description = stringResource(Res.string.settings_device_ui_description),
                        checked = uiState.isUiAutomationEnabled,
                        onCheckedChange = actions.onToggleUiAutomation,
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivilegedModeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.handCursor(),
    )
}

@Composable
private fun DeviceControlStatusRow(
    status: DeviceControlStatus?,
    isProbing: Boolean,
    onRefresh: () -> Unit,
    onGrantShizuku: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isProbing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp).padding(end = 8.dp))
                Text(
                    text = stringResource(Res.string.settings_device_checking),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val (label, color) = when (status?.effectiveMode) {
                    "root" -> stringResource(Res.string.settings_device_status_root) to MaterialTheme.colorScheme.primary
                    "shizuku" -> stringResource(Res.string.settings_device_status_shizuku) to MaterialTheme.colorScheme.primary
                    "accessibility" ->
                        "Accessibility service connected" to MaterialTheme.colorScheme.primary
                    else -> stringResource(Res.string.settings_device_status_none) to MaterialTheme.colorScheme.error
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                )
            }
        }
        status?.detail?.let { detail ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRefresh, modifier = Modifier.handCursor()) {
                Text(stringResource(Res.string.settings_device_refresh))
            }
            if (status != null && status.shizukuInstalled && !status.shizukuPermissionGranted) {
                OutlinedButton(onClick = onGrantShizuku, modifier = Modifier.handCursor()) {
                    Text(stringResource(Res.string.settings_device_grant_shizuku))
                }
            }
        }
    }
}
