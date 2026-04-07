@file:OptIn(ExperimentalMaterial3Api::class)

package com.inspiredandroid.kai.ui.dynamicui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inspiredandroid.kai.ui.KaiOutlinedTextField
import com.inspiredandroid.kai.ui.components.KaiChip
import com.inspiredandroid.kai.ui.handCursor
import kotlinx.coroutines.delay
import kotlin.time.Clock

val LocalPreviewImages = staticCompositionLocalOf<Map<String, ImageBitmap>> { emptyMap() }

@Composable
fun KaiUiRenderer(
    node: KaiUiNode,
    isInteractive: Boolean,
    onCallback: (event: String, data: Map<String, String>) -> Unit,
    modifier: Modifier = Modifier,
    wrapInCard: Boolean = true,
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

    if (wrapInCard) {
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
    } else {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            Column(modifier = modifier.fillMaxWidth().wrapContentHeight()) {
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
        is DividerNode -> HorizontalDivider(Modifier.padding(vertical = 4.dp))
        is SwitchNode -> RenderSwitch(node, isInteractive, formState)
        is SliderNode -> RenderSlider(node, isInteractive, formState)
        is RadioGroupNode -> RenderRadioGroup(node, isInteractive, formState)
        is ProgressNode -> RenderProgress(node)
        is CountdownNode -> RenderCountdown(node, isInteractive, formState, toggleState, onCallback)
        is AlertNode -> RenderAlert(node)
        is ChipGroupNode -> RenderChipGroup(node, isInteractive, formState)
        is IconNode -> RenderIcon(node)
        is CodeNode -> RenderCode(node)
        is BoxNode -> RenderBox(node, isInteractive, formState, toggleState, onCallback, depth)
        is TabsNode -> RenderTabs(node, isInteractive, formState, toggleState, onCallback, depth)
        is AccordionNode -> RenderAccordion(node, isInteractive, formState, toggleState, onCallback, depth)
        is QuoteNode -> RenderQuote(node)
        is BadgeNode -> RenderBadge(node)
        is StatNode -> RenderStat(node)
        is AvatarNode -> RenderAvatar(node)
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
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
    val allStats = node.children.isNotEmpty() && node.children.all { it is StatNode }
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
        horizontalArrangement = if (allStats) Arrangement.SpaceEvenly else Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
    ) {
        for (child in node.children) {
            RenderNode(child, isInteractive, formState, toggleState, onCallback, depth + 1)
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
        Column(
            modifier = Modifier.padding(16.dp).wrapContentHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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

                null -> {}
            }
        } catch (_: Exception) {
            // Prevent crashes from action handlers
        }
    }

    val hoverModifier = Modifier.handCursor()
    when (node.variant) {
        ButtonVariant.OUTLINED -> OutlinedButton(onClick = onClick, enabled = enabled, modifier = hoverModifier) { Text(node.label) }
        ButtonVariant.TEXT -> TextButton(onClick = onClick, enabled = enabled, modifier = hoverModifier) { Text(node.label) }
        ButtonVariant.TONAL -> FilledTonalButton(onClick = onClick, enabled = enabled, modifier = hoverModifier) { Text(node.label) }
        ButtonVariant.FILLED, null -> Button(onClick = onClick, enabled = enabled, modifier = hoverModifier) { Text(node.label) }
    }
}

@Composable
private fun RenderTextInput(
    node: TextInputNode,
    isInteractive: Boolean,
    formState: MutableMap<String, String>,
) {
    KaiOutlinedTextField(
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
    val toggle = { formState[node.id] = (!checked).toString() }
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .handCursor()
            .then(
                if (isInteractive) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = toggle,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = isInteractive,
            modifier = Modifier.indication(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 20.dp),
            ),
            interactionSource = interactionSource,
        )
        Text(node.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
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
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).handCursor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (option in node.options) {
                DropdownMenuItem(
                    text = { Text(option) },
                    modifier = Modifier.handCursor(),
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
    val modifier = if (node.aspectRatio != null && node.aspectRatio > 0f) {
        Modifier.fillMaxWidth().aspectRatio(node.aspectRatio.coerceIn(0.1f, 10f))
    } else {
        Modifier.fillMaxWidth().then(
            if (node.height != null) Modifier.height(node.height.dp) else Modifier,
        )
    }
    val previewBitmap = LocalPreviewImages.current[node.url]
    if (previewBitmap != null) {
        Image(
            bitmap = previewBitmap,
            contentDescription = node.alt,
            modifier = modifier.clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        coil3.compose.AsyncImage(
            model = node.url,
            contentDescription = node.alt,
            modifier = modifier.clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun RenderTable(node: TableNode) {
    val columnCount = maxOf(
        node.headers.size,
        node.rows.maxOfOrNull { it.size } ?: 0,
    )
    if (columnCount == 0) return
    Column(Modifier.fillMaxWidth().wrapContentHeight()) {
        if (node.headers.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                for (index in 0 until columnCount) {
                    Text(
                        text = node.headers.getOrElse(index) { "" },
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            HorizontalDivider()
        }
        for (row in node.rows) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                for (index in 0 until columnCount) {
                    Text(
                        text = row.getOrElse(index) { "" },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
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

// --- New component renderers ---

@Composable
private fun RenderSwitch(
    node: SwitchNode,
    isInteractive: Boolean,
    formState: MutableMap<String, String>,
) {
    val checked = formState[node.id]?.toBooleanStrictOrNull() ?: false
    val toggle = { formState[node.id] = (!checked).toString() }
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .handCursor()
            .then(
                if (isInteractive) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = toggle,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Text(
            text = node.label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = isInteractive,
            interactionSource = interactionSource,
        )
    }
}

@Composable
private fun RenderSlider(
    node: SliderNode,
    isInteractive: Boolean,
    formState: MutableMap<String, String>,
) {
    val min = node.min ?: 0f
    val max = node.max ?: 100f
    val step = node.step
    val currentValue = formState[node.id]?.toFloatOrNull() ?: (node.value ?: min)

    Column(Modifier.fillMaxWidth()) {
        if (node.label != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(node.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = formatSliderValue(currentValue, step),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        val steps = if (step != null && step > 0) {
            ((max - min) / step).toInt() - 1
        } else {
            0
        }
        Slider(
            value = currentValue.coerceIn(min, max),
            onValueChange = { formState[node.id] = formatSliderValue(it, step) },
            valueRange = min..max,
            steps = steps.coerceAtLeast(0),
            enabled = isInteractive,
            modifier = Modifier.fillMaxWidth()
                .handCursor(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    drawStopIndicator = null,
                    drawTick = { _, _ -> },
                )
            },
        )
    }
}

private fun formatSliderValue(value: Float, step: Float?): String {
    if (step != null && step > 0) {
        val rounded = kotlin.math.round(value / step) * step
        if (rounded == rounded.toLong().toFloat()) {
            return rounded.toLong().toString()
        }
        // Determine decimal places from step (e.g. step=0.1 → 1 decimal)
        val stepStr = step.toString()
        val decimals = stepStr.substringAfter('.', "").trimEnd('0').length.coerceIn(1, 6)
        var factor = 1f
        repeat(decimals) { factor *= 10f }
        return (kotlin.math.round(rounded * factor) / factor).toString()
    }
    return if (value == value.toLong().toFloat()) {
        value.toLong().toString()
    } else {
        val rounded = kotlin.math.round(value * 100.0f) / 100.0f
        rounded.toString()
    }
}

@Composable
private fun RenderRadioGroup(
    node: RadioGroupNode,
    isInteractive: Boolean,
    formState: MutableMap<String, String>,
) {
    val selected = formState[node.id] ?: ""
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (node.label != null) {
            Text(
                text = node.label,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        for (option in node.options) {
            key(option) {
                val interactionSource = remember { MutableInteractionSource() }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .handCursor()
                        .then(
                            if (isInteractive) {
                                Modifier.clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = { formState[node.id] = option },
                                )
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    RadioButton(
                        selected = selected == option,
                        onClick = null,
                        enabled = isInteractive,
                        modifier = Modifier.indication(
                            interactionSource = interactionSource,
                            indication = ripple(bounded = false, radius = 20.dp),
                        ),
                        interactionSource = interactionSource,
                    )
                    Text(
                        text = option,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderProgress(node: ProgressNode) {
    Column(Modifier.fillMaxWidth()) {
        if (node.label != null) {
            Text(
                text = node.label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        if (node.value != null) {
            LinearProgressIndicator(
                progress = { node.value.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                gapSize = 0.dp,
            )
        }
    }
}

@Composable
private fun RenderCountdown(
    node: CountdownNode,
    isInteractive: Boolean,
    formState: MutableMap<String, String>,
    toggleState: MutableMap<String, Boolean>,
    onCallback: (String, Map<String, String>) -> Unit,
) {
    val targetMs = remember { Clock.System.now().toEpochMilliseconds() + node.seconds.toLong() * 1000L }
    var remainingSeconds by remember { mutableStateOf<Long>(node.seconds.toLong()) }
    var expired by remember { mutableStateOf(false) }
    val currentOnCallback by rememberUpdatedState(onCallback)

    LaunchedEffect(targetMs) {
        while (true) {
            val diff = (targetMs - Clock.System.now().toEpochMilliseconds()) / 1000L
            remainingSeconds = diff.coerceAtLeast(0L)
            if (diff <= 0L) {
                if (!expired) {
                    expired = true
                    node.id?.let { formState[it] = "0" }
                    if (node.action != null) {
                        try {
                            when (val action = node.action) {
                                is CallbackAction -> {
                                    val data = collectFormData(action, formState)
                                    currentOnCallback(action.event, data)
                                }

                                is ToggleAction -> {
                                    toggleState[action.targetId] = !(toggleState[action.targetId] ?: true)
                                }

                                is OpenUrlAction -> {}

                                null -> {}
                            }
                        } catch (_: Exception) {}
                    }
                }
                break
            }
            node.id?.let { formState[it] = diff.toString() }
            delay(1000L)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        if (node.label != null) {
            Text(
                text = node.label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        val h = remainingSeconds / 3600
        val m = (remainingSeconds % 3600) / 60
        val s = remainingSeconds % 60
        val formatted = if (h > 0) {
            "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
        } else {
            "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
        }
        Text(
            text = formatted,
            style = MaterialTheme.typography.headlineMedium,
            color = if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun RenderAlert(node: AlertNode) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val successContainer = if (isDark) Color(0xFF1B3A1B) else Color(0xFFE8F5E9)
    val onSuccessContainer = if (isDark) Color(0xFFC8E6C9) else Color(0xFF1B5E20)
    val warningContainer = if (isDark) Color(0xFF3D2600) else Color(0xFFFFF3E0)
    val onWarningContainer = if (isDark) Color(0xFFFF9100) else Color(0xFFE65100)
    val containerColor = when (node.severity) {
        AlertSeverity.SUCCESS -> successContainer
        AlertSeverity.WARNING -> warningContainer
        AlertSeverity.ERROR -> MaterialTheme.colorScheme.errorContainer
        AlertSeverity.INFO, null -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when (node.severity) {
        AlertSeverity.SUCCESS -> onSuccessContainer
        AlertSeverity.WARNING -> onWarningContainer
        AlertSeverity.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        AlertSeverity.INFO, null -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AlertIcon(node.severity, contentColor, containerColor)
            Spacer(Modifier.width(12.dp))
            Column {
                if (node.title != null) {
                    Text(
                        text = node.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    text = node.message,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun AlertIcon(severity: AlertSeverity?, contentColor: Color, containerColor: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(20.dp)
            .background(contentColor, androidx.compose.foundation.shape.CircleShape),
    ) {
        when (severity) {
            AlertSeverity.SUCCESS -> Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = containerColor)
            AlertSeverity.ERROR -> Icon(Icons.Default.Close, null, Modifier.size(14.dp), tint = containerColor)
            AlertSeverity.WARNING -> Text("!", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = containerColor)
            AlertSeverity.INFO, null -> Text("i", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = containerColor)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RenderChipGroup(
    node: ChipGroupNode,
    isInteractive: Boolean,
    formState: MutableMap<String, String>,
) {
    val isDisplayOnly = node.selection == "none"
    val isMulti = node.selection == "multi"

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        for (chip in node.chips) {
            val value = chip.value.ifEmpty { chip.label }
            key(value) {
                if (isDisplayOnly) {
                    KaiChip { Text(chip.label) }
                } else {
                    val isSelected by remember {
                        derivedStateOf {
                            val csv = formState[node.id] ?: ""
                            csv.split(",").contains(value)
                        }
                    }
                    KaiChip(
                        selected = isSelected,
                        onClick = {
                            if (!isInteractive) return@KaiChip
                            val current = (formState[node.id] ?: "").split(",").filter { it.isNotEmpty() }.toSet()
                            val newSelection = if (isMulti) {
                                if (isSelected) current - value else current + value
                            } else {
                                if (isSelected) emptySet() else setOf(value)
                            }
                            formState[node.id] = newSelection.joinToString(",")
                        },
                        enabled = isInteractive,
                    ) {
                        Text(chip.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderIcon(node: IconNode) {
    val imageVector = resolveIcon(node.name)
    val size = (node.size ?: 24).dp
    if (imageVector != null) {
        val color = when (node.color) {
            "primary" -> MaterialTheme.colorScheme.primary
            "secondary" -> MaterialTheme.colorScheme.secondary
            "error" -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurface
        }
        Icon(
            imageVector = imageVector,
            contentDescription = node.name,
            modifier = Modifier.size(size),
            tint = color,
        )
    } else if (node.name.isNotEmpty() && node.name.any { it.code > 0x2600 }) {
        Text(
            text = node.name,
            fontSize = size.value.sp,
        )
    }
}

@Composable
private fun RenderCode(node: CodeNode) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            if (node.language != null) {
                Text(
                    text = node.language,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Text(
                text = node.code,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun RenderQuote(node: QuoteNode) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.5.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = node.text,
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (node.source != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "— ${node.source}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RenderBadge(node: BadgeNode) {
    val backgroundColor = when (node.color) {
        "primary" -> MaterialTheme.colorScheme.primary
        "secondary" -> MaterialTheme.colorScheme.secondary
        "error" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val contentColor = when (node.color) {
        "primary" -> MaterialTheme.colorScheme.onPrimary
        "secondary" -> MaterialTheme.colorScheme.onSecondary
        "error" -> MaterialTheme.colorScheme.onError
        else -> MaterialTheme.colorScheme.onPrimary
    }
    Surface(
        color = backgroundColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = node.value,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun RenderStat(node: StatNode) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(min = 72.dp),
    ) {
        Text(
            text = node.value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = node.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (node.description != null) {
            Text(
                text = node.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RenderAvatar(node: AvatarNode) {
    val sizeDp = (node.size ?: 40).coerceIn(24, 80).dp
    if (node.imageUrl != null) {
        Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier.size(sizeDp),
        ) {
            coil3.compose.AsyncImage(
                model = node.imageUrl,
                contentDescription = node.name,
                modifier = Modifier.size(sizeDp),
            )
        }
    } else if (node.name != null) {
        val initials = node.name.split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier.size(sizeDp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(sizeDp)) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    } else {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier.size(sizeDp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(sizeDp)) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(sizeDp * 0.6f),
                )
            }
        }
    }
}

@Composable
private fun RenderBox(
    node: BoxNode,
    isInteractive: Boolean,
    formState: MutableMap<String, String>,
    toggleState: MutableMap<String, Boolean>,
    onCallback: (String, Map<String, String>) -> Unit,
    depth: Int,
) {
    // LLMs frequently misuse box when they mean column, causing children to stack/overlap.
    // Only use Box layout for single-child centering; fall back to Column for multiple children.
    if (node.children.size <= 1 && node.contentAlignment != null) {
        val alignment = when (node.contentAlignment) {
            "center" -> Alignment.Center
            "top_start" -> Alignment.TopStart
            "top_center" -> Alignment.TopCenter
            "top_end" -> Alignment.TopEnd
            "center_start" -> Alignment.CenterStart
            "center_end" -> Alignment.CenterEnd
            "bottom_start" -> Alignment.BottomStart
            "bottom_center" -> Alignment.BottomCenter
            "bottom_end" -> Alignment.BottomEnd
            else -> Alignment.TopStart
        }
        Box(
            contentAlignment = alignment,
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        ) {
            for (child in node.children) {
                RenderNode(child, isInteractive, formState, toggleState, onCallback, depth + 1)
            }
        }
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        ) {
            RenderChildren(node.children, isInteractive, formState, toggleState, onCallback, depth)
        }
    }
}

@Composable
private fun RenderTabs(
    node: TabsNode,
    isInteractive: Boolean,
    formState: MutableMap<String, String>,
    toggleState: MutableMap<String, Boolean>,
    onCallback: (String, Map<String, String>) -> Unit,
    depth: Int,
) {
    if (node.tabs.isEmpty()) return
    var selectedIndex by remember { mutableIntStateOf((node.selectedIndex ?: 0).coerceIn(0, node.tabs.lastIndex)) }
    val pillShape = RoundedCornerShape(50)

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val bleed = 12.dp.roundToPx()
                    val wider = if (constraints.maxWidth == Int.MAX_VALUE) {
                        constraints.maxWidth
                    } else {
                        constraints.maxWidth + bleed * 2
                    }
                    val placeable = measurable.measure(
                        constraints.copy(minWidth = 0, maxWidth = wider),
                    )
                    layout(wider, placeable.height) {
                        placeable.place(0, 0)
                    }
                }
                .horizontalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.width(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(pillShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, pillShape)
                    .padding(4.dp),
            ) {
                node.tabs.forEachIndexed { index, tab ->
                    val isSelected = selectedIndex == index
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(32.dp)
                            .clip(pillShape)
                            .then(
                                if (isSelected) {
                                    Modifier.background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        pillShape,
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .clickable { selectedIndex = index }
                            .handCursor()
                            .padding(horizontal = 16.dp),
                    ) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
        }

        val selectedTab = node.tabs.getOrNull(selectedIndex)
        if (selectedTab != null) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                RenderChildren(selectedTab.children, isInteractive, formState, toggleState, onCallback, depth)
            }
        }
    }
}

@Composable
private fun RenderAccordion(
    node: AccordionNode,
    isInteractive: Boolean,
    formState: MutableMap<String, String>,
    toggleState: MutableMap<String, Boolean>,
    onCallback: (String, Map<String, String>) -> Unit,
    depth: Int,
) {
    var expanded by remember { mutableStateOf(node.expanded ?: false) }

    Surface(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().handCursor(),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = node.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                ) {
                    RenderChildren(node.children, isInteractive, formState, toggleState, onCallback, depth)
                }
            }
        }
    }
}

// --- Icon resolution ---

private fun resolveIcon(name: String): ImageVector? = when (name) {
    "home" -> Icons.Default.Home
    "settings" -> Icons.Default.Settings
    "search" -> Icons.Default.Search
    "add" -> Icons.Default.Add
    "delete" -> Icons.Default.Delete
    "edit" -> Icons.Default.Edit
    "check", "done" -> Icons.Default.Check
    "check_circle" -> Icons.Default.CheckCircle
    "close" -> Icons.Default.Close
    "arrow_back" -> Icons.AutoMirrored.Filled.ArrowBack
    "arrow_forward" -> Icons.AutoMirrored.Filled.ArrowForward
    "star" -> Icons.Default.Star
    "favorite" -> Icons.Default.Favorite
    "share" -> Icons.Default.Share
    "info" -> Icons.Default.Info
    "warning" -> Icons.Default.Warning
    "person" -> Icons.Default.Person
    "group" -> Icons.Default.Face
    "mail", "email" -> Icons.Default.Email
    "phone" -> Icons.Default.Call
    "calendar", "date_range", "schedule" -> Icons.Default.DateRange
    "clock", "access_time" -> Icons.Filled.AccessTime
    "location", "place" -> Icons.Default.LocationOn
    "photo", "image" -> Icons.Filled.Image
    "refresh" -> Icons.Default.Refresh
    "menu" -> Icons.Default.Menu
    "more", "more_vert" -> Icons.Default.MoreVert
    "send" -> Icons.AutoMirrored.Filled.Send
    "notifications" -> Icons.Default.Notifications
    "expand_more" -> Icons.Default.KeyboardArrowDown
    "expand_less" -> Icons.Default.KeyboardArrowUp
    "trending_up" -> Icons.AutoMirrored.Filled.TrendingUp
    "trending_down" -> Icons.AutoMirrored.Filled.TrendingDown
    "trending_flat" -> Icons.AutoMirrored.Filled.TrendingFlat
    "thumb_up" -> Icons.Default.ThumbUp
    "thumb_down" -> Icons.Filled.ThumbDown
    "visibility" -> Icons.Filled.Visibility
    "visibility_off" -> Icons.Filled.VisibilityOff
    "lock" -> Icons.Default.Lock
    "lock_open" -> Icons.Filled.LockOpen
    "shopping_cart", "cart" -> Icons.Default.ShoppingCart
    "play_arrow", "play" -> Icons.Default.PlayArrow
    "pause" -> Icons.Filled.Pause
    "stop" -> Icons.Filled.Stop
    "skip_next" -> Icons.Filled.SkipNext
    "skip_previous" -> Icons.Filled.SkipPrevious
    "download" -> Icons.Filled.Download
    "upload" -> Icons.Filled.Upload
    "cloud" -> Icons.Filled.Cloud
    "attach_file", "attachment" -> Icons.Filled.AttachFile
    "link" -> Icons.Filled.Link
    "code" -> Icons.Filled.Code
    "terminal" -> Icons.Filled.Terminal
    "build", "construction" -> Icons.Default.Build
    "bug_report", "bug" -> Icons.Filled.BugReport
    "lightbulb", "idea" -> Icons.Filled.Lightbulb
    "science", "flask" -> Icons.Filled.Science
    "school", "education" -> Icons.Filled.School
    "work", "business" -> Icons.Filled.Work
    "account_circle" -> Icons.Default.AccountCircle
    "language", "globe" -> Icons.Filled.Language
    "translate" -> Icons.Filled.Translate
    "dark_mode", "moon" -> Icons.Filled.DarkMode
    "light_mode", "sun" -> Icons.Filled.LightMode
    "bolt", "flash", "lightning" -> Icons.Filled.Bolt
    "rocket_launch", "rocket" -> Icons.Filled.RocketLaunch
    "savings", "money" -> Icons.Filled.Savings
    "payments", "credit_card" -> Icons.Filled.Payments
    "receipt" -> Icons.Filled.Receipt
    "inventory" -> Icons.Filled.Inventory
    "category" -> Icons.Filled.Category
    "dashboard" -> Icons.Filled.Dashboard
    "analytics" -> Icons.Filled.Analytics
    "bar_chart", "chart" -> Icons.Filled.BarChart
    "pie_chart" -> Icons.Filled.PieChart
    "show_chart" -> Icons.AutoMirrored.Filled.ShowChart
    "timer" -> Icons.Filled.Timer
    "alarm" -> Icons.Filled.Alarm
    "task", "task_alt" -> Icons.Filled.TaskAlt
    "bookmark" -> Icons.Filled.Bookmark
    "flag" -> Icons.Filled.Flag
    "label", "tag" -> Icons.AutoMirrored.Filled.Label
    "pin", "push_pin" -> Icons.Filled.PushPin
    "copy", "content_copy" -> Icons.Filled.ContentCopy
    "paste", "content_paste" -> Icons.Filled.ContentPaste
    "cut", "content_cut" -> Icons.Filled.ContentCut
    "undo" -> Icons.AutoMirrored.Filled.Undo
    "redo" -> Icons.AutoMirrored.Filled.Redo
    "filter", "filter_list" -> Icons.Filled.FilterList
    "sort" -> Icons.AutoMirrored.Filled.Sort
    "swap", "swap_horiz" -> Icons.Filled.SwapHoriz
    "sync" -> Icons.Filled.Sync
    "wifi" -> Icons.Filled.Wifi
    "bluetooth" -> Icons.Filled.Bluetooth
    "battery_full", "battery" -> Icons.Filled.BatteryFull
    "speed" -> Icons.Filled.Speed
    "security", "shield" -> Icons.Filled.Security
    "verified" -> Icons.Filled.Verified
    "health", "medical", "healing" -> Icons.Filled.Healing
    "fitness", "fitness_center" -> Icons.Filled.FitnessCenter
    "restaurant", "food" -> Icons.Filled.Restaurant
    "local_cafe", "coffee" -> Icons.Filled.LocalCafe
    "flight", "airplane" -> Icons.Filled.Flight
    "hotel" -> Icons.Filled.Hotel
    "directions_car", "car" -> Icons.Filled.DirectionsCar
    "public", "earth" -> Icons.Filled.Public
    "map" -> Icons.Filled.Map
    "explore", "compass" -> Icons.Filled.Explore
    "pets", "pet" -> Icons.Filled.Pets
    "eco", "leaf", "nature" -> Icons.Filled.Eco
    "water_drop", "water" -> Icons.Filled.WaterDrop
    "sunny", "weather" -> Icons.Filled.WbSunny
    "celebration", "party" -> Icons.Filled.Celebration
    "emoji_events", "trophy" -> Icons.Filled.EmojiEvents
    "military_tech", "medal" -> Icons.Filled.MilitaryTech
    "workspace_premium", "premium" -> Icons.Filled.WorkspacePremium
    else -> null
}

// --- Form state initialization ---

private fun initializeFormState(node: KaiUiNode, formState: MutableMap<String, String>) {
    when (node) {
        is TextInputNode -> node.value?.let { if (node.id !in formState) formState[node.id] = it }

        is CheckboxNode -> if (node.id !in formState) formState[node.id] = (node.checked ?: false).toString()

        is SelectNode -> node.selected?.let { if (node.id !in formState) formState[node.id] = it }

        is SwitchNode -> if (node.id !in formState) formState[node.id] = (node.checked ?: false).toString()

        is SliderNode -> if (node.id !in formState) formState[node.id] = formatSliderValue(node.value ?: node.min ?: 0f, node.step)

        is RadioGroupNode -> node.selected?.let { if (node.id !in formState) formState[node.id] = it }

        is ChipGroupNode -> if (node.selection != "none" && node.id !in formState) {
            formState[node.id] = ""
        }

        is ColumnNode -> node.children.forEach { initializeFormState(it, formState) }

        is RowNode -> node.children.forEach { initializeFormState(it, formState) }

        is CardNode -> node.children.forEach { initializeFormState(it, formState) }

        is ListNode -> node.items.forEach { initializeFormState(it, formState) }

        is BoxNode -> node.children.forEach { initializeFormState(it, formState) }

        is TabsNode -> node.tabs.forEach { tab -> tab.children.forEach { initializeFormState(it, formState) } }

        is AccordionNode -> node.children.forEach { initializeFormState(it, formState) }

        else -> {}
    }
}

private fun collectFormData(action: CallbackAction, formState: Map<String, String>): Map<String, String> {
    val collected = mutableMapOf<String, String>()
    action.dataAsStrings?.let { collected.putAll(it) }
    action.collectFrom?.forEach { inputId ->
        formState[inputId]?.let { collected[inputId] = it }
    }
    return collected
}
