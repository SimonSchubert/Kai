package com.kainext.duo.agents

import kotlinx.coroutines.CancellationException

/**
 * Turns a request into an ordered plan.
 *
 * Implementations must not throw for a bad model reply: a plan that cannot be parsed is a
 * degraded plan, not an error. [LlmPlanner] enforces that.
 */
interface Planner {
    suspend fun plan(request: SwarmRequest): PlanResult
}

/**
 * A [Planner] backed by a language model.
 *
 * [complete] is injected rather than the planner owning an HTTP client: the swarm module stays in
 * `commonMain` with no knowledge of providers, credentials or retries, and the caller decides
 * which model answers. It receives a prompt and returns the model's raw text.
 */
class LlmPlanner(
    private val complete: suspend (prompt: String) -> String,
) : Planner {

    override suspend fun plan(request: SwarmRequest): PlanResult {
        if (request.instruction.isBlank()) {
            return degradedPlan(request, reason = "empty instruction")
        }
        val raw = try {
            complete(SwarmPrompts.buildPlannerPrompt(request))
        } catch (e: CancellationException) {
            // User stop or dead scope: never turn cancellation into a plan.
            throw e
        } catch (e: Throwable) {
            return degradedPlan(request, reason = "planner call failed: ${e.message ?: "unknown"}")
        }
        val parsed = SwarmPrompts.parsePlan(raw, request.maxSteps)
            ?: return degradedPlan(request, reason = "planner reply was not a usable plan")
        // A plan with no steps is not usable either.
        if (parsed.steps.isEmpty()) {
            return degradedPlan(request, reason = "planner produced no steps")
        }
        return parsed
    }

    /**
     * Collapses the whole request into one GENERAL step.
     *
     * Why this is the right failure mode: a one-step plan still does the work — the worker gets
     * the original instruction and can call any tool. Failing the whole request because the
     * planner's JSON was malformed would throw away a perfectly good request over a formatting
     * problem.
     */
    private fun degradedPlan(request: SwarmRequest, reason: String): PlanResult = PlanResult(
        steps = listOf(
            PlanStep(
                id = "s1",
                description = request.instruction,
                assignedWorker = WorkerKind.GENERAL,
                dependsOn = emptyList(),
            ),
        ),
        degraded = true,
        reasoning = reason,
    )
}

/** A planner that always returns the same single-step plan. Useful for tests and for direct runs. */
class SingleStepPlanner(private val worker: WorkerKind = WorkerKind.GENERAL) : Planner {
    override suspend fun plan(request: SwarmRequest): PlanResult = PlanResult(
        steps = listOf(
            PlanStep(id = "s1", description = request.instruction, assignedWorker = worker),
        ),
        degraded = false,
        reasoning = "single-step plan",
    )
}
