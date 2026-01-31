package com.inspiredandroid.kai.data

import com.russhwolf.settings.Settings
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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
    fun getApiKey(service: Service): String = if (service.requiresApiKey || service.supportsOptionalApiKey) {
        settings.getString(service.apiKeyKey, "")
    } else {
        ""
    }

    fun setApiKey(service: Service, apiKey: String) {
        if (service.requiresApiKey || service.supportsOptionalApiKey) {
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

    // Base URL (for self-hosted services like OpenAI-compatible APIs)
    fun getBaseUrl(service: Service): String = when (service) {
        Service.OpenAICompatible -> settings.getString(service.baseUrlKey, Service.DEFAULT_OPENAI_COMPATIBLE_BASE_URL)
        else -> ""
    }

    fun setBaseUrl(service: Service, baseUrl: String) {
        if (service == Service.OpenAICompatible) {
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

    // Encryption key for conversation storage
    @OptIn(ExperimentalEncodingApi::class)
    fun getEncryptionKey(): ByteArray? {
        val encoded = settings.getStringOrNull(KEY_ENCRYPTION_KEY) ?: return null
        return try {
            Base64.decode(encoded)
        } catch (e: Exception) {
            null
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun setEncryptionKey(key: ByteArray) {
        settings.putString(KEY_ENCRYPTION_KEY, Base64.encode(key))
    }

    // Migration from legacy unencrypted settings
    fun migrateFromLegacyIfNeeded(legacySettings: Settings?) {
        if (legacySettings == null) return
        if (settings.getBoolean(KEY_MIGRATION_COMPLETE, false)) return

        // Migrate general settings
        migrateString(legacySettings, KEY_CURRENT_SERVICE_ID)
        migrateInt(legacySettings, KEY_APP_OPENS)
        migrateString(legacySettings, KEY_ENCRYPTION_KEY)

        // Migrate per-service settings
        for (service in Service.all) {
            if (service.settingsKeyPrefix.isNotEmpty()) {
                migrateString(legacySettings, service.apiKeyKey)
                migrateString(legacySettings, service.modelIdKey)
            }
        }
        migrateString(legacySettings, Service.OpenAICompatible.baseUrlKey)

        settings.putBoolean(KEY_MIGRATION_COMPLETE, true)
    }

    private fun migrateString(legacy: Settings, key: String) {
        val value = legacy.getStringOrNull(key)
        if (value != null && settings.getStringOrNull(key) == null) {
            settings.putString(key, value)
        }
    }

    private fun migrateInt(legacy: Settings, key: String) {
        if (legacy.hasKey(key) && !settings.hasKey(key)) {
            settings.putInt(key, legacy.getInt(key, 0))
        }
    }

    companion object {
        const val KEY_CURRENT_SERVICE_ID = "current_service_id"
        const val KEY_APP_OPENS = "app_opens"
        const val KEY_ENCRYPTION_KEY = "encryption_key"
        const val KEY_MIGRATION_COMPLETE = "migration_complete_v1"
    }
}
