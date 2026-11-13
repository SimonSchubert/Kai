package com.kainext.duo.agents

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Prompt text and the tolerant parser that reads the planner's reply.
 *
 * Kept in its own file because the wording is load-bearing: the parser below accepts a narrow
 * shape, and every rule in the prompt exists to keep the model inside it.
 */
internal object SwarmPrompts {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /** Asks the model for a JSON plan. */
    fun buildPlannerPrompt(request: SwarmRequest): String = buildString {
        appendLine("Break the user's request into a short, ordered plan.")
        appendLine()
        appendLine("## Request")
        appendLine(request.instruction)
        if (request.context.isNotBlank()) {
            appendLine()
            appendLine("## Context")
            appendLine(request.context.take(MAX_CONTEXT_CHARS))
        }
        appendLine()
        appendLine("## Rules")
        appendLine("- At most ${request.maxSteps} steps. Fewer is better: prefer 1-4.")
        appendLine("- One action per step. \"Open Settings and enable Wi-Fi\" is two steps.")
        appendLine("- worker is one of: UI (taps/typing/navigation), SYSTEM (shell commands),")
        appendLine("  RESEARCH (web/URL reading), GENERAL (reasoning, no tool).")
        appendLine("- dependsOn lists ids of steps that must finish first. Use [] when there are none.")
        appendLine("- If the whole request is a single action, return exactly one step.")
        appendLine()
        appendLine("## Output")
        appendLine("Reply with JSON only — no prose, no markdown fences:")
        appendLine(
            """{"steps":[{"id":"s1","description":"...","worker":"UI","dependsOn":[]}],""" +
                """"reasoning":"one sentence"}""",
        )
    }

    /**
     * Parses a planner reply.
     *
     * Returns null rather than throwing when nothing usable is present — the caller degrades to a
     * single GENERAL step, because a plan we cannot read is not a reason to abandon the request.
     */
    fun parsePlan(raw: String, maxSteps: Int): PlanResult? {
        val body = extractJsonObject(raw) ?: return null
        val stepsArray = runCatching { body["steps"]?.jsonArray }.getOrNull() ?: return null
        if (stepsArray.isEmpty()) return null

        val steps = mutableListOf<PlanStep>()
        var index = 0
        for (element in stepsArray) {
            if (steps.size >= maxSteps) break
            val obj = runCatching { element.jsonObject }.getOrNull() ?: continue
            val description = obj["description"]?.jsonPrimitive?.contentOrNull?.trim()
                ?.takeIf { it.isNotEmpty() } ?: continue
            index++
            val id = obj["id"]?.jsonPrimitive?.contentOrNull?.trim()
                ?.takeIf { it.isNotEmpty() } ?: "s$index"
            val worker = parseWorker(obj["worker"]?.jsonPrimitive?.contentOrNull)
            val dependsOn = runCatching {
                obj["dependsOn"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    ?.filter { it.isNotBlank() && it != id }
                    .orEmpty()
            }.getOrDefault(emptyList())
            steps += PlanStep(id = id, description = description, assignedWorker = worker, dependsOn = dependsOn)
        }
        if (steps.isEmpty()) return null

        // Drop dependencies on ids that do not exist in the plan: a dangling reference would make
        // the orchestrator wait forever for a step that will never run.
        val known = steps.map { it.id }.toSet()
        val sanitized = steps.map { step ->
            val valid = step.dependsOn.filter { it in known }
            if (valid.size == step.dependsOn.size) step else step.copy(dependsOn = valid)
        }
        val reasoning = body["reasoning"]?.jsonPrimitive?.contentOrNull.orEmpty()
        return PlanResult(steps = sanitized, degraded = false, reasoning = reasoning)
    }

    /** Asks the model which tool to use for one step, and with what arguments. */
    fun buildWorkerPrompt(step: PlanStep, tools: List<String>, context: String): String = buildString {
        appendLine("Choose the single tool that accomplishes this step.")
        appendLine()
        appendLine("## Step")
        appendLine(step.description)
        if (context.isNotBlank()) {
            appendLine()
            appendLine("## What earlier steps found")
            appendLine(context.take(MAX_CONTEXT_CHARS))
        }
        appendLine()
        appendLine("## Available tools")
        tools.forEach { appendLine("- $it") }
        appendLine()
        appendLine("## Output")
        appendLine("Reply with JSON only:")
        appendLine("""{"tool":"tool_name","arguments":{...}}""")
        appendLine("If no tool is needed, reply: {\"tool\":null}")
    }

    /** Parses the worker's tool choice. Returns null when the model says no tool is needed. */
    fun parseWorkerChoice(raw: String): WorkerChoice? {
        val body = extractJsonObject(raw) ?: return null
        val toolName = body["tool"]?.jsonPrimitive?.contentOrNull?.trim()
        if (toolName.isNullOrEmpty() || toolName == "null") return null
        val args = runCatching {
            body["arguments"]?.jsonObject?.entries?.associate { (key, value) ->
                key to (value.jsonPrimitive.contentOrNull ?: value.toString())
            }.orEmpty()
        }.getOrDefault(emptyMap())
        return WorkerChoice(toolName, args)
    }

    private fun parseWorker(raw: String?): WorkerKind = when (raw?.trim()?.uppercase()) {
        "UI" -> WorkerKind.UI
        "SYSTEM" -> WorkerKind.SYSTEM
        "RESEARCH" -> WorkerKind.RESEARCH
        else -> WorkerKind.GENERAL
    }

    /**
     * Pulls the first balanced JSON object out of [raw].
     *
     * Models wrap JSON in ```json fences, prefix it with "Here is the plan:", or append a summary
     * afterwards. Scanning for balanced braces handles all three without a regex that would choke
     * on braces inside string literals.
     */
    private fun extractJsonObject(raw: String): JsonObject? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until raw.length) {
            val c = raw[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '{' -> depth++
                c == '}' -> {
                    depth--
                    if (depth == 0) {
                        return runCatching { json.parseToJsonElement(raw.substring(start, i + 1)).jsonObject }
                            .getOrNull()
                    }
                }
            }
        }
        return null
    }

    private const val MAX_CONTEXT_CHARS = 2_000
}

/** A tool the worker decided to call, with arguments flattened to strings. */
internal data class WorkerChoice(val toolName: String, val arguments: Map<String, String>)
