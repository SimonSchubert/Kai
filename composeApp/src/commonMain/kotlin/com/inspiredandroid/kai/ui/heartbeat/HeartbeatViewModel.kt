package com.inspiredandroid.kai.ui.heartbeat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.data.DataRepository
import com.inspiredandroid.kai.data.HeartbeatLogEntry
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.data.ServiceEntry
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HeartbeatUiState(
    val isHeartbeatEnabled: Boolean = false,
    val heartbeatIntervalMinutes: Int = 15,
    val activeHoursStart: Int = 8,
    val activeHoursEnd: Int = 22,
    val heartbeatPrompt: String = "",
    val heartbeatLog: ImmutableList<HeartbeatLogEntry> = kotlinx.collections.immutable.persistentListOf(),
    val heartbeatServiceEntries: ImmutableList<ServiceEntry> = kotlinx.collections.immutable.persistentListOf(),
    val heartbeatSelectedInstanceId: String? = null,
)

class HeartbeatViewModel(
    private val dataRepository: DataRepository,
) : ViewModel() {

    private val _isHeartbeatEnabled = MutableStateFlow(dataRepository.getHeartbeatConfig().enabled)
    private val _heartbeatIntervalMinutes = MutableStateFlow(dataRepository.getHeartbeatConfig().intervalMinutes)
    private val _activeHoursStart = MutableStateFlow(dataRepository.getHeartbeatConfig().activeHoursStart)
    private val _activeHoursEnd = MutableStateFlow(dataRepository.getHeartbeatConfig().activeHoursEnd)
    private val _heartbeatPrompt = MutableStateFlow(dataRepository.getHeartbeatPrompt())
    private val _heartbeatLog = MutableStateFlow(dataRepository.getHeartbeatLog().toImmutableList())
    private val _heartbeatServiceEntries = MutableStateFlow(
        dataRepository.getServiceEntries().filter { !Service.fromId(it.serviceId).isOnDevice }.toImmutableList()
    )
    private val _heartbeatSelectedInstanceId = MutableStateFlow(dataRepository.getHeartbeatInstanceId())

    val state = combine(
        _isHeartbeatEnabled,
        _heartbeatIntervalMinutes,
        _activeHoursStart,
        _activeHoursEnd,
        _heartbeatPrompt,
        _heartbeatLog,
        _heartbeatServiceEntries,
        _heartbeatSelectedInstanceId,
    ) { flows: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        HeartbeatUiState(
            isHeartbeatEnabled = flows[0] as Boolean,
            heartbeatIntervalMinutes = flows[1] as Int,
            activeHoursStart = flows[2] as Int,
            activeHoursEnd = flows[3] as Int,
            heartbeatPrompt = flows[4] as String,
            heartbeatLog = flows[5] as ImmutableList<HeartbeatLogEntry>,
            heartbeatServiceEntries = flows[6] as ImmutableList<ServiceEntry>,
            heartbeatSelectedInstanceId = flows[7] as String?,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HeartbeatUiState(
            isHeartbeatEnabled = _isHeartbeatEnabled.value,
            heartbeatIntervalMinutes = _heartbeatIntervalMinutes.value,
            activeHoursStart = _activeHoursStart.value,
            activeHoursEnd = _activeHoursEnd.value,
            heartbeatPrompt = _heartbeatPrompt.value,
            heartbeatLog = _heartbeatLog.value,
            heartbeatServiceEntries = _heartbeatServiceEntries.value,
            heartbeatSelectedInstanceId = _heartbeatSelectedInstanceId.value,
        ),
    )

    fun onScreenVisible() {
        val config = dataRepository.getHeartbeatConfig()
        _isHeartbeatEnabled.value = config.enabled
        _heartbeatIntervalMinutes.value = config.intervalMinutes
        _activeHoursStart.value = config.activeHoursStart
        _activeHoursEnd.value = config.activeHoursEnd
        _heartbeatPrompt.value = dataRepository.getHeartbeatPrompt()
        _heartbeatLog.value = dataRepository.getHeartbeatLog().toImmutableList()
        _heartbeatServiceEntries.value = dataRepository.getServiceEntries().filter { !Service.fromId(it.serviceId).isOnDevice }.toImmutableList()
        _heartbeatSelectedInstanceId.value = dataRepository.getHeartbeatInstanceId()
    }

    fun onToggleHeartbeat(enabled: Boolean) {
        dataRepository.setHeartbeatEnabled(enabled)
        _isHeartbeatEnabled.value = enabled
    }

    fun onChangeInterval(minutes: Int) {
        dataRepository.setHeartbeatIntervalMinutes(minutes)
        _heartbeatIntervalMinutes.value = minutes
    }

    fun onChangeActiveHours(start: Int, end: Int) {
        dataRepository.setHeartbeatActiveHours(start, end)
        _activeHoursStart.value = start
        _activeHoursEnd.value = end
    }

    fun onSaveHeartbeatPrompt(prompt: String) {
        dataRepository.setHeartbeatPrompt(prompt)
        _heartbeatPrompt.value = prompt
    }

    fun onChangeHeartbeatService(instanceId: String?) {
        dataRepository.setHeartbeatInstanceId(instanceId)
        _heartbeatSelectedInstanceId.value = instanceId
    }
}