@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.kainext.duo.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * A user-declared goal the assistant can plan against.
 *
 * Goals are persisted as one JSON list in app settings (see [AppSettings.getGoalsJson]) and are
 * intentionally simple: a title, an optional description, an optional target date, a lifecycle
 * status and an ordered list of [GoalPlan] steps. Progress is derived, never stored, so the
 * percent shown in the UI can never drift from the plan list it was computed from.
 */
@Immutable
@Serializable
data class Goal(
    val id: String,
    val title: String,
    val description: String = "",
    /** Free-form grouping label (e.g. "工作"). Empty means ungrouped. */
    val category: String = "",
    val priority: GoalPriority = GoalPriority.MEDIUM,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val createdAtEpochMs: Long = 0L,
    /** Optional deadline. Null means the goal has no fixed end date. */
    val targetDateEpochMs: Long? = null,
    val plans: List<GoalPlan> = emptyList(),
) {
    val createdAt: Instant get() = Instant.fromEpochMilliseconds(createdAtEpochMs)

    val targetDate: Instant? get() = targetDateEpochMs?.let { Instant.fromEpochMilliseconds(it) }

    val totalPlanCount: Int get() = plans.size

    val completedPlanCount: Int get() = plans.count { it.status == GoalPlanStatus.DONE }

    /** Whole-percent progress. A goal with no plans counts as complete once it is marked DONE. */
    val completionPercent: Int
        get() = when {
            plans.isEmpty() -> if (status == GoalStatus.DONE) 100 else 0
            else -> completedPlanCount * 100 / plans.size
        }

    /** 0f..1f progress, for progress indicators. */
    val completionFraction: Float
        get() = when {
            plans.isEmpty() -> if (status == GoalStatus.DONE) 1f else 0f
            else -> completedPlanCount.toFloat() / plans.size
        }

    val isCompleted: Boolean get() = status == GoalStatus.DONE

    /**
     * Overdue at a caller-supplied instant. Exists so tests and the aggregate stats can pin the
     * clock instead of depending on wall time, which would make [GoalStats] assertions flaky.
     */
    fun isOverdueAt(nowEpochMs: Long): Boolean {
        val deadline = targetDateEpochMs ?: return false
        return !isCompleted && deadline < nowEpochMs
    }

    /** True when the deadline has passed and the goal isn't done yet. */
    val isOverdue: Boolean get() = isOverdueAt(ClockNow.epochMs())

    val nextOpenPlan: GoalPlan? get() = plans.firstOrNull { it.status != GoalPlanStatus.DONE && it.status != GoalPlanStatus.SKIPPED }
}

/** A single step of a [Goal]. */
@Immutable
@Serializable
data class GoalPlan(
    val id: String,
    val title: String,
    val description: String = "",
    val status: GoalPlanStatus = GoalPlanStatus.PENDING,
    val createdAtEpochMs: Long = 0L,
    val completedAtEpochMs: Long? = null,
) {
    val isDone: Boolean get() = status == GoalPlanStatus.DONE
}

@Serializable
enum class GoalStatus {
    /** Being worked on. */
    ACTIVE,

    /** Temporarily on hold; still visible but not counting as active work. */
    PAUSED,

    /** Finished. */
    DONE,

    /** Hidden from the active list, kept for history. */
    ARCHIVED,
}

@Serializable
enum class GoalPriority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT,
}

@Serializable
enum class GoalPlanStatus {
    PENDING,
    IN_PROGRESS,
    DONE,
    SKIPPED,
}

/**
 * Single source of truth for the overview numbers. Aggregation lives here rather than in each
 * caller so the card, the repository and the test fake can never disagree — they previously
 * carried three copies of this loop.
 */
fun List<Goal>.toGoalStats(nowEpochMs: Long = ClockNow.epochMs()): GoalStats {
    val totalPlans = sumOf { it.totalPlanCount }
    val completedPlans = sumOf { it.completedPlanCount }
    return GoalStats(
        totalGoals = size,
        activeGoals = count { it.status == GoalStatus.ACTIVE },
        completedGoals = count { it.status == GoalStatus.DONE },
        overdueGoals = count { it.isOverdueAt(nowEpochMs) },
        totalPlans = totalPlans,
        completedPlans = completedPlans,
        // Goals with no plans fall back to their own completion state, matching
        // [Goal.completionPercent] so the aggregate never contradicts a single card.
        overallCompletionPercent = when {
            totalPlans > 0 -> completedPlans * 100 / totalPlans
            isEmpty() -> 0
            else -> count { it.completionPercent == 100 } * 100 / size
        },
    )
}

/** Aggregated numbers for the goals overview card. */
@Immutable
data class GoalStats(
    val totalGoals: Int = 0,
    val activeGoals: Int = 0,
    val completedGoals: Int = 0,
    val overdueGoals: Int = 0,
    val totalPlans: Int = 0,
    val completedPlans: Int = 0,
    val overallCompletionPercent: Int = 0,
)

/**
 * Indirection around [kotlin.time.Clock] so [Goal.isOverdue] stays a pure, testable property
 * while still reading the real clock in production.
 */
internal object ClockNow {
    fun epochMs(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
}
