package com.inspiredandroid.kai.data

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Locks in the legacy → `TaskTrigger` migration path in [TaskStore.loadTasks]. Users on
 * older versions of the app have `ScheduledTask` JSON in settings without a `trigger`
 * field; decoding lands them on the default (`TIME`). Rows that carry a `cron` need to
 * be upgraded to `CRON` on first load so the scheduler routes them correctly.
 */
class TaskStoreMigrationTest {

    private fun freshSettings(): AppSettings = AppSettings(MapSettings())

    @Test
    fun `legacy cron task without trigger field upgrades to CRON`() = runTest {
        val settings = freshSettings()
        // Simulate pre-migration JSON: no `trigger` field, `cron` is set.
        settings.setScheduledTasksJson(
            """[{"id":"t1","description":"Morning","prompt":"Do thing","scheduledAtEpochMs":0,"createdAtEpochMs":0,"cron":"0 9 * * *","status":"PENDING","lastResult":null,"consecutiveFailures":0}]""",
        )
        val store = TaskStore(settings)

        val tasks = store.getAllTasks()
        assertEquals(1, tasks.size)
        assertEquals(TaskTrigger.CRON, tasks[0].trigger)
        assertEquals("0 9 * * *", tasks[0].cron)
    }

    @Test
    fun `legacy cron migration persists so later loads don't rewrite`() = runTest {
        val settings = freshSettings()
        settings.setScheduledTasksJson(
            """[{"id":"t1","description":"Morning","prompt":"Do thing","scheduledAtEpochMs":0,"createdAtEpochMs":0,"cron":"0 9 * * *","status":"PENDING","lastResult":null,"consecutiveFailures":0}]""",
        )
        val store = TaskStore(settings)

        // First load triggers the upgrade.
        store.getAllTasks()
        // Stored JSON should now contain the upgraded trigger field.
        val storedJson = settings.getScheduledTasksJson()
        assertEquals(true, storedJson.contains("\"trigger\":\"CRON\""))
    }

    @Test
    fun `legacy one-shot task without trigger field stays TIME`() = runTest {
        val settings = freshSettings()
        settings.setScheduledTasksJson(
            """[{"id":"t1","description":"One shot","prompt":"Do thing","scheduledAtEpochMs":1700000000000,"createdAtEpochMs":0,"cron":null,"status":"PENDING","lastResult":null,"consecutiveFailures":0}]""",
        )
        val store = TaskStore(settings)

        val tasks = store.getAllTasks()
        assertEquals(1, tasks.size)
        assertEquals(TaskTrigger.TIME, tasks[0].trigger)
    }

    @Test
    fun `addTask with on_heartbeat trigger excludes from getDueTasks and getPendingTasks`() = runTest {
        val settings = freshSettings()
        val store = TaskStore(settings)

        store.addTask(
            description = "Greeting",
            prompt = "Say hi",
            scheduledAtEpochMs = 0L,
            trigger = TaskTrigger.HEARTBEAT,
        )
        store.addTask(
            description = "One shot",
            prompt = "Do it",
            scheduledAtEpochMs = 1L,
            trigger = TaskTrigger.TIME,
        )

        // Due tasks: only the TIME task (due because scheduledAt <= now).
        assertEquals(listOf("One shot"), store.getDueTasks().map { it.description })
        // Pending tasks (chat-visible scheduled): only the TIME task.
        assertEquals(listOf("One shot"), store.getPendingTasks().map { it.description })
        // Heartbeat additions: only the HEARTBEAT task.
        assertEquals(listOf("Greeting"), store.getPendingHeartbeatAdditions().map { it.description })
    }
}
