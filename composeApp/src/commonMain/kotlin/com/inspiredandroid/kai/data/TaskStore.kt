package com.inspiredandroid.kai.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class TaskStore(private val appSettings: AppSettings) {

    private val json = SharedJson
    private val mutex = Mutex()

    private fun loadTasks(): MutableList<ScheduledTask> = try {
        json.decodeFromString<List<ScheduledTask>>(appSettings.getScheduledTasksJson()).toMutableList()
    } catch (e: Exception) {
        println("TaskStore: failed to load tasks: ${e.message}")
        mutableListOf()
    }

    private fun saveTasks(tasks: List<ScheduledTask>) {
        appSettings.setScheduledTasksJson(json.encodeToString(tasks))
    }

    suspend fun addTask(
        description: String,
        prompt: String,
        scheduledAtEpochMs: Long,
        cron: String? = null,
    ): ScheduledTask = mutex.withLock {
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
        task
    }

    fun getTask(id: String): ScheduledTask? = loadTasks().find { it.id == id }

    fun getAllTasks(): List<ScheduledTask> = loadTasks()

    fun getPendingTasks(): List<ScheduledTask> = loadTasks().filter { it.status == TaskStatus.PENDING }

    suspend fun updateTask(task: ScheduledTask): ScheduledTask = mutex.withLock {
        val tasks = loadTasks()
        val index = tasks.indexOfFirst { it.id == task.id }
        if (index >= 0) {
            tasks[index] = task
            saveTasks(tasks)
        }
        task
    }

    suspend fun removeTask(id: String): Boolean = mutex.withLock {
        val tasks = loadTasks()
        val removed = tasks.removeAll { it.id == id }
        if (removed) saveTasks(tasks)
        removed
    }

    fun getDueTasks(): List<ScheduledTask> {
        val now = Clock.System.now().toEpochMilliseconds()
        return loadTasks().filter { it.scheduledAtEpochMs <= now && it.status == TaskStatus.PENDING }
    }
}
