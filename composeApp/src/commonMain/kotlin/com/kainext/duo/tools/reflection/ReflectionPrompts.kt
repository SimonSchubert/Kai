package com.kainext.duo.tools.reflection

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Prompt text for the self-correction loop.
 *
 * Kept as a separate object from [SelfCorrectingExecutor] because prompts are the part that gets
 * tuned most often, and because the same text is useful outside the loop: the system prompt is
 * appended to every agent run, while the reflection prompt is only built when something fails.
 *
 * Everything here is English by design — it is model input, not user-facing copy.
 */
object ReflectionPrompts {

    /** Max characters of a single command echoed into the prompt before it is shortened. */
    private const val MAX_COMMAND_CHARS = 1_500

    /** Markdown code fence, used both to build the prompt and to unwrap model replies. */
    private const val FENCE = "```"

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * The "you may fix your own mistakes" block to append to the agent's system prompt.
     *
     * Why it exists: without it models tend to report the first failure verbatim and stop, even
     * though the failure is usually a fixable detail (wrong path, missing privilege, typo). The
     * prompt states the budget explicitly so the model spends its retries instead of
     * over-running, and tells it to stop when the approach itself is wrong so it does not burn
     * all three attempts on the same broken idea.
     */
    fun errorCorrectionSystemPrompt(): String = """
        ## Self-correction on tool failures

        A tool call that fails is not the end of the task. When a shell command or UI action
        fails, you get to analyse the error and try a corrected version, up to 3 attempts total.

        For each failure:
        1. Read stderr (and the exit code) before doing anything else. Do not retry blind.
        2. Classify the cause:
           - permission (exit 126, "Permission denied", "Operation not permitted")
             -> consider a privileged backend (root / Shizuku), an app-private path, or a
                different directory you actually own
           - not found (exit 127, "No such file or directory")
             -> check the spelling, use an absolute path, or verify with `command -v` that the
                binary exists on this device at all
           - usage / syntax (exit 2, "invalid option", "unrecognized argument")
             -> the command line is malformed; rewrite it rather than repeating it
           - environment (exit 1 with a specific complaint, 137 = likely low memory)
             -> reduce the work: narrower query, pagination, fewer files, shorter output
           - timeout (no exit code, or a message about timing out)
             -> do less per invocation, add a bound (`head`, `-n`, `timeout`), or split the job
        3. Change something real. Never resend a command identical to one that already failed —
           it will fail identically and wastes the budget.
        4. If two attempts failed for different reasons and neither got closer, the method is
           probably wrong. Stop and explain what you tried and what blocked you, instead of
           spending the last attempt.

        Always tell the user what you tried and why it failed if you give up. Never claim a task
        succeeded when the last attempt failed.
    """.trimIndent()

    /**
     * The reflection request: goal, full attempt history, and a strict output contract.
     *
     * The JSON schema is spelled out field by field and repeated as an example because that is
     * what reliably produces parseable output across providers; [parseReflectionResponse] is
     * tolerant anyway, but a prompt that invites prose is a prompt that gets prose.
     */
    fun buildReflectionPrompt(request: ReflectionRequest): String = buildString {
        appendLine("## Task")
        appendLine("Intent (what the user actually wants):")
        appendLine(request.intent.trim())
        appendLine()

        appendLine("## What has been tried")
        if (request.attempts.isEmpty()) {
            appendLine("(no attempt has been recorded yet)")
        } else {
            request.attempts.forEach { record ->
                appendLine("### Attempt ${record.attemptNumber} of ${request.maxAttempts}")
                val command = record.command
                if (command.isNullOrBlank()) {
                    appendLine("command: (not specified — you chose how to act on the intent)")
                } else {
                    appendLine("command:")
                    appendLine(FENCE + "sh")
                    appendLine(command.middleTruncate(MAX_COMMAND_CHARS))
                    appendLine(FENCE)
                }
                appendLine("result: ${if (record.outcome.success) "success" else "failure"}")
                appendLine(record.explanation)
                appendLine()
            }
        }

        val attemptsLeft = (request.maxAttempts - request.attempts.size).coerceAtLeast(0)
        appendLine("## Your job")
        appendLine(
            "The attempts above did not achieve the intent. Decide whether one more corrected " +
                "attempt is worth making.",
        )
        appendLine("Attempts remaining after this decision: $attemptsLeft")
        appendLine()

        appendLine("Rules:")
        appendLine("- Do NOT repeat a command that already failed. It will fail the same way.")
        appendLine(
            "- If the error is a permission problem, change the approach: use a privileged " +
                "backend (root / Shizuku), an app-private path, or a location you can write to.",
        )
        appendLine(
            "- If the command was not found, verify the name or use an absolute path instead of " +
                "guessing at more names.",
        )
        appendLine(
            "- If the failure is a syntax/usage error, rewrite the command line; do not just " +
                "reorder the same flags.",
        )
        appendLine(
            "- If several attempts failed for different reasons and none got closer, the method " +
                "is wrong: set should_retry to false and explain the blocker.",
        )
        appendLine(
            "- If attempts remaining is 0, you must set should_retry to false.",
        )
        appendLine("- Exit code meaning for the codes you saw: read the explanations above; they " +
            "state whether a retry is worth it and what to change.")
        appendLine()

        appendLine("## Output format")
        appendLine("Reply with a single JSON object and nothing else. No markdown fence, no prose.")
        appendLine(FENCE + "json")
        appendLine("{")
        appendLine("  \"corrected_command\": \"the full command to run next, or null if you cannot name one\",")
        appendLine("  \"reasoning\": \"one or two sentences: what failed and what you changed\",")
        appendLine("  \"should_retry\": true,")
        appendLine("  \"give_up_reason\": null")
        appendLine("}")
        appendLine(FENCE)
        appendLine("Fields:")
        appendLine("- corrected_command (string or null): the exact next command. Use null when the " +
            "next step is not a single shell command; the executor will then re-plan from the intent.")
        appendLine("- reasoning (string): what the previous failure means and why this command differs.")
        appendLine("- should_retry (boolean): false to stop now.")
        appendLine("- give_up_reason (string or null): required when should_retry is false; a short " +
            "explanation the user can understand.")
    }.trimEnd()

