package com.inspiredandroid.kai.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.Key
import com.inspiredandroid.kai.data.RemoteDataRepository
import com.inspiredandroid.kai.getBackgroundDispatcher
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(settings: Settings, private val dataRepository: RemoteDataRepository) : ViewModel() {

    init {
        if (settings.getString(Key.GROQ_API_KEY, "").isNotBlank()) {
            updateGroqModels()
        }
    }

    private val _state = MutableStateFlow(
        SettingsUiState(
            onClickService = ::onClickService,
            onClickGroqModel = ::onClickGrowModel,
            onClickGeminiModel = ::onClickGeminiModel,
            groqApiKey = settings.getString(Key.GROQ_API_KEY, ""),
            geminiApiKey = settings.getString(Key.GEMINI_API_KEY, ""),
            onChangeGroqApiKey = ::onChangeGroqApiKey,
            onChangeGeminiApiKey = ::onChangeGeminiApiKey,
        ),
    )

    val state = combine(
        _state,
        dataRepository.groqModels,
        dataRepository.geminiModels,
        dataRepository.services,
    ) { state, groqModels, geminiModels, services ->
        state.copy(
            services = services,
            groqModels = groqModels,
            growSelectedModel = groqModels.firstOrNull { it.isSelected },
            geminiModels = geminiModels,
            geminiSelectedModel = geminiModels.firstOrNull { it.isSelected },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _state.value,
    )

    fun onClickService(id: String) {
        dataRepository.updateSelectedService(id)
    }

    fun onClickGrowModel(id: String) {
        dataRepository.updateGroqModel(id)
    }

    fun onClickGeminiModel(id: String) {
        dataRepository.updateGeminiModel(id)
    }

    fun onChangeGroqApiKey(apiKey: String) {
        dataRepository.changeGroqApiKey(apiKey)
        _state.update {
            it.copy(groqApiKey = apiKey)
        }
    }

    fun onChangeGeminiApiKey(apiKey: String) {
        dataRepository.changeGeminiApiKey(apiKey)
        _state.update {
            it.copy(geminiApiKey = apiKey)
        }
    }

    private fun updateGroqModels() {
        viewModelScope.launch(context = getBackgroundDispatcher()) {
            dataRepository.fetchGroqModels()
        }
    }
}
