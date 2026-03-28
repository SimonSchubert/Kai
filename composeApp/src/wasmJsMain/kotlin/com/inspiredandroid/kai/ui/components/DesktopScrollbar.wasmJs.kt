package com.inspiredandroid.kai.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun VerticalScrollbarForList(
    listState: LazyListState,
    modifier: Modifier,
) {}

@Composable
actual fun VerticalScrollbarForScroll(
    scrollState: ScrollState,
    modifier: Modifier,
) {}

@Composable
actual fun VerticalScrollbarForGrid(
    gridState: LazyGridState,
    modifier: Modifier,
) {}
