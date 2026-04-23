@file:OptIn(kotlin.time.ExperimentalTime::class)

// Pure builder for the heartbeat USER-message prompt. Like `buildChatSystemPrompt`,
// every input is explicit so tests can call it directly with hand-crafted inputs.
// The heartbeat prompt is a single shape — always sent as a user message.

package com.inspiredandroid.kai.data

import kotlin.time.Instant

/** A pending (polled-but-not-yet-heartbeat-picked-up) email rendered into the `## New Emails` section. */
internal data class HeartbeatPendingEmail(
    val accountEmail: String,
    val from: String,
    val subject: String,
    val preview: String,
)

/** A pending (polled-but-not-yet-heartbeat-picked-up) SMS rendered into the `## New SMS` section. */
internal data class HeartbeatPendingSms(
    val id: Long,
    val from: String,
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
 * @param heartbeatAdditions tasks with trigger=HEARTBEAT; rendered as `## Heartbeat Additions` so their prompts run on every heartbeat. Empty list = section omitted
 * @param recentResponses last heartbeat responses to include for continuity; empty list = section omitted
 * @param pendingTasks time/cron tasks to include in the `## Pending Tasks` section (heartbeat tasks belong to [heartbeatAdditions] instead); empty list = section omitted
 * @param emailAccounts email account statuses; empty list = section omitted
 * @param pendingEmails new emails polled since the last heartbeat pickup; empty list = section omitted
 * @param pendingSms new SMS polled since the last heartbeat pickup; empty list = section omitted
 * @param promotionCandidates memory promotion candidates; empty list = section omitted
 */
internal fun buildHeartbeatPrompt(
    customOrDefaultPrompt: String,
    heartbeatAdditions: List<ScheduledTask>,
    recentResponses: List<String>,
    pendingTasks: List<ScheduledTask>,
    emailAccounts: List<EmailAccountSummary>,
    pendingEmails: List<HeartbeatPendingEmail>,
    pendingSms: List<HeartbeatPendingSms>,
    promotionCandidates: List<HeartbeatPromotionCandidate>,
): String = buildString {
    append(customOrDefaultPrompt)
    append("\n")

    if (heartbeatAdditions.isNotEmpty()) {
        append("\n## Heartbeat Additions\n")
        append("Standing instructions the user asked to run on every heartbeat. Address each in your response alongside the main self-check — if all are satisfied and nothing else needs attention, respond with your acknowledgement rather than HEARTBEAT_OK (the additions are the attention).\n")
        for (addition in heartbeatAdditions) {
            append("- **")
            append(addition.description)
            append("** (id: ")
            append(addition.id)
            append("): ")
            append(addition.prompt)
            append('\n')
        }
    }

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

    if (pendingSms.isNotEmpty()) {
        append("\n## New SMS\n")
        append("These SMS arrived since the last heartbeat. Summarise briefly; only flag items that genuinely need attention.\n")
        for (msg in pendingSms) {
            append("- **")
            append(msg.from.ifBlank { "(unknown sender)" })
            append("** (id: ")
            append(msg.id)
            append(")")
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
