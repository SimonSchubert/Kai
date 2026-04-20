package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.defaultUiScale
import com.inspiredandroid.kai.ui.ThemeMode
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

enum class ImportSection {
    SERVICES,
    SOUL,
    MEMORY,
    SCHEDULING,
    HEARTBEAT,
    EMAIL,
    SPLINTERLANDS,
    TOOLS,
    MCP,
    CONVERSATIONS,
}

fun detectImportSections(json: JsonObject): Map<ImportSection, String?> {
    val sections = mutableMapOf<ImportSection, String?>()
    if (json["configured_services"] != null || json["current_service_id"] != null || json["free_fallback_enabled"] != null || json["instance_settings"] != null) {
        val count = json["configured_services"]?.jsonArray?.size
        sections[ImportSection.SERVICES] = count?.let { "$it" }
    }
    if (json["soul_text"] != null) {
        sections[ImportSection.SOUL] = null
    }
    if (json["memory_enabled"] != null || json["agent_memories"] != null) {
        val count = json["agent_memories"]?.jsonArray?.size
        sections[ImportSection.MEMORY] = count?.let { "$it" }
    }
    if (json["scheduling_enabled"] != null || json["scheduled_tasks"] != null) {
        val count = json["scheduled_tasks"]?.jsonArray?.size
        sections[ImportSection.SCHEDULING] = count?.let { "$it" }
    }
    if (json["heartbeat_config"] != null || json["heartbeat_prompt"] != null || json["heartbeat_log"] != null) {
        sections[ImportSection.HEARTBEAT] = null
    }
    if (json["email_enabled"] != null || json["email_accounts"] != null) {
        val count = json["email_accounts"]?.jsonArray?.size
        sections[ImportSection.EMAIL] = count?.let { "$it" }
    }
    if (json["splinterlands_enabled"] != null || json["splinterlands_account"] != null) {
        sections[ImportSection.SPLINTERLANDS] = null
    }
    if (json["tool_overrides"] != null) {
        val enabled = json["tool_overrides"]?.jsonObject?.count { (_, v) ->
            try {
                v.jsonPrimitive.content.toBoolean()
            } catch (_: Exception) {
                false
            }
        }
        sections[ImportSection.TOOLS] = enabled?.let { "$it" }
    }
    if (json["mcp_servers"] != null) {
        val count = json["mcp_servers"]?.jsonArray?.size
        sections[ImportSection.MCP] = count?.let { "$it" }
    }
    if (json["conversations"] != null) {
        val count = try {
            json["conversations"]?.jsonArray?.size
        } catch (_: Exception) {
            null
        }
        sections[ImportSection.CONVERSATIONS] = count?.let { "$it" }
    }
    return sections
}

data class ServiceInstance(
    val instanceId: String,
    val serviceId: String,
)

