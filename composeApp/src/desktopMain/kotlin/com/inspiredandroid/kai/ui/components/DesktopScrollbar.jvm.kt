package com.inspiredandroid.kai.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
private fun scrollbarStyle(): ScrollbarStyle {
    val onSurface = MaterialTheme.colorScheme.onSurface
    return remember(onSurface) {
        ScrollbarStyle(
            minimalHeight = 48.dp,
            thickness = 8.dp,
            shape = RoundedCornerShape(4.dp),
            hoverDurationMillis = 300,
            unhoverColor = onSurface.copy(alpha = 0.3f),
            hoverColor = onSurface.copy(alpha = 0.5f),
        )
    }
}

@Composable
actual fun VerticalScrollbarForList(
    listState: LazyListState,
    modifier: Modifier,
) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(listState),
        modifier = modifier,
        style = scrollbarStyle(),
    )
}

@Composable
actual fun VerticalScrollbarForScroll(
    scrollState: ScrollState,
    modifier: Modifier,
) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(scrollState),
        modifier = modifier,
        style = scrollbarStyle(),
    )
}

@Composable
actual fun VerticalScrollbarForGrid(
    gridState: LazyGridState,
    modifier: Modifier,
) {
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(gridState),
        modifier = modifier,
        style = scrollbarStyle(),
    )
}
