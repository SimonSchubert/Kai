package com.kainext.duo.agents

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Runs a plan: hands each step to its worker, respecting dependencies and a concurrency cap.
 *
 * Why an orchestrator rather than a loop over steps: independent steps are the common case (read
 * a URL and take a screenshot have no ordering), and running them sequentially wastes the user's
 * time for no reason. The dependency graph is what makes concurrency safe — a step never starts
 * before the steps it declares are done.
 *
 * Cancellation: every `suspend` call here propagates `CancellationException` untouched. The
 * caller cancels by cancelling its own scope; there is no separate `cancel()` to forget to call.
 */
@OptIn(ExperimentalTime::class)
class SwarmOrchestrator(
    private val planner: Planner,
    private val workers: Map<WorkerKind, Worker>,
    private val blackboard: Blackboard,
    /** How many steps may run at once. Bounded because each one may hit a rate-limited API. */
    private val maxParallel: Int = 3,
    /** Hard cap on steps, applied after planning so a runaway planner cannot spawn forever. */
    private val maxSteps: Int = 12,
    /** Whole-run budget. A plan that cannot finish in this long is not going to finish. */
    private val totalTimeoutMs: Long = 300_000,
) {

    suspend fun run(request: SwarmRequest): SwarmResult {
        val plan = planner.plan(request.copy(maxSteps = maxSteps))
        val steps = plan.steps.take(maxSteps)
        if (steps.isEmpty()) {
            return SwarmResult(plan = plan, success = false, stopReason = "planner produced no steps")
        }

        val results = mutableListOf<StepResult>()
        val completed = mutableSetOf<String>()
        val failed = mutableSetOf<String>()

        val finished = withTimeoutOrNull(totalTimeoutMs) {
            val byId: Map<String, PlanStep> = steps.associateBy { it.id }
            val pending: MutableSet<String> = steps.map { it.id }.toMutableSet()

            while (pending.isNotEmpty()) {
                // A step is runnable when every dependency has finished. Dependencies that failed
                // are handled below by skipping the dependent step.
                val runnable: List<PlanStep> = pending.mapNotNull { id -> byId[id] }
                    .filter { step ->
                        step.dependsOn.all { depId -> depId in completed || depId in failed }
                    }

                if (runnable.isEmpty()) {
                    // Nothing can run: the remaining steps form a dependency cycle, or depend on
                    // ids that were never planned. Both mean the plan is broken; stop rather than
                    // spin forever.
                    pending.forEach { id ->
                        results += StepResult(
                            stepId = id,
                            workerKind = byId[id]?.assignedWorker ?: WorkerKind.GENERAL,
                            status = StepStatus.SKIPPED,
                            error = "unresolved dependency",
                        )
                    }
                    break
                }

                val batch: List<StepResult> = coroutineScope {
                    runnable.map { step ->
                        async<StepResult> {
                            // A failed dependency makes the step unrunnable: record it as skipped
                            // without spending a model call on work whose premise is false.
                            val blockedBy = step.dependsOn.firstOrNull { depId -> depId in failed }
                            if (blockedBy != null) {
                                StepResult(
                                    stepId = step.id,
                                    workerKind = step.assignedWorker,
                                    status = StepStatus.SKIPPED,
                                    error = "dependency $blockedBy failed",
                                )
                            } else {
                            executeStep(step)
                            }
                        }
                    }.awaitAll()
                }

                for (result in batch) {
                    results += result
                    pending.remove(result.stepId)
                    when (result.status) {
                        StepStatus.DONE -> completed += result.stepId
                        StepStatus.FAILED, StepStatus.SKIPPED -> failed += result.stepId
                        else -> Unit
                    }
                }
            }
            Unit
        }

        val timedOut = finished == null
        val succeeded = !timedOut && results.isNotEmpty() && results.none {
            it.status == StepStatus.FAILED || it.status == StepStatus.SKIPPED
        }
        return SwarmResult(
            plan = plan,
            stepResults = results,
            blackboardSnapshot = blackboard.snapshot(),
            success = succeeded,
            timedOut = timedOut,
            stopReason = when {
                timedOut -> "timed out after ${totalTimeoutMs}ms"
                else -> null
            },
        )
    }

    /**
     * Runs one step and records it on the blackboard.
     *
     * A missing worker falls back to GENERAL rather than failing: a plan naming a kind the caller
     * did not wire is a configuration gap, and a plain answer is more useful than an error.
     */
    private suspend fun executeStep(step: PlanStep): StepResult {
        val worker = workers[step.assignedWorker] ?: workers[WorkerKind.GENERAL]
            ?: return StepResult(
                stepId = step.id,
                workerKind = step.assignedWorker,
                status = StepStatus.FAILED,
                error = "no worker registered for ${step.assignedWorker}",
            )
        val result = try {
            worker.execute(step, blackboard)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            WorkerResult(success = false, error = e.message ?: "worker threw")
        }

        blackboard.write(
            BlackboardEntry(
                stepId = step.id,
                workerKind = worker.kind,
                content = result.output.ifBlank { result.error.orEmpty() },
                success = result.success,
                timestampMs = Clock.System.now().toEpochMilliseconds(),
            ),
        )
        return StepResult(
            stepId = step.id,
            workerKind = worker.kind,
            status = if (result.success) StepStatus.DONE else StepStatus.FAILED,
            output = result.output,
            error = result.error,
        )
    }
}
