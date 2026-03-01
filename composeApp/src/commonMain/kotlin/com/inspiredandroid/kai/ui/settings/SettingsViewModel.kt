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
            tools = dataRepository.getToolDefinitions(),
            onSelectTab = ::onSelectTab,
            onSelectService = ::onSelectService,
            onSelectModel = ::onSelectModel,
            onChangeApiKey = ::onChangeApiKey,
            onChangeBaseUrl = ::onChangeBaseUrl,
            onToggleTool = ::onToggleTool,
            soulText = dataRepository.getSoulText(),
            onSaveSoul = ::onSaveSoul,
            memoryInstructions = dataRepository.getMemoryInstructions(),
            onSaveMemoryInstructions = ::onSaveMemoryInstructions,
            memories = dataRepository.getMemories(),
            onDeleteMemory = ::onDeleteMemory,
        ),
    )

    val state = currentService.flatMapLatest { service ->
        combine(
            _state,
            dataRepository.getModels(service),
        ) { localState, models ->
            localState.copy(
                currentService = service,
                models = models,
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

    private fun onSelectTab(tab: SettingsTab) {
        _state.update { it.copy(currentTab = tab) }
    }

    private fun onSelectService(service: Service) {
        dataRepository.selectService(service)
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

    private fun onSaveSoul(text: String) {
        dataRepository.setSoulText(text)
        _state.update { it.copy(soulText = text) }
    }

    private fun onSaveMemoryInstructions(text: String) {
        dataRepository.setMemoryInstructions(text)
        _state.update { it.copy(memoryInstructions = text) }
    }

    private fun onDeleteMemory(key: String) {
        dataRepository.deleteMemory(key)
        _state.update { it.copy(memories = dataRepository.getMemories()) }
    }

    private fun onToggleTool(toolId: String, enabled: Boolean) {
        dataRepository.setToolEnabled(toolId, enabled)
        _state.update { state ->
            state.copy(
                tools = state.tools.map { tool ->
                    if (tool.id == toolId) tool.copy(isEnabled = enabled) else tool
                },
            )
        }
    }

    private fun checkConnectionDebounced(service: Service) {
        connectionCheckJob?.cancel()
        connectionCheckJob = viewModelScope.launch {
            delay(800)
            checkConnection(service)
        }
    }

    private fun checkConnection(service: Service) {
        if (service == Service.Free) {
            _state.update { it.copy(connectionStatus = ConnectionStatus.Connected) }
            return
        }
        if (service.requiresApiKey && dataRepository.getApiKey(service).isBlank()) {
            _state.update { it.copy(connectionStatus = ConnectionStatus.Unknown) }
            return
        }
        validateConnectionWithStatus(service)
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
