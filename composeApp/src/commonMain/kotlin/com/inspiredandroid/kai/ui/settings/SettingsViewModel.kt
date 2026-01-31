package com.inspiredandroid.kai.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.data.DataRepository
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.getBackgroundDispatcher
import com.inspiredandroid.kai.network.GeminiInvalidApiKeyException
import com.inspiredandroid.kai.network.GeminiRateLimitExceededException
import com.inspiredandroid.kai.network.OpenAICompatibleConnectionException
import com.inspiredandroid.kai.network.OpenAICompatibleInvalidApiKeyException
import com.inspiredandroid.kai.network.OpenAICompatibleQuotaExhaustedException
import com.inspiredandroid.kai.network.OpenAICompatibleRateLimitExceededException
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
                models = if (service == Service.Groq || service == Service.XAI || service == Service.OpenRouter) models.sortedByDescending { it.createdAt } else models,
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
        // Update _state BEFORE currentService to avoid race condition:
        // flatMapLatest triggers on currentService change, so _state must have
        // the correct apiKey/baseUrl before that happens
        _state.update {
            it.copy(
                currentService = service,
                apiKey = dataRepository.getApiKey(service),
                baseUrl = dataRepository.getBaseUrl(service),
                connectionStatus = ConnectionStatus.Unknown,
            )
        }
        currentService.value = service
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

            Service.Gemini, Service.Groq, Service.XAI, Service.OpenRouter -> {
                // These services require an API key
                val hasApiKey = dataRepository.getApiKey(service).isNotBlank()
                if (hasApiKey) {
                    validateConnectionWithStatus(service)
                } else {
                    _state.update { it.copy(connectionStatus = ConnectionStatus.Unknown) }
                }
            }

            Service.OpenAICompatible -> {
                // OpenAI-compatible APIs don't always need an API key, just validate connection
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
            } catch (e: Exception) {
                val status = when (e) {
                    is OpenAICompatibleInvalidApiKeyException, is GeminiInvalidApiKeyException ->
                        ConnectionStatus.ErrorInvalidKey

                    is OpenAICompatibleQuotaExhaustedException ->
                        ConnectionStatus.ErrorQuotaExhausted

                    is OpenAICompatibleRateLimitExceededException, is GeminiRateLimitExceededException ->
                        ConnectionStatus.ErrorRateLimited

                    is OpenAICompatibleConnectionException ->
                        ConnectionStatus.ErrorConnectionFailed

                    else -> ConnectionStatus.Error
                }
                _state.update { it.copy(connectionStatus = status) }
            }
        }
    }
}
