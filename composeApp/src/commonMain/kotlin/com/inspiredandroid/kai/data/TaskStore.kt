package com.inspiredandroid.kai.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Both pending task lists produced by [TaskStore.getPendingTasksPartitioned]. */
data class PendingTaskPartition(
    val scheduled: List<ScheduledTask>,
    val heartbeatAdditions: List<ScheduledTask>,
)

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class TaskStore(private val appSettings: AppSettings) {

    private val json = SharedJson
    private val mutex = Mutex()

    private fun loadTasks(): MutableList<ScheduledTask> = try {
        val decoded = json.decodeFromString<List<ScheduledTask>>(appSettings.getScheduledTasksJson())
        // Migration: tasks persisted before the `trigger` field existed decode with
        // the default (TIME). Upgrade rows that carry a cron expression to CRON so the
        // scheduler can distinguish time/cron from heartbeat additions. Persist the
        // upgrade the first time we see it so every subsequent load is a no-op map.
        var migrated = false
        val upgraded = decoded.map { task ->
            if (task.trigger == TaskTrigger.TIME && task.cron != null) {
                migrated = true
                task.copy(trigger = TaskTrigger.CRON)
            } else {
                task
            }
        }.toMutableList()
        if (migrated) saveTasks(upgraded)
        upgraded
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
        trigger: TaskTrigger = if (cron != null) TaskTrigger.CRON else TaskTrigger.TIME,
    ): ScheduledTask = mutex.withLock {
        val tasks = loadTasks()
        val now = Clock.System.now()
        val effectiveScheduledAt = when (trigger) {
            TaskTrigger.HEARTBEAT -> 0L

            // heartbeat tasks are not time-gated
            TaskTrigger.CRON -> if (scheduledAtEpochMs == 0L) {
                try {
                    CronExpression(cron!!).nextAfter(now)?.toEpochMilliseconds() ?: now.toEpochMilliseconds()
                } catch (_: Exception) {
                    now.toEpochMilliseconds()
                }
            } else {
                scheduledAtEpochMs
            }

            TaskTrigger.TIME -> scheduledAtEpochMs
        }
        val task = ScheduledTask(
            id = Uuid.random().toString(),
            description = description,
            prompt = prompt,
            scheduledAtEpochMs = effectiveScheduledAt,
            createdAtEpochMs = now.toEpochMilliseconds(),
            cron = cron,
            trigger = trigger,
        )
        tasks.add(task)
        saveTasks(tasks)
        task
    }

    fun getAllTasks(): List<ScheduledTask> = loadTasks()

    /**
     * All PENDING non-heartbeat tasks — what the user thinks of as "scheduled". Heartbeat-
     * triggered tasks are surfaced separately via [getPendingHeartbeatAdditions].
     */
    fun getPendingTasks(): List<ScheduledTask> = loadTasks().filter { it.status == TaskStatus.PENDING && it.trigger != TaskTrigger.HEARTBEAT }

    /** Standing additions to every heartbeat self-check. */
    fun getPendingHeartbeatAdditions(): List<ScheduledTask> = loadTasks().filter { it.status == TaskStatus.PENDING && it.trigger == TaskTrigger.HEARTBEAT }

    /**
     * Both pending scheduled tasks and heartbeat additions from a single load. Hot-path
     * callers (chat system prompt, heartbeat prompt) need both lists per invocation;
     * combining avoids re-parsing the tasks JSON twice.
     */
    fun getPendingTasksPartitioned(): PendingTaskPartition {
        val (additions, scheduled) = loadTasks()
            .filter { it.status == TaskStatus.PENDING }
            .partition { it.trigger == TaskTrigger.HEARTBEAT }
        return PendingTaskPartition(scheduled = scheduled, heartbeatAdditions = additions)
    }

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
        return loadTasks().filter {
            it.trigger != TaskTrigger.HEARTBEAT &&
                it.scheduledAtEpochMs <= now &&
                it.status == TaskStatus.PENDING
        }
    }
}
