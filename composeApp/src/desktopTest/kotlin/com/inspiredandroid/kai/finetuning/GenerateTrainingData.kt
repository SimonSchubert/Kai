package com.inspiredandroid.kai.finetuning

import com.inspiredandroid.kai.ui.markdown.KaiUiBlock
import com.inspiredandroid.kai.ui.markdown.KaiUiError
import com.inspiredandroid.kai.ui.markdown.parseMarkdown
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test

/**
 * Generates training data for Mistral fine-tuning from golden examples and saved integration
 * test responses.
 *
 * ## How to run
 *
 * ```
 * ./gradlew :composeApp:desktopTest --tests "*GenerateTrainingData*" --info
 * ```
 *
 * ## Output
 *
 * Writes JSONL files to `tools/finetuning/output/`:
 * - `training_data.jsonl` — 90% of examples
 * - `validation_data.jsonl` — 10% of examples
 *
 * ## Sources
 *
 * 1. **Golden examples** in `tools/finetuning/golden/` (`.md` files) — hand-crafted ideal responses
 * 2. **Saved integration test responses** in `build/reports/kaiui-integration/` — curated
 *    successful responses from strong LLM providers (run KaiUiValidationTest first)
 */
class GenerateTrainingData {

    @Serializable
    private data class Message(val role: String, val content: String)

    @Serializable
    private data class TrainingExample(val messages: List<Message>)

    private val json = Json { prettyPrint = false }

    @Test
    fun `generate training data`() {
        val projectRoot = findProjectRoot()
        val goldenDir = File(projectRoot, "tools/finetuning/golden")
        val outputDir = File(projectRoot, "tools/finetuning/output").apply { mkdirs() }
        val reportDir = File(projectRoot, "build/reports/kaiui-integration")

        val examples = mutableListOf<TrainingExample>()

        // Source 1: Golden examples
        val goldenExamples = loadGoldenExamples(goldenDir)
        println("[GenerateTrainingData] Loaded ${goldenExamples.size} golden examples")
        examples.addAll(goldenExamples)

        // Source 2: Curated integration test responses
        if (reportDir.isDirectory) {
            val curatedExamples = loadCuratedResponses(reportDir)
            println("[GenerateTrainingData] Loaded ${curatedExamples.size} curated responses from integration tests")
            examples.addAll(curatedExamples)
        } else {
            println("[GenerateTrainingData] No integration test reports found at ${reportDir.absolutePath}")
            println("    Run KaiUiValidationTest first to collect LLM responses for curation.")
        }

        // Validate all examples
        val valid = mutableListOf<TrainingExample>()
        var invalidCount = 0
        for (example in examples) {
            val assistantContent = example.messages.last { it.role == "assistant" }.content
            if (validateKaiUi(assistantContent)) {
                valid.add(example)
            } else {
                invalidCount++
                val userMsg = example.messages.firstOrNull { it.role == "user" }?.content?.take(80) ?: "?"
                println("  [SKIP] Invalid kai-ui in example: $userMsg")
            }
        }
        println("[GenerateTrainingData] Valid: ${valid.size}, Invalid: $invalidCount")

        if (valid.isEmpty()) {
            println("[GenerateTrainingData] No valid examples to write. Aborting.")
            return
        }

        // Shuffle and split 90/10
        val shuffled = valid.shuffled()
        val splitIndex = (shuffled.size * 0.9).toInt().coerceAtLeast(1)
        val training = shuffled.take(splitIndex)
        val validation = shuffled.drop(splitIndex)

        // Write JSONL
        writeJsonl(File(outputDir, "training_data.jsonl"), training)
        writeJsonl(File(outputDir, "validation_data.jsonl"), validation)

        println("[GenerateTrainingData] Output:")
        println("  Training:   ${training.size} examples → ${File(outputDir, "training_data.jsonl").absolutePath}")
        println("  Validation: ${validation.size} examples → ${File(outputDir, "validation_data.jsonl").absolutePath}")
    }

    // region Golden examples ------------------------------------------------------------------

    /**
     * Parses golden example markdown files from `tools/finetuning/golden/`.
     *
     * Expected format:
     * ```
     * ---
     * mode: dynamic_ui|interactive
     * slug: example-name
     * description: What this example demonstrates
     * ---
     *
     * ## User
     * The user message...
     *
     * ## Assistant
     * The ideal assistant response...
     * ```
     */
    private fun loadGoldenExamples(goldenDir: File): List<TrainingExample> {
        if (!goldenDir.isDirectory) {
            println("[GenerateTrainingData] Golden examples directory not found: ${goldenDir.absolutePath}")
            return emptyList()
        }

        return goldenDir.listFiles()
            ?.filter { it.extension == "md" }
            ?.sorted()
            ?.mapNotNull { file -> parseGoldenFile(file) }
            ?: emptyList()
    }

    private fun parseGoldenFile(file: File): TrainingExample? {
        val content = file.readText()

        // Parse frontmatter
        val frontmatterMatch = Regex("^---\\n(.*?)\\n---", RegexOption.DOT_MATCHES_ALL).find(content)
        if (frontmatterMatch == null) {
            println("  [WARN] No frontmatter in ${file.name}")
            return null
        }
        val frontmatter = frontmatterMatch.groupValues[1]
        val mode = Regex("mode:\\s*(\\w+)").find(frontmatter)?.groupValues?.get(1) ?: "dynamic_ui"

        // Parse user and assistant sections
        val body = content.substring(frontmatterMatch.range.last + 1).trim()
        val userMatch = Regex("## User\\n(.*?)(?=\\n## Assistant)", RegexOption.DOT_MATCHES_ALL).find(body)
        val assistantMatch = Regex("## Assistant\\n(.*?)$", RegexOption.DOT_MATCHES_ALL).find(body)

        if (userMatch == null || assistantMatch == null) {
            println("  [WARN] Missing User/Assistant sections in ${file.name}")
            return null
        }

        val userMessage = userMatch.groupValues[1].trim()
        val assistantResponse = assistantMatch.groupValues[1].trim()

        val systemPrompt = buildSystemPrompt(
            if (mode == "interactive") Mode.INTERACTIVE else Mode.DYNAMIC_UI,
        )

        return TrainingExample(
            messages = listOf(
                Message(role = "system", content = systemPrompt),
                Message(role = "user", content = userMessage),
                Message(role = "assistant", content = assistantResponse),
            ),
        )
    }

