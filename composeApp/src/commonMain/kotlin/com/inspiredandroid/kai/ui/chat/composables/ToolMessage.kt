package com.inspiredandroid.kai.ui.chat.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.tool_executing_content_description
import kai.composeapp.generated.resources.waiting_brewing
import kai.composeapp.generated.resources.waiting_content_description
import kai.composeapp.generated.resources.waiting_thinking
import kai.composeapp.generated.resources.waiting_working
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

data class ToolEntry(
    val id: String,
    val name: String,
    val visible: Boolean,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WaitingResponseRow(
    executingTools: List<Pair<String, String>>,
) {
    val knownTools = remember { mutableStateListOf<ToolEntry>() }

    // Update known tools: mark existing ones visible/invisible, add new ones
    val currentIds = executingTools.map { it.first }.toSet()
    // Add new tools
    for ((id, name) in executingTools) {
        if (knownTools.none { it.id == id }) {
            knownTools.add(ToolEntry(id, name, visible = true))
        }
    }
    // Update visibility
    for (i in knownTools.indices) {
        val tool = knownTools[i]
        val shouldBeVisible = tool.id in currentIds
        if (tool.visible != shouldBeVisible) {
            knownTools[i] = tool.copy(visible = shouldBeVisible)
        }
    }

    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WaitingChip()

        for (tool in knownTools) {
            key(tool.id) {
                AnimatedVisibility(
                    visible = tool.visible,
                    enter = fadeIn(tween(300)) + expandHorizontally(tween(300)),
                    exit = fadeOut(tween(300)) + shrinkHorizontally(tween(300)),
                ) {
                    ToolChip(toolName = tool.name)
                }
            }
        }
    }
}

@Composable
private fun WaitingChip() {
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
    )
    val waitingCd = stringResource(Res.string.waiting_content_description)
    val waitingTexts = remember {
        listOf(
            Res.string.waiting_thinking,
            Res.string.waiting_working,
            Res.string.waiting_brewing,
        )
    }
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            index = (index + 1) % waitingTexts.size
        }
    }

    Row(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(8.dp),
            )
            .padding(12.dp)
            .semantics { contentDescription = waitingCd },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .scale(pulseScale)
                .alpha(pulseAlpha)
                .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(waitingTexts[index]),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ToolChip(toolName: String) {
    Row(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.primaryContainer,
                RoundedCornerShape(8.dp),
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.size(16.dp),
            imageVector = Icons.Default.Build,
            contentDescription = stringResource(Res.string.tool_executing_content_description),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = toolName,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
