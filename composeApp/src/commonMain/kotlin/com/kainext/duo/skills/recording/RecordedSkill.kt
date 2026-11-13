package com.kainext.duo.skills.recording

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A procedure learned by watching the user operate their phone, stored as a reusable skill.
 *
 * **Why this exists next to [com.kainext.duo.skills.SkillManifest]:** a manifest is the *loaded*
 * view of a `SKILL.md` that already lives in the sandbox; this is the *authored* form that produces
 * such a file. Keeping the two apart means the recorder never needs sandbox access, the format can
 * be validated before anything is written, and the JSON round-trip (which [SkillManifest] does not
 * support) stays available for editing and export.
 *
 * [name] is a human display name and is expected to be user-editable ("每日签到"). It is *not* the
 * storage id — [toSkillId] derives that, because ids have a much stricter grammar.
 *
 * [description] is one sentence, shown in the skills list and in the prompt section. It must stay
 * short: it is repeated for every skill on every prompt build.
 *
 * [context] carries what the recording was made in. Without it a skill cannot tell whether it is
 * even applicable ("this taps 签到, but in which app?"), and the AI could apply it on the wrong
 * screen.
 */
@Serializable
data class RecordedSkill(
    val name: String,
    val description: String,
    val actions: List<RecordedAction>,
    val context: SkillContext,
    val createdAtMs: Long = 0L,
    val recordedFrom: String? = null,
    val version: Int = 1,
) {

    /**
     * True when [query] plausibly refers to this skill.
     *
     * **Why a keyword test and not embedding search:** this runs on the chat hot path inside
     * `commonMain`, where no embedding model is guaranteed to exist, and the candidate set is a
     * handful of skills. Case-insensitive substring matching over [SkillContext.keywords] plus the
     * name and description is deterministic, instant, and debuggable — and for the actual use case
     * ("帮我签到", "check in") it is accurate, because the keywords *are* the words the user typed
     * during recording.
     *
     * Matching is bidirectional on purpose: the query usually contains the keyword
     * ("帮我签到" contains "签到"), but a keyword can also be the longer form ("checkin" contains a
     * typed "check"). Both indicate the same intent.
     */
    fun matches(query: String): Boolean {
        val needle = query.trim().lowercase()
        if (needle.length < MIN_MATCH_LENGTH) return false
        return matchCandidates().any { candidate ->
            val hay = candidate.trim().lowercase()
            if (hay.length < MIN_MATCH_LENGTH) {
                false
            } else {
                hay in needle || needle in hay
            }
        }
    }

    /** Everything [matches] compares against: keywords, name, and description. */
    private fun matchCandidates(): List<String> = buildList {
        add(name)
        add(description)
        addAll(context.keywords)
    }

    /** Pretty JSON for export and hand-editing. Unknown keys are tolerated on the way back in. */
    fun toJson(): String = SkillJson.encodeToString(RecordedSkill.serializer(), this)

    /**
     * Renders this skill as a standard `SKILL.md`, ready to be written to `~/skills/<id>/SKILL.md`.
     *
     * The body deliberately carries the steps **twice**: numbered natural language for the model to
     * follow directly, then the raw actions as JSON for anything that wants to replay them
     * mechanically. A model reading only the prose can still act; a replayer reading only the JSON
     * still has exact parameters. Neither has to parse the other's format.
     */
    fun toSkillMarkdown(): String {
        val id = toSkillId()
        val steps = actions.mapIndexed { index, action -> "${index + 1}. ${action.describeStep()}" }
        val actionJson = SkillJson.encodeToString(ListSerializer(RecordedAction.serializer()), actions)
        return buildString {
            append("---\n")
            append("name: ").append(id).append('\n')
            append("description: ").append(frontmatterDescription()).append('\n')
            append("---\n\n")
            append("# ").append(name).append("\n\n")
            append(frontmatterDescription()).append('\n')
            if (recordedFrom != null) {
                append("\nRecorded from `").append(recordedFrom).append("`")
                if (context.appLabel != null) append(" (").append(context.appLabel).append(')')
                append(". Replay the steps in order; the numbers in the JSON block match the list.\n")
            } else {
                append("\nReplay the steps in order; the numbers in the JSON block match the list.\n")
            }
            if (context.keywords.isNotEmpty()) {
                append("\nTrigger words: ").append(context.keywords.joinToString(", ")).append('\n')
            }
            append("\n## Steps\n\n")
            if (steps.isEmpty()) {
                append("(no steps recorded)\n")
            } else {
                for (step in steps) append(step).append('\n')
            }
            append("\n## Recorded actions\n\n```json\n")
            append(actionJson)
            append("\n```\n")
        }
    }

    /**
     * A filesystem- and frontmatter-safe id derived from [name].
     *
     * Lowercase letters and digits survive, everything else becomes a hyphen, runs collapse, and
     * the ends are trimmed — matching `SkillFrontmatterParser`'s grammar exactly, so a generated
     * skill can never be rejected for a malformed name. Capped at 60 characters (the parser allows
     * 64) to leave room for a suffix without re-truncating later.
     *
     * **Non-ASCII names need a hash suffix.** `commonMain` has no ICU transliteration, so "每日签到"
     * would otherwise slug to the empty string — and every CJK-named skill would collide on the same
     * fallback id. A short stable hash of the original name keeps ids distinct while the readable
     * part stays a prefix. This is a real limitation, not a stub: the user-facing name is still
     * correct, only the on-disk folder is opaque.
     */
    fun toSkillId(): String {
        val slug = slugify(name)
        val base = if (slug.isNotEmpty()) slug else "recorded-skill"
        val needsHash = name.any { it.code > ASCII_MAX }
        val candidate = if (needsHash) {
            base.take(MAX_ID_LENGTH - HASH_SUFFIX_LENGTH - 1).trimEnd('-') + "-" + nameHashSuffix(name)
        } else {
            base.take(MAX_ID_LENGTH).trimEnd('-')
        }
        return if (candidate.isEmpty()) "recorded-skill" else candidate
    }

    /** [description] flattened to a single frontmatter-safe line, capped at the parser's limit. */
    private fun frontmatterDescription(): String {
        val flat = description.replace('\r', ' ').replace('\n', ' ').trim()
        val fallback = if (name.isNotBlank()) name.trim() else "Recorded skill"
        val text = if (flat.isEmpty()) fallback else flat
        return if (text.length <= MAX_DESCRIPTION_LENGTH) text else text.take(MAX_DESCRIPTION_LENGTH - 3) + "..."
    }

    companion object {
        /** Minimum length for a match candidate, so a one-letter keyword cannot match everything. */
        private const val MIN_MATCH_LENGTH = 2

        /** Mirrors `SkillFrontmatterParser`'s 64-char ceiling, minus a safety margin. */
        private const val MAX_ID_LENGTH = 60

        /** Mirrors `SkillFrontmatterParser`'s description ceiling. */
        private const val MAX_DESCRIPTION_LENGTH = 1024

        /** Length of the hex suffix appended for names that lose information when slugified. */
        private const val HASH_SUFFIX_LENGTH = 4

        private const val ASCII_MAX = 127

        /** Tolerant reader: a hand-edited or forward-compatible file must not fail to load. */
        private val SkillJson = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /**
         * Parses a skill previously produced by [toJson].
         *
         * Returns null instead of throwing: this reads files the user may have hand-edited (or that
         * an older build wrote), and one corrupt skill must not take down the skills list. Callers
         * treat null as "skip this entry".
         */
        fun fromJson(raw: String): RecordedSkill? = runCatching {
            SkillJson.decodeFromString(RecordedSkill.serializer(), raw)
        }.getOrNull()

        /**
         * Derives trigger keywords from the recording: the text of every [RecordedAction.TapText]
         * plus the package's short name.
         *
         * **Why tap text:** the words the user tapped *are* the words they will use to ask for the
         * procedure again — tapping "签到" once is exactly the evidence that "帮我签到" should find
         * this skill. **Why the package short name:** it is the only hint about the app, so "支付宝
         * 签到" still matches when the button label is generic. **Why capped at 12:** the list is
         * injected into the prompt and matched linearly on every message; beyond a dozen entries the
         * marginal recall is negligible and the false-positive rate climbs.
         */
        fun extractKeywords(
            actions: List<RecordedAction>,
            packageName: String? = null,
            appLabel: String? = null,
        ): List<String> {
            val candidates = buildList {
                for (action in actions) {
                    if (action is RecordedAction.TapText) add(action.text)
                }
                if (!packageName.isNullOrBlank()) add(packageName.substringAfterLast('.'))
                if (!appLabel.isNullOrBlank()) add(appLabel)
            }
            val seen = mutableSetOf<String>()
            val result = mutableListOf<String>()
            for (candidate in candidates) {
                val trimmed = candidate.trim()
                if (trimmed.length !in MIN_KEYWORD_LENGTH..MAX_KEYWORD_LENGTH) continue
                // A keyword made only of punctuation or symbols carries no signal.
                if (trimmed.none { it.isLetterOrDigit() }) continue
                if (!seen.add(trimmed.lowercase())) continue
                result.add(trimmed)
                if (result.size >= MAX_KEYWORDS) break
            }
            return result
        }

        private const val MIN_KEYWORD_LENGTH = 2
        private const val MAX_KEYWORD_LENGTH = 32
        private const val MAX_KEYWORDS = 12

        /**
         * Lowercase ASCII/digit run with single hyphens between words. Non-ASCII characters become
         * separators, so "Order Coffee" and "order--coffee" both yield "order-coffee".
         */
        private fun slugify(value: String): String {
            val builder = StringBuilder(value.length)
            var lastWasHyphen = false
            for (ch in value.lowercase()) {
                if (ch in 'a'..'z' || ch in '0'..'9') {
                    builder.append(ch)
                    lastWasHyphen = false
                } else if (!lastWasHyphen && builder.isNotEmpty()) {
                    builder.append('-')
                    lastWasHyphen = true
                }
            }
            return builder.toString().trim('-')
        }

        /** Stable 4-hex-digit digest of [value]; FNV-1a so it is identical on every platform. */
        private fun nameHashSuffix(value: String): String {
            var hash = FNV_OFFSET_BASIS
            for (ch in value) {
                hash = hash xor ch.code
                hash *= FNV_PRIME
            }
            val hex = (hash and 0xFFFF).toString(16)
            return hex.padStart(HASH_SUFFIX_LENGTH, '0').takeLast(HASH_SUFFIX_LENGTH)
        }

        /** FNV-1a 32-bit offset basis, as a signed Int (`2166136261u` reinterpreted). */
        private const val FNV_OFFSET_BASIS = -2128831035
        private const val FNV_PRIME = 16777619
    }
}

/**
 * The situation a skill was recorded in — the evidence used to decide whether it applies now.
 *
 * **Why the screen size is stored:** replaying a recording made on a different aspect ratio is the
 * one case where the stored relative coordinates can land on the wrong control. Knowing the
 * original size lets a replayer warn about, or compensate for, the mismatch instead of silently
 * tapping the wrong place.
 */
@Serializable
data class SkillContext(
    val packageName: String? = null,
    val appLabel: String? = null,
    val keywords: List<String> = emptyList(),
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
)