    /**
     * Tolerant extraction of a [ReflectionResponse] from raw model output.
     *
     * Real responses arrive wrapped in ```` ```json ```` fences, prefixed with "Here is my
     * analysis:", or followed by a trailing sentence, so a strict `decodeFromString` fails often
     * enough to be useless. The strategy is:
     *
     * 1. Strip a fenced block if there is one, else cut from the first `{` to the last `}`.
     * 2. Read the four fields by *both* snake_case and camelCase names, because models mix them
     *    even when the prompt shows only one spelling.
     *
     * Fields are read by hand rather than through the generated serializer because the serializer
     * rejects a whole response over a single unexpected shape — one stringified boolean and the
     * entire correction is lost, turning a recoverable failure into a dead end.
     *
     * Field coercion is deliberately loose: `should_retry` may arrive as `"false"` (a string) and
     * `corrected_command` may arrive as the literal string `"null"` — both are common model
     * outputs and both are normalised here rather than pushed onto the caller.
     *
     * @return the parsed response, or `null` when no JSON object can be decoded. `null` is a safe
     *   stop for the retry loop (no correction available), never an exception mid-loop.
     */
    fun parseReflectionResponse(raw: String): ReflectionResponse? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val candidates = buildList {
            val fenced = fencedJson(trimmed)
            if (fenced != null) add(fenced)
            val sliced = jsonObjectSlice(trimmed)
            if (sliced != null) add(sliced)
        }.distinct()

        for (candidate in candidates) {
            val obj = runCatching { jsonParser.parseToJsonElement(candidate).jsonObject }.getOrNull()
            if (obj != null) {
                return ReflectionResponse(
                    correctedCommand = obj.stringField("corrected_command", "correctedCommand"),
                    reasoning = obj.stringField("reasoning").orEmpty(),
                    shouldRetry = obj.boolField("should_retry", "shouldRetry") ?: true,
                    giveUpReason = obj.stringField("give_up_reason", "giveUpReason"),
                )
            }
        }
        return null
    }

    /** First present, non-null-ish string among [names], searched in order. */
    private fun JsonObject.stringField(vararg names: String): String? =
        names.firstNotNullOfOrNull { name -> get(name)?.asTextOrNull() }

    /** First present boolean among [names]; accepts real booleans and `"true"` / `"false"`. */
    private fun JsonObject.boolField(vararg names: String): Boolean? =
        names.firstNotNullOfOrNull { name -> get(name)?.asBooleanOrNull() }

    /**
     * Text of a JSON value, or `null` when it is absent, `JsonNull`, a nested object/array, or the
     * literal string `"null"`. The last case matters because models routinely emit `"null"` as a
     * string when the schema says "string or null".
     */
    private fun JsonElement.asTextOrNull(): String? {
        val primitive = this as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        return primitive.contentOrNull?.takeIf {
            it.isNotBlank() && !it.equals("null", ignoreCase = true)
        }
    }

    /** Boolean value of a JSON value, accepting real booleans and the usual string spellings. */
    private fun JsonElement.asBooleanOrNull(): Boolean? {
        val primitive = this as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        primitive.booleanOrNull?.let { return it }
        return when (primitive.contentOrNull?.trim()?.lowercase()) {
            "true", "yes", "1" -> true
            "false", "no", "0" -> false
            else -> null
        }
    }

    /**
     * Content of the first fenced block (```` ``` ```` or ```` ```json ````), when it holds a JSON
     * object. A response with no fence at all returns `null` and is handled by [jsonObjectSlice].
     */
    private fun fencedJson(text: String): String? {
        val open = text.indexOf(FENCE)
        if (open < 0) return null
        val newline = text.indexOf('\n', open)
        if (newline < 0) return null
        val bodyStart = newline + 1
        val close = text.indexOf(FENCE, bodyStart)
        val body = if (close < 0) text.substring(bodyStart) else text.substring(bodyStart, close)
        val candidate = body.trim()
        return candidate.takeIf { it.startsWith("{") && it.endsWith("}") }
    }

    /**
     * The outermost `{...}` span, used when the model did not use a fence. A first-`{` to
     * last-`}` slice is enough here: the payload is a flat object of strings and booleans, and a
     * nested object would still be balanced, so the slice stays valid JSON.
     */
    private fun jsonObjectSlice(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return text.substring(start, end + 1)
    }
}
