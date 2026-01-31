package com.inspiredandroid.kai.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.data.DataRepository
import com.inspiredandroid.kai.getBackgroundDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class HistoryViewModel(
    private val dataRepository: DataRepository,
    private val backgroundDispatcher: CoroutineContext = getBackgroundDispatcher(),
) : ViewModel() {

    private val showDeleteAllDialogFlow = MutableStateFlow(false)

    val state = combine(
        dataRepository.savedConversations,
        showDeleteAllDialogFlow,
    ) { conversations, showDialog ->
        HistoryUiState(
            conversations = conversations.sortedByDescending { it.updatedAt },
            showDeleteAllDialog = showDialog,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState(),
    )

    fun deleteConversation(id: String) {
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.deleteConversation(id)
        }
    }

    fun showDeleteAllDialog() {
        showDeleteAllDialogFlow.update { true }
    }

    fun hideDeleteAllDialog() {
        showDeleteAllDialogFlow.update { false }
    }

    fun deleteAllConversations() {
        viewModelScope.launch(backgroundDispatcher) {
            dataRepository.deleteAllConversations()
            showDeleteAllDialogFlow.update { false }
        }
    }
}
