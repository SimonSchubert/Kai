package com.inspiredandroid.kai.skills

/**
 * Tiny YAML-subset parser for SKILL.md frontmatter. Only handles `name` and
 * `description` fields — anything else in the frontmatter is ignored. Mirrors
 * Anthropic's validation rules (id ≤ 64 chars, lowercase letters/digits/hyphens
 * only; description ≤ 1024 chars, non-empty).
 */
object SkillFrontmatterParser {

    private val idRegex = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")

    sealed class Result {
        data class Ok(val id: String, val description: String, val body: String) : Result()
        data class Err(val reason: String) : Result()
    }

    fun parse(source: String): Result {
        val normalized = source.replace("\r\n", "\n").trimStart()
        if (!normalized.startsWith("---\n")) {
            return Result.Err("Missing frontmatter (must start with '---').")
        }
        val afterOpen = normalized.removePrefix("---\n")
        val closeIdx = afterOpen.indexOf("\n---")
        if (closeIdx < 0) {
            return Result.Err("Frontmatter not closed (expected a second '---').")
        }
        val frontmatter = afterOpen.substring(0, closeIdx)
        val body = afterOpen.substring(closeIdx + 4).trimStart('\n')

        var name: String? = null
        var description: String? = null
        for (rawLine in frontmatter.split('\n')) {
            val line = rawLine.trimEnd()
            if (line.isBlank() || line.startsWith("#")) continue
            val sepIdx = line.indexOf(':')
            if (sepIdx <= 0) continue
            val key = line.substring(0, sepIdx).trim()
            val value = line.substring(sepIdx + 1).trim().trim('"', '\'')
            when (key) {
                "name" -> name = value
                "description" -> description = value
            }
        }

        val id = name ?: return Result.Err("Missing 'name' in frontmatter.")
        if (id.length > 64) return Result.Err("'name' must be ≤ 64 characters.")
        if (!idRegex.matches(id)) return Result.Err("'name' must be lowercase letters, digits, and hyphens only.")

        val desc = description ?: return Result.Err("Missing 'description' in frontmatter.")
        if (desc.isEmpty()) return Result.Err("'description' must be non-empty.")
        if (desc.length > 1024) return Result.Err("'description' must be ≤ 1024 characters.")

        return Result.Ok(id, desc, body)
    }

    fun displayName(id: String): String = id.split('-').joinToString(" ") { part ->
        part.replaceFirstChar { it.titlecase() }
    }
}
