package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.data.TaskStatus
import com.inspiredandroid.kai.data.TaskStore
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
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

object SchedulingTools {

    fun scheduleTaskTool(taskStore: TaskStore) = object : Tool {
        override val schema = ToolSchema(
            name = "schedule_task",
            description = "Schedule a task for future or recurring execution. Use this for reminders, delayed sends, or recurring jobs. When scheduling a reminder, write the prompt as the actual reminder text the user will see. Include recent context if appropriate. At least one of execute_at or cron must be provided.",
            parameters = mapOf(
                "description" to ParameterSchema(type = "string", description = "Human-readable description of the task", required = true),
                "prompt" to ParameterSchema(type = "string", description = "The prompt to send to the AI when the task fires", required = true),
                "execute_at" to ParameterSchema(type = "string", description = "ISO 8601 datetime string for when to execute (e.g. 2025-03-15T09:00:00)", required = false),
                "cron" to ParameterSchema(type = "string", description = "Cron expression for recurring tasks (e.g. '0 9 * * 1' for every Monday at 9am)", required = false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val description = args["description"]?.toString()
                ?: return mapOf("success" to false, "error" to "Missing description")
            val prompt = args["prompt"]?.toString()
                ?: return mapOf("success" to false, "error" to "Missing prompt")
            val executeAt = args["execute_at"]?.toString()
            val cron = args["cron"]?.toString()

            if (executeAt == null && cron == null) {
                return mapOf("success" to false, "error" to "At least one of execute_at or cron must be provided")
            }

            val scheduledAtEpochMs = if (executeAt != null) {
                try {
                    parseIso8601ToEpochMs(executeAt)
                } catch (e: Exception) {
                    return mapOf("success" to false, "error" to "Invalid execute_at format: ${e.message}")
                }
            } else {
                0L // Cron-only tasks use 0 as placeholder
            }

            val task = taskStore.addTask(
                description = description,
                prompt = prompt,
                scheduledAtEpochMs = scheduledAtEpochMs,
                cron = cron,
            )

            return mapOf(
                "success" to true,
                "task_id" to task.id,
                "description" to task.description,
                "scheduled_at" to (executeAt ?: "recurring"),
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
