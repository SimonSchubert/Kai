package com.kainext.duo.skills.recording

/**
 * Renders recorded skills into a system-prompt section.
 *
 * **Why this is a separate object rather than a method on [RecordedSkill]:** prompt composition is a
 * policy decision (how many skills, how verbose, what happens when they overflow) while the skill
 * itself is data. Keeping them apart means the budget can be tuned without touching the stored
 * format, and [com.kainext.duo.data.ChatSystemPromptBuilder] can call this with no dependency on the
 * recorder at all.
 *
 * **Why the section is written imperatively** ("follow the recorded steps instead of exploring the
 * screen"): a recorded procedure is only useful if the model *prefers* it over its default behaviour
 * of poking around the UI. Models otherwise re-derive a plan from scratch and the recording is dead
 * weight. The instruction is therefore explicit about precedence, and names the fallback (explore)
 * only when nothing matches.
 *
 * **Why the length budget is hard and truncation is visible:** this text goes into every request's
 * system prompt. Ten recorded skills with twenty steps each would add thousands of tokens to every
 * turn, forever. The budget keeps the cost predictable; the explicit `... (more skills omitted)`
 * marker means the model (and a debugging human) can tell the list was cut rather than assuming the
 * omitted skills do not exist.
 */
object RecordedSkillPrompt {

    /** Total character ceiling for the rendered section, including the header and trailer. */
    const val MAX_TOTAL_CHARS = 4000

    /** Characters allowed per skill before its step list is cut short. */
    const val DEFAULT_MAX_CHARS_PER_SKILL = 600

    /** Skills rendered per prompt build; the rest are reported as omitted. */
    const val DEFAULT_MAX_SKILLS = 8

    /** Steps rendered per skill even when the character budget would allow more. */
    private const val MAX_STEPS_PER_SKILL = 30

    private const val HEADER =
        "## Recorded skills\n" +
            "These are step-by-step procedures recorded from the user's own actions. When the user " +
            "asks for something matching one of these, follow the recorded steps instead of " +
            "exploring the screen. If no recorded skill matches, work it out normally."

    private const val OMITTED_TRAILER = "\n... (more skills omitted)"

    /**
     * Renders [skills] as a prompt section, or the empty string when there is nothing to render.
     *
     * Returning `""` (not null, not a header-only block) matters: the prompt builder appends sections
     * unconditionally, so an empty string is what makes "the user has no recorded skills" cost
     * exactly zero characters.
     *
     * [skills] are rendered in the given order until [maxSkills], [maxCharsPerSkill], or
     * [MAX_TOTAL_CHARS] runs out — whichever binds first. A skill that does not fit in the remaining
     * total budget is skipped along with everything after it, and the trailer is appended, because
     * partial ordering is meaningless to the model: showing skill 4 but not skill 3 would suggest 3
     * does not exist.
     */
    fun render(
        skills: List<RecordedSkill>,
        maxSkills: Int = DEFAULT_MAX_SKILLS,
        maxCharsPerSkill: Int = DEFAULT_MAX_CHARS_PER_SKILL,
    ): String {
        if (skills.isEmpty() || maxSkills <= 0) return ""

        val builder = StringBuilder(HEADER)
        var rendered = 0
        var omitted = false

        for (skill in skills) {
            if (rendered >= maxSkills) {
                omitted = true
                break
            }
            val block = renderSkill(skill, maxCharsPerSkill)
            // +1 for the blank line between blocks, +the trailer in case this is the last one.
            if (builder.length + block.length + 1 + OMITTED_TRAILER.length > MAX_TOTAL_CHARS) {
                omitted = true
                break
            }
            builder.append("\n\n").append(block)
            rendered++
        }

        // Nothing fit: return empty rather than a header promising skills that are not shown.
        if (rendered == 0) return ""
        if (omitted) {
            builder.append(OMITTED_TRAILER)
        }
        return builder.toString()
    }

