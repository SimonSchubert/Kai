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
import kotlin.test.assertContains
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
    fun `export excludes daemon_enabled app_opens and encryption_key`() {
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
        // tool_c has no explicit override, but is exported with its default (true)
        assertEquals(true, json["tool_overrides"]?.jsonObject?.get("tool_c")?.jsonPrimitive?.boolean)

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
    fun `import resets missing settings to defaults`() {
        val target = createAppSettings()
        target.setSoulText("original")
        target.setMemoryEnabled(false)
        target.setMcpServersJson("""[{"id":"srv1"}]""")
        target.setMemoriesJson("""[{"key":"k1","value":"v1","category":"GENERAL"}]""")

        // Import JSON that only has version — missing keys should reset to defaults
        val json = JsonObject(mapOf("version" to JsonPrimitive(1)))
        val errors = target.importFromJson(json, toolIds)

        assertEquals(0, errors)
        assertEquals("", target.getSoulText())
        assertTrue(target.isMemoryEnabled())
        assertEquals("", target.getMemoriesJson())
        assertEquals("", target.getMcpServersJson())
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
            "agent_memories": [{"key": "m1", "content": "User likes cats", "category": "PREFERENCE"}],
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
        val errors = target.importFromJson(parsed, toolIds)
        assertEquals(0, errors)

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

    @Test
    fun `import with malformed field does not block other sections`() {
        val json = JsonObject(
            mapOf(
                "version" to JsonPrimitive(1),
                "soul_text" to Json.parseToJsonElement("[1,2,3]"), // malformed: array instead of string
                "memory_enabled" to JsonPrimitive(false),
                "agent_memories" to Json.parseToJsonElement("""[{"key":"m1"}]"""),
                "mcp_servers" to Json.parseToJsonElement("""[{"id":"srv1"}]"""),
            ),
        )
        val target = createAppSettings()
        val errors = target.importFromJson(json, toolIds)

        assertEquals(1, errors) // soul_text section fails
        assertFalse(target.isMemoryEnabled()) // memory section still imported
        assertTrue(target.getMemoriesJson().contains("m1"))
        assertTrue(target.getMcpServersJson().contains("srv1"))
    }

    @Test
    fun `import with all valid fields returns zero errors`() {
        val appSettings = createAppSettings()
        appSettings.setSoulText("Test")
        appSettings.setMemoryEnabled(false)
        appSettings.setMcpServersJson("""[{"id":"srv1"}]""")

        val json = appSettings.exportToJson(toolIds)
        val target = createAppSettings()
        val errors = target.importFromJson(json, toolIds)

        assertEquals(0, errors)
    }

    @Test
    fun `import clears old instance settings before applying new`() {
        val target = createAppSettings()
        target.setConfiguredServiceInstances(
            listOf(ServiceInstance("old_instance", "openai")),
        )
        target.setInstanceApiKey("old_instance", "old-key")
        target.setInstanceModelId("old_instance", "old-model")

        val json = JsonObject(
            mapOf(
                "version" to JsonPrimitive(1),
                "configured_services" to Json.parseToJsonElement("""[{"instanceId":"new_instance","serviceId":"openai"}]"""),
                "instance_settings" to Json.parseToJsonElement("""[{"instanceId":"new_instance","api_key":"new-key"}]"""),
            ),
        )
        target.importFromJson(json, toolIds)

        // Old instance keys should be cleared
        assertEquals("", target.getInstanceApiKey("old_instance"))
        assertEquals("", target.getInstanceModelId("old_instance"))
        // New instance keys should be set
        assertEquals("new-key", target.getInstanceApiKey("new_instance"))
    }

    @Test
    fun `import resets tool overrides before applying new`() {
        val target = createAppSettings()
        target.setToolEnabled("tool_a", false)
        target.setToolEnabled("tool_b", false)

        // Import with only tool_a override — tool_b should be reset
        val json = JsonObject(
            mapOf(
                "version" to JsonPrimitive(1),
                "tool_overrides" to Json.parseToJsonElement("""{"tool_a": false}"""),
            ),
        )
        target.importFromJson(json, toolIds)

        assertFalse(target.isToolEnabled("tool_a"))
        assertTrue(target.isToolEnabled("tool_b")) // reset to default (true)
    }

    @Test
    fun `import with sections filter only imports selected sections`() {
        val appSettings = createAppSettings()
        appSettings.setSoulText("New soul")
        appSettings.setMcpServersJson("""[{"id":"srv1"}]""")
        appSettings.setMemoryEnabled(false)

        val json = appSettings.exportToJson(toolIds)

        val target = createAppSettings()
        target.setSoulText("Original soul")
        target.setMemoryEnabled(true)
        target.setMcpServersJson("""[{"id":"original"}]""")

        // Only import SOUL section, merge mode (leave others unchanged)
        target.importFromJson(json, toolIds, sections = setOf(ImportSection.SOUL), replace = false)

        assertEquals("New soul", target.getSoulText())
        // Memory and MCP should be unchanged (merge mode)
        assertTrue(target.isMemoryEnabled())
        assertTrue(target.getMcpServersJson().contains("original"))
    }

    @Test
    fun `import with replace mode resets unselected sections`() {
        val target = createAppSettings()
        target.setSoulText("Original soul")
        target.setMemoryEnabled(false)
        target.setMcpServersJson("""[{"id":"srv1"}]""")

        val json = JsonObject(
            mapOf(
                "version" to JsonPrimitive(1),
                "soul_text" to JsonPrimitive("New soul"),
            ),
        )

        // Only import SOUL, replace mode — unselected sections reset to defaults
        target.importFromJson(json, toolIds, sections = setOf(ImportSection.SOUL), replace = true)

        assertEquals("New soul", target.getSoulText())
        // Memory and MCP should be reset to defaults
        assertTrue(target.isMemoryEnabled()) // default is true
        assertEquals("", target.getMcpServersJson()) // default is empty
    }

    @Test
    fun `import with merge mode preserves unselected sections`() {
        val target = createAppSettings()
        target.setSoulText("Original soul")
        target.setMemoryEnabled(false)
        target.setMcpServersJson("""[{"id":"srv1"}]""")

        val json = JsonObject(
            mapOf(
                "version" to JsonPrimitive(1),
                "soul_text" to JsonPrimitive("New soul"),
            ),
        )

        // Only import SOUL, merge mode — unselected sections stay unchanged
        target.importFromJson(json, toolIds, sections = setOf(ImportSection.SOUL), replace = false)

        assertEquals("New soul", target.getSoulText())
        // Memory and MCP should be preserved
        assertFalse(target.isMemoryEnabled())
        assertTrue(target.getMcpServersJson().contains("srv1"))
    }

    @Test
    fun `detectImportSections returns correct sections with counts`() {
        val json = JsonObject(
            mapOf(
                "version" to JsonPrimitive(1),
                "soul_text" to JsonPrimitive("hello"),
                "mcp_servers" to Json.parseToJsonElement("""[{"id":"srv1"},{"id":"srv2"}]"""),
                "memory_enabled" to JsonPrimitive(true),
                "agent_memories" to Json.parseToJsonElement("""[{"key":"m1"},{"key":"m2"},{"key":"m3"}]"""),
            ),
        )
        val sections = detectImportSections(json)
        assertContains(sections.keys, ImportSection.SOUL)
        assertContains(sections.keys, ImportSection.MCP)
        assertContains(sections.keys, ImportSection.MEMORY)
        assertFalse(ImportSection.SERVICES in sections)
        assertFalse(ImportSection.SCHEDULING in sections)
        assertFalse(ImportSection.HEARTBEAT in sections)
        assertFalse(ImportSection.EMAIL in sections)
        assertFalse(ImportSection.TOOLS in sections)
        // Check counts
        assertNull(sections[ImportSection.SOUL]) // soul has no count
        assertEquals("2", sections[ImportSection.MCP])
        assertEquals("3", sections[ImportSection.MEMORY])
    }

    @Test
    fun `import tasks with missing id field auto-generates ids`() {
        val json = JsonObject(
            mapOf(
                "version" to JsonPrimitive(1),
                "scheduling_enabled" to JsonPrimitive(true),
                "scheduled_tasks" to Json.parseToJsonElement(
                    """[
                        {"prompt": "Remind me", "description": "test"},
                        {"prompt": "Another task"}
                    ]""",
                ),
            ),
        )
        val target = createAppSettings()
        val errors = target.importFromJson(json, toolIds)
        assertEquals(0, errors)

        val tasksJson = target.getScheduledTasksJson()
        val tasks = SharedJson.decodeFromString<List<ScheduledTask>>(tasksJson)
        assertEquals(2, tasks.size)
        assertTrue(tasks[0].id.isNotBlank())
        assertTrue(tasks[1].id.isNotBlank())
        assertTrue(tasks[0].id != tasks[1].id)
        assertEquals("Remind me", tasks[0].prompt)
    }

    @Test
    fun `import tasks with invalid status defaults to PENDING`() {
        val json = JsonObject(
            mapOf(
                "version" to JsonPrimitive(1),
                "scheduled_tasks" to Json.parseToJsonElement(
                    """[{"id": "t1", "prompt": "test", "description": "d", "status": "INVALID_STATUS",
                        "scheduledAtEpochMs": 1000, "createdAtEpochMs": 1000}]""",
                ),
            ),
        )
        val target = createAppSettings()
        target.importFromJson(json, toolIds)

        val tasks = SharedJson.decodeFromString<List<ScheduledTask>>(target.getScheduledTasksJson())
        assertEquals(1, tasks.size)
        assertEquals(TaskStatus.PENDING, tasks[0].status)
    }

    @Test
    fun `import normalizes old-format OpenAI-compatible base URL by appending v1`() {
        val json = JsonObject(
            mapOf(
                "version" to JsonPrimitive(1),
                "configured_services" to Json.parseToJsonElement(
                    """[{"instanceId":"compat1","serviceId":"openai-compatible"}]""",
                ),
                "instance_settings" to Json.parseToJsonElement(
                    """[{"instanceId":"compat1","api_key":"","base_url":"http://localhost:11434"}]""",
                ),
            ),
        )
        val target = createAppSettings()
        target.importFromJson(json, toolIds)

        assertEquals("http://localhost:11434/v1", target.getInstanceBaseUrl("compat1"))
    }

    @Test
    fun `import does not double-append v1 to already-versioned base URL`() {
        val json = JsonObject(
            mapOf(
                "version" to JsonPrimitive(1),
                "configured_services" to Json.parseToJsonElement(
                    """[{"instanceId":"compat1","serviceId":"openai-compatible"}]""",
                ),
                "instance_settings" to Json.parseToJsonElement(
                    """[{"instanceId":"compat1","base_url":"http://localhost:11434/v1"}]""",
                ),
            ),
        )
        val target = createAppSettings()
        target.importFromJson(json, toolIds)

        assertEquals("http://localhost:11434/v1", target.getInstanceBaseUrl("compat1"))
    }

    @Test
    fun `import memories with missing category defaults to GENERAL`() {
        val json = JsonObject(
            mapOf(
                "version" to JsonPrimitive(1),
                "memory_enabled" to JsonPrimitive(true),
                "agent_memories" to Json.parseToJsonElement(
                    """[{"key": "m1", "content": "User likes cats"}]""",
                ),
            ),
        )
        val target = createAppSettings()
        target.importFromJson(json, toolIds)

        val memories = SharedJson.decodeFromString<List<MemoryEntry>>(target.getMemoriesJson())
        assertEquals(1, memories.size)
        assertEquals(MemoryCategory.GENERAL, memories[0].category)
        assertEquals("User likes cats", memories[0].content)
    }

    @Test
    fun `import memories fallback path preserves category`() {
        // createdAt as non-numeric string forces the primary decode to fail,
        // exercising the manual-extraction fallback in sanitizeMemories()
        val json = JsonObject(
            mapOf(
                "version" to JsonPrimitive(1),
                "memory_enabled" to JsonPrimitive(true),
                "agent_memories" to Json.parseToJsonElement(
                    """[{"key": "m1", "content": "prefers dark mode", "createdAt": "not-a-number", "updatedAt": "also-not", "category": "PREFERENCE", "hitCount": "3", "source": "chat"}]""",
                ),
            ),
        )
        val target = createAppSettings()
        target.importFromJson(json, toolIds)

        val memories = SharedJson.decodeFromString<List<MemoryEntry>>(target.getMemoriesJson())
        assertEquals(1, memories.size)
        assertEquals("m1", memories[0].key)
        assertEquals("prefers dark mode", memories[0].content)
        assertEquals(MemoryCategory.PREFERENCE, memories[0].category)
        assertEquals(3, memories[0].hitCount)
        assertEquals("chat", memories[0].source)
    }

    @Test
    fun `import memories fallback path with invalid category defaults to GENERAL`() {
        val json = JsonObject(
            mapOf(
                "version" to JsonPrimitive(1),
                "memory_enabled" to JsonPrimitive(true),
                "agent_memories" to Json.parseToJsonElement(
                    """[{"key": "m2", "content": "test", "createdAt": "bad", "updatedAt": "bad", "category": "NONEXISTENT"}]""",
                ),
            ),
        )
        val target = createAppSettings()
        target.importFromJson(json, toolIds)

        val memories = SharedJson.decodeFromString<List<MemoryEntry>>(target.getMemoriesJson())
        assertEquals(1, memories.size)
        assertEquals(MemoryCategory.GENERAL, memories[0].category)
    }

    @Test
    fun `import valid tasks round-trips unchanged`() {
        val appSettings = createAppSettings()
        // Use the export from a settings instance with proper tasks
        appSettings.setScheduledTasksJson(
            """[{"id":"t1","description":"desc","prompt":"p","scheduledAtEpochMs":1000,"createdAtEpochMs":2000,"cron":null,"status":"COMPLETED","lastResult":"done"}]""",
        )
        appSettings.setSchedulingEnabled(true)

        val exported = appSettings.exportToJson(toolIds)

        val target = createAppSettings()
        target.importFromJson(exported, toolIds)

        val tasks = SharedJson.decodeFromString<List<ScheduledTask>>(target.getScheduledTasksJson())
        assertEquals(1, tasks.size)
        assertEquals("t1", tasks[0].id)
        assertEquals("desc", tasks[0].description)
        assertEquals("p", tasks[0].prompt)
        assertEquals(1000L, tasks[0].scheduledAtEpochMs)
        assertEquals(2000L, tasks[0].createdAtEpochMs)
        assertEquals(TaskStatus.COMPLETED, tasks[0].status)
        assertEquals("done", tasks[0].lastResult)
    }

    @Test
    fun `export includes conversations when present`() {
        val appSettings = createAppSettings()
        val convData = ConversationsData(
            conversations = listOf(
                Conversation(
                    id = "conv1",
                    messages = listOf(Conversation.Message(id = "msg1", role = "user", content = "Hello")),
                    createdAt = 1000L,
                    updatedAt = 2000L,
                    title = "Test chat",
                ),
            ),
        )
        appSettings.setConversationsJson(SharedJson.encodeToString(convData))

        val json = appSettings.exportToJson(toolIds)

        assertTrue(json.containsKey("conversations"))
        val conversations = json["conversations"]!!.jsonArray
        assertEquals(1, conversations.size)
        assertEquals("conv1", conversations[0].jsonObject["id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `export omits conversations when empty`() {
        val appSettings = createAppSettings()
        val json = appSettings.exportToJson(toolIds)
        assertFalse(json.containsKey("conversations"))
    }

    @Test
    fun `import conversations round-trips correctly`() {
        val appSettings = createAppSettings()
        val convData = ConversationsData(
            conversations = listOf(
                Conversation(
                    id = "conv1",
                    messages = listOf(
                        Conversation.Message(id = "msg1", role = "user", content = "Hello"),
                        Conversation.Message(id = "msg2", role = "assistant", content = "Hi there!"),
                    ),
                    createdAt = 1000L,
                    updatedAt = 2000L,
                    title = "Test chat",
                    type = Conversation.TYPE_CHAT,
                ),
                Conversation(
                    id = "conv2",
                    messages = listOf(Conversation.Message(id = "msg3", role = "user", content = "Heartbeat")),
                    createdAt = 3000L,
                    updatedAt = 4000L,
                    title = "HB",
                    type = Conversation.TYPE_HEARTBEAT,
                ),
            ),
        )
        appSettings.setConversationsJson(SharedJson.encodeToString(convData))

        val exported = appSettings.exportToJson(toolIds)

        val target = createAppSettings()
        target.importFromJson(exported, toolIds)

        val imported = SharedJson.decodeFromString<ConversationsData>(target.getConversationsJson()!!)
        assertEquals(2, imported.conversations.size)
        assertEquals("conv1", imported.conversations[0].id)
        assertEquals("Test chat", imported.conversations[0].title)
        assertEquals(2, imported.conversations[0].messages.size)
        assertEquals("conv2", imported.conversations[1].id)
        assertEquals(Conversation.TYPE_HEARTBEAT, imported.conversations[1].type)
    }

    @Test
    fun `import conversations with malformed entries skips invalid ones`() {
        val json = JsonObject(
            mapOf(
                "version" to JsonPrimitive(1),
                "conversations" to Json.parseToJsonElement(
                    """[
                        {"id": "conv1", "messages": [{"id": "m1", "role": "user", "content": "Hi"}], "createdAt": 1000, "updatedAt": 2000, "title": "Good"},
                        {"bad": "entry"},
                        {"id": "conv3", "messages": [], "createdAt": 3000, "updatedAt": 4000}
                    ]""",
                ),
            ),
        )
        val target = createAppSettings()
        target.importFromJson(json, toolIds)

        val imported = SharedJson.decodeFromString<ConversationsData>(target.getConversationsJson()!!)
        assertEquals(2, imported.conversations.size)
        assertEquals("conv1", imported.conversations[0].id)
        assertEquals("conv3", imported.conversations[1].id)
    }

    @Test
    fun `detect import sections finds conversations`() {
        val json = JsonObject(
            mapOf(
                "version" to JsonPrimitive(1),
                "conversations" to Json.parseToJsonElement(
                    """[{"id": "c1", "messages": [], "createdAt": 1000, "updatedAt": 2000}]""",
                ),
            ),
        )
        val sections = detectImportSections(json)
        assertTrue(ImportSection.CONVERSATIONS in sections)
        assertEquals("1", sections[ImportSection.CONVERSATIONS])
    }

    @Test
    fun `import with replace clears conversations when section not selected`() {
        val appSettings = createAppSettings()
        val convData = ConversationsData(
            conversations = listOf(
                Conversation(id = "conv1", messages = emptyList(), createdAt = 1000L, updatedAt = 2000L),
            ),
        )
        appSettings.setConversationsJson(SharedJson.encodeToString(convData))

        val json = JsonObject(mapOf("version" to JsonPrimitive(1), "soul_text" to JsonPrimitive("test")))
        appSettings.importFromJson(json, toolIds, sections = setOf(ImportSection.SOUL), replace = true)

        assertEquals("", appSettings.getConversationsJson())
    }
}