private val versionPathRegex = Regex("/v\\d+$")

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
        } catch (_: Exception) {
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
     */
    fun migrateInstanceSettingsIfNeeded() {
        if (settings.getBoolean(KEY_INSTANCE_MIGRATION_COMPLETE, false)) return

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

        settings.putBoolean(KEY_INSTANCE_MIGRATION_COMPLETE, true)
    }

    /**
     * Migrate existing OpenAI-compatible base URLs to include `/v1` path segment.
     * Previously `/v1` was hardcoded in the endpoint paths; now the base URL should
     * include it (following the OpenAI SDK convention).
     */
    fun migrateBaseUrlsToV1PathIfNeeded() {
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

        // Also migrate legacy per-service base URL key
        val legacyBaseUrl = settings.getString(Service.OpenAICompatible.baseUrlKey, "")
        if (legacyBaseUrl.isNotBlank()) {
            settings.putString(Service.OpenAICompatible.baseUrlKey, ensureBaseUrlHasVersionPath(legacyBaseUrl))
        }

        settings.putBoolean(KEY_BASE_URL_V1_MIGRATION_COMPLETE, true)
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

    fun getConversationsJson(): String? = settings.getStringOrNull(KEY_CONVERSATIONS)

    fun setConversationsJson(json: String) {
        settings.putString(KEY_CONVERSATIONS, json)
    }

    fun getCurrentConversationId(): String? = settings.getStringOrNull(KEY_CURRENT_CONVERSATION_ID)

    fun setCurrentConversationId(id: String?) {
        if (id == null) {
            settings.remove(KEY_CURRENT_CONVERSATION_ID)
        } else {
            settings.putString(KEY_CURRENT_CONVERSATION_ID, id)
        }
    }

    fun getCurrentInteractiveMode(): Boolean = settings.getBoolean(KEY_CURRENT_INTERACTIVE_MODE, false)

    fun setCurrentInteractiveMode(enabled: Boolean) {
        settings.putBoolean(KEY_CURRENT_INTERACTIVE_MODE, enabled)
    }

    fun isCurrentConversationMigrated(): Boolean = settings.getBoolean(KEY_CURRENT_CONVERSATION_MIGRATED, false)

    fun markCurrentConversationMigrated() {
        settings.putBoolean(KEY_CURRENT_CONVERSATION_MIGRATED, true)
    }

    fun getEncryptionKey(): ByteArray? {
        val encoded = settings.getStringOrNull(KEY_ENCRYPTION_KEY) ?: return null
        return try {
            @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
            kotlin.io.encoding.Base64.decode(encoded)
        } catch (_: Exception) {
            null
        }
    }

    fun runMigrations(legacySettings: Settings?) {
        migrateFromLegacyIfNeeded(legacySettings)
        migrateConfiguredServicesIfNeeded()
        migrateInstanceSettingsIfNeeded()
        migrateBaseUrlsToV1PathIfNeeded()
    }

    // Migration from legacy unencrypted settings
    fun migrateFromLegacyIfNeeded(legacySettings: Settings?) {
        if (legacySettings == null) return
        if (settings.getBoolean(KEY_MIGRATION_COMPLETE, false)) return

        // Migrate general settings
        migrateString(legacySettings, KEY_CURRENT_SERVICE_ID)
        migrateInt(legacySettings, KEY_APP_OPENS)

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

    fun getFreeMode(): FreeMode {
        val stored = settings.getStringOrNull(KEY_FREE_MODE) ?: return FreeMode.FAST
        return FreeMode.entries.find { it.name == stored } ?: FreeMode.FAST
    }

    fun setFreeMode(mode: FreeMode) {
        settings.putString(KEY_FREE_MODE, mode.name)
    }

    fun isFreeServicePrimary(): Boolean = settings.getBoolean(KEY_FREE_SERVICE_PRIMARY, false)

    fun setFreeServicePrimary(primary: Boolean) {
        settings.putBoolean(KEY_FREE_SERVICE_PRIMARY, primary)
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

    // Dynamic UI
    fun isDynamicUiEnabled(): Boolean = settings.getBoolean(KEY_DYNAMIC_UI_ENABLED, true)

    fun setDynamicUiEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_DYNAMIC_UI_ENABLED, enabled)
    }

    // Daemon mode
    fun isDaemonEnabled(): Boolean = settings.getBoolean(KEY_DAEMON_ENABLED, false)

    fun setDaemonEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_DAEMON_ENABLED, enabled)
    }

    // Linux Sandbox
    fun isSandboxEnabled(): Boolean = settings.getBoolean(KEY_SANDBOX_ENABLED, true)

    fun setSandboxEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_SANDBOX_ENABLED, enabled)
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

    // Theme Mode
    private val _themeModeFlow = MutableStateFlow(
        try {
            ThemeMode.valueOf(settings.getString(KEY_THEME_MODE, ThemeMode.AUTO.name))
        } catch (_: Exception) {
            ThemeMode.AUTO
        }
    )
    val themeModeFlow: StateFlow<ThemeMode> = _themeModeFlow

    fun getThemeMode(): ThemeMode = _themeModeFlow.value

    fun setThemeMode(mode: ThemeMode) {
        settings.putString(KEY_THEME_MODE, mode.name)
        _themeModeFlow.value = mode
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

    // Local model context size
    fun getModelContextTokens(modelId: String): Int = settings.getInt("$KEY_MODEL_CONTEXT_PREFIX$modelId", 0)

    fun setModelContextTokens(modelId: String, contextTokens: Int) {
        settings.putInt("$KEY_MODEL_CONTEXT_PREFIX$modelId", contextTokens)
    }

    // Splinterlands
    fun isSplinterlandsEnabled(): Boolean = settings.getBoolean(KEY_SPLINTERLANDS_ENABLED, false)

    fun setSplinterlandsEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_SPLINTERLANDS_ENABLED, enabled)
    }

    fun getSplinterlandsAccountJson(): String = settings.getString(KEY_SPLINTERLANDS_ACCOUNT, "")

    fun setSplinterlandsAccountJson(json: String) {
        settings.putString(KEY_SPLINTERLANDS_ACCOUNT, json)
    }

    fun getSplinterlandsPostingKey(): String = settings.getString(KEY_SPLINTERLANDS_POSTING_KEY, "")

    fun getSplinterlandsPostingKey(accountId: String): String = settings.getString("${KEY_SPLINTERLANDS_POSTING_KEY}_$accountId", "")
        .ifEmpty { getSplinterlandsPostingKey() } // fallback to legacy key

    fun setSplinterlandsPostingKey(accountId: String, key: String) {
        settings.putString("${KEY_SPLINTERLANDS_POSTING_KEY}_$accountId", key)
    }

    fun getSplinterlandsInstanceId(): String = settings.getString(KEY_SPLINTERLANDS_INSTANCE_ID, "")

    fun setSplinterlandsInstanceId(instanceId: String) {
        settings.putString(KEY_SPLINTERLANDS_INSTANCE_ID, instanceId)
    }

    fun getSplinterlandsInstanceIdsJson(): String = settings.getString(KEY_SPLINTERLANDS_INSTANCE_IDS, "")

    fun setSplinterlandsInstanceIdsJson(json: String) {
        settings.putString(KEY_SPLINTERLANDS_INSTANCE_IDS, json)
    }

    fun getSplinterlandsBattleLogJson(): String = settings.getString(KEY_SPLINTERLANDS_BATTLE_LOG, "")

    fun setSplinterlandsBattleLogJson(json: String) {
        settings.putString(KEY_SPLINTERLANDS_BATTLE_LOG, json)
    }

    fun exportToJson(toolIds: List<String>): JsonObject {
        val map = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
        map["version"] = JsonPrimitive(1)

        // Services
        val configuredJson = settings.getString(KEY_CONFIGURED_SERVICES, "")
        if (configuredJson.isNotBlank()) {
            map["configured_services"] = Json.parseToJsonElement(configuredJson)
        }
        map["current_service_id"] = JsonPrimitive(settings.getString(KEY_CURRENT_SERVICE_ID, Service.Free.id))
        map["free_fallback_enabled"] = JsonPrimitive(isFreeFallbackEnabled())

        // Per-instance settings
        val instances = getConfiguredServiceInstances()
        if (instances.isNotEmpty()) {
            val instanceSettings = kotlinx.serialization.json.JsonArray(
                instances.map { instance ->
                    JsonObject(
                        buildMap {
                            put("instanceId", JsonPrimitive(instance.instanceId))
                            val apiKey = getInstanceApiKey(instance.instanceId)
                            if (apiKey.isNotBlank()) put("api_key", JsonPrimitive(apiKey))
                            val modelId = getInstanceModelId(instance.instanceId)
                            if (modelId.isNotBlank()) put("model_id", JsonPrimitive(modelId))
                            val baseUrl = getInstanceBaseUrl(instance.instanceId)
                            if (baseUrl.isNotBlank()) put("base_url", JsonPrimitive(baseUrl))
                        },
                    )
                },
            )
            map["instance_settings"] = instanceSettings
        }

        // Soul
        val soul = getSoulText()
        if (soul.isNotBlank()) map["soul_text"] = JsonPrimitive(soul)

        // Memory
        map["memory_enabled"] = JsonPrimitive(isMemoryEnabled())
        val memoriesJson = getMemoriesJson()
        if (memoriesJson.isNotBlank() && memoriesJson != "[]") {
            map["agent_memories"] = Json.parseToJsonElement(memoriesJson)
        }

        // Scheduling
        map["scheduling_enabled"] = JsonPrimitive(isSchedulingEnabled())
        val tasksJson = getScheduledTasksJson()
        if (tasksJson.isNotBlank() && tasksJson != "[]") {
            map["scheduled_tasks"] = Json.parseToJsonElement(tasksJson)
        }

        // Heartbeat
        val heartbeatConfig = getHeartbeatConfigJson()
        if (heartbeatConfig.isNotBlank()) {
            map["heartbeat_config"] = Json.parseToJsonElement(heartbeatConfig)
        }
        val heartbeatPrompt = getHeartbeatPrompt()
        if (heartbeatPrompt.isNotBlank()) map["heartbeat_prompt"] = JsonPrimitive(heartbeatPrompt)
        val heartbeatLog = getHeartbeatLogJson()
        if (heartbeatLog.isNotBlank()) {
            map["heartbeat_log"] = Json.parseToJsonElement(heartbeatLog)
        }

        // Email
        map["email_enabled"] = JsonPrimitive(isEmailEnabled())
        val emailAccountsJson = getEmailAccountsJson()
        if (emailAccountsJson.isNotBlank()) {
            map["email_accounts"] = Json.parseToJsonElement(emailAccountsJson)
            // Export per-account passwords and sync state
            try {
                val accounts = Json.parseToJsonElement(emailAccountsJson).jsonArray
                val passwords = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
                val syncStates = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
                for (account in accounts) {
                    val id = account.jsonObject["id"]?.jsonPrimitive?.content ?: continue
                    val password = getEmailPassword(id)
                    if (password.isNotBlank()) passwords[id] = JsonPrimitive(password)
                    val syncState = getEmailSyncStateJson(id)
                    if (syncState.isNotBlank()) syncStates[id] = Json.parseToJsonElement(syncState)
                }
                if (passwords.isNotEmpty()) map["email_passwords"] = JsonObject(passwords)
                if (syncStates.isNotEmpty()) map["email_sync_states"] = JsonObject(syncStates)
            } catch (_: Exception) {
            }
        }
        map["email_poll_interval"] = JsonPrimitive(getEmailPollIntervalMinutes())

        // Splinterlands
        map["splinterlands_enabled"] = JsonPrimitive(isSplinterlandsEnabled())
        val splinterlandsAccountJson = getSplinterlandsAccountJson()
        if (splinterlandsAccountJson.isNotBlank()) {
            map["splinterlands_account"] = Json.parseToJsonElement(splinterlandsAccountJson)
        }
        val splinterlandsInstanceIdsJson = getSplinterlandsInstanceIdsJson()
        if (splinterlandsInstanceIdsJson.isNotBlank()) {
            map["splinterlands_instance_ids"] = Json.parseToJsonElement(splinterlandsInstanceIdsJson)
        }
        val splinterlandsBattleLogJson = getSplinterlandsBattleLogJson()
        if (splinterlandsBattleLogJson.isNotBlank()) {
            map["splinterlands_battle_log"] = Json.parseToJsonElement(splinterlandsBattleLogJson)
        }

        // Tools — export enabled state for all known tools
        val toolStates = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
        for (toolId in toolIds) {
            toolStates[toolId] = JsonPrimitive(isToolEnabled(toolId))
        }
        if (toolStates.isNotEmpty()) map["tool_overrides"] = JsonObject(toolStates)

        // MCP
        val mcpJson = getMcpServersJson()
        if (mcpJson.isNotBlank()) {
            map["mcp_servers"] = Json.parseToJsonElement(mcpJson)
        }

        // Conversations
        val conversationsJson = getConversationsJson()
        if (!conversationsJson.isNullOrBlank()) {
            try {
                val convData = SharedJson.decodeFromString<ConversationsData>(conversationsJson)
                if (convData.conversations.isNotEmpty()) {
                    map["conversations"] = Json.parseToJsonElement(SharedJson.encodeToString(convData.conversations))
                }
            } catch (_: Exception) {
                // Skip if conversations JSON is malformed
            }
        }

        return JsonObject(map)
    }

    fun importFromJson(
        json: JsonObject,
        toolIds: List<String>,
        sections: Set<ImportSection> = ImportSection.entries.toSet(),
        replace: Boolean = true,
    ): Int {
        var errors = 0

        // Snapshot old instance IDs before overwriting configured_services
        val oldInstances = try {
            getConfiguredServiceInstances()
        } catch (_: Exception) {
            emptyList()
        }

        // Services
        if (ImportSection.SERVICES in sections) {
            try {
                settings.putString(KEY_CONFIGURED_SERVICES, json["configured_services"]?.toString() ?: "")
                settings.putString(KEY_CURRENT_SERVICE_ID, json["current_service_id"]?.jsonPrimitive?.content ?: Service.Free.id)
                settings.putBoolean(KEY_FREE_FALLBACK_ENABLED, json["free_fallback_enabled"]?.jsonPrimitive?.content?.toBoolean() ?: true)
            } catch (_: Exception) {
                errors++
            }

            // Per-instance settings — clear old instance keys, then apply new
            try {
                oldInstances.forEach { removeInstanceSettings(it.instanceId) }
                val importedInstances = getConfiguredServiceInstances()
                json["instance_settings"]?.jsonArray?.forEach { element ->
                    val obj = element.jsonObject
                    val instanceId = obj["instanceId"]?.jsonPrimitive?.content ?: return@forEach
                    obj["api_key"]?.jsonPrimitive?.content?.let { setInstanceApiKey(instanceId, it) }
                    obj["model_id"]?.jsonPrimitive?.content?.let { setInstanceModelId(instanceId, it) }
                    obj["base_url"]?.jsonPrimitive?.content?.let { baseUrl ->
                        val service = importedInstances.find { it.instanceId == instanceId }
                            ?.let { Service.fromId(it.serviceId) }
                        if (service == Service.OpenAICompatible && baseUrl.isNotBlank()) {
                            setInstanceBaseUrl(instanceId, ensureBaseUrlHasVersionPath(baseUrl))
                        } else {
                            setInstanceBaseUrl(instanceId, baseUrl)
                        }
                    }
                }
            } catch (_: Exception) {
                errors++
            }
        } else if (replace) {
            settings.putString(KEY_CONFIGURED_SERVICES, "")
            settings.putString(KEY_CURRENT_SERVICE_ID, Service.Free.id)
            settings.putBoolean(KEY_FREE_FALLBACK_ENABLED, true)
            oldInstances.forEach { removeInstanceSettings(it.instanceId) }
        }

        // Soul
        if (ImportSection.SOUL in sections) {
            try {
                setSoulText(json["soul_text"]?.jsonPrimitive?.content ?: "")
            } catch (_: Exception) {
                errors++
            }
        } else if (replace) {
            setSoulText("")
        }

        // Memory
        if (ImportSection.MEMORY in sections) {
            try {
                setMemoryEnabled(json["memory_enabled"]?.jsonPrimitive?.content?.toBoolean() ?: true)
                val memoriesElement = json["agent_memories"]
                setMemoriesJson(if (memoriesElement != null) sanitizeMemories(memoriesElement) else "")
            } catch (_: Exception) {
                errors++
            }
        } else if (replace) {
            setMemoryEnabled(true)
            setMemoriesJson("")
        }

        // Scheduling
        if (ImportSection.SCHEDULING in sections) {
            try {
                setSchedulingEnabled(json["scheduling_enabled"]?.jsonPrimitive?.content?.toBoolean() ?: false)
                val tasksElement = json["scheduled_tasks"]
                setScheduledTasksJson(if (tasksElement != null) sanitizeScheduledTasks(tasksElement) else "")
            } catch (_: Exception) {
                errors++
            }
        } else if (replace) {
            setSchedulingEnabled(false)
            setScheduledTasksJson("")
        }

        // Heartbeat
        if (ImportSection.HEARTBEAT in sections) {
            try {
                setHeartbeatConfigJson(json["heartbeat_config"]?.toString() ?: "")
                setHeartbeatPrompt(json["heartbeat_prompt"]?.jsonPrimitive?.content ?: "")
                setHeartbeatLogJson(json["heartbeat_log"]?.toString() ?: "")
            } catch (_: Exception) {
                errors++
            }
        } else if (replace) {
            setHeartbeatConfigJson("")
            setHeartbeatPrompt("")
            setHeartbeatLogJson("")
        }

        // Email
        if (ImportSection.EMAIL in sections) {
            try {
                setEmailEnabled(json["email_enabled"]?.jsonPrimitive?.content?.toBoolean() ?: true)
                setEmailAccountsJson(json["email_accounts"]?.toString() ?: "")
                json["email_passwords"]?.jsonObject?.forEach { (accountId, pw) ->
                    setEmailPassword(accountId, pw.jsonPrimitive.content)
                }
                json["email_sync_states"]?.jsonObject?.forEach { (accountId, sync) ->
                    setEmailSyncStateJson(accountId, sync.toString())
                }
                setEmailPollIntervalMinutes(json["email_poll_interval"]?.jsonPrimitive?.content?.toInt() ?: 15)
            } catch (_: Exception) {
                errors++
            }
        } else if (replace) {
            setEmailEnabled(true)
            setEmailAccountsJson("")
            setEmailPollIntervalMinutes(15)
        }

        // Splinterlands
        if (ImportSection.SPLINTERLANDS in sections) {
            try {
                setSplinterlandsEnabled(json["splinterlands_enabled"]?.jsonPrimitive?.content?.toBoolean() ?: false)
                setSplinterlandsAccountJson(json["splinterlands_account"]?.toString() ?: "")
                setSplinterlandsInstanceIdsJson(json["splinterlands_instance_ids"]?.toString() ?: "")
                setSplinterlandsBattleLogJson(json["splinterlands_battle_log"]?.toString() ?: "")
            } catch (_: Exception) {
                errors++
            }
        } else if (replace) {
            setSplinterlandsEnabled(false)
            setSplinterlandsAccountJson("")
            setSplinterlandsInstanceIdsJson("")
            setSplinterlandsBattleLogJson("")
        }

        // Tools — reset all tool overrides, then apply
        if (ImportSection.TOOLS in sections) {
            try {
                for (toolId in toolIds) {
                    settings.remove("$KEY_TOOL_PREFIX$toolId")
                }
                json["tool_overrides"]?.jsonObject?.forEach { (toolId, enabled) ->
                    setToolEnabled(toolId, enabled.jsonPrimitive.content.toBoolean())
                }
            } catch (_: Exception) {
                errors++
            }
        } else if (replace) {
            for (toolId in toolIds) {
                settings.remove("$KEY_TOOL_PREFIX$toolId")
            }
        }

        // MCP
        if (ImportSection.MCP in sections) {
            try {
                setMcpServersJson(json["mcp_servers"]?.toString() ?: "")
            } catch (_: Exception) {
                errors++
            }
        } else if (replace) {
            setMcpServersJson("")
        }

        // Conversations
        if (ImportSection.CONVERSATIONS in sections) {
            try {
                val element = json["conversations"]
                if (element != null) {
                    val conversations = sanitizeConversations(element)
                    val wrapped = SharedJson.encodeToString(ConversationsData(conversations = conversations))
                    setConversationsJson(wrapped)
                } else {
                    setConversationsJson("")
                }
            } catch (_: Exception) {
                errors++
            }
        } else if (replace) {
            setConversationsJson("")
        }

        return errors
    }

    @OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
    private fun sanitizeScheduledTasks(element: JsonElement): String {
        val array = try {
            element.jsonArray
        } catch (_: Exception) {
            return "[]"
        }
        val now = Clock.System.now().toEpochMilliseconds()
        val tasks = array.mapNotNull { item ->
            try {
                SharedJson.decodeFromString<ScheduledTask>(item.toString())
            } catch (_: Exception) {
                try {
                    val obj = item.jsonObject
                    ScheduledTask(
                        id = obj["id"]?.jsonPrimitive?.content ?: Uuid.random().toString(),
                        description = obj["description"]?.jsonPrimitive?.content ?: "",
                        prompt = obj["prompt"]?.jsonPrimitive?.content ?: "",
                        scheduledAtEpochMs = obj["scheduledAtEpochMs"]?.jsonPrimitive?.content?.toLongOrNull() ?: now,
                        createdAtEpochMs = obj["createdAtEpochMs"]?.jsonPrimitive?.content?.toLongOrNull() ?: now,
                        cron = obj["cron"]?.jsonPrimitive?.content,
                        lastResult = obj["lastResult"]?.jsonPrimitive?.content,
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }
        return SharedJson.encodeToString(tasks)
    }

    @OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
    private fun sanitizeMemories(element: JsonElement): String {
        val array = try {
            element.jsonArray
        } catch (_: Exception) {
            return "[]"
        }
        val now = Clock.System.now().toEpochMilliseconds()
        val memories = array.mapNotNull { item ->
            try {
                SharedJson.decodeFromString<MemoryEntry>(item.toString())
            } catch (_: Exception) {
                try {
                    val obj = item.jsonObject
                    MemoryEntry(
                        key = obj["key"]?.jsonPrimitive?.content ?: Uuid.random().toString(),
                        content = obj["content"]?.jsonPrimitive?.content ?: "",
                        createdAt = obj["createdAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: now,
                        updatedAt = obj["updatedAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: now,
                        category = obj["category"]?.jsonPrimitive?.content?.let { name ->
                            try {
                                MemoryCategory.valueOf(name)
                            } catch (_: Exception) {
                                MemoryCategory.GENERAL
                            }
                        } ?: MemoryCategory.GENERAL,
                        hitCount = obj["hitCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1,
                        source = obj["source"]?.jsonPrimitive?.content,
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }
        return SharedJson.encodeToString(memories)
    }

    private fun sanitizeConversations(element: JsonElement): List<Conversation> {
        val array = try {
            element.jsonArray
        } catch (_: Exception) {
            return emptyList()
        }
        return array.mapNotNull { item ->
            try {
                SharedJson.decodeFromString<Conversation>(item.toString())
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun ensureBaseUrlHasVersionPath(url: String): String {
        val trimmed = url.trimEnd('/')
        // If URL already ends with a version path segment like /v1, /v2, /api/v1, etc. — leave it
        if (trimmed.contains(versionPathRegex)) return trimmed
        return "$trimmed/v1"
    }

    companion object {
        const val KEY_CURRENT_SERVICE_ID = "current_service_id"
        const val KEY_APP_OPENS = "app_opens"

        const val KEY_CONVERSATIONS = "conversations_json"
        const val KEY_CURRENT_CONVERSATION_ID = "current_conversation_id"
        const val KEY_CURRENT_INTERACTIVE_MODE = "current_interactive_mode"
        const val KEY_CURRENT_CONVERSATION_MIGRATED = "current_conversation_migrated"
        const val KEY_ENCRYPTION_KEY = "encryption_key"
        const val KEY_MIGRATION_COMPLETE = "migration_complete_v1"
        const val KEY_TOOL_PREFIX = "tool_enabled_"
        const val KEY_SOUL = "soul_text"
        const val KEY_MEMORY_ENABLED = "memory_enabled"
        const val KEY_MEMORY_INSTRUCTIONS = "memory_instructions"
        const val KEY_AGENT_MEMORIES = "agent_memories"
        const val KEY_SCHEDULED_TASKS = "scheduled_tasks"
        const val KEY_SCHEDULING_ENABLED = "scheduling_enabled"
        const val KEY_DYNAMIC_UI_ENABLED = "dynamic_ui_enabled"
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
        const val KEY_FREE_MODE = "free_mode"
        const val KEY_FREE_SERVICE_PRIMARY = "free_service_primary"
        const val KEY_SERVICES_MIGRATION_COMPLETE = "services_migration_complete_v1"
        const val KEY_UI_SCALE = "ui_scale"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_MCP_SERVERS = "mcp_servers"
        const val KEY_INSTANCE_MIGRATION_COMPLETE = "instance_migration_complete_v1"
        const val KEY_BASE_URL_V1_MIGRATION_COMPLETE = "base_url_v1_migration_complete"

        const val KEY_SPLINTERLANDS_ENABLED = "splinterlands_enabled"
        const val KEY_SPLINTERLANDS_ACCOUNT = "splinterlands_account"
        const val KEY_SPLINTERLANDS_POSTING_KEY = "splinterlands_posting_key"
        const val KEY_SPLINTERLANDS_BATTLE_LOG = "splinterlands_battle_log"
        const val KEY_SPLINTERLANDS_INSTANCE_ID = "splinterlands_instance_id"
        const val KEY_SPLINTERLANDS_INSTANCE_IDS = "splinterlands_instance_ids"

        const val KEY_MODEL_CONTEXT_PREFIX = "model_context_"

        const val KEY_SANDBOX_ENABLED = "sandbox_enabled"

        // Basic memory guidance shared by every chat variant. The advanced `## Structured
        // Learning` block lives in `ChatSystemPromptBuilder.DEFAULT_STRUCTURED_LEARNING_SECTION`
        // and is composed in only for the remote variant.
        const val DEFAULT_MEMORY_INSTRUCTIONS =
            "You have persistent memory across conversations. " +
                "All your stored memories are listed in the system prompt grouped by category.\n\n" +
                "When you learn important information about the user (name, preferences, projects, goals, etc.), " +
                "proactively use the memory_store tool to save it.\n" +
                "Use the memory_forget tool to remove outdated or incorrect memories.\n" +
                "Do not store trivial or transient information."
    }
}
