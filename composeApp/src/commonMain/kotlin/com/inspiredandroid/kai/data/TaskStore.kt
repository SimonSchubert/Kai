package com.inspiredandroid.kai.data

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class TaskStore(private val appSettings: AppSettings) {

    private val json = SharedJson

    private fun loadTasks(): MutableList<ScheduledTask> = try {
        json.decodeFromString<List<ScheduledTask>>(appSettings.getScheduledTasksJson()).toMutableList()
    } catch (e: Exception) {
        mutableListOf()
    }

    private fun saveTasks(tasks: List<ScheduledTask>) {
        appSettings.setScheduledTasksJson(json.encodeToString(tasks))
    }

    fun addTask(
        description: String,
        prompt: String,
        scheduledAtEpochMs: Long,
        cron: String? = null,
    ): ScheduledTask {
        val tasks = loadTasks()
        val now = Clock.System.now()
        val effectiveScheduledAt = if (cron != null && scheduledAtEpochMs == 0L) {
            // Compute the first execution time from the cron expression
            try {
                CronExpression(cron).nextAfter(now)?.toEpochMilliseconds() ?: now.toEpochMilliseconds()
            } catch (_: Exception) {
                now.toEpochMilliseconds()
            }
        } else {
            scheduledAtEpochMs
        }
        val task = ScheduledTask(
            id = Uuid.random().toString(),
            description = description,
            prompt = prompt,
            scheduledAtEpochMs = effectiveScheduledAt,
            createdAtEpochMs = now.toEpochMilliseconds(),
            cron = cron,
        )
        tasks.add(task)
        saveTasks(tasks)
        return task
    }

    fun getTask(id: String): ScheduledTask? = loadTasks().find { it.id == id }

    fun getAllTasks(): List<ScheduledTask> = loadTasks()

    fun updateTask(task: ScheduledTask): ScheduledTask {
        val tasks = loadTasks()
        val index = tasks.indexOfFirst { it.id == task.id }
        if (index >= 0) {
            tasks[index] = task
            saveTasks(tasks)
        }
        return task
    }

    fun removeTask(id: String): Boolean {
        val tasks = loadTasks()
        val removed = tasks.removeAll { it.id == id }
        if (removed) saveTasks(tasks)
        return removed
    }

    fun getDueTasks(): List<ScheduledTask> {
        val now = Clock.System.now().toEpochMilliseconds()
        return loadTasks().filter { it.scheduledAtEpochMs <= now && it.status == TaskStatus.PENDING }
    }
}
