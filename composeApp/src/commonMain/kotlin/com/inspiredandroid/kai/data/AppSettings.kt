package com.inspiredandroid.kai.data

import com.russhwolf.settings.Settings

class AppSettings(private val settings: Settings) {

    // Service selection
    fun selectService(service: Service) {
        settings.putString(KEY_CURRENT_SERVICE_ID, service.id)
    }

    fun currentService(): Service {
        val id = settings.getString(KEY_CURRENT_SERVICE_ID, Service.Free.id)
        return Service.fromId(id)
    }

    // API Keys
    fun getApiKey(service: Service): String = if (service.requiresApiKey) {
        settings.getString(service.apiKeyKey, "")
    } else {
        ""
    }

    fun setApiKey(service: Service, apiKey: String) {
        if (service.requiresApiKey) {
            settings.putString(service.apiKeyKey, apiKey)
        }
    }

    // Model selection
    fun getSelectedModelId(service: Service): String = settings.getString(service.modelIdKey, service.defaultModel ?: "")

    fun setSelectedModelId(service: Service, modelId: String) {
        if (service.modelIdKey.isNotEmpty()) {
            settings.putString(service.modelIdKey, modelId)
        }
    }

    // Base URL (for self-hosted services like Ollama)
    fun getBaseUrl(service: Service): String = when (service) {
        Service.Ollama -> settings.getString(service.baseUrlKey, Service.DEFAULT_OLLAMA_BASE_URL)
        else -> ""
    }

    fun setBaseUrl(service: Service, baseUrl: String) {
        if (service == Service.Ollama) {
            settings.putString(service.baseUrlKey, baseUrl)
        }
    }

    // App open tracking
    fun trackAppOpen(): Int {
        val currentCount = settings.getInt(KEY_APP_OPENS, 0)
        val newCount = currentCount + 1
        settings.putInt(KEY_APP_OPENS, newCount)
        return newCount
    }

    companion object {
        const val KEY_CURRENT_SERVICE_ID = "current_service_id"
        const val KEY_APP_OPENS = "app_opens"
    }
}
