package com.inspiredandroid.kai.ui.soul

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.data.DataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SoulUiState(
    val soulText: String = "",
)

class SoulViewModel(
    private val dataRepository: DataRepository,
) : ViewModel() {

    private val _soulText = MutableStateFlow(dataRepository.getSoulText())

    val state = combine(_soulText) { flows ->
        @Suppress("UNCHECKED_CAST")
        val soulText = flows[0] as String
        SoulUiState(soulText = soulText)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SoulUiState(soulText = _soulText.value),
    )

    fun onScreenVisible() {
        _soulText.value = dataRepository.getSoulText()
    }

    fun onSaveSoul(soulText: String) {
        dataRepository.setSoulText(soulText)
        _soulText.value = soulText
    }
}