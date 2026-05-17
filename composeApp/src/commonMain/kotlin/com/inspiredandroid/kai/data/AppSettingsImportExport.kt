@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, kotlin.time.ExperimentalTime::class)

package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.data.AppSettings.Companion.KEY_CONFIGURED_SERVICES
import com.inspiredandroid.kai.data.AppSettings.Companion.KEY_CURRENT_SERVICE_ID
import com.inspiredandroid.kai.data.AppSettings.Companion.KEY_FREE_FALLBACK_ENABLED
import com.inspiredandroid.kai.data.AppSettings.Companion.KEY_TOOL_PREFIX
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.uuid.Uuid

fun AppSettings.exportToJson(
    toolIds: List<String>,
    sections: Set<ImportSection> = ImportSection.entries.toSet(),
): JsonObject {
    val map = mutableMapOf<String, JsonElement>()
    map["version"] = JsonPrimitive(1)

    if (ImportSection.SERVICES in sections) {
        val configuredJson = settings.getString(KEY_CONFIGURED_SERVICES, "")
        if (configuredJson.isNotBlank()) {
            map["configured_services"] = Json.parseToJsonElement(configuredJson)
        }
        map["current_service_id"] = JsonPrimitive(settings.getString(KEY_CURRENT_SERVICE_ID, Service.Free.id))
        map["free_fallback_enabled"] = JsonPrimitive(isFreeFallbackEnabled())

        val instances = getConfiguredServiceInstances()
        if (instances.isNotEmpty()) {
            val instanceSettings = JsonArray(
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
    }

    if (ImportSection.SOUL in sections) {
        val soul = getSoulText()
        if (soul.isNotBlank()) map["soul_text"] = JsonPrimitive(soul)
    }

    if (ImportSection.MEMORY in sections) {
        map["memory_enabled"] = JsonPrimitive(isMemoryEnabled())
        val memoriesJson = getMemoriesJson()
        if (memoriesJson.isNotBlank() && memoriesJson != "[]") {
            map["agent_memories"] = Json.parseToJsonElement(memoriesJson)
        }
    }

    if (ImportSection.SCHEDULING in sections) {
        map["scheduling_enabled"] = JsonPrimitive(isSchedulingEnabled())
        val tasksJson = getScheduledTasksJson()
        if (tasksJson.isNotBlank() && tasksJson != "[]") {
            map["scheduled_tasks"] = Json.parseToJsonElement(tasksJson)
        }
    }

    if (ImportSection.HEARTBEAT in sections) {
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
    }

    if (ImportSection.EMAIL in sections) {
        map["email_enabled"] = JsonPrimitive(isEmailEnabled())
        val emailAccountsJson = getEmailAccountsJson()
        if (emailAccountsJson.isNotBlank()) {
            map["email_accounts"] = Json.parseToJsonElement(emailAccountsJson)
            try {
                val accounts = Json.parseToJsonElement(emailAccountsJson).jsonArray
                val passwords = mutableMapOf<String, JsonElement>()
                val syncStates = mutableMapOf<String, JsonElement>()
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
    }

    if (ImportSection.SMS in sections) {
        map["sms_enabled"] = JsonPrimitive(isSmsEnabled())
        map["sms_poll_interval"] = JsonPrimitive(getSmsPollIntervalMinutes())
        map["sms_send_enabled"] = JsonPrimitive(isSmsSendEnabled())
    }

    if (ImportSection.SPLINTERLANDS in sections) {
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
    }

    if (ImportSection.TOOLS in sections) {
        val toolStates = mutableMapOf<String, JsonElement>()
        for (toolId in toolIds) {
            toolStates[toolId] = JsonPrimitive(isToolEnabled(toolId))
        }
        if (toolStates.isNotEmpty()) map["tool_overrides"] = JsonObject(toolStates)
    }

    if (ImportSection.MCP in sections) {
        val mcpJson = getMcpServersJson()
        if (mcpJson.isNotBlank()) {
            map["mcp_servers"] = Json.parseToJsonElement(mcpJson)
        }
    }

    if (ImportSection.CONVERSATIONS in sections) {
        val conversationsJson = getConversationsJson()
        if (!conversationsJson.isNullOrBlank()) {
            try {
                val convData = SharedJson.decodeFromString<ConversationsData>(conversationsJson)
                if (convData.conversations.isNotEmpty()) {
                    map["conversations"] = Json.parseToJsonElement(SharedJson.encodeToString(convData.conversations))
                }
            } catch (_: Exception) {
            }
        }
    }

    return JsonObject(map)
}

fun AppSettings.importFromJson(
    json: JsonObject,
    toolIds: List<String>,
    sections: Set<ImportSection> = ImportSection.entries.toSet(),
    replace: Boolean = true,
): Int {
    var errors = 0

    val oldInstances = try {
        getConfiguredServiceInstances()
    } catch (_: Exception) {
        emptyList()
    }

    if (ImportSection.SERVICES in sections) {
        try {
            settings.putString(KEY_CONFIGURED_SERVICES, json["configured_services"]?.toString() ?: "")
            settings.putString(KEY_CURRENT_SERVICE_ID, json["current_service_id"]?.jsonPrimitive?.content ?: Service.Free.id)
            settings.putBoolean(KEY_FREE_FALLBACK_ENABLED, json["free_fallback_enabled"]?.jsonPrimitive?.content?.toBoolean() ?: true)
        } catch (_: Exception) {
            errors++
        }

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

    if (ImportSection.SOUL in sections) {
        try {
            setSoulText(json["soul_text"]?.jsonPrimitive?.content ?: "")
        } catch (_: Exception) {
            errors++
        }
    } else if (replace) {
        setSoulText("")
    }

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

    if (ImportSection.SMS in sections) {
        try {
            setSmsEnabled(json["sms_enabled"]?.jsonPrimitive?.content?.toBoolean() ?: false)
            setSmsPollIntervalMinutes(json["sms_poll_interval"]?.jsonPrimitive?.content?.toInt() ?: 15)
            setSmsSendEnabled(json["sms_send_enabled"]?.jsonPrimitive?.content?.toBoolean() ?: false)
        } catch (_: Exception) {
            errors++
        }
    } else if (replace) {
        setSmsEnabled(false)
        setSmsPollIntervalMinutes(15)
        setSmsSendEnabled(false)
    }

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

    if (ImportSection.MCP in sections) {
        try {
            setMcpServersJson(json["mcp_servers"]?.toString() ?: "")
        } catch (_: Exception) {
            errors++
        }
    } else if (replace) {
        setMcpServersJson("")
    }

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
