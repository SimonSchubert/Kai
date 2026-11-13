package com.kainext.duo.tools

import com.kainext.duo.data.AppSettings
import com.kainext.duo.data.EmailStore
import com.kainext.duo.data.MemoryStore
import com.kainext.duo.data.TaskStore
import com.kainext.duo.mcp.McpServerManager
import com.kainext.duo.network.tools.Tool

/**
 * The tool set every platform builds, in the order the agent receives it.
 *
 * Each platform's `getAvailableTools()` used to repeat this gating block verbatim, which is how
 * they drifted — Android in particular had re-implemented the common-tool checks inline instead
 * of calling [CommonTools.getCommonTools]. Platform-specific tools are contributed through
 * [platformExtras]; MCP tools stay last everywhere.
 */
fun buildAgentToolSet(
    appSettings: AppSettings,
    memoryStore: MemoryStore,
    taskStore: TaskStore,
    mcpServerManager: McpServerManager,
    // Web has no email support and injects no store.
    emailStore: EmailStore? = null,
    platformExtras: MutableList<Tool>.() -> Unit = {},
): List<Tool> = buildList {
    addAll(CommonTools.getCommonTools(appSettings))

    if (appSettings.isMemoryEnabled()) {
        addAll(CommonTools.getMemoryTools(memoryStore))
    }

    // Heartbeat tools ride the scheduling switch — they are only reachable from a scheduled run.
    if (appSettings.isSchedulingEnabled()) {
        addAll(SchedulingTools.getSchedulingTools(taskStore))
        addAll(HeartbeatTools.getHeartbeatTools(memoryStore, appSettings))
    }

    if (emailStore != null && appSettings.isEmailEnabled()) {
        addAll(EmailTools.getEmailTools(emailStore))
    }

    platformExtras()

    addAll(mcpServerManager.getEnabledMcpTools(reservedNames = mapTo(HashSet()) { it.schema.name }))
}
