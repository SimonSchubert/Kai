package com.inspiredandroid.kai.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.data.RemoteDataRepository
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.getBackgroundDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(private val dataRepository: RemoteDataRepository) : ViewModel() {

    private val currentService = MutableStateFlow(dataRepository.currentService())

    init {
        val currentService = currentService.value
        if (currentService.requiresApiKey && dataRepository.getApiKey(currentService).isNotBlank()) {
            fetchModels(currentService)
        }
    }

    private val _state = MutableStateFlow(
        SettingsUiState(
            currentService = currentService.value,
            apiKey = dataRepository.getApiKey(currentService.value),
            onSelectService = ::onSelectService,
            onSelectModel = ::onSelectModel,
            onChangeApiKey = ::onChangeApiKey,
        ),
    )

    val state = currentService.flatMapLatest { service ->
        dataRepository.getModels(service).map { models ->
            _state.value.copy(
                currentService = service,
                apiKey = dataRepository.getApiKey(service),
                models = if (service == Service.Groq) models.sortedByDescending { it.createdAt } else models,
                selectedModel = models.firstOrNull { it.isSelected },
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _state.value,
    )

    private fun onSelectService(service: Service) {
        dataRepository.selectService(service)
        currentService.value = service
        _state.update {
            it.copy(
                currentService = service,
                apiKey = dataRepository.getApiKey(service),
            )
        }
    }

    private fun onSelectModel(modelId: String) {
        dataRepository.updateSelectedModel(currentService.value, modelId)
    }

    private fun onChangeApiKey(apiKey: String) {
        val service = currentService.value
        dataRepository.updateApiKey(service, apiKey)
        _state.update {
            it.copy(apiKey = apiKey)
        }
    }

    private fun fetchModels(service: Service) {
        viewModelScope.launch(context = getBackgroundDispatcher()) {
            try {
                dataRepository.fetchModels(service)
            } catch (_: Exception) {
                // Network error - models remain at default values
            }
        }
    }
}
