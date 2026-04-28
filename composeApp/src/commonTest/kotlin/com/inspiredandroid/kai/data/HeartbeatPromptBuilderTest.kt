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
        heartbeatAdditions: List<ScheduledTask> = emptyList(),
        recentResponses: List<String> = emptyList(),
        pendingTasks: List<ScheduledTask> = emptyList(),
        emailAccounts: List<EmailAccountSummary> = emptyList(),
        pendingEmails: List<HeartbeatPendingEmail> = emptyList(),
        pendingSms: List<HeartbeatPendingSms> = emptyList(),
        pendingNotifications: List<HeartbeatPendingNotification> = emptyList(),
        promotionCandidates: List<HeartbeatPromotionCandidate> = emptyList(),
    ) = buildHeartbeatPrompt(
        customOrDefaultPrompt = customOrDefaultPrompt,
        heartbeatAdditions = heartbeatAdditions,
        recentResponses = recentResponses,
        pendingTasks = pendingTasks,
        emailAccounts = emailAccounts,
        pendingEmails = pendingEmails,
        pendingSms = pendingSms,
        pendingNotifications = pendingNotifications,
        promotionCandidates = promotionCandidates,
    )

    @Test
    fun `default emits only the opening prompt and a trailing newline`() {
        val out = build(customOrDefaultPrompt = "[HEARTBEAT] check yourself")
        assertEquals("[HEARTBEAT] check yourself\n", out)
    }

    @Test
    fun `includes Heartbeat Additions when list non-empty`() {
        val out = build(
            heartbeatAdditions = listOf(
                task(id = "h1", description = "Greeting").copy(
                    prompt = "Say hi warmly.",
                    trigger = TaskTrigger.HEARTBEAT,
                ),
            ),
        )
        assertTrue("## Heartbeat Additions" in out)
        assertTrue("- **Greeting** (id: h1): Say hi warmly." in out)
    }

    @Test
    fun `omits Heartbeat Additions when list is empty`() {
        val out = build()
        assertFalse("## Heartbeat Additions" in out)
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
                EmailAccountSummary(email = "me@example.com", unreadCount = 3, lastSyncEpochMs = 1000L),
                EmailAccountSummary(email = "work@example.com", unreadCount = 0, lastSyncEpochMs = 0L),
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
                EmailAccountSummary(email = "me@example.com", unreadCount = 0, lastSyncEpochMs = 0L),
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
    fun `includes New Emails with subject from account and preview`() {
        val out = build(
            pendingEmails = listOf(
                HeartbeatPendingEmail(
                    accountEmail = "me@example.com",
                    from = "boss@example.com",
                    subject = "Urgent",
                    preview = "Please review the doc.",
                ),
            ),
        )
        assertTrue("## New Emails" in out)
        assertTrue("- **Urgent** — boss@example.com [me@example.com]: Please review the doc." in out)
    }

    @Test
    fun `New Emails renders placeholder for blank subject and omits empty preview`() {
        val out = build(
            pendingEmails = listOf(
                HeartbeatPendingEmail(
                    accountEmail = "me@example.com",
                    from = "sender@example.com",
                    subject = "",
                    preview = "",
                ),
            ),
        )
        assertTrue("- **(no subject)** — sender@example.com [me@example.com]\n" in out)
    }

    @Test
    fun `omits New Emails when empty`() {
        val out = build()
        assertFalse("## New Emails" in out)
    }

    @Test
    fun `includes New SMS with sender and preview`() {
        val out = build(
            pendingSms = listOf(
                HeartbeatPendingSms(
                    id = 42L,
                    from = "+1234567890",
                    preview = "Your verification code is 123456",
                ),
            ),
        )
        assertTrue("## New SMS" in out)
        assertTrue("- **+1234567890** (id: 42): Your verification code is 123456" in out)
    }

    @Test
    fun `New SMS renders placeholder for blank sender and omits empty preview`() {
        val out = build(
            pendingSms = listOf(HeartbeatPendingSms(id = 7L, from = "", preview = "")),
        )
        assertTrue("- **(unknown sender)** (id: 7)\n" in out)
    }

    @Test
    fun `omits New SMS when empty`() {
        val out = build()
        assertFalse("## New SMS" in out)
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
                EmailAccountSummary(email = "me@example.com", unreadCount = 2, lastSyncEpochMs = 0L),
            ),
            pendingEmails = listOf(
                HeartbeatPendingEmail(
                    accountEmail = "me@example.com",
                    from = "sender@example.com",
                    subject = "Hi",
                    preview = "hello",
                ),
            ),
            pendingSms = listOf(
                HeartbeatPendingSms(id = 99L, from = "+15551234", preview = "code 4242"),
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
            "## New Emails",
            "- **Hi** — sender@example.com [me@example.com]: hello",
            "## New SMS",
            "- **+15551234** (id: 99): code 4242",
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
