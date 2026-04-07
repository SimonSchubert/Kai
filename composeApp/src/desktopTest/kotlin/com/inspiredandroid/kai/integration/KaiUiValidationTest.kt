package com.inspiredandroid.kai.integration

import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.network.Requests
import com.inspiredandroid.kai.network.ServiceCredentials
import com.inspiredandroid.kai.network.dtos.anthropic.AnthropicChatRequestDto
import com.inspiredandroid.kai.network.dtos.anthropic.extractText
import com.inspiredandroid.kai.network.dtos.gemini.GeminiChatRequestDto
import com.inspiredandroid.kai.network.dtos.gemini.extractText
import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto
import com.inspiredandroid.kai.ui.dynamicui.KaiUiParser
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import kotlin.test.Test

/**
 * Integration test that exercises real LLM providers with a battery of prompts and
 * validates that their responses contain well-formed kai-ui blocks.
 *
 * By default this test is **skipped** — it only runs when `KAI_INTEGRATION=1` is set
 * in the environment, because it makes real (paid) API calls.
 *
 * ## How to run
 *
 * ```
 * KAI_INTEGRATION=1 \
 *   KAI_OPENAI_KEY=sk-... \
 *   KAI_ANTHROPIC_KEY=sk-ant-... \
 *   ./gradlew :composeApp:desktopTest --tests "*KaiUiValidationTest*" --info
 * ```
 *
 * Any subset of provider keys can be supplied — the test auto-detects which ones are
 * present and only runs those. Supported env vars:
 *
 * | Provider     | Key env var           | Model env var (optional) | Default model                |
 * |--------------|-----------------------|--------------------------|------------------------------|
 * | OpenAI       | KAI_OPENAI_KEY        | KAI_OPENAI_MODEL         | gpt-4o-mini                  |
 * | Anthropic    | KAI_ANTHROPIC_KEY     | KAI_ANTHROPIC_MODEL      | claude-3-5-haiku-latest      |
 * | Gemini       | KAI_GEMINI_KEY        | KAI_GEMINI_MODEL         | gemini-2.0-flash             |
 * | Mistral      | KAI_MISTRAL_KEY       | KAI_MISTRAL_MODEL        | mistral-small-latest         |
 * | Groq         | KAI_GROQ_KEY          | KAI_GROQ_MODEL           | llama-3.3-70b-versatile      |
 * | OpenRouter   | KAI_OPENROUTER_KEY    | KAI_OPENROUTER_MODEL     | openai/gpt-4o-mini           |
 * | DeepSeek     | KAI_DEEPSEEK_KEY      | KAI_DEEPSEEK_MODEL       | deepseek-chat                |
 * | xAI          | KAI_XAI_KEY           | KAI_XAI_MODEL            | grok-2-latest                |
 * | Cerebras     | KAI_CEREBRAS_KEY      | KAI_CEREBRAS_MODEL       | llama-3.3-70b                |
 * | Moonshot     | KAI_MOONSHOT_KEY      | KAI_MOONSHOT_MODEL       | moonshot-v1-8k               |
 * | Together     | KAI_TOGETHER_KEY      | KAI_TOGETHER_MODEL       | meta-llama/Llama-3.3-70B-Instruct-Turbo |
 * | Mistral FT   | KAI_MISTRAL_FT_KEY    | KAI_MISTRAL_FT_MODEL     | ft:open-mistral-7b:latest  |
 *
 * Additional knobs:
 * - `KAI_REPORT_DIR` — where to write the report (default: `build/reports/kaiui-integration`)
 * - `KAI_MIN_SUCCESS_RATE` — 0.0-1.0, fail the test below this rate (default: 0.0, never fail)
 * - `KAI_REPLAY_STRICT` — set to `1` to make the replay test fail when any saved response
 *   no longer parses cleanly. Default off (report-only).
 *
 * ## What it produces
 *
 * Under the report directory:
 * - `report.md` — overall summary table plus per-provider/per-prompt status
 * - `<provider>/<slug>.raw.txt` — raw assistant response for every prompt
 * - `<provider>/<slug>.error.txt` — only for prompts where the parser produced
 *   `ErrorSegment` or no `UiSegment` at all; contains raw JSON plus parser diagnostics
 *
 * The `.raw.txt` files are pure LLM output with no headers — safe to feed back
 * through `KaiUiParser.parse()` directly. See the `replay saved responses` test
 * below for an offline re-parse harness.
 *
 * ## Iteration workflow
 *
 * 1. Run the online integration test once with API keys (costs money, saves responses)
 * 2. Modify the parser / prompt
 * 3. Run the `replay saved responses` test (no keys, free, fast) to check the fix
 * 4. When green, copy the now-passing payloads into `KaiUiParserTest` as regressions
 */
