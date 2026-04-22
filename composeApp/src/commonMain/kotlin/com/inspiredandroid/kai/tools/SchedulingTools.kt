package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.data.TaskStatus
import com.inspiredandroid.kai.data.TaskStore
import com.inspiredandroid.kai.data.TaskTrigger
import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.network.tools.ToolSchema
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.tool_cancel_task_description
import kai.composeapp.generated.resources.tool_cancel_task_name
import kai.composeapp.generated.resources.tool_list_tasks_description
import kai.composeapp.generated.resources.tool_list_tasks_name
import kai.composeapp.generated.resources.tool_schedule_task_description
import kai.composeapp.generated.resources.tool_schedule_task_name
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Instant

object SchedulingTools {

    fun scheduleTaskTool(taskStore: TaskStore) = object : Tool {
        override val schema = ToolSchema(
            name = "schedule_task",
            description = "Schedule a prompt to run later, recurring, or on every heartbeat. This is the ONLY way to run something after this turn — reminders, follow-ups, periodic updates, check-ins, standing heartbeat additions (greetings, always-summarise-emails): all go through this tool. Each run starts a fresh conversation, so embed the context the prompt needs. Exactly one trigger must be provided: execute_at (one-off at a datetime), cron (recurring on a schedule), or on_heartbeat=true (appended to every heartbeat self-check).",
            parameters = mapOf(
                "description" to ParameterSchema(type = "string", description = "Human-readable description of the task", required = true),
                "prompt" to ParameterSchema(type = "string", description = "For execute_at/cron: the full prompt sent to the AI when it fires. For on_heartbeat: the instruction appended to each heartbeat self-check (e.g. 'Greet the user warmly with a time-appropriate greeting.').", required = true),
                "execute_at" to ParameterSchema(type = "string", description = "ISO 8601 datetime string for a one-off run (e.g. 2025-03-15T09:00:00)", required = false),
                "cron" to ParameterSchema(type = "string", description = "Cron expression for recurring tasks (e.g. '0 9 * * 1' for every Monday at 9am)", required = false),
                "on_heartbeat" to ParameterSchema(type = "boolean", description = "Set to true to run this prompt on every heartbeat self-check. Use for standing additions to heartbeat behaviour.", required = false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val description = args["description"]?.toString()
                ?: return mapOf("success" to false, "error" to "Missing description")
            val prompt = args["prompt"]?.toString()
                ?: return mapOf("success" to false, "error" to "Missing prompt")
            val executeAt = args["execute_at"]?.toString()
            val cron = args["cron"]?.toString()
            val onHeartbeat = args["on_heartbeat"] as? Boolean ?: false

            val triggerCount = listOf(executeAt != null, cron != null, onHeartbeat).count { it }
            if (triggerCount == 0) {
                return mapOf("success" to false, "error" to "Exactly one of execute_at, cron, or on_heartbeat must be provided")
            }
            if (triggerCount > 1) {
                return mapOf("success" to false, "error" to "execute_at, cron, and on_heartbeat are mutually exclusive — pick one")
            }

            val trigger = when {
                onHeartbeat -> TaskTrigger.HEARTBEAT
                cron != null -> TaskTrigger.CRON
                else -> TaskTrigger.TIME
            }

            val scheduledAtEpochMs = if (executeAt != null) {
                try {
                    parseIso8601ToEpochMs(executeAt)
                } catch (e: Exception) {
                    return mapOf("success" to false, "error" to "Invalid execute_at format: ${e.message}")
                }
            } else {
                0L // cron and heartbeat tasks don't use this field at creation time
            }

            val task = taskStore.addTask(
                description = description,
                prompt = prompt,
                scheduledAtEpochMs = scheduledAtEpochMs,
                cron = cron,
                trigger = trigger,
            )

            return mapOf(
                "success" to true,
                "task_id" to task.id,
                "description" to task.description,
                "trigger" to trigger.name,
                "scheduled_at" to (executeAt ?: "n/a"),
                "cron" to (cron ?: "none"),
            )
        }
    }

    fun cancelTaskTool(taskStore: TaskStore) = object : Tool {
        override val schema = ToolSchema(
            name = "cancel_task",
            description = "Cancel a scheduled task by its ID. When the user asks to stop, cancel, or remove any scheduled or recurring task, call this tool with the matching task ID from the Scheduled Tasks list. If unsure which task, call list_tasks first.",
            parameters = mapOf(
                "task_id" to ParameterSchema(type = "string", description = "The ID of the task to cancel", required = true),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val taskId = args["task_id"]?.toString()
                ?: return mapOf("success" to false, "error" to "Missing task_id")

            val removed = taskStore.removeTask(taskId)
            return if (removed) {
                mapOf("success" to true, "task_id" to taskId, "status" to "REMOVED")
            } else {
                mapOf("success" to false, "error" to "Task not found: $taskId")
            }
        }
    }

    fun listTasksTool(taskStore: TaskStore) = object : Tool {
        override val schema = ToolSchema(
            name = "list_tasks",
            description = "List all scheduled tasks with their IDs, descriptions, and status. Call this before cancel_task if you need to find a task ID. Optionally filter by status.",
            parameters = mapOf(
                "status" to ParameterSchema(type = "string", description = "Filter by status: PENDING or COMPLETED", required = false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val statusFilter = args["status"]?.toString()?.uppercase()
            val allTasks = taskStore.getAllTasks()

            val filtered = if (statusFilter != null) {
                val status = try {
                    TaskStatus.valueOf(statusFilter)
                } catch (e: Exception) {
                    return mapOf("success" to false, "error" to "Invalid status: $statusFilter. Use PENDING or COMPLETED")
                }
                allTasks.filter { it.status == status }
            } else {
                allTasks
            }

            return mapOf(
                "success" to true,
                "count" to filtered.size,
                "tasks" to filtered.map { task ->
                    mapOf(
                        "id" to task.id,
                        "description" to task.description,
                        "prompt" to task.prompt,
                        "trigger" to task.trigger.name,
                        "scheduled_at_epoch_ms" to task.scheduledAtEpochMs,
                        "created_at_epoch_ms" to task.createdAtEpochMs,
                        "cron" to (task.cron ?: "none"),
                        "status" to task.status.name,
                        "last_result" to (task.lastResult ?: "none"),
                    )
                },
            )
        }
    }

    val scheduleTaskToolInfo = ToolInfo(
        id = "schedule_task",
        name = "Schedule Task",
        description = "Schedule a task for future execution",
        nameRes = Res.string.tool_schedule_task_name,
        descriptionRes = Res.string.tool_schedule_task_description,
    )

    val cancelTaskToolInfo = ToolInfo(
        id = "cancel_task",
        name = "Cancel Task",
        description = "Cancel a scheduled task",
        nameRes = Res.string.tool_cancel_task_name,
        descriptionRes = Res.string.tool_cancel_task_description,
    )

    val listTasksToolInfo = ToolInfo(
        id = "list_tasks",
        name = "List Tasks",
        description = "List all scheduled tasks",
        nameRes = Res.string.tool_list_tasks_name,
        descriptionRes = Res.string.tool_list_tasks_description,
    )

    val schedulingToolDefinitions = listOf(scheduleTaskToolInfo, cancelTaskToolInfo, listTasksToolInfo)

    fun getSchedulingTools(taskStore: TaskStore): List<Tool> = listOf(
        scheduleTaskTool(taskStore),
        cancelTaskTool(taskStore),
        listTasksTool(taskStore),
    )

    private fun parseIso8601ToEpochMs(isoString: String): Long {
        // Try parsing as Instant first (with timezone offset)
        return try {
            Instant.parse(isoString).toEpochMilliseconds()
        } catch (e: Exception) {
            // Fall back to LocalDateTime (no timezone) and use system default
            val localDateTime = LocalDateTime.parse(isoString)
            localDateTime.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        }
    }
}
