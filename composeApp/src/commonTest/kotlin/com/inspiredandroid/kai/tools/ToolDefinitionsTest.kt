package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.data.MemoryStore
import com.inspiredandroid.kai.network.tools.Tool
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the pairing between a tool's executable schema and the [com.inspiredandroid.kai.network.tools.ToolInfo]
 * the UI resolves its display name from.
 *
 * A tool that runs but has no definition falls back to showing its raw id in chat, which is how
 * the notification tools regressed: `notificationToolDefinitions` existed but nothing referenced it.
 */
class ToolDefinitionsTest {

    private val definitionIds = CommonTools.commonToolDefinitions.map { it.id }

    @Test
    fun everyCommonToolHasADefinition() {
        // Tools with no collaborators, gated by per-tool switches.
        val stateless: List<Tool> = listOf(
            CommonTools.localTimeTool,
            CommonTools.ipLocationTool,
            CommonTools.openUrlTool,
            WebSearchTool,
            FetchUrlTool,
        )
        val missing = stateless.map { it.schema.name }.filterNot { it in definitionIds }
        assertTrue(missing.isEmpty(), "Executable tools with no ToolInfo (chat shows the raw id): $missing")
    }

    @Test
    fun everyMemoryToolHasADefinition() {
        val missing = CommonTools.getMemoryTools(MemoryStore(AppSettings(MapSettings())))
            .map { it.schema.name }
            .filterNot { it in definitionIds }
        assertTrue(missing.isEmpty(), "Memory tools with no ToolInfo: $missing")
    }

    @Test
    fun notificationToolDefinitionsAreRegistered() {
        val missing = NotificationTools.notificationToolDefinitions
            .map { it.id }
            .filterNot { it in definitionIds }
        assertTrue(missing.isEmpty(), "Notification tools missing from commonToolDefinitions: $missing")
    }

    @Test
    fun definitionIdsAreUnique() {
        val duplicates = definitionIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertEquals(emptySet(), duplicates, "Duplicate tool definition ids")
    }

    @Test
    fun masterToggleControlledToolsAreNotUserToggleable() {
        // These are gated by a single switch in Settings → Agent, so the Tools tab must not
        // offer per-tool switches for them — nothing would read the value back.
        val masterToggled = SchedulingTools.schedulingToolDefinitions +
            HeartbeatTools.heartbeatToolDefinitions +
            EmailTools.emailToolDefinitions +
            SmsTools.smsToolDefinitions +
            NotificationTools.notificationToolDefinitions
        val leaked = masterToggled.filter { it.userToggleable }.map { it.id }
        assertTrue(leaked.isEmpty(), "Master-toggle-controlled tools exposed as per-tool switches: $leaked")
    }
}