class KaiUiValidationTest {

    @Test
    fun `validate kai-ui output across providers`() {
        if (System.getenv("KAI_INTEGRATION") != "1") {
            println("[KaiUiValidationTest] Skipped — set KAI_INTEGRATION=1 to run.")
            return
        }

        val providers = discoverProviders()
        if (providers.isEmpty()) {
            println("[KaiUiValidationTest] Skipped — no provider API keys found in env.")
            println("    Set any of: KAI_OPENAI_KEY, KAI_ANTHROPIC_KEY, KAI_GEMINI_KEY, KAI_GROQ_KEY, KAI_OPENROUTER_KEY")
            return
        }

        val reportDir = File(System.getenv("KAI_REPORT_DIR") ?: "build/reports/kaiui-integration")
        reportDir.mkdirs()
        val requests = Requests()
        val allResults = mutableListOf<PromptResult>()

        for (provider in providers) {
            println("\n[KaiUiValidationTest] === ${provider.label} (${provider.modelId}) ===")
            val providerDir = File(reportDir, provider.slug).apply { mkdirs() }

            for (prompt in TEST_PROMPTS) {
                val result = runPrompt(requests, provider, prompt, providerDir)
                allResults.add(result)
                val marker = when (result.status) {
                    Status.SUCCESS -> "OK  "
                    Status.PARTIAL -> "WARN"
                    Status.NO_UI -> "NOUI"
                    Status.PARSE_ERROR -> "ERR "
                    Status.API_ERROR -> "API "
                }
                println("  [$marker] ${prompt.slug}: ${result.summary}")
            }
        }

        writeReport(reportDir, allResults, providers)
        printSummary(allResults, providers)

        val minRate = System.getenv("KAI_MIN_SUCCESS_RATE")?.toDoubleOrNull() ?: 0.0
        if (minRate > 0.0) {
            val rate = allResults.count { it.status == Status.SUCCESS }.toDouble() / allResults.size
            check(rate >= minRate) {
                "Success rate %.1f%% is below required %.1f%%".format(rate * 100, minRate * 100)
            }
        }
    }

