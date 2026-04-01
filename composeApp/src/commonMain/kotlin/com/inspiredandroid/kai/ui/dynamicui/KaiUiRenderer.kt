package com.inspiredandroid.kai.ui.dynamicui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun KaiUiRenderer(
    node: KaiUiNode,
    isInteractive: Boolean,
    onCallback: (event: String, data: Map<String, String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val formState = remember { mutableStateMapOf<String, String>() }
    val toggleState = remember { mutableStateMapOf<String, Boolean>() }
    var hasError by remember { mutableStateOf(false) }

    LaunchedEffect(node) {
        try {
            initializeFormState(node, formState)
        } catch (_: Exception) {
            hasError = true
        }
    }

    if (hasError) {
        Text(
            text = "Failed to render UI",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier,
        )
        return
    }

    Card(modifier = modifier.fillMaxWidth().wrapContentHeight()) {
        Column(Modifier.padding(12.dp).wrapContentHeight()) {
            RenderNode(
                node = node,
                isInteractive = isInteractive,
                formState = formState,
                toggleState = toggleState,
                onCallback = safeCallback(onCallback),
            )
        }
    }
}

private fun safeCallback(
    onCallback: (String, Map<String, String>) -> Unit,
): (String, Map<String, String>) -> Unit = { event, data ->
    try {
        onCallback(event, data)
    } catch (_: Exception) {
        // Silently handle callback errors to prevent crashes
    }
}

private const val MAX_DEPTH = 10

@Composable
private fun RenderNode(
    node: KaiUiNode,
    isInteractive: Boolean,
    formState: MutableMap<String, String>,
    toggleState: MutableMap<String, Boolean>,
    onCallback: (String, Map<String, String>) -> Unit,
    depth: Int = 0,
) {
    if (depth > MAX_DEPTH) return

    val nodeId = node.id
    if (nodeId != null && toggleState[nodeId] == false) return

    when (node) {
        is ColumnNode -> RenderColumn(node, isInteractive, formState, toggleState, onCallback, depth)
        is RowNode -> RenderRow(node, isInteractive, formState, toggleState, onCallback, depth)
        is CardNode -> RenderCard(node, isInteractive, formState, toggleState, onCallback, depth)
        is TextNode -> RenderText(node)
        is ButtonNode -> RenderButton(node, isInteractive, formState, toggleState, onCallback)
        is TextInputNode -> RenderTextInput(node, isInteractive, formState)
        is CheckboxNode -> RenderCheckbox(node, isInteractive, formState)
        is SelectNode -> RenderSelect(node, isInteractive, formState)
        is ImageNode -> RenderImage(node)
        is TableNode -> RenderTable(node)
        is ListNode -> RenderList(node, isInteractive, formState, toggleState, onCallback, depth)
        is SpacerNode -> Spacer(Modifier.height((node.height ?: 8).dp))
        is DividerNode -> HorizontalDivider(Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun RenderChildren(
    children: List<KaiUiNode>,
    isInteractive: Boolean,
    formState: MutableMap<String, String>,
    toggleState: MutableMap<String, Boolean>,
    onCallback: (String, Map<String, String>) -> Unit,
    depth: Int,
) {
    for (child in children) {
        RenderNode(child, isInteractive, formState, toggleState, onCallback, depth + 1)
    }
}

@Composable
private fun RenderColumn(
    node: ColumnNode,
    isInteractive: Boolean,
    formState: MutableMap<String, String>,
    toggleState: MutableMap<String, Boolean>,
    onCallback: (String, Map<String, String>) -> Unit,
    depth: Int,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy((node.spacing ?: 8).dp),
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .then(if (node.padding != null) Modifier.padding(node.padding.dp) else Modifier),
    ) {
        RenderChildren(node.children, isInteractive, formState, toggleState, onCallback, depth)
    }
}

@Composable
private fun RenderRow(
    node: RowNode,
    isInteractive: Boolean,
    formState: MutableMap<String, String>,
    toggleState: MutableMap<String, Boolean>,
    onCallback: (String, Map<String, String>) -> Unit,
    depth: Int,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy((node.spacing ?: 8).dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .then(if (node.padding != null) Modifier.padding(node.padding.dp) else Modifier),
    ) {
        for (child in node.children) {
            Box(Modifier.weight(1f).wrapContentHeight()) {
                RenderNode(child, isInteractive, formState, toggleState, onCallback, depth + 1)
            }
        }
    }
}

@Composable
private fun RenderCard(
    node: CardNode,
    isInteractive: Boolean,
    formState: MutableMap<String, String>,
    toggleState: MutableMap<String, Boolean>,
    onCallback: (String, Map<String, String>) -> Unit,
    depth: Int,
) {
    Card(Modifier.fillMaxWidth().wrapContentHeight()) {
        Column(Modifier.padding((node.padding ?: 12).dp).wrapContentHeight()) {
            RenderChildren(node.children, isInteractive, formState, toggleState, onCallback, depth)
        }
    }
}

@Composable
private fun RenderText(node: TextNode) {
    val style = when (node.style) {
        TextNodeStyle.HEADLINE -> MaterialTheme.typography.headlineSmall
        TextNodeStyle.TITLE -> MaterialTheme.typography.titleMedium
        TextNodeStyle.BODY -> MaterialTheme.typography.bodyLarge
        TextNodeStyle.CAPTION -> MaterialTheme.typography.bodySmall
        null -> MaterialTheme.typography.bodyLarge
    }
    val color = when (node.color) {
        "primary" -> MaterialTheme.colorScheme.primary
        "secondary" -> MaterialTheme.colorScheme.secondary
        "error" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = node.value.replace("**", ""),
        style = style,
        color = color,
        fontWeight = if (node.bold == true || node.value.startsWith("**")) FontWeight.Bold else null,
        fontStyle = if (node.italic == true) FontStyle.Italic else null,
    )
}

@Composable
private fun RenderButton(
    node: ButtonNode,
    isInteractive: Boolean,
    formState: MutableMap<String, String>,
    toggleState: MutableMap<String, Boolean>,
    onCallback: (String, Map<String, String>) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val enabled = isInteractive && (node.enabled != false)
    val onClick: () -> Unit = {
        try {
            when (val action = node.action) {
                is CallbackAction -> {
                    val data = collectFormData(action, formState)
                    onCallback(action.event, data)
                }

                is ToggleAction -> {
                    toggleState[action.targetId] = !(toggleState[action.targetId] ?: true)
                }

                is OpenUrlAction -> {
                    uriHandler.openUri(action.url)
                }
            }
        } catch (_: Exception) {
            // Prevent crashes from action handlers
        }
    }

    Button(onClick = onClick, enabled = enabled) { Text(node.label) }
}

@Composable
private fun RenderTextInput(
    node: TextInputNode,
    isInteractive: Boolean,
    formState: MutableMap<String, String>,
) {
    OutlinedTextField(
        value = formState[node.id] ?: "",
        onValueChange = { formState[node.id] = it },
        label = node.label?.let { { Text(it) } },
        placeholder = node.placeholder?.let { { Text(it) } },
        enabled = isInteractive,
        singleLine = node.multiline != true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RenderCheckbox(
    node: CheckboxNode,
    isInteractive: Boolean,
    formState: MutableMap<String, String>,
) {
    val checked = formState[node.id]?.toBooleanStrictOrNull() ?: false
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked,
            onCheckedChange = { formState[node.id] = it.toString() },
            enabled = isInteractive,
        )
        Text(node.label, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenderSelect(
    node: SelectNode,
    isInteractive: Boolean,
    formState: MutableMap<String, String>,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = formState[node.id] ?: ""

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (isInteractive) expanded = it },
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = node.label?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            enabled = isInteractive,
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (option in node.options) {
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        formState[node.id] = option
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RenderImage(node: ImageNode) {
    val modifier = Modifier.fillMaxWidth().then(
        if (node.height != null) Modifier.height(node.height.dp) else Modifier,
    )
    coil3.compose.AsyncImage(
        model = node.url,
        contentDescription = node.alt,
        modifier = modifier,
    )
}

@Composable
private fun RenderTable(node: TableNode) {
    Column(Modifier.fillMaxWidth().wrapContentHeight()) {
        if (node.headers.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                for (header in node.headers) {
                    Text(
                        text = header,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            HorizontalDivider()
        }
        for (row in node.rows) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                for ((index, cell) in row.withIndex()) {
                    Text(
                        text = cell,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    // If row has fewer cells than headers, skip remaining
                    if (index >= node.headers.size - 1) break
                }
            }
        }
    }
}

@Composable
private fun RenderList(
    node: ListNode,
    isInteractive: Boolean,
    formState: MutableMap<String, String>,
    toggleState: MutableMap<String, Boolean>,
    onCallback: (String, Map<String, String>) -> Unit,
    depth: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((index, item) in node.items.withIndex()) {
            Row {
                val prefix = if (node.ordered == true) "${index + 1}. " else "\u2022 "
                Text(prefix, style = MaterialTheme.typography.bodyLarge)
                Column(Modifier.weight(1f)) {
                    RenderNode(item, isInteractive, formState, toggleState, onCallback, depth + 1)
                }
            }
        }
    }
}

private fun initializeFormState(node: KaiUiNode, formState: MutableMap<String, String>) {
    when (node) {
        is TextInputNode -> node.value?.let { if (node.id !in formState) formState[node.id] = it }
        is CheckboxNode -> if (node.id !in formState) formState[node.id] = (node.checked ?: false).toString()
        is SelectNode -> node.selected?.let { if (node.id !in formState) formState[node.id] = it }
        is ColumnNode -> node.children.forEach { initializeFormState(it, formState) }
        is RowNode -> node.children.forEach { initializeFormState(it, formState) }
        is CardNode -> node.children.forEach { initializeFormState(it, formState) }
        is ListNode -> node.items.forEach { initializeFormState(it, formState) }
        else -> {}
    }
}

private fun collectFormData(action: CallbackAction, formState: Map<String, String>): Map<String, String> {
    val collected = mutableMapOf<String, String>()
    action.data?.let { collected.putAll(it) }
    action.collectFrom?.forEach { inputId ->
        formState[inputId]?.let { collected[inputId] = it }
    }
    return collected
}
