package com.kainext.duo.agents

import com.kainext.duo.network.tools.Tool
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Executes one plan step.
 *
 * A worker owns its own tool family: the UI worker cannot run shell commands, and the SYSTEM
 * worker cannot tap the screen. That restriction is the point of splitting them — it keeps each
 * worker's prompt small and its tool choice unambiguous.
 */
interface Worker {
    val kind: WorkerKind
    suspend fun execute(step: PlanStep, blackboard: Blackboard): WorkerResult
}

/**
 * A [Worker] that picks one of its [tools] for the step and runs it.
 *
 * [complete] is the model call that chooses the tool; injecting it keeps this module free of
 * provider details. When the model declines to pick a tool, the step succeeds with the model's
 * own answer as its output, which is the correct behaviour for a pure-reasoning step.
 */
@OptIn(ExperimentalTime::class)
class ToolBackedWorker(
    override val kind: WorkerKind,
    private val tools: List<Tool>,
    private val complete: suspend (prompt: String) -> String,
) : Worker {

    override suspend fun execute(step: PlanStep, blackboard: Blackboard): WorkerResult {
        val startedAt = Clock.System.now().toEpochMilliseconds()
        val toolSummaries = tools.map { "${it.schema.name}: ${it.schema.description}" }
        val prompt = SwarmPrompts.buildWorkerPrompt(
            step = step,
            tools = toolSummaries,
            context = blackboard.snapshot(),
        )

        val choice = try {
            SwarmPrompts.parseWorkerChoice(complete(prompt))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return WorkerResult(
                success = false,
                error = "tool selection failed: ${e.message ?: "unknown"}",
                durationMs = Clock.System.now().toEpochMilliseconds() - startedAt,
            )
        }

        // No tool chosen: treat the model's reply as the step's answer. A GENERAL step is usually
        // pure reasoning, and failing it for not calling a tool would be wrong.
        if (choice == null) {
            return WorkerResult(
                success = true,
                output = "(no tool needed)",
                durationMs = Clock.System.now().toEpochMilliseconds() - startedAt,
            )
        }

        val tool = tools.firstOrNull { it.schema.name == choice.toolName }
            ?: return WorkerResult(
                success = false,
                error = "unknown tool '${choice.toolName}'. Available: ${tools.joinToString { it.schema.name }}",
                durationMs = Clock.System.now().toEpochMilliseconds() - startedAt,
            )

        return try {
            val result = tool.execute(choice.arguments.mapValues { it.value as Any })
            WorkerResult(
                success = true,
                output = result.toString(),
                toolCalls = listOf(tool.schema.name),
                durationMs = Clock.System.now().toEpochMilliseconds() - startedAt,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            WorkerResult(
                success = false,
                error = "${tool.schema.name} failed: ${e.message ?: "unknown"}",
                toolCalls = listOf(tool.schema.name),
                durationMs = Clock.System.now().toEpochMilliseconds() - startedAt,
            )
        }
    }
}

/**
 * A worker that answers without tools.
 *
 * Used for GENERAL steps and as the fallback when a plan names a worker kind the caller did not
 * supply — better a plain answer than a hard failure that strands the whole plan.
 */
@OptIn(ExperimentalTime::class)
class DirectAnswerWorker(
    override val kind: WorkerKind,
    private val complete: suspend (prompt: String) -> String,
) : Worker {

    override suspend fun execute(step: PlanStep, blackboard: Blackboard): WorkerResult {
        val startedAt = Clock.System.now().toEpochMilliseconds()
        val prompt = buildString {
            appendLine(step.description)
            val context = blackboard.snapshot()
            if (context.isNotBlank()) {
                appendLine()
                appendLine("Context from earlier steps:")
                appendLine(context)
            }
        }
        return try {
            WorkerResult(
                success = true,
                output = complete(prompt),
                durationMs = Clock.System.now().toEpochMilliseconds() - startedAt,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            WorkerResult(
                success = false,
                error = e.message ?: "worker failed",
                durationMs = Clock.System.now().toEpochMilliseconds() - startedAt,
            )
        }
    }
}
