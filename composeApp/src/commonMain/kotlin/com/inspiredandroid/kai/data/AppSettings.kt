package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.defaultUiScale
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class ServiceInstance(
    val instanceId: String,
    val serviceId: String,
)

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

    // Configured services (ordered list of service instances)
    fun getConfiguredServiceInstances(): List<ServiceInstance> {
        val json = settings.getString(KEY_CONFIGURED_SERVICES, "")
        if (json.isBlank()) return emptyList()
        return try {
            val array = Json.parseToJsonElement(json).jsonArray
            array.map { element ->
                if (element is kotlinx.serialization.json.JsonObject) {
                    // New format: {"instanceId":"openai","serviceId":"openai"}
                    ServiceInstance(
                        instanceId = element["instanceId"]?.jsonPrimitive?.content ?: "",
                        serviceId = element["serviceId"]?.jsonPrimitive?.content ?: "",
                    )
                } else {
                    // Legacy format: plain string "openai" — instanceId == serviceId
                    val id = element.jsonPrimitive.content
                    ServiceInstance(instanceId = id, serviceId = id)
                }
            }.filter { it.instanceId.isNotBlank() && it.serviceId.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setConfiguredServiceInstances(instances: List<ServiceInstance>) {
        val jsonArray = kotlinx.serialization.json.JsonArray(
            instances.map { instance ->
                JsonObject(
                    mapOf(
                        "instanceId" to JsonPrimitive(instance.instanceId),
                        "serviceId" to JsonPrimitive(instance.serviceId),
                    ),
                )
            },
        )
        settings.putString(KEY_CONFIGURED_SERVICES, jsonArray.toString())
    }

    fun migrateConfiguredServicesIfNeeded() {
        if (settings.getBoolean(KEY_SERVICES_MIGRATION_COMPLETE, false)) return

        val existing = getConfiguredServiceInstances()
        val existingServiceIds = existing.map { it.serviceId }.toSet()
        val instances = existing.toMutableList()

        // Add the current service if not already present
        val currentServiceId = settings.getString(KEY_CURRENT_SERVICE_ID, Service.Free.id)
        val currentService = Service.fromId(currentServiceId)
        if (currentService != Service.Free && currentService.id !in existingServiceIds) {
            instances.add(ServiceInstance(instanceId = currentService.id, serviceId = currentService.id))
        }

        // Also add any service that has a legacy API key configured
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

    // Per-instance settings (API key, model, base URL)
    fun getInstanceApiKey(instanceId: String): String = settings.getString("instance_${instanceId}_api_key", "")

    fun setInstanceApiKey(instanceId: String, apiKey: String) {
        settings.putString("instance_${instanceId}_api_key", apiKey)
    }

    fun getInstanceModelId(instanceId: String): String = settings.getString("instance_${instanceId}_model_id", "")

    fun setInstanceModelId(instanceId: String, modelId: String) {
        settings.putString("instance_${instanceId}_model_id", modelId)
    }

    fun getInstanceBaseUrl(instanceId: String): String = settings.getString("instance_${instanceId}_base_url", "")

    fun setInstanceBaseUrl(instanceId: String, baseUrl: String) {
        settings.putString("instance_${instanceId}_base_url", baseUrl)
    }

    fun removeInstanceSettings(instanceId: String) {
        settings.remove("instance_${instanceId}_api_key")
        settings.remove("instance_${instanceId}_model_id")
        settings.remove("instance_${instanceId}_base_url")
    }

    /**
     * Migrate per-service settings to per-instance settings.
     * For existing users, the first instance of each service type uses the service's
     * legacy key prefix. This copies those values to the new instance_ keys.
     * Runs every time (idempotent) to catch instances added by repair migrations.
     */
    fun migrateInstanceSettingsIfNeeded() {
        val instances = getConfiguredServiceInstances()
        for (instance in instances) {
            val service = Service.fromId(instance.serviceId)
            if (service == Service.Free) continue
            // Copy legacy per-service API key to per-instance key
            val legacyApiKey = getApiKey(service)
            if (legacyApiKey.isNotBlank() && getInstanceApiKey(instance.instanceId).isBlank()) {
                setInstanceApiKey(instance.instanceId, legacyApiKey)
            }
            // Copy legacy model
            val legacyModel = getSelectedModelId(service)
            if (legacyModel.isNotBlank() && getInstanceModelId(instance.instanceId).isBlank()) {
                setInstanceModelId(instance.instanceId, legacyModel)
            }
            // Copy legacy base URL for OpenAI-Compatible
            if (service == Service.OpenAICompatible) {
                val legacyBaseUrl = getBaseUrl(service)
                if (legacyBaseUrl.isNotBlank() && getInstanceBaseUrl(instance.instanceId).isBlank()) {
                    setInstanceBaseUrl(instance.instanceId, legacyBaseUrl)
                }
            }
        }
    }

    fun generateInstanceId(serviceId: String): String {
        val existing = getConfiguredServiceInstances()
        val existingIds = existing.map { it.instanceId }.toSet()
        // First instance of a type: use serviceId directly
        if (serviceId !in existingIds) return serviceId
        // Subsequent: serviceId_2, serviceId_3, etc.
        var counter = 2
        while ("${serviceId}_$counter" in existingIds) counter++
        return "${serviceId}_$counter"
    }

    // App open tracking
    fun trackAppOpen(): Int {
        val currentCount = settings.getInt(KEY_APP_OPENS, 0)
        val newCount = currentCount + 1
        settings.putInt(KEY_APP_OPENS, newCount)
        return newCount
    }

    // Tool enable/disable settings
    fun isToolEnabled(toolId: String, defaultEnabled: Boolean = true): Boolean = settings.getBoolean("$KEY_TOOL_PREFIX$toolId", defaultEnabled)

    fun setToolEnabled(toolId: String, enabled: Boolean) {
        settings.putBoolean("$KEY_TOOL_PREFIX$toolId", enabled)
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

    // Free fallback
    fun isFreeFallbackEnabled(): Boolean = settings.getBoolean(KEY_FREE_FALLBACK_ENABLED, true)

    fun setFreeFallbackEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_FREE_FALLBACK_ENABLED, enabled)
    }

    // Soul (system prompt)
    fun getSoulText(): String = settings.getString(KEY_SOUL, "")

    fun setSoulText(text: String) {
        settings.putString(KEY_SOUL, text)
    }

    // Memory
    fun isMemoryEnabled(): Boolean = settings.getBoolean(KEY_MEMORY_ENABLED, true)

    fun setMemoryEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_MEMORY_ENABLED, enabled)
    }

    fun getMemoryInstructions(): String = settings.getString(KEY_MEMORY_INSTRUCTIONS, DEFAULT_MEMORY_INSTRUCTIONS)

    // Agent memories
    fun getMemoriesJson(): String = settings.getString(KEY_AGENT_MEMORIES, "[]")

    fun setMemoriesJson(json: String) {
        settings.putString(KEY_AGENT_MEMORIES, json)
    }

    // Scheduling
    fun isSchedulingEnabled(): Boolean = settings.getBoolean(KEY_SCHEDULING_ENABLED, true)

    fun setSchedulingEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_SCHEDULING_ENABLED, enabled)
    }

    // Daemon mode
    fun isDaemonEnabled(): Boolean = settings.getBoolean(KEY_DAEMON_ENABLED, false)

    fun setDaemonEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_DAEMON_ENABLED, enabled)
    }

    fun getScheduledTasksJson(): String = settings.getString(KEY_SCHEDULED_TASKS, "[]")

    fun setScheduledTasksJson(json: String) {
        settings.putString(KEY_SCHEDULED_TASKS, json)
    }

    // Heartbeat config
    fun getHeartbeatConfigJson(): String = settings.getString(KEY_HEARTBEAT_CONFIG, "")

    fun setHeartbeatConfigJson(json: String) {
        settings.putString(KEY_HEARTBEAT_CONFIG, json)
    }

    // Heartbeat log
    fun getHeartbeatLogJson(): String = settings.getString(KEY_HEARTBEAT_LOG, "")

    fun setHeartbeatLogJson(json: String) {
        settings.putString(KEY_HEARTBEAT_LOG, json)
    }

    // Heartbeat prompt
    fun getHeartbeatPrompt(): String = settings.getString(KEY_HEARTBEAT_PROMPT, "")

    fun setHeartbeatPrompt(text: String) {
        settings.putString(KEY_HEARTBEAT_PROMPT, text)
    }

    // MCP Servers
    fun getMcpServersJson(): String = settings.getString(KEY_MCP_SERVERS, "")

    fun setMcpServersJson(json: String) {
        settings.putString(KEY_MCP_SERVERS, json)
    }

    // UI Scale
    private val _uiScaleFlow = MutableStateFlow(settings.getFloat(KEY_UI_SCALE, defaultUiScale))
    val uiScaleFlow: StateFlow<Float> = _uiScaleFlow

    fun getUiScale(): Float = _uiScaleFlow.value

    fun setUiScale(scale: Float) {
        settings.putFloat(KEY_UI_SCALE, scale)
        _uiScaleFlow.value = scale
    }

    // Email
    fun isEmailEnabled(): Boolean = settings.getBoolean(KEY_EMAIL_ENABLED, true)

    fun setEmailEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_EMAIL_ENABLED, enabled)
    }

    fun getEmailAccountsJson(): String = settings.getString(KEY_EMAIL_ACCOUNTS, "")

    fun setEmailAccountsJson(json: String) {
        settings.putString(KEY_EMAIL_ACCOUNTS, json)
    }

    fun getEmailPassword(accountId: String): String = settings.getString("${KEY_EMAIL_PASSWORD_PREFIX}$accountId", "")

    fun setEmailPassword(accountId: String, password: String) {
        settings.putString("${KEY_EMAIL_PASSWORD_PREFIX}$accountId", password)
    }

    fun removeEmailPassword(accountId: String) {
        settings.remove("${KEY_EMAIL_PASSWORD_PREFIX}$accountId")
    }

    fun getEmailSyncStateJson(accountId: String): String = settings.getString("${KEY_EMAIL_SYNC_PREFIX}$accountId", "")

    fun setEmailSyncStateJson(accountId: String, json: String) {
        settings.putString("${KEY_EMAIL_SYNC_PREFIX}$accountId", json)
    }

    fun getEmailPollIntervalMinutes(): Int = settings.getInt(KEY_EMAIL_POLL_INTERVAL, 15)

    fun setEmailPollIntervalMinutes(minutes: Int) {
        settings.putInt(KEY_EMAIL_POLL_INTERVAL, minutes)
    }

    companion object {
        const val KEY_CURRENT_SERVICE_ID = "current_service_id"
        const val KEY_APP_OPENS = "app_opens"
        const val KEY_ENCRYPTION_KEY = "encryption_key"
        const val KEY_MIGRATION_COMPLETE = "migration_complete_v1"
        const val KEY_TOOL_PREFIX = "tool_enabled_"
        const val KEY_SOUL = "soul_text"
        const val KEY_MEMORY_ENABLED = "memory_enabled"
        const val KEY_MEMORY_INSTRUCTIONS = "memory_instructions"
        const val KEY_AGENT_MEMORIES = "agent_memories"
        const val KEY_SCHEDULED_TASKS = "scheduled_tasks"
        const val KEY_SCHEDULING_ENABLED = "scheduling_enabled"
        const val KEY_DAEMON_ENABLED = "daemon_enabled"
        const val KEY_HEARTBEAT_CONFIG = "heartbeat_config"
        const val KEY_HEARTBEAT_PROMPT = "heartbeat_prompt"
        const val KEY_HEARTBEAT_LOG = "heartbeat_log"

        const val KEY_EMAIL_ENABLED = "email_enabled"
        const val KEY_EMAIL_ACCOUNTS = "email_accounts"
        const val KEY_EMAIL_PASSWORD_PREFIX = "email_password_"
        const val KEY_EMAIL_SYNC_PREFIX = "email_sync_"
        const val KEY_EMAIL_POLL_INTERVAL = "email_poll_interval"
        const val KEY_CONFIGURED_SERVICES = "configured_services"
        const val KEY_FREE_FALLBACK_ENABLED = "free_fallback_enabled"
        const val KEY_SERVICES_MIGRATION_COMPLETE = "services_migration_complete_v1"
        const val KEY_UI_SCALE = "ui_scale"
        const val KEY_MCP_SERVERS = "mcp_servers"

        const val DEFAULT_MEMORY_INSTRUCTIONS =
            "You have persistent memory across conversations. " +
                "All your stored memories are listed in the system prompt grouped by category.\n\n" +
                "When you learn important information about the user (name, preferences, projects, goals, etc.), " +
                "proactively use the memory_store tool to save it.\n" +
                "Use the memory_forget tool to remove outdated or incorrect memories.\n" +
                "Do not store trivial or transient information.\n\n" +
                "## Structured Learning\n" +
                "Use memory_learn to record categorized learnings:\n" +
                "- Record user corrections and preferences as PREFERENCE entries\n" +
                "- Record things that worked well as LEARNING entries\n" +
                "- Record error resolutions as ERROR entries\n" +
                "Use memory_reinforce when a stored learning produced a good outcome."
    }
}
