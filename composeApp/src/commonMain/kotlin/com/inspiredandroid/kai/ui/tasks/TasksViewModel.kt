package com.inspiredandroid.kai.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.data.DataRepository
import com.inspiredandroid.kai.data.ScheduledTask
import com.inspiredandroid.kai.data.TaskStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TaskSortOption {
    NEXT_EXECUTION,
    OLDEST,
    NEWEST,
}

data class TasksUiState(
    val allTasks: ImmutableList<ScheduledTask> = kotlinx.collections.immutable.persistentListOf(),
    val filteredTasks: ImmutableList<ScheduledTask> = kotlinx.collections.immutable.persistentListOf(),
    val searchQuery: String = "",
    val selectedStatus: TaskStatus? = null,
    val sortBy: TaskSortOption = TaskSortOption.NEXT_EXECUTION,
    val isSchedulingEnabled: Boolean = true,
    val pendingDeletionId: String? = null,
)

class TasksViewModel(
    private val dataRepository: DataRepository,
) : ViewModel() {

    private var pendingDeleteJob: Job? = null

    private val _searchQuery = MutableStateFlow("")
    private val _selectedStatus = MutableStateFlow<TaskStatus?>(null)
    private val _sortBy = MutableStateFlow(TaskSortOption.NEXT_EXECUTION)

    private val _allTasks = MutableStateFlow(
        dataRepository.getScheduledTasks().toImmutableList()
    )

    private val _isSchedulingEnabled = MutableStateFlow(dataRepository.isSchedulingEnabled())

    private val _pendingDeletionId = MutableStateFlow<String?>(null)

    val state = combine(
        _allTasks,
        _searchQuery,
        _selectedStatus,
        _sortBy,
        _isSchedulingEnabled,
        _pendingDeletionId,
    ) { flows: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val allTasks = flows[0] as ImmutableList<ScheduledTask>
        val query = flows[1] as String
        val status = flows[2] as TaskStatus?
        val sort = flows[3] as TaskSortOption
        val enabled = flows[4] as Boolean
        val pendingId = flows[5] as String?
        val filtered = filterAndSortTasks(allTasks, query, status, sort)
        TasksUiState(
            allTasks = allTasks,
            filteredTasks = filtered.toImmutableList(),
            searchQuery = query,
            selectedStatus = status,
            sortBy = sort,
            isSchedulingEnabled = enabled,
            pendingDeletionId = pendingId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TasksUiState(
            allTasks = _allTasks.value,
            filteredTasks = _allTasks.value,
            isSchedulingEnabled = _isSchedulingEnabled.value,
        ),
    )

    fun refresh() {
        _allTasks.value = dataRepository.getScheduledTasks().toImmutableList()
        _isSchedulingEnabled.value = dataRepository.isSchedulingEnabled()
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onSelectStatus(status: TaskStatus?) {
        _selectedStatus.value = status
    }

    fun onSortBy(option: TaskSortOption) {
        _sortBy.value = option
    }

    fun onToggleScheduling(enabled: Boolean) {
        dataRepository.setSchedulingEnabled(enabled)
        _isSchedulingEnabled.value = enabled
    }

    fun onCancelTask(id: String) {
        _pendingDeletionId.value = id
        pendingDeleteJob?.cancel()
        pendingDeleteJob = viewModelScope.launch {
            delay(4000)
            confirmCancel(id)
        }
    }

    fun undoCancel() {
        pendingDeleteJob?.cancel()
        _pendingDeletionId.value = null
    }

    private fun confirmCancel(id: String) {
        viewModelScope.launch {
            dataRepository.cancelScheduledTask(id)
            _allTasks.value = dataRepository.getScheduledTasks().toImmutableList()
            if (_pendingDeletionId.value == id) {
                _pendingDeletionId.value = null
            }
        }
    }

    private fun filterAndSortTasks(
        tasks: List<ScheduledTask>,
        query: String,
        status: TaskStatus?,
        sort: TaskSortOption,
    ): List<ScheduledTask> {
        var result = tasks

        if (query.isNotBlank()) {
            val lowerQuery = query.lowercase()
            result = result.filter { task ->
                task.description.lowercase().contains(lowerQuery) ||
                    task.prompt.lowercase().contains(lowerQuery)
            }
        }

        if (status != null) {
            result = result.filter { it.status == status }
        }

        result = when (sort) {
            TaskSortOption.NEXT_EXECUTION -> result.sortedBy { it.scheduledAtEpochMs }
            TaskSortOption.OLDEST -> result.sortedBy { it.createdAtEpochMs }
            TaskSortOption.NEWEST -> result.sortedByDescending { it.createdAtEpochMs }
        }

        return result
    }
}
