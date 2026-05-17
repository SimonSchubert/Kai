package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.data.AppSettings.Companion.KEY_APP_OPENS
import com.inspiredandroid.kai.data.AppSettings.Companion.KEY_BASE_URL_V1_MIGRATION_COMPLETE
import com.inspiredandroid.kai.data.AppSettings.Companion.KEY_CURRENT_SERVICE_ID
import com.inspiredandroid.kai.data.AppSettings.Companion.KEY_INSTANCE_MIGRATION_COMPLETE
import com.inspiredandroid.kai.data.AppSettings.Companion.KEY_MIGRATION_COMPLETE
import com.inspiredandroid.kai.data.AppSettings.Companion.KEY_SERVICES_MIGRATION_COMPLETE
import com.russhwolf.settings.Settings

internal val versionPathRegex = Regex("/v\\d+$")

fun AppSettings.runMigrations(legacySettings: Settings?) {
    migrateFromLegacyIfNeeded(legacySettings)
    migrateConfiguredServicesIfNeeded()
    migrateInstanceSettingsIfNeeded()
    migrateBaseUrlsToV1PathIfNeeded()
}

fun AppSettings.migrateFromLegacyIfNeeded(legacySettings: Settings?) {
    if (legacySettings == null) return
    if (settings.getBoolean(KEY_MIGRATION_COMPLETE, false)) return

    migrateString(legacySettings, KEY_CURRENT_SERVICE_ID)
    migrateInt(legacySettings, KEY_APP_OPENS)

    for (service in Service.all) {
        if (service.settingsKeyPrefix.isNotEmpty()) {
            migrateString(legacySettings, service.apiKeyKey)
            migrateString(legacySettings, service.modelIdKey)
        }
    }
    migrateString(legacySettings, Service.OpenAICompatible.baseUrlKey)

    settings.putBoolean(KEY_MIGRATION_COMPLETE, true)
}

private fun AppSettings.migrateString(legacy: Settings, key: String) {
    val value = legacy.getStringOrNull(key)
    if (value != null && settings.getStringOrNull(key) == null) {
        settings.putString(key, value)
    }
}

private fun AppSettings.migrateInt(legacy: Settings, key: String) {
    if (legacy.hasKey(key) && !settings.hasKey(key)) {
        settings.putInt(key, legacy.getInt(key, 0))
    }
}

fun AppSettings.migrateConfiguredServicesIfNeeded() {
    if (settings.getBoolean(KEY_SERVICES_MIGRATION_COMPLETE, false)) return

    val existing = getConfiguredServiceInstances()
    val existingServiceIds = existing.map { it.serviceId }.toSet()
    val instances = existing.toMutableList()

    val currentServiceId = settings.getString(KEY_CURRENT_SERVICE_ID, Service.Free.id)
    val currentService = Service.fromId(currentServiceId)
    if (currentService != Service.Free && currentService.id !in existingServiceIds) {
        instances.add(ServiceInstance(instanceId = currentService.id, serviceId = currentService.id))
    }

    for (service in Service.all) {
        if (service == Service.Free) continue
        if (service.id in existingServiceIds) continue
        if (instances.any { it.serviceId == service.id }) continue
        val apiKey = getApiKey(service)
        if (apiKey.isNotBlank()) {
            instances.add(ServiceInstance(instanceId = service.id, serviceId = service.id))
        }
    }

    if (instances.size > existing.size) {
        setConfiguredServiceInstances(instances)
    }

    settings.putBoolean(KEY_SERVICES_MIGRATION_COMPLETE, true)
}

/**
 * Migrate per-service settings to per-instance settings.
 * For existing users, the first instance of each service type uses the service's
 * legacy key prefix. This copies those values to the new instance_ keys.
 */
fun AppSettings.migrateInstanceSettingsIfNeeded() {
    if (settings.getBoolean(KEY_INSTANCE_MIGRATION_COMPLETE, false)) return

    val instances = getConfiguredServiceInstances()
    for (instance in instances) {
        val service = Service.fromId(instance.serviceId)
        if (service == Service.Free) continue
        val legacyApiKey = getApiKey(service)
        if (legacyApiKey.isNotBlank() && getInstanceApiKey(instance.instanceId).isBlank()) {
            setInstanceApiKey(instance.instanceId, legacyApiKey)
        }
        val legacyModel = getSelectedModelId(service)
        if (legacyModel.isNotBlank() && getInstanceModelId(instance.instanceId).isBlank()) {
            setInstanceModelId(instance.instanceId, legacyModel)
        }
        if (service == Service.OpenAICompatible) {
            val legacyBaseUrl = getBaseUrl(service)
            if (legacyBaseUrl.isNotBlank() && getInstanceBaseUrl(instance.instanceId).isBlank()) {
                setInstanceBaseUrl(instance.instanceId, legacyBaseUrl)
            }
        }
    }

    settings.putBoolean(KEY_INSTANCE_MIGRATION_COMPLETE, true)
}

/**
 * Migrate existing OpenAI-compatible base URLs to include `/v1` path segment.
 * Previously `/v1` was hardcoded in the endpoint paths; now the base URL should
 * include it (following the OpenAI SDK convention).
 */
fun AppSettings.migrateBaseUrlsToV1PathIfNeeded() {
    if (settings.getBoolean(KEY_BASE_URL_V1_MIGRATION_COMPLETE, false)) return

    val instances = getConfiguredServiceInstances()
    for (instance in instances) {
        val service = Service.fromId(instance.serviceId)
        if (service != Service.OpenAICompatible) continue
        val baseUrl = getInstanceBaseUrl(instance.instanceId)
        if (baseUrl.isNotBlank()) {
            setInstanceBaseUrl(instance.instanceId, ensureBaseUrlHasVersionPath(baseUrl))
        }
    }

    val legacyBaseUrl = settings.getString(Service.OpenAICompatible.baseUrlKey, "")
    if (legacyBaseUrl.isNotBlank()) {
        settings.putString(Service.OpenAICompatible.baseUrlKey, ensureBaseUrlHasVersionPath(legacyBaseUrl))
    }

    settings.putBoolean(KEY_BASE_URL_V1_MIGRATION_COMPLETE, true)
}

internal fun ensureBaseUrlHasVersionPath(url: String): String {
    val trimmed = url.trimEnd('/')
    if (trimmed.contains(versionPathRegex)) return trimmed
    return "$trimmed/v1"
}
