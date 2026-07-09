package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.DesktopSandboxController
import com.inspiredandroid.kai.SandboxController
import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.network.tools.ToolSchema
import kotlin.time.Duration.Companion.seconds
import org.koin.java.KoinJavaComponent.inject

object ProcessManagerTool : Tool {

    private val sandboxController: SandboxController by inject(SandboxController::class.java)

    internal val processManager = ProcessManager()

    override val timeout = 10.seconds

    override val schema = ToolSchema(
        name = "manage_process",
        description = """Manage background shell processes started with execute_shell_command (background=true).
Actions:
- list: Show all running and finished background processes
- log: Get output from a process (params: session_id, offset, limit)
- kill: Terminate a running process (params: session_id)
- remove: Remove a finished process from the list (params: session_id)""",
        parameters = mapOf(
            "action" to ParameterSchema("string", "Action to perform: list, log, kill, or remove", true),
            "session_id" to ParameterSchema("string", "Session ID of the process (required for log, kill, remove)", false),
            "offset" to ParameterSchema("integer", "Line offset for log output (default: 0)", false),
            "limit" to ParameterSchema("integer", "Max lines to return for log (default: 200)", false),
        ),
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val action = args["action"] as? String
            ?: return mapOf("success" to false, "error" to "Action is required")

        return when (action) {
            "list" -> processManager.list()

            "log" -> {
                val sessionId = args["session_id"] as? String
                    ?: return mapOf("success" to false, "error" to "session_id is required for log")
                val offset = (args["offset"] as? Number)?.toInt() ?: 0
                val limit = (args["limit"] as? Number)?.toInt() ?: 200
                processManager.log(sessionId, offset, limit)
            }

            "kill" -> {
                val sessionId = args["session_id"] as? String
                    ?: return mapOf("success" to false, "error" to "session_id is required for kill")
                val controller = sandboxController as? DesktopSandboxController
                    ?: return mapOf("success" to false, "error" to "Sandbox controller unavailable")
                processManager.kill(controller::closeSession, sessionId)
            }

            "remove" -> {
                val sessionId = args["session_id"] as? String
                    ?: return mapOf("success" to false, "error" to "session_id is required for remove")
                val controller = sandboxController as? DesktopSandboxController
                    ?: return mapOf("success" to false, "error" to "Sandbox controller unavailable")
                processManager.remove(controller::closeSession, sessionId)
            }

            else -> mapOf("success" to false, "error" to "Unknown action: $action. Use: list, log, kill, remove")
        }
    }

    val toolInfo = ToolInfo(
        id = "manage_process",
        name = "Manage Process",
        description = "Manage background shell processes",
        nameRes = null,
        descriptionRes = null,
        isEnabled = false,
    )
}
