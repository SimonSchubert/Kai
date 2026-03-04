package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.data.HeartbeatManager
import com.inspiredandroid.kai.data.MemoryStore
import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.network.tools.ToolSchema
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.tool_configure_heartbeat_description
import kai.composeapp.generated.resources.tool_configure_heartbeat_name
import kai.composeapp.generated.resources.tool_promote_learning_description
import kai.composeapp.generated.resources.tool_promote_learning_name
import kai.composeapp.generated.resources.tool_trigger_heartbeat_description
import kai.composeapp.generated.resources.tool_trigger_heartbeat_name

object HeartbeatTools {

    fun configureHeartbeatTool(heartbeatManager: HeartbeatManager) = object : Tool {
        override val schema = ToolSchema(
            name = "configure_heartbeat",
            description = "Enable or disable periodic self-checks (heartbeat). Configure interval and active hours.",
            parameters = mapOf(
                "enabled" to ParameterSchema(type = "boolean", description = "Whether heartbeat is enabled", required = false),
                "interval_minutes" to ParameterSchema(type = "integer", description = "Minutes between heartbeats (minimum 5)", required = false),
                "active_hours_start" to ParameterSchema(type = "integer", description = "Start hour for active window (0-23)", required = false),
                "active_hours_end" to ParameterSchema(type = "integer", description = "End hour for active window (0-23)", required = false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            var config = heartbeatManager.getConfig()

            (args["enabled"] as? Boolean)?.let { config = config.copy(enabled = it) }
            (args["interval_minutes"] as? Number)?.toInt()?.let { minutes ->
                if (minutes < 5) return mapOf("success" to false, "error" to "Interval must be at least 5 minutes")
                config = config.copy(intervalMinutes = minutes)
            }
            (args["active_hours_start"] as? Number)?.toInt()?.let { hour ->
                if (hour !in 0..23) return mapOf("success" to false, "error" to "active_hours_start must be 0-23")
                config = config.copy(activeHoursStart = hour)
            }
            (args["active_hours_end"] as? Number)?.toInt()?.let { hour ->
                if (hour !in 0..23) return mapOf("success" to false, "error" to "active_hours_end must be 0-23")
                config = config.copy(activeHoursEnd = hour)
            }

            heartbeatManager.saveConfig(config)
            return mapOf(
                "success" to true,
                "enabled" to config.enabled,
                "interval_minutes" to config.intervalMinutes,
                "active_hours_start" to config.activeHoursStart,
                "active_hours_end" to config.activeHoursEnd,
            )
        }
    }

    fun triggerHeartbeatTool(heartbeatManager: HeartbeatManager) = object : Tool {
        override val schema = ToolSchema(
            name = "trigger_heartbeat",
            description = "Trigger a heartbeat on the next poll cycle by resetting the last heartbeat time.",
            parameters = emptyMap(),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val config = heartbeatManager.getConfig()
            heartbeatManager.saveConfig(config.copy(lastHeartbeatEpochMs = 0L, enabled = true))
            return mapOf("success" to true, "message" to "Heartbeat will trigger on next poll cycle")
        }
    }

    fun promoteLearningTool(memoryStore: MemoryStore, appSettings: AppSettings) = object : Tool {
        override val schema = ToolSchema(
            name = "promote_learning",
            description = "Promote a well-established memory into the soul/system prompt. Use this for patterns that have been reinforced multiple times and should become permanent behavior.",
            parameters = mapOf(
                "memory_key" to ParameterSchema(type = "string", description = "The key of the memory to promote", required = true),
                "soul_addition" to ParameterSchema(type = "string", description = "The text to append to the soul/system prompt", required = true),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val memoryKey = args["memory_key"]?.toString()
                ?: return mapOf("success" to false, "error" to "Missing memory_key")
            val soulAddition = args["soul_addition"]?.toString()
                ?: return mapOf("success" to false, "error" to "Missing soul_addition")

            val memories = memoryStore.getAllMemories()
            val memory = memories.find { it.key == memoryKey }
                ?: return mapOf("success" to false, "error" to "Memory not found: $memoryKey")

            // Append to soul text
            val currentSoul = appSettings.getSoulText()
            val newSoul = if (currentSoul.isEmpty()) {
                soulAddition
            } else {
                "$currentSoul\n\n$soulAddition"
            }
            appSettings.setSoulText(newSoul)

            // Remove the promoted memory
            memoryStore.forget(memoryKey)

            return mapOf(
                "success" to true,
                "promoted_key" to memoryKey,
                "hit_count" to memory.hitCount,
                "message" to "Memory promoted to soul. Original memory removed.",
            )
        }
    }

    val configureHeartbeatToolInfo = ToolInfo(
        id = "configure_heartbeat",
        name = "Configure Heartbeat",
        description = "Configure periodic self-check behavior",
        nameRes = Res.string.tool_configure_heartbeat_name,
        descriptionRes = Res.string.tool_configure_heartbeat_description,
    )

    val triggerHeartbeatToolInfo = ToolInfo(
        id = "trigger_heartbeat",
        name = "Trigger Heartbeat",
        description = "Trigger a heartbeat on next cycle",
        nameRes = Res.string.tool_trigger_heartbeat_name,
        descriptionRes = Res.string.tool_trigger_heartbeat_description,
    )

    val promoteLearningToolInfo = ToolInfo(
        id = "promote_learning",
        name = "Promote Learning",
        description = "Promote a reinforced learning into the system prompt",
        nameRes = Res.string.tool_promote_learning_name,
        descriptionRes = Res.string.tool_promote_learning_description,
    )

    val heartbeatToolDefinitions = listOf(configureHeartbeatToolInfo, triggerHeartbeatToolInfo, promoteLearningToolInfo)

    fun getHeartbeatTools(heartbeatManager: HeartbeatManager, memoryStore: MemoryStore, appSettings: AppSettings): List<Tool> = listOf(
        configureHeartbeatTool(heartbeatManager),
        triggerHeartbeatTool(heartbeatManager),
        promoteLearningTool(memoryStore, appSettings),
    )
}
