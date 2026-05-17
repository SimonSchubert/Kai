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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.data.ImportSection
import com.inspiredandroid.kai.data.SharedJson
import com.inspiredandroid.kai.data.detectImportSections
import com.inspiredandroid.kai.saveFileToDevice
import com.inspiredandroid.kai.ui.components.VerticalScrollbarForScroll
import com.inspiredandroid.kai.ui.handCursor
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.settings_export
import kai.composeapp.generated.resources.settings_export_import_description
import kai.composeapp.generated.resources.settings_export_import_title
import kai.composeapp.generated.resources.settings_export_preview_title
import kai.composeapp.generated.resources.settings_import
import kai.composeapp.generated.resources.settings_import_error
import kai.composeapp.generated.resources.settings_import_partial
import kai.composeapp.generated.resources.settings_import_preview_title
import kai.composeapp.generated.resources.settings_import_replace_all
import kai.composeapp.generated.resources.settings_import_replace_all_description
import kai.composeapp.generated.resources.settings_import_section_conversations
import kai.composeapp.generated.resources.settings_import_section_email
import kai.composeapp.generated.resources.settings_import_section_heartbeat
import kai.composeapp.generated.resources.settings_import_section_mcp
import kai.composeapp.generated.resources.settings_import_section_memory
import kai.composeapp.generated.resources.settings_import_section_scheduling
import kai.composeapp.generated.resources.settings_import_section_services
import kai.composeapp.generated.resources.settings_import_section_soul
import kai.composeapp.generated.resources.settings_import_section_tools
import kai.composeapp.generated.resources.settings_import_success
import kai.composeapp.generated.resources.settings_mcp_cancel
import kai.composeapp.generated.resources.settings_sms
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ExportImportSection(
    onExportSettings: (Set<ImportSection>) -> String,
    onPrepareExport: () -> Map<ImportSection, String?>,
    onImportSettings: (ByteArray, Set<ImportSection>, Boolean) -> ImportResult,
) {
    val isPreview = LocalInspectionMode.current
    val scope = rememberCoroutineScope()
    var importResult by remember { mutableStateOf<ImportResult?>(null) }
    var importPreview by remember { mutableStateOf<Pair<String, ImmutableMap<ImportSection, String?>>?>(null) }
    var exportPreview by remember { mutableStateOf<ImmutableMap<ImportSection, String?>?>(null) }

    val filePickerLauncher = if (!isPreview) {
        rememberFilePickerLauncher(
            type = FileKitType.File(extensions = listOf("json")),
        ) { file ->
            if (file != null) {
                scope.launch {
                    val bytes = file.readBytes()
                    try {
                        val jsonString = bytes.decodeToString()
                        val jsonObject = SharedJson.parseToJsonElement(jsonString).jsonObject
                        val detectedSections = detectImportSections(jsonObject).toImmutableMap()
                        importPreview = jsonString to detectedSections
                    } catch (_: Exception) {
                        importResult = ImportResult.Failure
                    }
                }
            }
        }
    } else {
        null
    }

    importPreview?.let { (jsonString, sectionDetails) ->
        ImportPreviewDialog(
            sectionDetails = sectionDetails,
            onConfirm = { selectedSections, replace ->
                importResult = onImportSettings(jsonString.encodeToByteArray(), selectedSections, replace)
                importPreview = null
            },
            onDismiss = { importPreview = null },
        )
    }

    exportPreview?.let { sectionDetails ->
        ExportPreviewDialog(
            sectionDetails = sectionDetails,
            onConfirm = { selectedSections ->
                val json = onExportSettings(selectedSections)
                exportPreview = null
                scope.launch {
                    saveFileToDevice(
                        bytes = json.encodeToByteArray(),
                        baseName = "kai-settings",
                        extension = "json",
                    )
                }
            },
            onDismiss = { exportPreview = null },
        )
    }

    Text(
        text = stringResource(Res.string.settings_export_import_title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(Res.string.settings_export_import_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = {
                importResult = null
                exportPreview = onPrepareExport().toImmutableMap()
            },
            modifier = Modifier.handCursor(),
        ) {
            Text(stringResource(Res.string.settings_export))
        }
        OutlinedButton(
            onClick = {
                importResult = null
                filePickerLauncher?.launch()
            },
            modifier = Modifier.handCursor(),
        ) {
            Text(stringResource(Res.string.settings_import))
        }
    }
    if (importResult != null) {
        Spacer(Modifier.height(8.dp))
        val (text, color) = when (val result = importResult!!) {
            is ImportResult.Success -> stringResource(Res.string.settings_import_success) to MaterialTheme.colorScheme.primary
            is ImportResult.PartialSuccess -> stringResource(Res.string.settings_import_partial, result.errorCount) to MaterialTheme.colorScheme.primary
            is ImportResult.Failure -> stringResource(Res.string.settings_import_error) to MaterialTheme.colorScheme.error
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}

@Composable
private fun ImportPreviewDialog(
    sectionDetails: ImmutableMap<ImportSection, String?>,
    onConfirm: (Set<ImportSection>, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var replace by remember { mutableStateOf(true) }
    var selectedSections by remember { mutableStateOf<Set<ImportSection>>(sectionDetails.keys) }
    val sortedEntries = remember(sectionDetails) { sectionDetails.entries.sortedBy { it.key } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(Res.string.settings_import_preview_title))
        },
        text = {
            val importScrollState = rememberScrollState()
            Box {
                Column(modifier = Modifier.verticalScroll(importScrollState)) {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { replace = !replace }
                            .handCursor(),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(Res.string.settings_import_replace_all),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (replace) {
                                Text(
                                    text = stringResource(Res.string.settings_import_replace_all_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = replace,
                            onCheckedChange = { replace = it },
                            modifier = Modifier.handCursor(),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    for ((section, count) in sortedEntries) {
                        Row(
                            verticalAlignment = CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedSections = if (section in selectedSections) {
                                        selectedSections - section
                                    } else {
                                        selectedSections + section
                                    }
                                }
                                .handCursor()
                                .padding(vertical = 4.dp),
                        ) {
                            Checkbox(
                                checked = section in selectedSections,
                                onCheckedChange = { checked ->
                                    selectedSections = if (checked) selectedSections + section else selectedSections - section
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = sectionDisplayName(section),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (count != null) {
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "($count)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                VerticalScrollbarForScroll(
                    scrollState = importScrollState,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedSections, replace) },
                enabled = selectedSections.isNotEmpty(),
                modifier = Modifier.handCursor(),
            ) {
                Text(stringResource(Res.string.settings_import))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.handCursor(),
            ) {
                Text(stringResource(Res.string.settings_mcp_cancel))
            }
        },
    )
}

@Composable
private fun ExportPreviewDialog(
    sectionDetails: ImmutableMap<ImportSection, String?>,
    onConfirm: (Set<ImportSection>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedSections by remember { mutableStateOf<Set<ImportSection>>(sectionDetails.keys) }
    val sortedEntries = remember(sectionDetails) { sectionDetails.entries.sortedBy { it.key } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(Res.string.settings_export_preview_title))
        },
        text = {
            val exportScrollState = rememberScrollState()
            Box {
                Column(modifier = Modifier.verticalScroll(exportScrollState)) {
                    for ((section, count) in sortedEntries) {
                        Row(
                            verticalAlignment = CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedSections = if (section in selectedSections) {
                                        selectedSections - section
                                    } else {
                                        selectedSections + section
                                    }
                                }
                                .handCursor()
                                .padding(vertical = 4.dp),
                        ) {
                            Checkbox(
                                checked = section in selectedSections,
                                onCheckedChange = { checked ->
                                    selectedSections = if (checked) selectedSections + section else selectedSections - section
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = sectionDisplayName(section),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (count != null) {
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "($count)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                VerticalScrollbarForScroll(
                    scrollState = exportScrollState,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedSections) },
                enabled = selectedSections.isNotEmpty(),
                modifier = Modifier.handCursor(),
            ) {
                Text(stringResource(Res.string.settings_export))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.handCursor(),
            ) {
                Text(stringResource(Res.string.settings_mcp_cancel))
            }
        },
    )
}

@Composable
private fun sectionDisplayName(section: ImportSection): String = when (section) {
    ImportSection.SERVICES -> stringResource(Res.string.settings_import_section_services)
    ImportSection.SOUL -> stringResource(Res.string.settings_import_section_soul)
    ImportSection.MEMORY -> stringResource(Res.string.settings_import_section_memory)
    ImportSection.SCHEDULING -> stringResource(Res.string.settings_import_section_scheduling)
    ImportSection.HEARTBEAT -> stringResource(Res.string.settings_import_section_heartbeat)
    ImportSection.EMAIL -> stringResource(Res.string.settings_import_section_email)
    ImportSection.SMS -> stringResource(Res.string.settings_sms)
    ImportSection.SPLINTERLANDS -> "Splinterlands"
    ImportSection.TOOLS -> stringResource(Res.string.settings_import_section_tools)
    ImportSection.MCP -> stringResource(Res.string.settings_import_section_mcp)
    ImportSection.CONVERSATIONS -> stringResource(Res.string.settings_import_section_conversations)
}
