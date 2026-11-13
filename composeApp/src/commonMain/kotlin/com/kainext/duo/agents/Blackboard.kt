package com.kainext.duo.agents

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Shared context between plan steps.
 *
 * The point of a blackboard over plain return values: a step often needs what an *earlier* step
 * found, not just its immediate predecessor. Step 3 may need the package name discovered in step
 * 1 even though it only depends on step 2. Writing everything to one place means a worker can ask
 * for "whatever has been learned so far" without the orchestrator threading every result through
 * every call.
 */
interface Blackboard {

    /** Every entry written so far, oldest first. */
    val entries: StateFlow<List<BlackboardEntry>>

    /** Records what a step produced. Safe to call concurrently. */
    suspend fun write(entry: BlackboardEntry)

    /** The most recent entry for [stepId], or null when that step has not written anything. */
    fun latest(stepId: String): BlackboardEntry?

    /**
     * A compact text digest of everything on the board, suitable for a prompt.
     * Bounded in both per-entry and total length — see [InMemoryBlackboard].
     */
    fun snapshot(): String
}

/**
 * The default [Blackboard].
 *
 * Two bounds keep this from becoming a token sink: each entry is clipped to
 * [MAX_ENTRY_CHARS], and the digest is clipped to [MAX_SNAPSHOT_CHARS]. Both are applied on
 * write/read rather than being left to callers, because a single chatty tool result would
 * otherwise dominate every subsequent prompt.
 *
 * [maxEntries] bounds memory: a long run with many parallel steps would otherwise accumulate
 * every tool output for the whole session. Dropping the oldest is correct here because the
 * snapshot is a summary of recent work, not an audit log.
 */
class InMemoryBlackboard(
    private val maxEntries: Int = 50,
) : Blackboard {

    private val mutex = Mutex()
    private val _entries = MutableStateFlow<List<BlackboardEntry>>(emptyList())

    override val entries: StateFlow<List<BlackboardEntry>> = _entries.asStateFlow()

    override suspend fun write(entry: BlackboardEntry) {
        val clipped = entry.copy(content = entry.content.take(MAX_ENTRY_CHARS))
        mutex.withLock {
            _entries.update { current ->
                val next = current + clipped
                if (next.size > maxEntries) next.takeLast(maxEntries) else next
            }
        }
    }

    override fun latest(stepId: String): BlackboardEntry? =
        _entries.value.lastOrNull { it.stepId == stepId }

    override fun snapshot(): String {
        val all = _entries.value
        if (all.isEmpty()) return ""
        return buildString {
            for (entry in all) {
                val marker = if (entry.success) "" else " [FAILED]"
                append("- ${entry.stepId} (${entry.workerKind.name.lowercase()})$marker: ")
                appendLine(entry.content.take(MAX_ENTRY_CHARS).replace('\n', ' ').trim())
                if (length > MAX_SNAPSHOT_CHARS) {
                    appendLine("… (earlier entries omitted)")
                    break
                }
            }
        }.take(MAX_SNAPSHOT_CHARS).trim()
    }

    companion object {
        /** Per-entry clip. Long enough for a command's output, short enough to keep prompts small. */
        const val MAX_ENTRY_CHARS = 800

        /** Total digest clip. Roughly 1k tokens, which is the budget a blackboard deserves. */
        const val MAX_SNAPSHOT_CHARS = 4_000
    }
}
