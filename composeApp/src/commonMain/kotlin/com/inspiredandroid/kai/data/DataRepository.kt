package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.ui.chat.History
import com.inspiredandroid.kai.ui.settings.SettingsModel
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.StateFlow

interface DataRepository {
    val chatHistory: StateFlow<List<History>>

    fun selectService(service: Service)
    fun updateApiKey(service: Service, apiKey: String)
    fun getApiKey(service: Service): String
    fun updateSelectedModel(service: Service, modelId: String)
    fun getModels(service: Service): StateFlow<List<SettingsModel>>
    suspend fun fetchModels(service: Service)

    suspend fun ask(question: String?, file: PlatformFile?)
    fun clearHistory()
    fun currentService(): Service
    fun isUsingSharedKey(): Boolean
}
