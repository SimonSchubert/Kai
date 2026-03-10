package com.inspiredandroid.kai.data

import com.russhwolf.settings.MapSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppSettingsExportImportTest {

    private val prettyJson = Json { prettyPrint = true }
    private val toolIds = listOf("tool_a", "tool_b", "tool_c")

    private fun createAppSettings(settings: MapSettings = MapSettings()) = AppSettings(settings)

    @Test
    fun `export includes version field`() {
        val appSettings = createAppSettings()
        val json = appSettings.exportToJson(toolIds)
        assertEquals(1, json["version"]?.jsonPrimitive?.int)
    }

    @Test
    fun `export excludes daemon_enabled, app_opens, encryption_key`() {
        val settings = MapSettings()
        val appSettings = AppSettings(settings)
        appSettings.setDaemonEnabled(true)
        appSettings.trackAppOpen()

        val json = appSettings.exportToJson(toolIds)
        assertNull(json["daemon_enabled"])
        assertNull(json["app_opens"])
        assertNull(json["encryption_key"])
    }

    @Test
    fun `export and import round-trips soul text`() {
        val appSettings = createAppSettings()
        appSettings.setSoulText("You are a helpful pirate.")

        val json = appSettings.exportToJson(toolIds)

        val target = createAppSettings()
        target.importFromJson(json, toolIds)
        assertEquals("You are a helpful pirate.", target.getSoulText())
    }

    @Test
    fun `export and import round-trips memory settings`() {
        val appSettings = createAppSettings()
        appSettings.setMemoryEnabled(false)
        appSettings.setMemoriesJson("""[{"key":"k1","value":"v1","category":"GENERAL"}]""")

        val json = appSettings.exportToJson(toolIds)

        val target = createAppSettings()
        target.importFromJson(json, toolIds)
        assertFalse(target.isMemoryEnabled())
        assertTrue(target.getMemoriesJson().contains("k1"))
    }

    @Test
    fun `export and import round-trips scheduling settings`() {
        val appSettings = createAppSettings()
        appSettings.setSchedulingEnabled(false)
        appSettings.setScheduledTasksJson("""[{"id":"t1","prompt":"test"}]""")

        val json = appSettings.exportToJson(toolIds)

        val target = createAppSettings()
        target.importFromJson(json, toolIds)
        assertFalse(target.isSchedulingEnabled())
        assertTrue(target.getScheduledTasksJson().contains("t1"))
    }

    @Test
    fun `export and import round-trips heartbeat settings`() {
        val appSettings = createAppSettings()
        appSettings.setHeartbeatConfigJson("""{"enabled":true,"intervalMinutes":60}""")
        appSettings.setHeartbeatPrompt("Check tasks")
        appSettings.setHeartbeatLogJson("""[{"timestamp":"2025-01-01"}]""")

        val json = appSettings.exportToJson(toolIds)

        val target = createAppSettings()
        target.importFromJson(json, toolIds)
        assertTrue(target.getHeartbeatConfigJson().contains("60"))
        assertEquals("Check tasks", target.getHeartbeatPrompt())
        assertTrue(target.getHeartbeatLogJson().contains("2025-01-01"))
    }

    @Test
    fun `export and import round-trips email settings`() {
        val appSettings = createAppSettings()
        appSettings.setEmailEnabled(false)
        appSettings.setEmailAccountsJson("""[{"id":"acc1","email":"test@test.com"}]""")
        appSettings.setEmailPassword("acc1", "secret123")
        appSettings.setEmailSyncStateJson("acc1", """{"lastUid":42}""")
        appSettings.setEmailPollIntervalMinutes(30)

        val json = appSettings.exportToJson(toolIds)

        val target = createAppSettings()
        target.importFromJson(json, toolIds)
        assertFalse(target.isEmailEnabled())
        assertTrue(target.getEmailAccountsJson().contains("acc1"))
        assertEquals("secret123", target.getEmailPassword("acc1"))
        assertTrue(target.getEmailSyncStateJson("acc1").contains("42"))
        assertEquals(30, target.getEmailPollIntervalMinutes())
    }

    @Test
    fun `export and import round-trips tool overrides`() {
        val appSettings = createAppSettings()
        appSettings.setToolEnabled("tool_a", false)
        appSettings.setToolEnabled("tool_b", true)

        val json = appSettings.exportToJson(toolIds)
        assertEquals(false, json["tool_overrides"]?.jsonObject?.get("tool_a")?.jsonPrimitive?.boolean)
        assertEquals(true, json["tool_overrides"]?.jsonObject?.get("tool_b")?.jsonPrimitive?.boolean)
        // tool_c has no override set, should not appear
        assertNull(json["tool_overrides"]?.jsonObject?.get("tool_c"))

        val target = createAppSettings()
        target.importFromJson(json, toolIds)
        assertFalse(target.isToolEnabled("tool_a"))
        assertTrue(target.isToolEnabled("tool_b"))
    }

    @Test
    fun `export and import round-trips configured services and per-instance settings`() {
        val appSettings = createAppSettings()
        appSettings.setConfiguredServiceInstances(
            listOf(
                ServiceInstance("openai", "openai"),
                ServiceInstance("gemini", "gemini"),
            ),
        )
        appSettings.selectService(Service.OpenAI)
        appSettings.setFreeFallbackEnabled(false)
        appSettings.setInstanceApiKey("openai", "sk-key")
        appSettings.setInstanceModelId("openai", "gpt-4")
        appSettings.setInstanceBaseUrl("gemini", "https://custom.url")

        val json = appSettings.exportToJson(toolIds)

        val target = createAppSettings()
        target.importFromJson(json, toolIds)
        val instances = target.getConfiguredServiceInstances()
        assertEquals(2, instances.size)
        assertEquals("openai", instances[0].instanceId)
        assertEquals("gemini", instances[1].instanceId)
        assertEquals(Service.OpenAI, target.currentService())
        assertFalse(target.isFreeFallbackEnabled())
        assertEquals("sk-key", target.getInstanceApiKey("openai"))
        assertEquals("gpt-4", target.getInstanceModelId("openai"))
        assertEquals("https://custom.url", target.getInstanceBaseUrl("gemini"))
    }

    @Test
    fun `export and import round-trips MCP servers`() {
        val appSettings = createAppSettings()
        appSettings.setMcpServersJson("""[{"id":"srv1","name":"Test","url":"http://localhost"}]""")

        val json = appSettings.exportToJson(toolIds)

        val target = createAppSettings()
        target.importFromJson(json, toolIds)
        assertTrue(target.getMcpServersJson().contains("srv1"))
    }

    @Test
    fun `export does not include ui_scale`() {
        val appSettings = createAppSettings()
        appSettings.setUiScale(1.5f)

        val json = appSettings.exportToJson(toolIds)
        assertNull(json["ui_scale"])
    }

    @Test
    fun `import ignores unknown keys gracefully`() {
        val json = JsonObject(
            mapOf(
                "version" to JsonPrimitive(1),
                "soul_text" to JsonPrimitive("hello"),
                "unknown_future_key" to JsonPrimitive("should be ignored"),
            ),
        )
        val target = createAppSettings()
        target.importFromJson(json, toolIds)
        assertEquals("hello", target.getSoulText())
    }

    @Test
    fun `import with missing keys does not overwrite existing settings`() {
        val target = createAppSettings()
        target.setSoulText("original")
        target.setMemoryEnabled(false)

        // Import JSON that only has version - no soul_text or memory_enabled
        val json = JsonObject(mapOf("version" to JsonPrimitive(1)))
        target.importFromJson(json, toolIds)

        // Existing values should be preserved
        assertEquals("original", target.getSoulText())
        assertFalse(target.isMemoryEnabled())
    }

    @Test
    fun `import does not restore daemon_enabled even if present in JSON`() {
        val json = JsonObject(
            mapOf(
                "version" to JsonPrimitive(1),
                "daemon_enabled" to JsonPrimitive(true),
            ),
        )
        val target = createAppSettings()
        target.importFromJson(json, toolIds)
        assertFalse(target.isDaemonEnabled())
    }

    @Test
    fun `exported JSON can be serialized and deserialized as string`() {
        val appSettings = createAppSettings()
        appSettings.setSoulText("Test soul")
        appSettings.setMemoryEnabled(false)

        val jsonObject = appSettings.exportToJson(toolIds)
        val jsonString = prettyJson.encodeToString(JsonObject.serializer(), jsonObject)

        // Parse back and import into a fresh instance
        val parsed = Json.parseToJsonElement(jsonString).jsonObject
        val target = createAppSettings()
        target.importFromJson(parsed, toolIds)

        assertEquals("Test soul", target.getSoulText())
        assertFalse(target.isMemoryEnabled())
    }

    /**
     * Snapshot test: this JSON represents a v1 export. If the export format changes,
     * this test ensures we can still import old exports correctly.
     */
    @Test
    fun `v1 snapshot JSON imports correctly`() {
        val v1Json = """
        {
            "version": 1,
            "configured_services": [
                {"instanceId": "openai", "serviceId": "openai"},
                {"instanceId": "gemini", "serviceId": "gemini"}
            ],
            "current_service_id": "openai",
            "free_fallback_enabled": false,
            "instance_settings": [
                {"instanceId": "openai", "api_key": "sk-abc", "model_id": "gpt-4o"},
                {"instanceId": "gemini", "api_key": "gem-key"}
            ],
            "soul_text": "Be helpful.",
            "memory_enabled": true,
            "agent_memories": [{"key": "m1", "value": "User likes cats", "category": "PREFERENCE"}],
            "scheduling_enabled": false,
            "scheduled_tasks": [{"id": "task1", "prompt": "Remind me"}],
            "heartbeat_config": {"enabled": true, "intervalMinutes": 45, "activeHoursStart": 9, "activeHoursEnd": 21},
            "heartbeat_prompt": "Check on things",
            "heartbeat_log": [{"timestamp": "2025-06-01T12:00:00Z"}],
            "email_enabled": true,
            "email_accounts": [{"id": "em1", "email": "user@example.com"}],
            "email_passwords": {"em1": "p4ss"},
            "email_sync_states": {"em1": {"lastUid": 100}},
            "email_poll_interval": 10,
            "tool_overrides": {"tool_a": false, "tool_b": true},
            "mcp_servers": [{"id": "mcp1", "name": "Local", "url": "http://localhost:3000"}]
        }
        """.trimIndent()

        val parsed = Json.parseToJsonElement(v1Json).jsonObject
        val target = createAppSettings()
        target.importFromJson(parsed, toolIds)

        // Services
        val instances = target.getConfiguredServiceInstances()
        assertEquals(2, instances.size)
        assertEquals("openai", instances[0].serviceId)
        assertEquals("gemini", instances[1].serviceId)
        assertEquals(Service.OpenAI, target.currentService())
        assertFalse(target.isFreeFallbackEnabled())

        // Per-instance
        assertEquals("sk-abc", target.getInstanceApiKey("openai"))
        assertEquals("gpt-4o", target.getInstanceModelId("openai"))
        assertEquals("gem-key", target.getInstanceApiKey("gemini"))

        // Soul
        assertEquals("Be helpful.", target.getSoulText())

        // Memory
        assertTrue(target.isMemoryEnabled())
        assertTrue(target.getMemoriesJson().contains("User likes cats"))

        // Scheduling
        assertFalse(target.isSchedulingEnabled())
        assertTrue(target.getScheduledTasksJson().contains("task1"))

        // Heartbeat
        assertTrue(target.getHeartbeatConfigJson().contains("45"))
        assertEquals("Check on things", target.getHeartbeatPrompt())
        assertTrue(target.getHeartbeatLogJson().contains("2025-06-01"))

        // Email
        assertTrue(target.isEmailEnabled())
        assertTrue(target.getEmailAccountsJson().contains("em1"))
        assertEquals("p4ss", target.getEmailPassword("em1"))
        assertTrue(target.getEmailSyncStateJson("em1").contains("100"))
        assertEquals(10, target.getEmailPollIntervalMinutes())

        // Tools
        assertFalse(target.isToolEnabled("tool_a"))
        assertTrue(target.isToolEnabled("tool_b"))

        // MCP
        assertTrue(target.getMcpServersJson().contains("mcp1"))
    }
}
