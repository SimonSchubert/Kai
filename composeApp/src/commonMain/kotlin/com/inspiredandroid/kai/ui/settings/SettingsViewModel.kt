package com.inspiredandroid.kai.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.data.DataRepository
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.getBackgroundDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(private val dataRepository: DataRepository) : ViewModel() {

    private val currentService = MutableStateFlow(dataRepository.currentService())
    private var connectionCheckJob: Job? = null
    private var hasCheckedInitialConnection = false

    private val _state = MutableStateFlow(
        SettingsUiState(
            currentService = currentService.value,
            apiKey = dataRepository.getApiKey(currentService.value),
            baseUrl = dataRepository.getBaseUrl(currentService.value),
            onSelectService = ::onSelectService,
            onSelectModel = ::onSelectModel,
            onChangeApiKey = ::onChangeApiKey,
            onChangeBaseUrl = ::onChangeBaseUrl,
        ),
    )

    val state = currentService.flatMapLatest { service ->
        combine(
            _state,
            dataRepository.getModels(service),
        ) { localState, models ->
            localState.copy(
                currentService = service,
                models = if (service == Service.Groq) models.sortedByDescending { it.createdAt } else models,
                selectedModel = models.firstOrNull { it.isSelected },
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _state.value,
    )

    fun onScreenVisible() {
        if (!hasCheckedInitialConnection) {
            hasCheckedInitialConnection = true
            checkConnection(currentService.value)
        }
    }

    private fun onSelectService(service: Service) {
        dataRepository.selectService(service)
        currentService.value = service
        _state.update {
            it.copy(
                currentService = service,
                apiKey = dataRepository.getApiKey(service),
                baseUrl = dataRepository.getBaseUrl(service),
                connectionStatus = ConnectionStatus.Unknown,
            )
        }
        checkConnection(service)
    }

    private fun onSelectModel(modelId: String) {
        dataRepository.updateSelectedModel(currentService.value, modelId)
    }

    private fun onChangeApiKey(apiKey: String) {
        val service = currentService.value
        dataRepository.updateApiKey(service, apiKey)
        dataRepository.clearModels(service)
        _state.update {
            it.copy(apiKey = apiKey, connectionStatus = ConnectionStatus.Unknown)
        }
        checkConnectionDebounced(service)
    }

    private fun onChangeBaseUrl(baseUrl: String) {
        val service = currentService.value
        dataRepository.updateBaseUrl(service, baseUrl)
        dataRepository.clearModels(service)
        _state.update {
            it.copy(baseUrl = baseUrl, connectionStatus = ConnectionStatus.Unknown)
        }
        checkConnectionDebounced(service)
    }

    private fun checkConnectionDebounced(service: Service) {
        connectionCheckJob?.cancel()
        connectionCheckJob = viewModelScope.launch {
            delay(800) // Debounce to avoid checking on every keystroke
            checkConnection(service)
        }
    }

    private fun checkConnection(service: Service) {
        when (service) {
            Service.Free -> {
                // Free tier always works
                _state.update { it.copy(connectionStatus = ConnectionStatus.Connected) }
            }

            Service.Gemini, Service.Groq -> {
                // These services require an API key
                val hasApiKey = dataRepository.getApiKey(service).isNotBlank()
                if (hasApiKey) {
                    validateConnectionWithStatus(service)
                } else {
                    _state.update { it.copy(connectionStatus = ConnectionStatus.Unknown) }
                }
            }

            Service.Ollama -> {
                // Ollama doesn't need an API key, just validate connection
                validateConnectionWithStatus(service)
            }
        }
    }

    private fun validateConnectionWithStatus(service: Service) {
        _state.update { it.copy(connectionStatus = ConnectionStatus.Checking) }
        viewModelScope.launch(context = getBackgroundDispatcher()) {
            try {
                dataRepository.validateConnection(service)
                _state.update { it.copy(connectionStatus = ConnectionStatus.Connected) }
            } catch (_: Exception) {
                _state.update { it.copy(connectionStatus = ConnectionStatus.Error) }
            }
        }
    }
}
