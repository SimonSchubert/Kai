package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.data.HeartbeatManager
import com.inspiredandroid.kai.data.MemoryStore
import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.network.tools.ToolSchema
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.tool_promote_learning_description
import kai.composeapp.generated.resources.tool_promote_learning_name
import kai.composeapp.generated.resources.tool_trigger_heartbeat_description
import kai.composeapp.generated.resources.tool_trigger_heartbeat_name

object HeartbeatTools {

    fun triggerHeartbeatTool(heartbeatManager: HeartbeatManager) = object : Tool {
        override val schema = ToolSchema(
            name = "trigger_heartbeat",
            description = "Force an immediate heartbeat self-check on the next poll cycle. Only works if heartbeat is already enabled by the user (Settings → Agent → Heartbeat). Use sparingly — a regular chat response is almost always the right answer.",
            parameters = emptyMap(),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val config = heartbeatManager.getConfig()
            if (!config.enabled) {
                return mapOf(
                    "success" to false,
                    "error" to "Heartbeat is disabled. The user must enable it in Settings → Agent → Heartbeat.",
                )
            }
            heartbeatManager.saveConfig(config.copy(lastHeartbeatEpochMs = 0L))
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

    val heartbeatToolDefinitions = listOf(triggerHeartbeatToolInfo, promoteLearningToolInfo)

    fun getHeartbeatTools(heartbeatManager: HeartbeatManager, memoryStore: MemoryStore, appSettings: AppSettings): List<Tool> = listOf(
        triggerHeartbeatTool(heartbeatManager),
        promoteLearningTool(memoryStore, appSettings),
    )
}