    /**
     * One skill block: title with its package, a trigger list, then numbered steps.
     *
     * The package name is in the heading because it is the model's only way to know *where* the
     * procedure applies — without it "每日签到" is ambiguous across every app on the device.
     *
     * Steps are truncated at [maxCharsPerSkill] on a whole-step boundary. A half-written step
     * ("3. tap \"签到…") would be worse than no step at all, since the model cannot tell that it is
     * incomplete.
     */
    fun renderSkill(skill: RecordedSkill, maxCharsPerSkill: Int = DEFAULT_MAX_CHARS_PER_SKILL): String {
        val builder = StringBuilder()
        builder.append("### ").append(skill.name)
        val app = skill.context.packageName ?: skill.recordedFrom
        if (!app.isNullOrBlank()) {
            builder.append(" (").append(app).append(')')
        }
        builder.append('\n')

        val triggers = skill.context.keywords.take(MAX_TRIGGERS_RENDERED)
        if (triggers.isNotEmpty()) {
            builder.append("Triggers: ").append(triggers.joinToString(", ")).append('\n')
        }

        if (skill.actions.isEmpty()) {
            builder.append("(no steps recorded)")
            return builder.toString()
        }

        var stepIndex = 0
        for (action in skill.actions) {
            if (stepIndex >= MAX_STEPS_PER_SKILL) {
                builder.append("... (${skill.actions.size - stepIndex} more steps)")
                break
            }
            val line = "${stepIndex + 1}. ${action.describeStep()}"
            // Always allow the first step through so a tiny budget still yields a usable block.
            if (stepIndex > 0 && builder.length + line.length + 1 > maxCharsPerSkill) {
                builder.append("... (${skill.actions.size - stepIndex} more steps)")
                break
            }
            builder.append(line).append('\n')
            stepIndex++
        }
        return builder.toString().trimEnd('\n')
    }

    /**
     * Skills that plausibly answer [query], best first.
     *
     * **Why scoring and not just [RecordedSkill.matches]:** several skills can match one query ("签到"
     * appears in two apps). Ordering by how strongly they match — exact name hit, then keyword, then
     * description — puts the most likely one first, and the renderer's `maxSkills` cap then keeps the
     * useful ones. Ties keep the caller's order so the result is deterministic.
     */
    fun selectForQuery(skills: List<RecordedSkill>, query: String, maxSkills: Int = DEFAULT_MAX_SKILLS): List<RecordedSkill> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return skills
            .mapNotNull { skill -> matchScore(skill, needle)?.let { skill to it } }
            .sortedByDescending { it.second }
            .take(maxSkills)
            .map { it.first }
    }

    /** Higher is a stronger match; null when the skill does not match at all. */
    private fun matchScore(skill: RecordedSkill, needle: String): Int? {
        if (needle.length < MIN_QUERY_LENGTH) return null
        val name = skill.name.lowercase()
        val description = skill.description.lowercase()
        var score: Int? = null
        if (name.contains(needle) || needle.contains(name)) score = maxOf(score ?: 0, NAME_SCORE)
        if (skill.context.keywords.any { keywordMatches(it, needle) }) score = maxOf(score ?: 0, KEYWORD_SCORE)
        if (description.contains(needle)) score = maxOf(score ?: 0, DESCRIPTION_SCORE)
        return score
    }

    private fun keywordMatches(keyword: String, needle: String): Boolean {
        val k = keyword.trim().lowercase()
        if (k.length < MIN_QUERY_LENGTH) return false
        return k in needle || needle in k
    }

    private const val MAX_TRIGGERS_RENDERED = 6
    private const val MIN_QUERY_LENGTH = 2

    /** Weights for [matchScore]: a name hit is the strongest signal, a description hit the weakest. */
    private const val NAME_SCORE = 3
    private const val KEYWORD_SCORE = 2
    private const val DESCRIPTION_SCORE = 1
}
