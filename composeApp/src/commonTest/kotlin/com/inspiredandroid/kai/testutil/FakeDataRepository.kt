package com.inspiredandroid.kai.testutil

import com.inspiredandroid.kai.data.DataRepository
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.ui.chat.History
import com.inspiredandroid.kai.ui.settings.SettingsModel
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class FakeDataRepository : DataRepository {

    private var currentService: Service = Service.Free
    private val apiKeys = mutableMapOf<Service, String>()
    private val baseUrls = mutableMapOf<Service, String>()
    private val modelsByService: Map<Service, MutableStateFlow<List<SettingsModel>>> =
        Service.all.associateWith { MutableStateFlow(emptyList()) }

    override val chatHistory: MutableStateFlow<List<History>> = MutableStateFlow(emptyList())

    val selectServiceCalls = mutableListOf<Service>()
    val updateApiKeyCalls = mutableListOf<Pair<Service, String>>()
    val updateSelectedModelCalls = mutableListOf<Pair<Service, String>>()
    val fetchModelsCalls = mutableListOf<Service>()
    val askCalls = mutableListOf<Pair<String?, PlatformFile?>>()
    var clearHistoryCalls = 0
    var askException: Exception? = null

    fun setCurrentService(service: Service) {
        currentService = service
    }

    fun setApiKey(service: Service, apiKey: String) {
        apiKeys[service] = apiKey
    }

    fun setModels(service: Service, models: List<SettingsModel>) {
        modelsByService[service]?.value = models
    }

    override fun selectService(service: Service) {
        selectServiceCalls.add(service)
        currentService = service
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

    override fun getModels(service: Service): StateFlow<List<SettingsModel>> = modelsByService[service] ?: MutableStateFlow(emptyList())

    override fun clearModels(service: Service) {
        modelsByService[service]?.value = emptyList()
    }

    override suspend fun fetchModels(service: Service) {
        fetchModelsCalls.add(service)
    }

    override suspend fun validateConnection(service: Service) {
        // No-op in tests
    }

    override fun updateBaseUrl(service: Service, baseUrl: String) {
        baseUrls[service] = baseUrl
    }

    override fun getBaseUrl(service: Service): String = baseUrls[service] ?: Service.DEFAULT_OPENAI_COMPATIBLE_BASE_URL

    override suspend fun ask(question: String?, file: PlatformFile?) {
        askCalls.add(question to file)
        askException?.let { throw it }
        if (question != null) {
            chatHistory.update { history ->
                history + History(role = History.Role.USER, content = question)
            }
        }
        chatHistory.update { history ->
            history + History(role = History.Role.ASSISTANT, content = "Test response")
        }
    }

    override fun clearHistory() {
        clearHistoryCalls++
        chatHistory.value = emptyList()
    }

    override fun currentService(): Service = currentService

    override fun isUsingSharedKey(): Boolean = currentService == Service.Free
}
