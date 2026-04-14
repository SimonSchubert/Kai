// Pure builders for the chat system prompt. Every input is passed explicitly — no DI,
// no suspend, no resource loading, no Clock — so tests can call `buildChatSystemPrompt`
// directly with hand-crafted inputs. Section composition is controlled by
// `SystemPromptVariant`; each `if (variant == ...)` block is the single source of
// truth for where a section belongs. No post-hoc regex stripping.

package com.inspiredandroid.kai.data

/**
 * Identifies which flavour of chat system prompt to build. Public because it's part of
 * [DataRepository.getActiveSystemPrompt]'s signature — callers pick the variant based on
 * whether they're dispatching to a remote or on-device service.
 */
enum class SystemPromptVariant {
    /** Full chat prompt for remote services — every section available. */
    CHAT_REMOTE,

    /**
     * Trimmed prompt for on-device LiteRT plain chat — only sections a 2-4B Gemma can
     * coherently attend to. Soul + basic memory guidance + runtime `## Context`. Drops
     * memory category dumps, scheduled tasks, Structured Learning guidance, and kai-ui
     * modes (the latter is also hidden from the UI for on-device services — see
     * `ChatScreen.kt`).
     */
    CHAT_LOCAL,
}

/** Runtime state rendered into the `## Context` section. */
internal data class ChatPromptRuntimeContext(
    val nowIsoString: String,
    val platform: String,
    val modelId: String,
    val providerName: String,
)

/** Which kai-ui section, if any, to render. */
internal enum class ChatPromptUiMode { NONE, DYNAMIC_UI, INTERACTIVE_UI }

/**
 * Total character budget for the memory category sections (`## Your Memories`, etc.)
 * when building the `CHAT_LOCAL` variant. Memories are appended in order — general →
 * preferences → learnings → errors — and entries that would push the combined size
 * past this budget are dropped silently at the entry boundary. Set generously enough
 * to cover a typical user's memory set (~20-40 entries) without starving the model's
 * attention budget on a 16K-context local model.
 */
private const val LOCAL_MEMORY_BUDGET_CHARS = 2_000

/**
 * Advanced memory guidance — references `memory_learn` (not in `LOCAL_TOOL_ALLOWLIST`)
 * and `memory_reinforce`. Only composed into the `CHAT_REMOTE` variant; the on-device
 * variant omits it entirely because small Gemma models can't reliably call
 * `memory_learn` (4 params + enum).
 */
internal const val DEFAULT_STRUCTURED_LEARNING_SECTION =
    "## Structured Learning\n" +
        "Use memory_learn to record categorized learnings:\n" +
        "- Record user corrections and preferences as PREFERENCE entries\n" +
        "- Record things that worked well as LEARNING entries\n" +
        "- Record error resolutions as ERROR entries\n" +
        "Use memory_reinforce when a stored learning produced a good outcome."

/**
 * Composes the full chat system prompt for the given [variant].
 *
 * Returns an empty string when there is literally nothing to render (which the caller
 * should map to `null`). All inputs are passed explicitly — memory lists are pre-split
 * by the caller so this function doesn't touch the `MemoryStore`.
 */
internal fun buildChatSystemPrompt(
    variant: SystemPromptVariant,
    soul: String,
    memoryInstructions: String?,
    generalMemories: List<MemoryEntry>,
    preferenceMemories: List<MemoryEntry>,
    learningMemories: List<MemoryEntry>,
    errorMemories: List<MemoryEntry>,
    pendingTasks: List<ScheduledTask>,
    runtime: ChatPromptRuntimeContext,
    uiMode: ChatPromptUiMode,
): String = buildString {
    append(soul)

    if (!memoryInstructions.isNullOrEmpty()) {
        if (isNotEmpty()) append("\n\n")
        append(memoryInstructions)
    }

    // Structured Learning is remote-only — references memory_learn, not in the local allowlist.
    if (variant == SystemPromptVariant.CHAT_REMOTE) {
        if (isNotEmpty()) append("\n\n")
        append(DEFAULT_STRUCTURED_LEARNING_SECTION)
    }

    // Memory category sections are emitted for BOTH variants. memory_store / memory_forget /
    // memory_reinforce are in the local allowlist, and memories may have been learned via
    // remote models — the local model should be able to reference them. A char-count budget
    // (unlimited for remote; [LOCAL_MEMORY_BUDGET_CHARS] for local) prevents runaway growth
    // on small on-device context windows.
    val memoryBudget = when (variant) {
        SystemPromptVariant.CHAT_REMOTE -> Int.MAX_VALUE
        SystemPromptVariant.CHAT_LOCAL -> LOCAL_MEMORY_BUDGET_CHARS
    }
    var remaining = memoryBudget
    remaining = appendMemoryCategorySection("Your Memories", generalMemories, withHitCount = false, remaining)
    remaining = appendMemoryCategorySection("User Preferences", preferenceMemories, withHitCount = false, remaining)
    remaining = appendMemoryCategorySection("Learnings", learningMemories, withHitCount = true, remaining)
    appendMemoryCategorySection("Known Issues & Resolutions", errorMemories, withHitCount = false, remaining)

    // Scheduled Tasks stays remote-only — scheduling tools aren't in the local allowlist.
    if (variant == SystemPromptVariant.CHAT_REMOTE && pendingTasks.isNotEmpty()) {
        appendScheduledTasksSection(pendingTasks)
    }

    appendContextSection(runtime)

    if (variant == SystemPromptVariant.CHAT_REMOTE) {
        when (uiMode) {
            ChatPromptUiMode.DYNAMIC_UI -> appendDynamicUiSection()
            ChatPromptUiMode.INTERACTIVE_UI -> appendInteractiveUiSection()
            ChatPromptUiMode.NONE -> {}
        }
    }
}

