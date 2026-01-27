package com.inspiredandroid.kai.testutil

import com.inspiredandroid.kai.data.DataRepository
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.ui.settings.SettingsModel
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class FakeDataRepository : DataRepository {

    private var _currentService: Service = Service.Free
    private val apiKeys = mutableMapOf<Service, String>()
    private val modelsByService: Map<Service, MutableStateFlow<List<SettingsModel>>> =
        Service.all.associateWith { MutableStateFlow(emptyList()) }

    val selectServiceCalls = mutableListOf<Service>()
    val updateApiKeyCalls = mutableListOf<Pair<Service, String>>()
    val updateSelectedModelCalls = mutableListOf<Pair<Service, String>>()
    val fetchModelsCalls = mutableListOf<Service>()

    fun setCurrentService(service: Service) {
        _currentService = service
    }

    fun setApiKey(service: Service, apiKey: String) {
        apiKeys[service] = apiKey
    }

    fun setModels(service: Service, models: List<SettingsModel>) {
        modelsByService[service]?.value = models
    }

    override fun selectService(service: Service) {
        selectServiceCalls.add(service)
        _currentService = service
    }

    override fun updateApiKey(service: Service, apiKey: String) {
        updateApiKeyCalls.add(service to apiKey)
        apiKeys[service] = apiKey
    }

    override fun getApiKey(service: Service): String = apiKeys[service] ?: ""

    override fun updateSelectedModel(service: Service, modelId: String) {
        updateSelectedModelCalls.add(service to modelId)
        modelsByService[service]?.update { models ->
            models.map { it.copy(isSelected = it.id == modelId) }
        }
    }

    override fun getModels(service: Service): StateFlow<List<SettingsModel>> =
        modelsByService[service] ?: MutableStateFlow(emptyList())

    override suspend fun fetchModels(service: Service) {
        fetchModelsCalls.add(service)
    }

    override suspend fun ask(question: String?, file: PlatformFile?) {
        // Not needed for SettingsViewModel tests
    }

    override fun clearHistory() {
        // Not needed for SettingsViewModel tests
    }

    override fun currentService(): Service = _currentService

    override fun isUsingSharedKey(): Boolean = _currentService == Service.Free
}
