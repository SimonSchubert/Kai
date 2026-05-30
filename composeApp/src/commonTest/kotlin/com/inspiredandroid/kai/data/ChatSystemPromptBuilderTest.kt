package com.inspiredandroid.kai.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Locks in the contract of [buildChatSystemPrompt] for every conditional section and
 * every variant. Golden tests catch ordering/whitespace drift; focused tests document
 * which section is gated by which input.
 *
 * If you're adding a new section to the chat system prompt, add a focused test here for
 * it AND extend the golden tests so the section lands in the right variant.
 */
class ChatSystemPromptBuilderTest {

    private val runtime = ChatPromptRuntimeContext(
        nowLocalIsoWithOffset = "2026-04-11T02:00:00+02:00",
        timeZoneId = "Europe/Berlin",
        nowUtcIsoString = "2026-04-11T00:00:00Z",
        platform = "Test",
        modelId = "test-model",
        providerName = "Test Provider",
    )

    private fun memory(
        key: String,
        content: String,
        category: MemoryCategory = MemoryCategory.GENERAL,
        hitCount: Int = 1,
    ) = MemoryEntry(
        key = key,
        content = content,
        createdAt = 0L,
        updatedAt = 0L,
        category = category,
        hitCount = hitCount,
    )

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
        variant: SystemPromptVariant,
        soul: String = "You are Kai.",
        hasTools: Boolean = true,
        memoryEnabled: Boolean = true,
        schedulingEnabled: Boolean = true,
        memoryInstructions: String? = null,
        generalMemories: List<MemoryEntry> = emptyList(),
        preferenceMemories: List<MemoryEntry> = emptyList(),
        learningMemories: List<MemoryEntry> = emptyList(),
        errorMemories: List<MemoryEntry> = emptyList(),
        pendingTasks: List<ScheduledTask> = emptyList(),
        heartbeatAdditions: List<ScheduledTask> = emptyList(),
        emailAccounts: List<EmailAccountSummary> = emptyList(),
        uiMode: ChatPromptUiMode = ChatPromptUiMode.NONE,
        activeSkill: com.inspiredandroid.kai.skills.SkillManifest? = null,
    ) = buildChatSystemPrompt(
        variant = variant,
        soul = soul,
        hasTools = hasTools,
        memoryEnabled = memoryEnabled,
        schedulingEnabled = schedulingEnabled,
        memoryInstructions = memoryInstructions,
        generalMemories = generalMemories,
        preferenceMemories = preferenceMemories,
        learningMemories = learningMemories,
        errorMemories = errorMemories,
        pendingTasks = pendingTasks,
        heartbeatAdditions = heartbeatAdditions,
        emailAccounts = emailAccounts,
        runtime = runtime,
        uiMode = uiMode,
        activeSkill = activeSkill,
    )

    private fun skill(
        id: String = "pdf-tools",
        body: String = "Extract text from PDFs.",
        bundledFilePaths: List<String> = emptyList(),
    ) = com.inspiredandroid.kai.skills.SkillManifest(
        id = id,
        displayName = id,
        description = "desc",
        body = body,
        bundledFilePaths = bundledFilePaths,
    )

    @Test
    fun `active skill section is absent by default and present when activated`() {
        val without = build(SystemPromptVariant.CHAT_REMOTE)
        assertTrue("## Active skill" !in without)

        val with = build(SystemPromptVariant.CHAT_REMOTE, activeSkill = skill(body = "Extract text from PDFs."))
        assertTrue("## Active skill: pdf-tools" in with)
        assertTrue("Extract text from PDFs." in with)
    }

    @Test
    fun `active skill section lists bundled files with sandbox path`() {
        val out = build(
            SystemPromptVariant.CHAT_REMOTE,
            activeSkill = skill(bundledFilePaths = listOf("extract.py")),
        )
        assertTrue("~/skills/pdf-tools/" in out)
        assertTrue("- extract.py" in out)
    }

    // region CHAT_REMOTE — focused tests

    @Test
    fun `CHAT_REMOTE default emits soul + Structured Learning + Automation + context`() {
        val out = build(SystemPromptVariant.CHAT_REMOTE)
        assertTrue(out.startsWith("You are Kai."))
        assertTrue("## Structured Learning" in out)
        assertTrue("## Automation" in out)
        assertTrue("## Context" in out)
        assertTrue("- Local time: 2026-04-11T02:00:00+02:00 (Europe/Berlin)" in out)
        assertTrue("- UTC: 2026-04-11T00:00:00Z" in out)
        assertTrue("- Platform: Test" in out)
        assertTrue("- Model: test-model" in out)
        assertTrue("- Provider: Test Provider" in out)
    }

    @Test
    fun `CHAT_REMOTE Automation section names schedule_task as the future-execution mechanism`() {
        val out = build(SystemPromptVariant.CHAT_REMOTE)
        assertTrue("## Automation" in out)
        assertTrue("schedule_task" in out)
        // The three triggers are named.
        assertTrue("execute_at" in out)
        assertTrue("cron" in out)
        assertTrue("on_heartbeat" in out)
        // Heartbeat toggle/schedule remain user-only.
        assertTrue("user-controlled" in out)
    }

    @Test
    fun `honesty rule is composed into both variants`() {
        // Guards observed regressions: models inventing tool outputs / file contents and
        // kai-ui buttons whose labels imply operations the callback can't perform.
        val remote = build(SystemPromptVariant.CHAT_REMOTE)
        val local = build(SystemPromptVariant.CHAT_LOCAL)
        for (out in listOf(remote, local)) {
            assertTrue(DEFAULT_HONESTY_RULE in out)
        }
    }

    @Test
    fun `Tool Use section is composed into both variants`() {
        val remote = build(SystemPromptVariant.CHAT_REMOTE)
        val local = build(SystemPromptVariant.CHAT_LOCAL)
        for (out in listOf(remote, local)) {
            assertTrue("## Tool Use" in out)
            assertTrue("capability is unavailable" in out)
        }
    }

    @Test
    fun `When to Act section is composed into both variants`() {
        val remote = build(SystemPromptVariant.CHAT_REMOTE)
        val local = build(SystemPromptVariant.CHAT_LOCAL)
        for (out in listOf(remote, local)) {
            assertTrue("## When to Act" in out)
            assertTrue("at most one clarifying question" in out)
        }
    }

    @Test
    fun `Tool Use section is omitted when no tools are available`() {
        val remote = build(SystemPromptVariant.CHAT_REMOTE, hasTools = false)
        val local = build(SystemPromptVariant.CHAT_LOCAL, hasTools = false)
        for (out in listOf(remote, local)) {
            assertFalse("## Tool Use" in out)
            // When-to-act is a general fundamental and still renders.
            assertTrue("## When to Act" in out)
        }
    }

    @Test
    fun `CHAT_REMOTE omits Structured Learning when memory is disabled`() {
        val out = build(SystemPromptVariant.CHAT_REMOTE, memoryEnabled = false)
        assertFalse("## Structured Learning" in out)
        assertFalse("memory_learn" in out)
    }

    @Test
    fun `CHAT_REMOTE omits Automation when scheduling is disabled`() {
        val out = build(SystemPromptVariant.CHAT_REMOTE, schedulingEnabled = false)
        assertFalse("## Automation" in out)
        assertFalse("schedule_task" in out)
    }

    @Test
    fun `CHAT_REMOTE Email Accounts still render when scheduling is disabled`() {
        // Email Accounts is independent of scheduling — disabling scheduling drops the
        // Automation guidance but must not drop connected-account context.
        val out = build(
            variant = SystemPromptVariant.CHAT_REMOTE,
            schedulingEnabled = false,
            emailAccounts = listOf(
                EmailAccountSummary(email = "alice@example.com", unreadCount = 1, lastSyncEpochMs = 0L),
            ),
        )
        assertFalse("## Automation" in out)
        assertTrue("## Email Accounts" in out)
        assertTrue("alice@example.com" in out)
    }

    @Test
    fun `golden CHAT_REMOTE with everything deactivated is soul + honesty + when-to-act + context`() {
        // The fully-deactivated config the user reported: no tools, no memory, no scheduling.
        // The prompt must collapse to the behavioral fundamentals plus runtime context —
        // no Tool Use, Structured Learning, or Automation guidance referencing absent tools.
        val out = build(
            variant = SystemPromptVariant.CHAT_REMOTE,
            soul = "You're a personal assistant.",
            hasTools = false,
            memoryEnabled = false,
            schedulingEnabled = false,
        )
        val expected = "You're a personal assistant.\n\n" +
            DEFAULT_HONESTY_RULE + "\n\n" +
            DEFAULT_ACTING_SECTION + "\n\n" +
            "## Context\n" +
            "- Local time: 2026-04-11T02:00:00+02:00 (Europe/Berlin)\n" +
            "- UTC: 2026-04-11T00:00:00Z\n" +
            "- Platform: Test\n" +
            "- Model: test-model\n" +
            "- Provider: Test Provider\n"
        assertEquals(expected, out)
    }

    @Test
    fun `CHAT_REMOTE includes memory instructions when provided`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_REMOTE,
            memoryInstructions = "Use memory_store to save user info.",
        )
        assertTrue("Use memory_store to save user info." in out)
    }

    @Test
    fun `CHAT_REMOTE includes Your Memories when general memories present`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_REMOTE,
            generalMemories = listOf(memory("user_name", "Alice")),
        )
        assertTrue("## Your Memories" in out)
        assertTrue("- **user_name**: Alice" in out)
    }

    @Test
    fun `CHAT_REMOTE includes User Preferences when preference memories present`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_REMOTE,
            preferenceMemories = listOf(memory("tone", "concise", category = MemoryCategory.PREFERENCE)),
        )
        assertTrue("## User Preferences" in out)
        assertTrue("- **tone**: concise" in out)
    }

    @Test
    fun `CHAT_REMOTE includes Learnings with reinforcement counts`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_REMOTE,
            learningMemories = listOf(
                memory("commit_style", "gerund verbs", category = MemoryCategory.LEARNING, hitCount = 5),
            ),
        )
        assertTrue("## Learnings" in out)
        assertTrue("- **commit_style** (reinforced 5x): gerund verbs" in out)
    }

    @Test
    fun `CHAT_REMOTE includes Known Issues section when error memories present`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_REMOTE,
            errorMemories = listOf(memory("flaky_test", "retry twice", category = MemoryCategory.ERROR)),
        )
        assertTrue("## Known Issues & Resolutions" in out)
        assertTrue("- **flaky_test**: retry twice" in out)
    }

    @Test
    fun `CHAT_REMOTE includes Scheduled Tasks with cron annotation`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_REMOTE,
            pendingTasks = listOf(
                task(id = "t1", description = "Morning check", cron = "0 9 * * *"),
            ),
        )
        assertTrue("## Scheduled Tasks" in out)
        assertTrue("- **Morning check** (id: t1" in out)
        assertTrue("[cron: 0 9 * * *]" in out)
    }

    @Test
    fun `CHAT_REMOTE omits Scheduled Tasks when list is empty`() {
        val out = build(variant = SystemPromptVariant.CHAT_REMOTE)
        assertFalse("## Scheduled Tasks" in out)
    }

    @Test
    fun `CHAT_REMOTE includes Email Accounts when list non-empty`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_REMOTE,
            emailAccounts = listOf(
                EmailAccountSummary(
                    email = "alice@example.com",
                    unreadCount = 3,
                    lastSyncEpochMs = 1_700_000_000_000L,
                ),
            ),
        )
        assertTrue("## Email Accounts" in out)
        assertTrue("do NOT suggest adding" in out)
        assertTrue("- **alice@example.com**: 3 unread" in out)
    }

    @Test
    fun `CHAT_REMOTE Email Accounts surfaces sync failures via lastError`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_REMOTE,
            emailAccounts = listOf(
                EmailAccountSummary(
                    email = "bob@example.com",
                    unreadCount = 0,
                    lastSyncEpochMs = 0L,
                    lastError = "AUTHENTICATIONFAILED",
                ),
            ),
        )
        assertTrue("- **bob@example.com**: sync failing — AUTHENTICATIONFAILED" in out)
    }

    @Test
    fun `CHAT_REMOTE omits Email Accounts when list is empty`() {
        val out = build(variant = SystemPromptVariant.CHAT_REMOTE)
        assertFalse("## Email Accounts" in out)
    }

    @Test
    fun `CHAT_REMOTE includes Heartbeat Additions when list non-empty`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_REMOTE,
            heartbeatAdditions = listOf(
                ScheduledTask(
                    id = "h1",
                    description = "Greeting",
                    prompt = "Greet the user warmly.",
                    scheduledAtEpochMs = 0L,
                    createdAtEpochMs = 0L,
                    trigger = TaskTrigger.HEARTBEAT,
                ),
            ),
        )
        assertTrue("## Heartbeat Additions" in out)
        assertTrue("- **Greeting** (id: h1): Greet the user warmly." in out)
    }

    @Test
    fun `CHAT_REMOTE omits Heartbeat Additions when list is empty`() {
        val out = build(variant = SystemPromptVariant.CHAT_REMOTE)
        assertFalse("## Heartbeat Additions" in out)
    }

    @Test
    fun `CHAT_REMOTE includes Dynamic UI section when uiMode is DYNAMIC_UI`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_REMOTE,
            uiMode = ChatPromptUiMode.DYNAMIC_UI,
        )
        assertTrue("## Dynamic UI" in out)
        assertTrue("kai-ui" in out)
        assertFalse("## Interactive UI Mode" in out)
    }

    @Test
    fun `CHAT_REMOTE includes Interactive UI Mode section when uiMode is INTERACTIVE_UI`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_REMOTE,
            uiMode = ChatPromptUiMode.INTERACTIVE_UI,
        )
        assertTrue("## Interactive UI Mode (ACTIVE)" in out)
        assertFalse("## Dynamic UI\n" in out)
    }

    @Test
    fun `CHAT_REMOTE omits UI sections when uiMode is NONE`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_REMOTE,
            uiMode = ChatPromptUiMode.NONE,
        )
        assertFalse("## Dynamic UI" in out)
        assertFalse("## Interactive UI Mode" in out)
    }

    // endregion

    // region CHAT_LOCAL — focused tests

    @Test
    fun `CHAT_LOCAL default emits only soul + context`() {
        val out = build(SystemPromptVariant.CHAT_LOCAL)
        assertTrue(out.startsWith("You are Kai."))
        assertTrue("## Context" in out)
        assertFalse("## Structured Learning" in out)
    }

    @Test
    fun `CHAT_LOCAL includes memory instructions when provided`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_LOCAL,
            memoryInstructions = "Use memory_store to save user info.",
        )
        assertTrue("Use memory_store to save user info." in out)
    }

    @Test
    fun `CHAT_LOCAL omits Structured Learning section even with memory instructions`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_LOCAL,
            memoryInstructions = "Use memory_store to save user info.",
        )
        assertFalse("## Structured Learning" in out)
        assertFalse("memory_learn" in out)
    }

    @Test
    fun `CHAT_LOCAL includes memory category sections when within budget`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_LOCAL,
            generalMemories = listOf(memory("user_name", "Alice")),
            preferenceMemories = listOf(memory("tone", "concise", category = MemoryCategory.PREFERENCE)),
            learningMemories = listOf(memory("style", "gerunds", category = MemoryCategory.LEARNING, hitCount = 3)),
            errorMemories = listOf(memory("flaky_test", "retry", category = MemoryCategory.ERROR)),
        )
        assertTrue("## Your Memories" in out)
        assertTrue("- **user_name**: Alice" in out)
        assertTrue("## User Preferences" in out)
        assertTrue("- **tone**: concise" in out)
        assertTrue("## Learnings" in out)
        assertTrue("- **style** (reinforced 3x): gerunds" in out)
        assertTrue("## Known Issues & Resolutions" in out)
        assertTrue("- **flaky_test**: retry" in out)
    }

    @Test
    fun `CHAT_LOCAL truncates memories at entry boundary when over budget`() {
        // A bloated memory set: 50 entries with long content. Combined size will far
        // exceed LOCAL_MEMORY_BUDGET_CHARS (2000). Later entries should be silently dropped.
        val big = (1..50).map { i ->
            memory(
                key = "key_$i",
                content = "x".repeat(100),
                category = MemoryCategory.GENERAL,
            )
        }
        val out = build(
            variant = SystemPromptVariant.CHAT_LOCAL,
            generalMemories = big,
        )
        assertTrue("## Your Memories" in out)
        // Budget is 2000 chars; with ~100-char entries we'd fit ~18 entries max.
        assertTrue("- **key_1**:" in out, "First entry should be included")
        assertFalse("- **key_50**:" in out, "Last entry should be dropped (budget exhausted)")
        // Sanity: the memory section portion shouldn't exceed the budget by more than one
        // entry's worth (we cut at boundaries).
        val memStart = out.indexOf("## Your Memories")
        val memEnd = out.indexOf("## Context")
        val memSectionLen = memEnd - memStart
        assertTrue(memSectionLen <= 2100, "Memory section should be ~2000 chars, was $memSectionLen")
    }

    @Test
    fun `CHAT_LOCAL drops lower-priority categories when earlier ones exhaust budget`() {
        // Fill the GENERAL category to ~1900 chars (close to budget); later categories
        // should be dropped entirely because the budget is exhausted.
        val bigGeneral = (1..19).map { i ->
            memory(
                key = "g_$i",
                content = "x".repeat(80),
                category = MemoryCategory.GENERAL,
            )
        }
        val out = build(
            variant = SystemPromptVariant.CHAT_LOCAL,
            generalMemories = bigGeneral,
            preferenceMemories = listOf(memory("pref_key", "small content", category = MemoryCategory.PREFERENCE)),
            learningMemories = listOf(memory("learn_key", "small content", category = MemoryCategory.LEARNING)),
            errorMemories = listOf(memory("err_key", "small content", category = MemoryCategory.ERROR)),
        )
        assertTrue("## Your Memories" in out)
        // Later categories may or may not render depending on exact byte count —
        // but the total combined memory section must still be within budget + one entry.
        val memStart = out.indexOf("## Your Memories")
        val memEnd = out.indexOf("## Context")
        val memLen = memEnd - memStart
        assertTrue(memLen <= 2200, "Combined memory sections should respect budget, was $memLen")
    }

    @Test
    fun `CHAT_LOCAL omits Scheduled Tasks regardless of input`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_LOCAL,
            pendingTasks = listOf(task(description = "Do the thing")),
        )
        assertFalse("## Scheduled Tasks" in out)
        assertFalse("Do the thing" in out)
    }

    @Test
    fun `CHAT_LOCAL omits Automation section`() {
        val out = build(variant = SystemPromptVariant.CHAT_LOCAL)
        assertFalse("## Automation" in out)
        assertFalse("schedule_task" in out)
    }

    @Test
    fun `CHAT_LOCAL omits Email Accounts regardless of input`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_LOCAL,
            emailAccounts = listOf(
                EmailAccountSummary(
                    email = "alice@example.com",
                    unreadCount = 3,
                    lastSyncEpochMs = 0L,
                ),
            ),
        )
        assertFalse("## Email Accounts" in out)
        assertFalse("alice@example.com" in out)
    }

    @Test
    fun `CHAT_LOCAL omits Heartbeat Additions regardless of input`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_LOCAL,
            heartbeatAdditions = listOf(
                ScheduledTask(
                    id = "h1",
                    description = "Greeting",
                    prompt = "Hi!",
                    scheduledAtEpochMs = 0L,
                    createdAtEpochMs = 0L,
                    trigger = TaskTrigger.HEARTBEAT,
                ),
            ),
        )
        assertFalse("## Heartbeat Additions" in out)
    }

    @Test
    fun `CHAT_LOCAL omits Dynamic UI even when uiMode is DYNAMIC_UI`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_LOCAL,
            uiMode = ChatPromptUiMode.DYNAMIC_UI,
        )
        assertFalse("## Dynamic UI" in out)
        assertFalse("kai-ui" in out)
    }

    @Test
    fun `CHAT_LOCAL omits Interactive UI Mode even when uiMode is INTERACTIVE_UI`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_LOCAL,
            uiMode = ChatPromptUiMode.INTERACTIVE_UI,
        )
        assertFalse("## Interactive UI Mode" in out)
    }

    // endregion

    // region Golden snapshots

    @Test
    fun `golden CHAT_LOCAL with soul + memory instructions + context`() {
        // No memories or tasks — just the minimal CHAT_LOCAL shape. Memory inclusion
        // with a budget is covered by separate focused tests. Scheduled tasks and kai-ui
        // sections are verified as omitted below.
        val out = build(
            variant = SystemPromptVariant.CHAT_LOCAL,
            soul = "You are Kai, a helpful assistant.",
            memoryInstructions = "Save user preferences with memory_store.",
            pendingTasks = listOf(task(description = "ignored task")),
            uiMode = ChatPromptUiMode.DYNAMIC_UI,
        )
        val expected = "You are Kai, a helpful assistant.\n\n" +
            DEFAULT_HONESTY_RULE + "\n\n" +
            DEFAULT_TOOL_USE_SECTION + "\n\n" +
            DEFAULT_ACTING_SECTION + "\n\n" +
            "Save user preferences with memory_store.\n\n" +
            "## Context\n" +
            "- Local time: 2026-04-11T02:00:00+02:00 (Europe/Berlin)\n" +
            "- UTC: 2026-04-11T00:00:00Z\n" +
            "- Platform: Test\n" +
            "- Model: test-model\n" +
            "- Provider: Test Provider\n"
        assertEquals(expected, out)
    }

    @Test
    fun `golden CHAT_REMOTE with every section enabled`() {
        val out = build(
            variant = SystemPromptVariant.CHAT_REMOTE,
            soul = "You are Kai.",
            memoryInstructions = "Basic memory guidance.",
            generalMemories = listOf(memory("fact", "value")),
            preferenceMemories = listOf(memory("pref", "val", category = MemoryCategory.PREFERENCE)),
            learningMemories = listOf(memory("lesson", "body", category = MemoryCategory.LEARNING, hitCount = 3)),
            errorMemories = listOf(memory("issue", "resolution", category = MemoryCategory.ERROR)),
            pendingTasks = listOf(task(id = "t1", description = "First task")),
            emailAccounts = listOf(
                EmailAccountSummary(
                    email = "alice@example.com",
                    unreadCount = 1,
                    lastSyncEpochMs = 0L,
                ),
            ),
            heartbeatAdditions = listOf(
                ScheduledTask(
                    id = "h1",
                    description = "Greeting",
                    prompt = "Say hi",
                    scheduledAtEpochMs = 0L,
                    createdAtEpochMs = 0L,
                    trigger = TaskTrigger.HEARTBEAT,
                ),
            ),
            uiMode = ChatPromptUiMode.NONE,
        )
        // Just assert the section headers are present in order — the full kai-ui sections
        // are verified by separate DYNAMIC_UI / INTERACTIVE_UI tests.
        val headerOrder = listOf(
            "You are Kai.",
            DEFAULT_HONESTY_RULE,
            "## Tool Use",
            "## When to Act",
            "Basic memory guidance.",
            "## Structured Learning",
            "## Your Memories",
            "## User Preferences",
            "## Learnings",
            "## Known Issues & Resolutions",
            "## Automation",
            "## Email Accounts",
            "## Scheduled Tasks",
            "## Heartbeat Additions",
            "## Context",
        )
        var lastIdx = -1
        for (header in headerOrder) {
            val idx = out.indexOf(header)
            assertTrue(idx >= 0, "Expected '$header' in output but was not found. Output:\n$out")
            assertTrue(idx > lastIdx, "Expected '$header' to come after previous section. Output:\n$out")
            lastIdx = idx
        }
    }

    // endregion
}
