@file:OptIn(kotlin.time.ExperimentalTime::class)

// Pure builder for the heartbeat USER-message prompt. Like `buildChatSystemPrompt`,
// every input is explicit so tests can call it directly with hand-crafted inputs.
// The heartbeat prompt is a single shape — always sent as a user message.

package com.inspiredandroid.kai.data

import kotlin.time.Instant

/** Email account status rendered into the `## Email Status` section. */
internal data class HeartbeatEmailStatus(
    val email: String,
    val unreadCount: Int,
    val lastSyncEpochMs: Long,
)

/** A pending (polled-but-not-yet-heartbeat-picked-up) email rendered into the `## New Emails` section. */
internal data class HeartbeatPendingEmail(
    val accountEmail: String,
    val from: String,
    val subject: String,
    val preview: String,
)

/** Memory promotion candidate rendered into the `## Promotion Candidates` section. */
internal data class HeartbeatPromotionCandidate(
    val key: String,
    val hitCount: Int,
    val category: MemoryCategory,
    val content: String,
)

/**
 * Composes the heartbeat prompt.
 *
 * @param customOrDefaultPrompt leading free text — custom user prompt or [HeartbeatManager.DEFAULT_HEARTBEAT_PROMPT]
 * @param recentResponses last heartbeat responses to include for continuity; empty list = section omitted
 * @param pendingTasks tasks to include in the `## Pending Tasks` section; empty list = section omitted
 * @param emailAccounts email account statuses; empty list = section omitted
 * @param pendingEmails new emails polled since the last heartbeat pickup; empty list = section omitted
 * @param promotionCandidates memory promotion candidates; empty list = section omitted
 */
internal fun buildHeartbeatPrompt(
    customOrDefaultPrompt: String,
    recentResponses: List<String>,
    pendingTasks: List<ScheduledTask>,
    emailAccounts: List<HeartbeatEmailStatus>,
    pendingEmails: List<HeartbeatPendingEmail>,
    promotionCandidates: List<HeartbeatPromotionCandidate>,
): String = buildString {
    append(customOrDefaultPrompt)
    append("\n")

    if (recentResponses.isNotEmpty()) {
        append("\n## Previous Heartbeat Results\n")
        for ((i, response) in recentResponses.withIndex()) {
            append(i + 1)
            append(". ")
            append(response)
            append('\n')
        }
    }

    if (pendingTasks.isNotEmpty()) {
        append("\n## Pending Tasks\n")
        for (t in pendingTasks) {
            append("- **")
            append(t.description)
            append("** (id: ")
            append(t.id)
            append(", scheduled: ")
            append(t.scheduledAt)
            append(")")
            if (t.cron != null) {
                append(" [cron: ")
                append(t.cron)
                append("]")
            }
            append('\n')
        }
    }

    if (emailAccounts.isNotEmpty()) {
        append("\n## Email Status\n")
        for (account in emailAccounts) {
            append("- **")
            append(account.email)
            append("**: ")
            append(account.unreadCount)
            append(" unread")
            if (account.lastSyncEpochMs > 0) {
                append(" (last sync: ")
                append(Instant.fromEpochMilliseconds(account.lastSyncEpochMs))
                append(")")
            }
            append('\n')
        }
    }

    if (pendingEmails.isNotEmpty()) {
        append("\n## New Emails\n")
        append("These arrived since the last heartbeat. Summarise briefly; only flag items that genuinely need attention.\n")
        for (msg in pendingEmails) {
            append("- **")
            append(msg.subject.ifBlank { "(no subject)" })
            append("** — ")
            append(msg.from)
            append(" [")
            append(msg.accountEmail)
            append("]")
            if (msg.preview.isNotBlank()) {
                append(": ")
                append(msg.preview)
            }
            append('\n')
        }
    }

    if (promotionCandidates.isNotEmpty()) {
        append("\n## Promotion Candidates\n")
        append("These memories have been reinforced ")
        append(promotionCandidates.first().hitCount)
        append("+ times. ")
        append("Consider using the promote_learning tool to add well-established patterns to your soul/system prompt:\n")
        for (entry in promotionCandidates) {
            append("- **")
            append(entry.key)
            append("** (hits: ")
            append(entry.hitCount)
            append(", category: ")
            append(entry.category)
            append("): ")
            append(entry.content)
            append('\n')
        }
    }
}
