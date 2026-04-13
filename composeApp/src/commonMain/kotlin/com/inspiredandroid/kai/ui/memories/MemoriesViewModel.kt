package com.inspiredandroid.kai.ui.memories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.data.DataRepository
import com.inspiredandroid.kai.data.MemoryCategory
import com.inspiredandroid.kai.data.MemoryEntry
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MemorySortOption {
    NEWEST,
    OLDEST,
    MOST_USED,
    LEAST_USED,
    LAST_MODIFIED,
}

data class MemoriesUiState(
    val allMemories: ImmutableList<MemoryEntry> = kotlinx.collections.immutable.persistentListOf(),
    val filteredMemories: ImmutableList<MemoryEntry> = kotlinx.collections.immutable.persistentListOf(),
    val searchQuery: String = "",
    val selectedCategory: MemoryCategory? = null,
    val sortBy: MemorySortOption = MemorySortOption.NEWEST,
    val isMemoryEnabled: Boolean = true,
    val pendingDeletionKey: String? = null,
)

class MemoriesViewModel(
    private val dataRepository: DataRepository,
) : ViewModel() {

    private var pendingDeleteJob: Job? = null

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow<MemoryCategory?>(null)
    private val _sortBy = MutableStateFlow(MemorySortOption.NEWEST)

    private val _allMemories = MutableStateFlow(
        dataRepository.getMemories().toImmutableList()
    )

    private val _isMemoryEnabled = MutableStateFlow(dataRepository.isMemoryEnabled())

    private val _pendingDeletionKey = MutableStateFlow<String?>(null)

    val state = combine(
        _allMemories,
        _searchQuery,
        _selectedCategory,
        _sortBy,
        _isMemoryEnabled,
        _pendingDeletionKey,
    ) { flows: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val allMemories = flows[0] as ImmutableList<MemoryEntry>
        val query = flows[1] as String
        val category = flows[2] as MemoryCategory?
        val sort = flows[3] as MemorySortOption
        val enabled = flows[4] as Boolean
        val pendingKey = flows[5] as String?
        val filtered = filterAndSortMemories(allMemories, query, category, sort)
        MemoriesUiState(
            allMemories = allMemories,
            filteredMemories = filtered.toImmutableList(),
            searchQuery = query,
            selectedCategory = category,
            sortBy = sort,
            isMemoryEnabled = enabled,
            pendingDeletionKey = pendingKey,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MemoriesUiState(
            allMemories = _allMemories.value,
            filteredMemories = _allMemories.value,
            isMemoryEnabled = _isMemoryEnabled.value,
        ),
    )

    fun refresh() {
        _allMemories.value = dataRepository.getMemories().toImmutableList()
        _isMemoryEnabled.value = dataRepository.isMemoryEnabled()
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onSelectCategory(category: MemoryCategory?) {
        _selectedCategory.value = category
    }

    fun onSortBy(option: MemorySortOption) {
        _sortBy.value = option
    }

    fun onToggleMemory(enabled: Boolean) {
        dataRepository.setMemoryEnabled(enabled)
        _isMemoryEnabled.value = enabled
    }

    fun onDeleteMemory(key: String) {
        _pendingDeletionKey.value = key
        pendingDeleteJob?.cancel()
        pendingDeleteJob = viewModelScope.launch {
            delay(4000)
            confirmDelete(key)
        }
    }

    fun undoDelete() {
        pendingDeleteJob?.cancel()
        _pendingDeletionKey.value = null
    }

    private fun confirmDelete(key: String) {
        viewModelScope.launch {
            dataRepository.deleteMemory(key)
            _allMemories.value = dataRepository.getMemories().toImmutableList()
            if (_pendingDeletionKey.value == key) {
                _pendingDeletionKey.value = null
            }
        }
    }

    private fun filterAndSortMemories(
        memories: List<MemoryEntry>,
        query: String,
        category: MemoryCategory?,
        sort: MemorySortOption,
    ): List<MemoryEntry> {
        var result = memories

        if (query.isNotBlank()) {
            val lowerQuery = query.lowercase()
            result = result.filter { memory ->
                memory.key.lowercase().contains(lowerQuery) ||
                    memory.content.lowercase().contains(lowerQuery)
            }
        }

        if (category != null) {
            result = result.filter { it.category == category }
        }

        result = when (sort) {
            MemorySortOption.NEWEST -> result.sortedByDescending { it.createdAt }
            MemorySortOption.OLDEST -> result.sortedBy { it.createdAt }
            MemorySortOption.MOST_USED -> result.sortedByDescending { it.hitCount }
            MemorySortOption.LEAST_USED -> result.sortedBy { it.hitCount }
            MemorySortOption.LAST_MODIFIED -> result.sortedByDescending { it.updatedAt }
        }

        return result
    }
}
