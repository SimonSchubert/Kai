package com.kainext.duo.agents

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
/**
 * Which specialist a plan step should be routed to.
 *
 * The set is deliberately small. Each kind maps to a distinct family of tools, and a plan that
 * needs something outside these four is a sign the request should be one GENERAL step rather than
 * a longer plan.
 */
enum class WorkerKind {
    /** Drives the device UI: taps, typing, navigation. */
    UI,

    /** Runs shell commands through root or Shizuku. */
    SYSTEM,

    /** Reads the web or fetches URLs. */
    RESEARCH,

    /** Anything else, or a step that needs no tool at all. */
    GENERAL,
}

/** Lifecycle of one plan step. Mirrored into the UI, so the names are part of the contract. */
enum class StepStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED,

    /** Skipped because a step it depended on failed. */
    SKIPPED,
}

/**
 * One unit of work produced by the planner.
 *
 * [id] is a short stable string ("s1", "s2") rather than a UUID: it appears in prompts and in the
 * blackboard, and a short id is both cheaper to serialise and easier for a model to reference
 * without transcription errors.
 */
@Immutable
data class PlanStep(
    val id: String,
    val description: String,
    val assignedWorker: WorkerKind = WorkerKind.GENERAL,
    /** Ids of steps that must finish before this one starts. */
    val dependsOn: List<String> = emptyList(),
    val status: StepStatus = StepStatus.PENDING,
)

/** The user's request, plus whatever context the caller wants the planner to see. */
@Immutable
data class SwarmRequest(
    val instruction: String,
    val context: String = "",
    /** Cap on steps the planner may produce. Enforced after parsing, not in the prompt alone. */
    val maxSteps: Int = 12,
)

/** What the planner decided. */
@Immutable
data class PlanResult(
    val steps: List<PlanStep>,
    /** True when parsing failed and the plan collapsed to a single GENERAL step. */
    val degraded: Boolean = false,
    val reasoning: String = "",
)

/** Outcome of one worker executing one step. */
@Immutable
data class WorkerResult(
    val success: Boolean,
    val output: String = "",
    val error: String? = null,
    val toolCalls: List<String> = emptyList(),
    val durationMs: Long = 0L,
)

/** Per-step record kept for the final result and for the blackboard snapshot. */
@Immutable
data class StepResult(
    val stepId: String,
    val workerKind: WorkerKind,
    val status: StepStatus,
    val output: String = "",
    val error: String? = null,
)

/** Everything the orchestrator produced. */
@Immutable
data class SwarmResult(
    val plan: PlanResult,
    val stepResults: List<StepResult> = emptyList(),
    val blackboardSnapshot: String = "",
    val success: Boolean = false,
    val timedOut: Boolean = false,
    /** Set when the run stopped early for a reason worth showing the user. */
    val stopReason: String? = null,
) {
    /** One-line summary for the chat transcript. */
    fun summary(): String {
        val done = stepResults.count { it.status == StepStatus.DONE }
        val failed = stepResults.count { it.status == StepStatus.FAILED }
        return when {
            timedOut -> "Stopped after timing out — $done/${stepResults.size} steps completed."
            success -> "Completed $done/${stepResults.size} steps."
            else -> buildString {
                append("$done/${stepResults.size} steps completed")
                if (failed > 0) append(", $failed failed")
                stopReason?.let { append(". ").append(it) }
                append(".")
            }
        }
    }
}

/**
 * One shared-context entry written by a worker.
 *
 * [content] is truncated on write rather than on read: the blackboard exists to be summarised
 * into a prompt, and an unbounded entry would blow up every later snapshot.
 */
@Immutable
data class BlackboardEntry(
    val stepId: String,
    val workerKind: WorkerKind,
    val content: String,
    val success: Boolean = true,
    val timestampMs: Long = 0L,
)

/** Serialisation names are explicit so a rename of the Kotlin type cannot silently break parsing. */
@SerialName("swarm_request")
internal data class SwarmRequestWire(val request: SwarmRequest)
