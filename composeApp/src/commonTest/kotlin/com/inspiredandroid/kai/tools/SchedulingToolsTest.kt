@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.data.TaskStore
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Contract tests for `schedule_task`'s `execute_at` parsing and the past-instant guard.
 * The tool rejects past-dated values so a UTC/local sign flip fails loudly on the same
 * turn instead of either firing instantly or (after backoff) silently sitting PENDING.
 */
class SchedulingToolsTest {

    private fun freshStore(): TaskStore = TaskStore(AppSettings(MapSettings()))

    private suspend fun schedule(
        executeAt: String,
        store: TaskStore = freshStore(),
    ): Map<*, *> = SchedulingTools.scheduleTaskTool(store).execute(
        mapOf(
            "description" to "t",
            "prompt" to "p",
            "execute_at" to executeAt,
        ),
    ) as Map<*, *>

    @Test
    fun `offset-qualified future instant succeeds`() = runTest {
        val nowPlus1h = Clock.System.now().plus(1.hours)
        val iso = nowPlus1h.toString() // e.g. "2026-04-22T21:29:39.123Z"
        val result = schedule(iso)
        assertEquals(true, result["success"])
    }

    @Test
    fun `naive future local datetime succeeds`() = runTest {
        // A naive string 10 minutes in the future in local time — mirrors what the
        // model produces after `get_local_time` + relative arithmetic.
        val zone = TimeZone.currentSystemDefault()
        val naive = Clock.System.now()
            .plus(10.minutes)
            .toLocalDateTime(zone)
            .toString() // no offset suffix
        val result = schedule(naive)
        assertEquals(true, result["success"], "expected success but got: $result")
    }

    @Test
    fun `past naive datetime is rejected with guidance message`() = runTest {
        // 2 hours in the past, in local time — the typical sign-flip symptom.
        val zone = TimeZone.currentSystemDefault()
        val past = Clock.System.now()
            .minus(2.hours)
            .toLocalDateTime(zone)
            .toString()
        val result = schedule(past)
        assertEquals(false, result["success"])
        val error = result["error"]?.toString().orEmpty()
        assertTrue("in the past" in error, "error should mention past: $error")
        assertTrue("Local time" in error, "error should point at Local time: $error")
    }

    @Test
    fun `past offset-qualified instant is rejected`() = runTest {
        val past = Clock.System.now().minus(2.hours).toString()
        val result = schedule(past)
        assertEquals(false, result["success"])
    }

    @Test
    fun `past-instant rejection does not persist the task`() = runTest {
        val store = freshStore()
        val past = Clock.System.now().minus(1.hours).toString()
        schedule(past, store)
        assertEquals(0, store.getAllTasks().size)
    }

    @Test
    fun `malformed execute_at returns Invalid execute_at format error`() = runTest {
        val result = schedule("not-a-datetime")
        assertEquals(false, result["success"])
        assertTrue("Invalid execute_at format" in result["error"].toString())
    }

    @Test
    fun `tool description mentions local timezone handling`() {
        val schema = SchedulingTools.scheduleTaskTool(freshStore()).schema
        val executeAtDesc = schema.parameters["execute_at"]?.description.orEmpty()
        assertTrue("local timezone" in executeAtDesc, "execute_at should explain local-vs-offset: $executeAtDesc")
        assertTrue("Local time" in schema.description, "top-level description should reference Local time: ${schema.description}")
    }
}