/**
 * Appends a memory category section subject to a char budget. Entries are added one by
 * one until the next entry would push the section past [remainingBudget]; remaining
 * entries are dropped silently. If no entries fit, the header is not emitted either.
 *
 * Returns the remaining budget after emission so the caller can thread it through the
 * next category section. [Int.MAX_VALUE] means unlimited (no truncation).
 */
private fun StringBuilder.appendMemoryCategorySection(
    header: String,
    entries: List<MemoryEntry>,
    withHitCount: Boolean,
    remainingBudget: Int,
): Int {
    if (entries.isEmpty() || remainingBudget <= 0) return remainingBudget

    val section = StringBuilder()
    section.append("\n\n## ").append(header).append("\n")
    val headerLen = section.length
    var included = 0
    for (entry in entries) {
        val entryStart = section.length
        section.append("- **").append(entry.key).append("**")
        if (withHitCount) {
            section.append(" (reinforced ").append(entry.hitCount).append("x)")
        }
        section.append(": ").append(entry.content).append('\n')
        if (section.length > remainingBudget) {
            // This entry pushed us over. Revert it and stop.
            section.setLength(entryStart)
            break
        }
        included++
    }
    if (included == 0) {
        // Not even the first entry fit — don't emit the header alone.
        return remainingBudget
    }
    append(section)
    return (remainingBudget - section.length).coerceAtLeast(0)
}

