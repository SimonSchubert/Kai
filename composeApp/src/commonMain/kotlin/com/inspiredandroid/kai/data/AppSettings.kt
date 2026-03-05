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