    /**
     * Offline re-parse of responses saved by a previous run of the integration test.
     *
     * Walks every `.raw.txt` file under the report directory, feeds each file through
     * `KaiUiParser.parse()`, and reports which ones still fail. Free, fast, and needs no
     * API keys — use this while iterating on parser fixes or prompt tweaks.
     *
     * Skips silently if no saved responses are found. Set `KAI_REPLAY_STRICT=1` to make
     * the test fail when any previously-failing response still fails to parse cleanly.
     */
    @Test
    fun `replay saved responses`() {
        val reportDir = File(System.getenv("KAI_REPORT_DIR") ?: "build/reports/kaiui-integration")
        if (!reportDir.isDirectory) {
            println("[KaiUiValidationTest] Replay skipped — no report directory at ${reportDir.absolutePath}")
            return
        }
        val rawFiles = reportDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".raw.txt") }
            .toList()
            .sortedBy { it.absolutePath }
        if (rawFiles.isEmpty()) {
            println("[KaiUiValidationTest] Replay skipped — no .raw.txt files under ${reportDir.absolutePath}")
            println("    Run the online test first with KAI_INTEGRATION=1 and at least one provider key.")
            return
        }

        println("\n[KaiUiValidationTest] Replaying ${rawFiles.size} saved responses from ${reportDir.absolutePath}")
        var success = 0
        var partial = 0
        var noUi = 0
        var parseError = 0
        val failures = mutableListOf<Pair<File, String>>()

        for (file in rawFiles) {
            val raw = file.readText()
            val segments = KaiUiParser.parse(raw)
            val ui = segments.filterIsInstance<KaiUiParser.UiSegment>().size
            val err = segments.filterIsInstance<KaiUiParser.ErrorSegment>().size
            val hasFence = KaiUiParser.containsUiBlocks(raw)
            val status = when {
                !hasFence && ui == 0 && err == 0 -> "NO_UI"
                err > 0 && ui == 0 -> "PARSE_ERROR"
                err > 0 -> "PARTIAL"
                ui > 0 -> "SUCCESS"
                else -> "NO_UI"
            }
            val rel = file.relativeTo(reportDir).path
            val marker = status.padEnd(11)
            when (status) {
                "SUCCESS" -> {
                    success++
                    println("  [$marker] $rel ($ui ui)")
                }

                "PARTIAL" -> {
                    partial++
                    failures += file to "PARTIAL: $ui ui, $err err"
                    println("  [$marker] $rel ($ui ui, $err err)")
                }

                "NO_UI" -> {
                    noUi++
                    failures += file to "NO_UI"
                    println("  [$marker] $rel")
                }

                "PARSE_ERROR" -> {
                    parseError++
                    failures += file to "PARSE_ERROR: $err err"
                    println("  [$marker] $rel ($err err)")
                }
            }
        }

        println("\n[KaiUiValidationTest] Replay summary: $success ok, $partial partial, $noUi no-ui, $parseError parse-error  (total ${rawFiles.size})")

        val strict = System.getenv("KAI_REPLAY_STRICT") == "1"
        if (strict && failures.isNotEmpty()) {
            error(
                "Replay strict mode: ${failures.size} file(s) did not parse cleanly:\n" +
                    failures.joinToString("\n") { (f, why) -> "  - ${f.relativeTo(reportDir).path} ($why)" },
            )
        }
    }

    // region Provider discovery ----------------------------------------------------------------

    private data class ProviderSpec(
        val slug: String,
        val label: String,
        val service: Service,
        val modelId: String,
        val apiKey: String,
        val kind: Kind,
    ) {
        enum class Kind { OPENAI_COMPAT, GEMINI, ANTHROPIC }
    }

    private fun discoverProviders(): List<ProviderSpec> {
        val out = mutableListOf<ProviderSpec>()
        env("KAI_OPENAI_KEY")?.let {
            out += ProviderSpec(
                "openai",
                "OpenAI",
                Service.OpenAI,
                env("KAI_OPENAI_MODEL") ?: "gpt-4o-mini",
                it,
                ProviderSpec.Kind.OPENAI_COMPAT,
            )
        }
        env("KAI_ANTHROPIC_KEY")?.let {
            out += ProviderSpec(
                "anthropic",
                "Anthropic",
                Service.Anthropic,
                env("KAI_ANTHROPIC_MODEL") ?: "claude-3-5-haiku-latest",
                it,
                ProviderSpec.Kind.ANTHROPIC,
            )
        }
        env("KAI_GEMINI_KEY")?.let {
            out += ProviderSpec(
                "gemini",
                "Gemini",
                Service.Gemini,
                env("KAI_GEMINI_MODEL") ?: "gemini-2.0-flash",
                it,
                ProviderSpec.Kind.GEMINI,
            )
        }
        env("KAI_MISTRAL_KEY")?.let {
            out += ProviderSpec(
                "mistral",
                "Mistral",
                Service.Mistral,
                env("KAI_MISTRAL_MODEL") ?: "mistral-small-latest",
                it,
                ProviderSpec.Kind.OPENAI_COMPAT,
            )
        }
        env("KAI_GROQ_KEY")?.let {
            out += ProviderSpec(
                "groq",
                "Groq",
                Service.Groq,
                env("KAI_GROQ_MODEL") ?: "llama-3.3-70b-versatile",
                it,
                ProviderSpec.Kind.OPENAI_COMPAT,
            )
        }
        env("KAI_OPENROUTER_KEY")?.let {
            out += ProviderSpec(
                "openrouter",
                "OpenRouter",
                Service.OpenRouter,
                env("KAI_OPENROUTER_MODEL") ?: "openai/gpt-4o-mini",
                it,
                ProviderSpec.Kind.OPENAI_COMPAT,
            )
        }
        env("KAI_DEEPSEEK_KEY")?.let {
            out += ProviderSpec(
                "deepseek",
                "DeepSeek",
                Service.DeepSeek,
                env("KAI_DEEPSEEK_MODEL") ?: "deepseek-chat",
                it,
                ProviderSpec.Kind.OPENAI_COMPAT,
            )
        }
        env("KAI_XAI_KEY")?.let {
            out += ProviderSpec(
                "xai",
                "xAI",
                Service.XAI,
                env("KAI_XAI_MODEL") ?: "grok-2-latest",
                it,
                ProviderSpec.Kind.OPENAI_COMPAT,
            )
        }
        env("KAI_CEREBRAS_KEY")?.let {
            out += ProviderSpec(
                "cerebras",
                "Cerebras",
                Service.Cerebras,
                env("KAI_CEREBRAS_MODEL") ?: "llama-3.3-70b",
                it,
                ProviderSpec.Kind.OPENAI_COMPAT,
            )
        }
        env("KAI_MOONSHOT_KEY")?.let {
            out += ProviderSpec(
                "moonshot",
                "Moonshot",
                Service.Moonshot,
                env("KAI_MOONSHOT_MODEL") ?: "moonshot-v1-8k",
                it,
                ProviderSpec.Kind.OPENAI_COMPAT,
            )
        }
        env("KAI_TOGETHER_KEY")?.let {
            out += ProviderSpec(
                "together",
                "Together",
                Service.Together,
                env("KAI_TOGETHER_MODEL") ?: "meta-llama/Llama-3.3-70B-Instruct-Turbo",
                it,
                ProviderSpec.Kind.OPENAI_COMPAT,
            )
        }
        env("KAI_MISTRAL_FT_KEY")?.let {
            out += ProviderSpec(
                "mistral-ft",
                "Mistral Fine-tuned",
                Service.Mistral,
                env("KAI_MISTRAL_FT_MODEL") ?: "ft:open-mistral-7b:latest",
                it,
                ProviderSpec.Kind.OPENAI_COMPAT,
            )
        }
        return out
    }

    private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

    // endregion

    // region Prompt execution ------------------------------------------------------------------

    private data class TestPrompt(
        val slug: String,
        val description: String,
        val userMessage: String,
        val mode: Mode,
    ) {
        enum class Mode { DYNAMIC_UI, INTERACTIVE }
    }

    private enum class Status { SUCCESS, PARTIAL, NO_UI, PARSE_ERROR, API_ERROR }

    private data class PromptResult(
        val provider: ProviderSpec,
        val prompt: TestPrompt,
        val status: Status,
        val summary: String,
        val uiSegmentCount: Int,
        val errorSegmentCount: Int,
        val rawResponse: String,
    )

    private fun runPrompt(
        requests: Requests,
        provider: ProviderSpec,
        prompt: TestPrompt,
        providerDir: File,
    ): PromptResult {
        val systemPrompt = buildSystemPrompt(prompt.mode)
        val raw = try {
            callApi(requests, provider, systemPrompt, prompt.userMessage)
        } catch (e: Exception) {
            val result = PromptResult(
                provider,
                prompt,
                Status.API_ERROR,
                "API error: ${e::class.simpleName}: ${e.message?.take(200)}",
                0,
                0,
                "",
            )
            File(providerDir, "${prompt.slug}.error.txt").writeText(
                "API error: ${e::class.qualifiedName}\n${e.message}\n\n${e.stackTraceToString()}",
            )
            return result
        }

        File(providerDir, "${prompt.slug}.raw.txt").writeText(raw)

        val segments = KaiUiParser.parse(raw)
        val uiSegments = segments.filterIsInstance<KaiUiParser.UiSegment>()
        val errorSegments = segments.filterIsInstance<KaiUiParser.ErrorSegment>()
        val hasFence = KaiUiParser.containsUiBlocks(raw)

        val status = when {
            !hasFence && uiSegments.isEmpty() && errorSegments.isEmpty() -> Status.NO_UI
            errorSegments.isNotEmpty() && uiSegments.isEmpty() -> Status.PARSE_ERROR
            errorSegments.isNotEmpty() -> Status.PARTIAL
            uiSegments.isNotEmpty() -> Status.SUCCESS
            else -> Status.NO_UI
        }

        if (status != Status.SUCCESS) {
            val errorFile = File(providerDir, "${prompt.slug}.error.txt")
            errorFile.writeText(
                buildString {
                    appendLine("# Status: $status")
                    appendLine("# Prompt: ${prompt.description}")
                    appendLine("# User message: ${prompt.userMessage}")
                    appendLine("# UI segments: ${uiSegments.size}")
                    appendLine("# Error segments: ${errorSegments.size}")
                    appendLine("# containsUiBlocks: $hasFence")
                    appendLine()
                    appendLine("## Raw response")
                    appendLine(raw)
                    if (errorSegments.isNotEmpty()) {
                        appendLine()
                        appendLine("## Error segments (rawJson)")
                        errorSegments.forEachIndexed { i, seg ->
                            appendLine("### [$i]")
                            appendLine(seg.rawJson)
                        }
                    }
                },
            )
        }

        val summary = "${uiSegments.size} ui, ${errorSegments.size} err, ${raw.length} chars"
        return PromptResult(provider, prompt, status, summary, uiSegments.size, errorSegments.size, raw)
    }

    private fun callApi(
        requests: Requests,
        provider: ProviderSpec,
        systemPrompt: String,
        userMessage: String,
    ): String = runBlocking {
        val creds = ServiceCredentials(apiKey = provider.apiKey, modelId = provider.modelId)
        when (provider.kind) {
            ProviderSpec.Kind.OPENAI_COMPAT -> {
                val messages = listOf(
                    OpenAICompatibleChatRequestDto.Message(role = "system", content = JsonPrimitive(systemPrompt)),
                    OpenAICompatibleChatRequestDto.Message(role = "user", content = JsonPrimitive(userMessage)),
                )
                val resp = requests.openAICompatibleChat(provider.service, creds, messages).getOrThrow()
                resp.choices.firstOrNull()?.message?.effectiveContent.orEmpty()
            }

            ProviderSpec.Kind.GEMINI -> {
                val messages = listOf(
                    GeminiChatRequestDto.Content(
                        parts = listOf(GeminiChatRequestDto.Part(text = userMessage)),
                        role = "user",
                    ),
                )
                val resp = requests.geminiChat(creds, messages, systemInstruction = systemPrompt).getOrThrow()
                resp.extractText()
            }

            ProviderSpec.Kind.ANTHROPIC -> {
                val messages = listOf(
                    AnthropicChatRequestDto.Message(role = "user", content = JsonPrimitive(userMessage)),
                )
                val resp = requests.anthropicChat(creds, messages, systemInstruction = systemPrompt).getOrThrow()
                resp.extractText()
            }
        }
    }

    // endregion

    // region Reporting -------------------------------------------------------------------------

    private fun writeReport(reportDir: File, results: List<PromptResult>, providers: List<ProviderSpec>) {
        val report = buildString {
            appendLine("# kai-ui Integration Report")
            appendLine()
            appendLine("Generated: ${java.time.LocalDateTime.now()}")
            appendLine()
            appendLine("## Summary")
            appendLine()
            appendLine("| Provider | Model | OK | Partial | NoUI | ParseErr | ApiErr |")
            appendLine("|----------|-------|----|---------|------|----------|--------|")
            for (p in providers) {
                val r = results.filter { it.provider.slug == p.slug }
                appendLine(
                    "| ${p.label} | `${p.modelId}` | ${r.count { it.status == Status.SUCCESS }} | " +
                        "${r.count { it.status == Status.PARTIAL }} | ${r.count { it.status == Status.NO_UI }} | " +
                        "${r.count { it.status == Status.PARSE_ERROR }} | ${r.count { it.status == Status.API_ERROR }} |",
                )
            }
            appendLine()
            appendLine("Total prompts: ${results.size}  |  Success: ${results.count { it.status == Status.SUCCESS }}")
            appendLine()
            for (p in providers) {
                appendLine("## ${p.label} (`${p.modelId}`)")
                appendLine()
                appendLine("| Prompt | Status | Summary |")
                appendLine("|--------|--------|---------|")
                for (r in results.filter { it.provider.slug == p.slug }) {
                    val escaped = r.summary.replace("|", "\\|")
                    appendLine("| `${r.prompt.slug}` | ${r.status} | $escaped |")
                }
                appendLine()
                val failed = results.filter { it.provider.slug == p.slug && it.status != Status.SUCCESS }
                if (failed.isNotEmpty()) {
                    appendLine("### Failed prompts")
                    appendLine()
                    for (r in failed) {
                        appendLine("- `${r.prompt.slug}` (${r.status}): see `${p.slug}/${r.prompt.slug}.error.txt`")
                    }
                    appendLine()
                }
            }
        }
        File(reportDir, "report.md").writeText(report)
        println("\n[KaiUiValidationTest] Report written to: ${File(reportDir, "report.md").absolutePath}")
    }

    private fun printSummary(results: List<PromptResult>, providers: List<ProviderSpec>) {
        println("\n[KaiUiValidationTest] === SUMMARY ===")
        for (p in providers) {
            val r = results.filter { it.provider.slug == p.slug }
            val ok = r.count { it.status == Status.SUCCESS }
            println("  ${p.label.padEnd(12)} $ok/${r.size} success  (${p.modelId})")
        }
        val total = results.size
        val successes = results.count { it.status == Status.SUCCESS }
        println("  ${"TOTAL".padEnd(12)} $successes/$total success")
    }

    // endregion

    // region System prompt (mirror of RemoteDataRepository.getActiveSystemPrompt) --------------

    /**
     * Duplicates the kai-ui portion of the app's system prompt. Mirrors
     * `RemoteDataRepository.getActiveSystemPrompt()` lines 1417-1495 (as of 2026-04-05).
     * Keep in sync manually if the real prompt changes — there is no automated drift check.
     */
    private fun buildSystemPrompt(mode: TestPrompt.Mode): String = buildString {
        val dynamicUiOnly = mode == TestPrompt.Mode.DYNAMIC_UI
        if (dynamicUiOnly) {
            append("\n## Dynamic UI\n")
            append("You can enhance your chat responses with interactive UI elements using kai-ui blocks. ")
            append("Proactively use them whenever you need input from the user — don't just ask in plain text if a form, selector, or buttons would be more natural. ")
            append("For example, if the user asks you to help plan a trip, present destination options as buttons; if you need preferences, show a form; if presenting choices, use interactive cards. ")
            append("Use kai-ui whenever collecting data, offering choices, presenting structured information, or guiding multi-step workflows. ")
            append("You can mix kai-ui blocks with regular markdown text naturally — use markdown for explanations and kai-ui for interactive elements.\n\n")
        } else {
            append("\n## Interactive UI Mode (ACTIVE)\n")
            append("You are in full-screen interactive UI mode. The user ONLY sees rendered kai-ui components — they cannot see markdown, plain text, or anything outside a kai-ui fence.\n")
            append("Your ENTIRE response must be a single ```kai-ui code fence. No text before it, no text after it, no markdown. If you write anything outside the fence, the user will NOT see it.\n\n")
        }

        append("Format: wrap a JSON object in ```kai-ui fences.\n\n")
        append("Components: column, row, card, box, text, button, text_input, checkbox, switch, select, radio_group, slider, chip_group, table, list, divider, image, icon, code, progress, countdown, alert, tabs, accordion, quote, badge, stat, avatar.\n")
        append("- text: {\"type\":\"text\",\"value\":\"...\",\"style\":\"headline|title|body|caption\",\"bold\":true,\"color\":\"primary|secondary|error\"} — do NOT use markdown formatting (**, *, #, etc.) in text values; use the bold/italic/style properties instead\n")
        append("- button: {\"type\":\"button\",\"label\":\"...\",\"action\":{...},\"variant\":\"filled|outlined|text|tonal\"}\n")
        append("- text_input: {\"type\":\"text_input\",\"id\":\"...\",\"label\":\"...\",\"placeholder\":\"...\",\"value\":\"...\"}\n")
        append("- checkbox: {\"type\":\"checkbox\",\"id\":\"...\",\"label\":\"...\",\"checked\":false}\n")
        append("- switch: {\"type\":\"switch\",\"id\":\"...\",\"label\":\"...\",\"checked\":false}\n")
        append("- select: {\"type\":\"select\",\"id\":\"...\",\"label\":\"...\",\"options\":[\"A\",\"B\"],\"selected\":\"A\"}\n")
        append("- radio_group: {\"type\":\"radio_group\",\"id\":\"...\",\"label\":\"...\",\"options\":[\"A\",\"B\"],\"selected\":\"A\"}\n")
        append("- slider: {\"type\":\"slider\",\"id\":\"...\",\"label\":\"...\",\"value\":50,\"min\":0,\"max\":100,\"step\":10}\n")
        append("- chip_group: {\"type\":\"chip_group\",\"id\":\"...\",\"chips\":[{\"label\":\"Tag\",\"value\":\"tag\"}],\"selection\":\"single|multi|none\"}\n")
        append("- list: {\"type\":\"list\",\"items\":[...],\"ordered\":false}\n")
        append("- table: {\"type\":\"table\",\"headers\":[\"Col1\",\"Col2\"],\"rows\":[[\"a\",\"b\"]]}\n")
        append("- code: {\"type\":\"code\",\"code\":\"...\",\"language\":\"kotlin\"}\n")
        append("- progress: {\"type\":\"progress\",\"value\":0.5,\"label\":\"50%\"}\n")
        append("- countdown: {\"type\":\"countdown\",\"seconds\":300,\"label\":\"Time left\",\"action\":{\"type\":\"callback\",\"event\":\"timer_done\"}}\n")
        append("- alert: {\"type\":\"alert\",\"message\":\"...\",\"title\":\"...\",\"severity\":\"info|success|warning|error\"}\n")
        append("- tabs: {\"type\":\"tabs\",\"tabs\":[{\"label\":\"Tab 1\",\"children\":[...]},{\"label\":\"Tab 2\",\"children\":[...]}],\"selectedIndex\":0}\n")
        append("- accordion: {\"type\":\"accordion\",\"title\":\"...\",\"children\":[...],\"expanded\":false}\n")
        append("- box: {\"type\":\"box\",\"children\":[...],\"contentAlignment\":\"center\"}\n")
        append("- quote: {\"type\":\"quote\",\"text\":\"...\",\"source\":\"Author Name\"}\n")
        append("- badge: {\"type\":\"badge\",\"value\":\"3\",\"color\":\"primary|secondary|error\"}\n")
        append("- stat: {\"type\":\"stat\",\"value\":\"\$1,234\",\"label\":\"Revenue\",\"description\":\"12% increase\"}\n")
        append("- avatar: {\"type\":\"avatar\",\"name\":\"John Doe\",\"imageUrl\":\"https://...\",\"size\":40}\n\n")
        append("Actions (on buttons, countdown expiry):\n")
        append("- callback: {\"type\":\"callback\",\"event\":\"event_name\",\"data\":{\"key\":\"val\"},\"collectFrom\":[\"input_id1\",\"input_id2\"]}\n")
        append("- toggle: {\"type\":\"toggle\",\"targetId\":\"element_id\"}\n")
        append("- open_url: {\"type\":\"open_url\",\"url\":\"https://...\"}\n\n")
        append("- Form inputs only store state locally. Their values are ONLY sent when a button's collectFrom includes their id. Always pair form inputs with a submit button that collects from them.\n\n")

        if (dynamicUiOnly) {
            append("Layout tips:\n")
            append("- Put buttons INSIDE cards, directly below related content\n")
            append("- Use rows for groups of buttons or chips — rows wrap automatically\n")
            append("- Keep button labels short (1-3 words)\n\n")
            append("Example:\n```kai-ui\n{\"type\":\"column\",\"children\":[{\"type\":\"text\",\"value\":\"Your name?\",\"style\":\"title\"},{\"type\":\"text_input\",\"id\":\"name\",\"placeholder\":\"Enter name\"},{\"type\":\"button\",\"label\":\"Submit\",\"action\":{\"type\":\"callback\",\"event\":\"submit\",\"collectFrom\":[\"name\"]}}]}\n```\n")
        } else {
            append("Rules:\n")
            append("- Each response is a COMPLETE screen layout. Include all content and actions in one kai-ui block.\n")
            append("- Always include clear primary action buttons so the user can proceed.\n")
            append("- Every screen MUST have at least one interactive element with a callback action.\n")
            append("- Use headline text for screen titles. Structure screens with cards for grouping related content.\n")
            append("- Do NOT include back buttons, navigation bars, or any navigation controls.\n")
            append("Layout:\n")
            append("- Put buttons INSIDE cards, directly below related content\n")
            append("- Use rows for groups of buttons or chips — rows wrap automatically\n")
            append("- Keep button labels short (1-3 words)\n")
            append("- Use columns for vertical flow. Use the full component set.\n\n")
            append("Limitations — respect these strictly:\n")
            append("- The UI is static once rendered. NEVER show loading, fetching, or verifying states.\n")
            append("- Each screen is independent.\n")
            append("- Only use the exact components and properties defined above.\n")
            append("- Start with simple, clean layouts.\n\n")
            append("Example:\n```kai-ui\n{\"type\":\"column\",\"children\":[{\"type\":\"text\",\"value\":\"Welcome\",\"style\":\"headline\"},{\"type\":\"card\",\"children\":[{\"type\":\"text\",\"value\":\"What would you like to do?\",\"style\":\"title\"},{\"type\":\"button\",\"label\":\"Get Started\",\"action\":{\"type\":\"callback\",\"event\":\"get_started\"}}]}]}\n```\n")
        }
    }

    // endregion

    // region Test prompts ----------------------------------------------------------------------

    companion object {
        private val TEST_PROMPTS = listOf(
            TestPrompt(
                "simple-question",
                "Simple question expecting a text response with kai-ui elements",
                "Ask me what my favorite color is using a form input and submit button.",
                TestPrompt.Mode.DYNAMIC_UI,
            ),
            TestPrompt(
                "quiz-buttons",
                "Multiple-choice quiz using a row of buttons",
                "Quiz me on the capitals of Europe. Give me one question with 4 button answers.",
                TestPrompt.Mode.DYNAMIC_UI,
            ),
            TestPrompt(
                "multi-field-form",
                "Form with multiple input types (text, select, slider, checkbox)",
                "I want to sign up for a newsletter. Show me a form with name, email, frequency selector, topics checkboxes, and a submit button.",
                TestPrompt.Mode.DYNAMIC_UI,
            ),
            TestPrompt(
                "table-data",
                "Data table with headers and rows",
                "Show me a comparison table of the top 3 programming languages with columns Name, Year, Paradigm.",
                TestPrompt.Mode.DYNAMIC_UI,
            ),
            TestPrompt(
                "tabs-layout",
                "Tabs with different content per tab",
                "Create a product info screen with three tabs: Overview, Specs, Reviews. Use tabs component.",
                TestPrompt.Mode.INTERACTIVE,
            ),
            TestPrompt(
                "dashboard-stats",
                "Dashboard with stat cards and a chart-like display",
                "Build a simple dashboard screen showing 3 stat cards (users, revenue, growth) and a list of recent activity with a refresh button.",
                TestPrompt.Mode.INTERACTIVE,
            ),
            TestPrompt(
                "list-items",
                "Bulleted list and action buttons",
                "Show me a todo list with 5 items and buttons to mark all done or add new.",
                TestPrompt.Mode.DYNAMIC_UI,
            ),
            TestPrompt(
                "chip-selection",
                "Chip group for tag selection",
                "Help me pick pizza toppings. Show a multi-select chip group with 8 options and an order button.",
                TestPrompt.Mode.DYNAMIC_UI,
            ),
            TestPrompt(
                "mixed-markdown",
                "Response that mixes markdown explanation with kai-ui interactive block",
                "Explain what a linked list is in a paragraph, then show me a button to see an example.",
                TestPrompt.Mode.DYNAMIC_UI,
            ),
            TestPrompt(
                "quote-and-stat",
                "Display components: quote and stat",
                "Show me an inspirational quote about learning programming and a stat showing there are 4 million developers in Germany.",
                TestPrompt.Mode.DYNAMIC_UI,
            ),
            TestPrompt(
                "nested-cards",
                "Nested layouts — card containing row containing buttons",
                "Build a game menu screen with a title, a card per game mode (Classic, Timed, Endless), each card with a Play button.",
                TestPrompt.Mode.INTERACTIVE,
            ),
            TestPrompt(
                "complex-screen",
                "Complex screen that stresses multiple components at once",
                "Design a booking screen for a restaurant reservation: date picker (use select for date and time), party size slider, special requests text input, dietary restrictions chips, and a confirm button.",
                TestPrompt.Mode.INTERACTIVE,
            ),
        )
    }

    // endregion
}
