package com.inspiredandroid.kai.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.formatContextWindow
import com.inspiredandroid.kai.formatReleaseDate
import com.inspiredandroid.kai.ui.KaiOutlinedTextField
import com.inspiredandroid.kai.ui.components.VerticalScrollbarForGrid
import com.inspiredandroid.kai.ui.handCursor
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.ic_arrow_drop_down
import kai.composeapp.generated.resources.model_sort_context
import kai.composeapp.generated.resources.model_sort_date
import kai.composeapp.generated.resources.model_sort_score
import kai.composeapp.generated.resources.settings_model_label
import kai.composeapp.generated.resources.settings_model_search
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelSelection(
    currentSelectedModel: SettingsModel?,
    models: ImmutableList<SettingsModel>,
    onClick: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    if (models.isNotEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            KaiOutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = currentSelectedModel?.let { it.displayName ?: it.id } ?: "",
                onValueChange = {},
                readOnly = true,
                label = {
                    Text(
                        stringResource(Res.string.settings_model_label),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                trailingIcon = {
                    Icon(
                        modifier = Modifier.handCursor(),
                        imageVector = vectorResource(Res.drawable.ic_arrow_drop_down),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                },
            )
            // Transparent overlay to capture clicks reliably on all platforms
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .handCursor()
                    .clickable { expanded = true },
            )
        }
        if (expanded) {
            ModalBottomSheet(
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                onDismissRequest = {
                    expanded = false
                },
            ) {
                var searchQuery by remember { mutableStateOf("") }
                val filteredModels = if (searchQuery.isBlank()) {
                    models
                } else {
                    models.filter {
                        it.id.contains(searchQuery, ignoreCase = true) ||
                            it.subtitle.contains(searchQuery, ignoreCase = true) ||
                            it.displayName?.contains(searchQuery, ignoreCase = true) == true
                    }
                }
                if (models.size > 6) {
                    KaiOutlinedTextField(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(stringResource(Res.string.settings_model_search))
                        },
                        singleLine = true,
                    )
                }
                var sortOption by remember { mutableStateOf(ModelSortOption.Score) }
                val sortedModels = remember(filteredModels, sortOption) {
                    filteredModels.sortedWith(sortOption.comparator)
                }
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ModelSortOption.entries.forEach { option ->
                        val isSelected = sortOption == option
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                            modifier = Modifier.handCursor(),
                            onClick = { sortOption = option },
                        ) {
                            Text(
                                text = stringResource(option.labelRes),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                val gridState = rememberLazyGridState()
                LaunchedEffect(sortOption) {
                    gridState.scrollToItem(0)
                }
                Box {
                    LazyVerticalGrid(
                        GridCells.Adaptive(300.dp),
                        state = gridState,
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(sortedModels, key = { it.id }) { model ->
                            ModelCard(
                                model = model,
                                isSelected = currentSelectedModel?.id == model.id,
                                onClick = {
                                    onClick(model.id)
                                    expanded = false
                                },
                            )
                        }
                    }
                    VerticalScrollbarForGrid(
                        gridState = gridState,
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

private enum class ModelSortOption(
    val labelRes: StringResource,
    val comparator: Comparator<SettingsModel>,
) {
    Date(Res.string.model_sort_date, compareByDescending<SettingsModel> { it.releaseDate }.thenBy { it.id }),
    Score(Res.string.model_sort_score, compareByDescending<SettingsModel> { it.arenaScore }.thenBy { it.id }),
    Ctx(Res.string.model_sort_context, compareByDescending<SettingsModel> { it.contextWindow }.thenBy { it.id }),
}

@Composable
private fun ModelCard(model: SettingsModel, isSelected: Boolean, onClick: () -> Unit) {
    val displayName = model.displayName?.takeIf { it.isNotBlank() && it != model.id }
    val title = displayName ?: model.id
    val secondary = if (displayName == null && model.subtitle.isNotBlank()) model.subtitle else null
    val contextText = model.contextWindow?.let { formatContextWindow(it) }
    val releaseText = model.releaseDate?.let { formatReleaseDate(it) }
    val detailText = listOfNotNull(releaseText, model.parameterCount, contextText)
        .joinToString("  ·  ").ifEmpty { null }

    Card(
        modifier = Modifier.handCursor().clip(CardDefaults.shape).clickable { onClick() },
        shape = CardDefaults.shape,
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                    modifier = Modifier.weight(1f),
                )
                model.arenaScore?.let { score ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$score",
                        style = MaterialTheme.typography.labelSmall,
                        color = arenaScoreColor(score),
                    )
                }
            }
            secondary?.let {
                Text(
                    text = it,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
            detailText?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
        }
    }
}

private fun arenaScoreColor(score: Int): Color = when {
    score >= 1400 -> Color(0xFF2E7D32)

    // green 800
    score >= 1350 -> Color(0xFF558B2F)

    // light green 800
    score >= 1300 -> Color(0xFF9E9D24)

    // lime 800
    score >= 1250 -> Color(0xFFF9A825)

    // yellow 800
    else -> Color(0xFFEF6C00) // orange 800
}
