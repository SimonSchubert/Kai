package com.inspiredandroid.kai.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Locks in the contract of [buildHeartbeatPrompt]. Each conditional section has its own
 * focused test; the golden test catches ordering/whitespace drift.
 *
 * If you're adding a new section to the heartbeat prompt, add a focused test here for it.
 */
class HeartbeatPromptBuilderTest {

    private fun task(
        id: String = "task-1",
        description: String = "Do the thing",
        scheduledAtEpochMs: Long = 0L,
        cron: String? = null,
    ) = ScheduledTask(
        id = id,
        description = description,
        prompt = "",
        scheduledAtEpochMs = scheduledAtEpochMs,
        createdAtEpochMs = 0L,
        cron = cron,
    )

    private fun build(
        customOrDefaultPrompt: String = "[TEST HEARTBEAT]",
        recentResponses: List<String> = emptyList(),
        pendingTasks: List<ScheduledTask> = emptyList(),
        emailAccounts: List<HeartbeatEmailStatus> = emptyList(),
        promotionCandidates: List<HeartbeatPromotionCandidate> = emptyList(),
    ) = buildHeartbeatPrompt(
        customOrDefaultPrompt = customOrDefaultPrompt,
        recentResponses = recentResponses,
        pendingTasks = pendingTasks,
        emailAccounts = emailAccounts,
        promotionCandidates = promotionCandidates,
    )

    @Test
    fun `default emits only the opening prompt and a trailing newline`() {
        val out = build(customOrDefaultPrompt = "[HEARTBEAT] check yourself")
        assertEquals("[HEARTBEAT] check yourself\n", out)
    }

    @Test
    fun `includes Previous Heartbeat Results when recentResponses non-empty`() {
        val out = build(recentResponses = listOf("HEARTBEAT_OK", "All fine"))
        assertTrue("## Previous Heartbeat Results" in out)
        assertTrue("1. HEARTBEAT_OK" in out)
        assertTrue("2. All fine" in out)
    }

    @Test
    fun `omits Previous Heartbeat Results when empty`() {
        val out = build()
        assertFalse("## Previous Heartbeat Results" in out)
    }

    @Test
    fun `includes Pending Tasks with cron annotation when task has cron`() {
        val out = build(
            pendingTasks = listOf(
                task(id = "t1", description = "Morning check", cron = "0 9 * * *"),
            ),
        )
        assertTrue("## Pending Tasks" in out)
        assertTrue("- **Morning check** (id: t1" in out)
        assertTrue("[cron: 0 9 * * *]" in out)
    }

    @Test
    fun `includes Pending Tasks without cron annotation for one-shot tasks`() {
        val out = build(
            pendingTasks = listOf(task(id = "t1", description = "One shot")),
        )
        assertTrue("## Pending Tasks" in out)
        assertTrue("- **One shot** (id: t1" in out)
        assertFalse("[cron:" in out)
    }

    @Test
    fun `omits Pending Tasks when empty`() {
        val out = build()
        assertFalse("## Pending Tasks" in out)
    }

    @Test
    fun `includes Email Status line per account with unread count`() {
        val out = build(
            emailAccounts = listOf(
                HeartbeatEmailStatus(email = "me@example.com", unreadCount = 3, lastSyncEpochMs = 1000L),
                HeartbeatEmailStatus(email = "work@example.com", unreadCount = 0, lastSyncEpochMs = 0L),
            ),
        )
        assertTrue("## Email Status" in out)
        assertTrue("- **me@example.com**: 3 unread" in out)
        assertTrue("- **work@example.com**: 0 unread" in out)
        // First account has a positive lastSync → include timestamp suffix
        assertTrue("last sync:" in out)
    }

    @Test
    fun `Email Status omits last sync suffix when lastSync is zero`() {
        val out = build(
            emailAccounts = listOf(
                HeartbeatEmailStatus(email = "me@example.com", unreadCount = 0, lastSyncEpochMs = 0L),
            ),
        )
        assertTrue("- **me@example.com**: 0 unread\n" in out)
        assertFalse("last sync:" in out)
    }

    @Test
    fun `omits Email Status when no accounts`() {
        val out = build()
        assertFalse("## Email Status" in out)
    }

    @Test
    fun `includes Promotion Candidates with hit count and category`() {
        val out = build(
            promotionCandidates = listOf(
                HeartbeatPromotionCandidate(
                    key = "commit_style",
                    hitCount = 7,
                    category = MemoryCategory.LEARNING,
                    content = "gerund verbs",
                ),
            ),
        )
        assertTrue("## Promotion Candidates" in out)
        assertTrue("reinforced 7+ times" in out)
        assertTrue("promote_learning" in out)
        assertTrue("- **commit_style** (hits: 7, category: LEARNING): gerund verbs" in out)
    }

    @Test
    fun `omits Promotion Candidates when empty`() {
        val out = build()
        assertFalse("## Promotion Candidates" in out)
    }

    @Test
    fun `golden full heartbeat prompt with every section`() {
        val out = build(
            customOrDefaultPrompt = "[HEARTBEAT] check",
            recentResponses = listOf("HEARTBEAT_OK"),
            pendingTasks = listOf(task(id = "t1", description = "First")),
            emailAccounts = listOf(
                HeartbeatEmailStatus(email = "me@example.com", unreadCount = 2, lastSyncEpochMs = 0L),
            ),
            promotionCandidates = listOf(
                HeartbeatPromotionCandidate(
                    key = "style",
                    hitCount = 5,
                    category = MemoryCategory.PREFERENCE,
                    content = "concise",
                ),
            ),
        )
        // Section headers in the exact order they must appear.
        val order = listOf(
            "[HEARTBEAT] check",
            "## Previous Heartbeat Results",
            "1. HEARTBEAT_OK",
            "## Pending Tasks",
            "- **First** (id: t1",
            "## Email Status",
            "- **me@example.com**: 2 unread",
            "## Promotion Candidates",
            "reinforced 5+ times",
            "- **style** (hits: 5, category: PREFERENCE): concise",
        )
        var lastIdx = -1
        for (header in order) {
            val idx = out.indexOf(header)
            assertTrue(idx >= 0, "Expected '$header' in output but was not found. Output:\n$out")
            assertTrue(idx > lastIdx, "Expected '$header' to come after previous section. Output:\n$out")
            lastIdx = idx
        }
    }
}