private fun StringBuilder.appendScheduledTasksSection(pendingTasks: List<ScheduledTask>) {
    append("\n\n## Scheduled Tasks\n")
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

private fun StringBuilder.appendContextSection(runtime: ChatPromptRuntimeContext) {
    append("\n\n## Context\n")
    append("- Date: ")
    append(runtime.nowIsoString)
    append('\n')
    append("- Platform: ")
    append(runtime.platform)
    append('\n')
    append("- Model: ")
    append(runtime.modelId)
    append('\n')
    append("- Provider: ")
    append(runtime.providerName)
    append('\n')
}

private fun StringBuilder.appendDynamicUiSection() {
    append("\n## Dynamic UI\n")
    append("You can enhance your chat responses with interactive UI elements using kai-ui blocks. ")
    append("Proactively use them whenever you need input from the user — don't just ask in plain text if a form, selector, or buttons would be more natural. ")
    append("For example, if the user asks you to help plan a trip, present destination options as buttons; if you need preferences, show a form; if presenting choices, use interactive cards. ")
    append("Use kai-ui whenever collecting data, offering choices, presenting structured information, or guiding multi-step workflows. ")
    append("You can mix kai-ui blocks with regular markdown text naturally — use markdown for explanations and kai-ui for interactive elements.\n\n")
    append(KAI_UI_COMPONENT_CATALOG)
    append("Layout tips:\n")
    append("- Put buttons INSIDE cards, directly below related content — never group all buttons separately at the bottom\n")
    append("- Use rows for groups of buttons or chips — rows wrap automatically, so any number of items is fine\n")
    append("- Keep button labels short (1-3 words)\n\n")
    append("Example:\n```kai-ui\n{\"type\":\"column\",\"children\":[{\"type\":\"text\",\"value\":\"Your name?\",\"style\":\"title\"},{\"type\":\"text_input\",\"id\":\"name\",\"placeholder\":\"Enter name\"},{\"type\":\"button\",\"label\":\"Submit\",\"action\":{\"type\":\"callback\",\"event\":\"submit\",\"collectFrom\":[\"name\"]}}]}\n```\n")
}

private fun StringBuilder.appendInteractiveUiSection() {
    append("\n## Interactive UI Mode (ACTIVE)\n")
    append("You are in full-screen interactive UI mode. The user ONLY sees rendered kai-ui components — they cannot see markdown, plain text, or anything outside a kai-ui fence.\n")
    append("Your ENTIRE response must be a single ```kai-ui code fence. No text before it, no text after it, no markdown. If you write anything outside the fence, the user will NOT see it.\n\n")
    append(KAI_UI_COMPONENT_CATALOG)
    append("Rules:\n")
    append("- Each response is a COMPLETE screen layout. Include all content and actions in one kai-ui block.\n")
    append("- Always include clear primary action buttons so the user can proceed.\n")
    append("- Every screen MUST have at least one interactive element with a callback action (button, countdown with expiry action, etc.). A screen without any callback is a dead end the user cannot proceed from.\n")
    append("- Use headline text for screen titles. Structure screens with cards for grouping related content.\n")
    append("- Use descriptive callback events (e.g., \"select_destination\", \"submit_form\") so you understand what the user selected.\n")
    append("- Do NOT include back buttons, navigation bars, or any navigation controls. The app provides a back button and close button in the toolbar. The user can also type instructions in a text field below your UI.\n")
    append("Layout:\n")
    append("- Put buttons INSIDE cards, directly below related content — never group all buttons separately at the bottom\n")
    append("- Use rows for groups of buttons or chips — rows wrap automatically, so any number of items is fine\n")
    append("- Keep button labels short (1-3 words)\n")
    append("- Use columns for vertical flow. Use the full component set: tabs, accordion, alerts, progress, chips, icons, etc.\n\n")
    append("Limitations — respect these strictly:\n")
    append("- The UI is static once rendered. NEVER show loading, fetching, or verifying states. You cannot fetch data or run operations asynchronously. Present all content immediately.\n")
    append("- Never use indeterminate progress (progress without a value) or text like \"Loading...\", \"Fetching...\", \"Verifying...\" as if something will happen later — nothing will.\n")
    append("- Each screen is independent. Only conversation history carries state between screens — there is no client-side state persistence, no session storage, no variables that survive across responses.\n")
    append("- Do not attempt to build multi-screen stateful applications (e.g., shopping carts that accumulate items, dashboards that refresh). Each response is a fresh, self-contained screen.\n")
    append("- Only use the exact components and properties defined above. Do not invent attributes, component types, or behaviors that are not listed. If a component doesn't support a feature, do not pretend it does.\n")
    append("- Start with simple, clean layouts. A well-structured screen with a few cards and clear actions is better than a complex layout that pushes the component set beyond its capabilities.\n")
    append("- When unsure whether something will work, use a simpler approach. A working simple screen is always better than a broken ambitious one.\n\n")
    append("Example:\n```kai-ui\n{\"type\":\"column\",\"children\":[{\"type\":\"text\",\"value\":\"Welcome\",\"style\":\"headline\"},{\"type\":\"card\",\"children\":[{\"type\":\"text\",\"value\":\"What would you like to do?\",\"style\":\"title\"},{\"type\":\"button\",\"label\":\"Get Started\",\"action\":{\"type\":\"callback\",\"event\":\"get_started\"}}]}]}\n```\n")
}

/**
 * Pre-computed kai-ui component catalog text — shared between [appendDynamicUiSection]
 * and [appendInteractiveUiSection]. Pre-building the ~3KB of static text once (instead
 * of re-running ~30 `append` calls per message) is the main reason this is a top-level
 * val rather than a helper function.
 */
private val KAI_UI_COMPONENT_CATALOG: String = buildString {
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
    append("- chip_group: {\"type\":\"chip_group\",\"id\":\"...\",\"chips\":[{\"label\":\"Tag\",\"value\":\"tag\"}],\"selection\":\"single|multi|none\"} — selection mode: \"single\" (default, one at a time), \"multi\" (any number), or \"none\" (display-only tags, no interaction, id not needed). For \"single\" and \"multi\" a button must collectFrom the chip_group id to send the selection.\n")
    append("- list: {\"type\":\"list\",\"items\":[...],\"ordered\":false} — bullet (or numbered) list; the app renders bullets/numbers automatically, so do NOT include bullet characters (•, -, *) or numbering in item text\n")
    append("- table: {\"type\":\"table\",\"headers\":[\"Col1\",\"Col2\"],\"rows\":[[\"a\",\"b\"]]} — columns share equal width; keep to 3-4 columns max on mobile, use short cell values\n")
    append("- icon: {\"type\":\"icon\",\"name\":\"home|settings|search|add|delete|edit|check|check_circle|close|arrow_back|arrow_forward|star|favorite|share|info|warning|person|group|mail|phone|calendar|location|refresh|menu|more|send|notifications|trending_up|trending_down|trending_flat|thumb_up|thumb_down|visibility|lock|shopping_cart|play|pause|stop|download|upload|cloud|attachment|link|code|terminal|build|bug|lightbulb|science|school|work|account_circle|language|translate|dark_mode|light_mode|bolt|rocket|money|credit_card|receipt|inventory|category|dashboard|analytics|chart|pie_chart|show_chart|timer|alarm|task|bookmark|flag|tag|pin|copy|paste|cut|undo|redo|filter|sort|swap|sync|wifi|bluetooth|battery|speed|shield|verified|health|fitness|food|coffee|airplane|hotel|car|earth|map|compass|pet|leaf|water|weather|party|trophy|medal|premium\",\"size\":24,\"color\":\"primary|secondary|error\"} — you can also use any emoji as the name (e.g. \"name\":\"⚔️\" or \"name\":\"🗺️\"); prefer emojis when they better convey the meaning than the generic Material icons\n")
    append("- code: {\"type\":\"code\",\"code\":\"...\",\"language\":\"kotlin\"}\n")
    append("- progress: {\"type\":\"progress\",\"value\":0.5,\"label\":\"50%\"} (always provide a value 0.0-1.0 to show a determinate bar; do NOT omit value to fake a loading state)\n")
    append("- countdown: {\"type\":\"countdown\",\"seconds\":300,\"label\":\"Time left\",\"action\":{\"type\":\"callback\",\"event\":\"timer_done\"}} (seconds is relative duration from render; action is optional, fires on expiry)\n")
    append("- alert: {\"type\":\"alert\",\"message\":\"...\",\"title\":\"...\",\"severity\":\"info|success|warning|error\"}\n")
    append("- tabs: {\"type\":\"tabs\",\"tabs\":[{\"label\":\"Tab 1\",\"children\":[...]},{\"label\":\"Tab 2\",\"children\":[...]}],\"selectedIndex\":0}\n")
    append("- accordion: {\"type\":\"accordion\",\"title\":\"...\",\"children\":[...],\"expanded\":false}\n")
    append("- box: {\"type\":\"box\",\"children\":[...],\"contentAlignment\":\"center|top_start|top_center|top_end|center_start|center_end|bottom_start|bottom_center|bottom_end\"}\n")
    append("- quote: {\"type\":\"quote\",\"text\":\"...\",\"source\":\"Author Name\"} — blockquote with accent border\n")
    append("- badge: {\"type\":\"badge\",\"value\":\"3\",\"color\":\"primary|secondary|error\"} — small colored pill for counts or status\n")
    append("- stat: {\"type\":\"stat\",\"value\":\"\$1,234\",\"label\":\"Revenue\",\"description\":\"12% increase\"} — large metric display\n")
    append("- avatar: {\"type\":\"avatar\",\"name\":\"John Doe\",\"imageUrl\":\"https://...\",\"size\":40} — circular image or initials (24-80dp)\n\n")
    append("Actions (on buttons, countdown expiry):\n")
    append("- callback: {\"type\":\"callback\",\"event\":\"event_name\",\"data\":{\"key\":\"val\"},\"collectFrom\":[\"input_id1\",\"input_id2\"]} — collects input values and sends them back as a user message (e.g. \"Pressed: event_name\" or \"Responded with: key: value\"). You then reply with text or more UI. Use callbacks for: collecting choices, submitting forms, navigating between steps, confirming actions. Do NOT create callback buttons that imply operations you cannot perform — callbacks only send a message, they do not trigger system actions like printing, file export, or downloads.\n")
    append("- toggle: {\"type\":\"toggle\",\"targetId\":\"element_id\"} — shows/hides an element locally\n")
    append("- open_url: {\"type\":\"open_url\",\"url\":\"https://...\"}\n")
    append("- copy_to_clipboard: {\"type\":\"button\",\"action\":{\"type\":\"copy_to_clipboard\",\"text\":\"...\"}} — renders as a clipboard icon button; omit the button label. Offer next to copyable content like snippets, commands, or tokens.\n\n")
    append("- Form inputs (text_input, checkbox, switch, select, radio_group, slider, chip_group) only store state locally. Their values are ONLY sent when a button's collectFrom includes their id. Always pair form inputs with a submit button that collects from them.\n\n")
}