    // endregion

    // region Curated integration test responses -----------------------------------------------

    /**
     * Loads successful LLM responses from KaiUiValidationTest report directory.
     * Only includes responses that parse cleanly (no error segments).
     */
    private fun loadCuratedResponses(reportDir: File): List<TrainingExample> {
        val examples = mutableListOf<TrainingExample>()

        // Map slug → prompt from the standard test prompts
        val promptMap = mapOf(
            "simple-question" to Pair("Ask me what my favorite color is using a form input and submit button.", Mode.DYNAMIC_UI),
            "quiz-buttons" to Pair("Quiz me on the capitals of Europe. Give me one question with 4 button answers.", Mode.DYNAMIC_UI),
            "multi-field-form" to Pair("I want to sign up for a newsletter. Show me a form with name, email, frequency selector, topics checkboxes, and a submit button.", Mode.DYNAMIC_UI),
            "table-data" to Pair("Show me a comparison table of the top 3 programming languages with columns Name, Year, Paradigm.", Mode.DYNAMIC_UI),
            "tabs-layout" to Pair("Create a product info screen with three tabs: Overview, Specs, Reviews. Use tabs component.", Mode.INTERACTIVE),
            "dashboard-stats" to Pair("Build a simple dashboard screen showing 3 stat cards (users, revenue, growth) and a list of recent activity with a refresh button.", Mode.INTERACTIVE),
            "list-items" to Pair("Show me a todo list with 5 items and buttons to mark all done or add new.", Mode.DYNAMIC_UI),
            "chip-selection" to Pair("Help me pick pizza toppings. Show a multi-select chip group with 8 options and an order button.", Mode.DYNAMIC_UI),
            "mixed-markdown" to Pair("Explain what a linked list is in a paragraph, then show me a button to see an example.", Mode.DYNAMIC_UI),
            "quote-and-stat" to Pair("Show me an inspirational quote about learning programming and a stat showing there are 4 million developers in Germany.", Mode.DYNAMIC_UI),
            "nested-cards" to Pair("Build a game menu screen with a title, a card per game mode (Classic, Timed, Endless), each card with a Play button.", Mode.INTERACTIVE),
            "complex-screen" to Pair("Design a booking screen for a restaurant reservation: date picker (use select for date and time), party size slider, special requests text input, dietary restrictions chips, and a confirm button.", Mode.INTERACTIVE),
        )

        val rawFiles = reportDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".raw.txt") }
            .toList()

        for (file in rawFiles) {
            val slug = file.nameWithoutExtension.removeSuffix(".raw")
            val (userMessage, mode) = promptMap[slug] ?: continue

            val raw = file.readText()
            val blocks = parseMarkdown(raw).blocks
            val hasUi = blocks.any { it is KaiUiBlock }
            val hasError = blocks.any { it is KaiUiError }

            // Only include clean successes
            if (hasUi && !hasError) {
                val systemPrompt = buildSystemPrompt(mode)
                examples.add(
                    TrainingExample(
                        messages = listOf(
                            Message(role = "system", content = systemPrompt),
                            Message(role = "user", content = userMessage),
                            Message(role = "assistant", content = raw),
                        ),
                    ),
                )
            }
        }

        return examples
    }

    // endregion

    // region Validation -----------------------------------------------------------------------

    /**
     * Validates that an assistant response contains at least one valid kai-ui block
     * with no parse errors.
     */
    private fun validateKaiUi(response: String): Boolean {
        val blocks = parseMarkdown(response).blocks
        val hasUi = blocks.any { it is KaiUiBlock }
        val hasError = blocks.any { it is KaiUiError }
        // Allow responses that don't contain kai-ui at all (mixed markdown-only for dynamic mode)
        if (!hasUi && !hasError) return false
        return hasUi && !hasError
    }

    // endregion

    // region System prompt (mirrors KaiUiValidationTest) --------------------------------------

    private enum class Mode { DYNAMIC_UI, INTERACTIVE }

    /**
     * Builds the kai-ui system prompt. Mirrors [KaiUiValidationTest.buildSystemPrompt].
     */
    private fun buildSystemPrompt(mode: Mode): String = buildString {
        val dynamicUiOnly = mode == Mode.DYNAMIC_UI
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

    // region Utilities ------------------------------------------------------------------------

    private fun writeJsonl(file: File, examples: List<TrainingExample>) {
        file.bufferedWriter().use { writer ->
            for (example in examples) {
                writer.write(json.encodeToString(example))
                writer.newLine()
            }
        }
    }

    private fun findProjectRoot(): File {
        var dir = File(System.getProperty("user.dir"))
        while (dir.parentFile != null) {
            if (File(dir, "settings.gradle.kts").exists() && File(dir, "composeApp").exists()) {
                return dir
            }
            dir = dir.parentFile
        }
        // Fallback: assume CWD is the project root
        return File(System.getProperty("user.dir"))
    }

    // endregion
}
