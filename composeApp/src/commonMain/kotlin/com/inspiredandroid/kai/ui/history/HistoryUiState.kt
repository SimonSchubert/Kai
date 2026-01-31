package com.inspiredandroid.kai.ui.history

import androidx.compose.runtime.Immutable
import com.inspiredandroid.kai.data.Conversation

@Immutable
data class HistoryUiState(
    val conversations: List<Conversation> = emptyList(),
    val showDeleteAllDialog: Boolean = false,
)
