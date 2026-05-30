package com.inspiredandroid.kai.skills

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkillMarketplaceManifestTest {

    @Test
    fun `flattens and normalizes skill paths across plugins`() {
        val manifest = """
            {
              "name": "anthropic-agent-skills",
              "plugins": [
                { "name": "document-skills", "source": "./", "skills": ["./skills/xlsx", "./skills/pdf"] },
                { "name": "example-skills", "source": "./", "skills": ["./skills/canvas-design"] }
              ]
            }
        """.trimIndent()

        val paths = SkillRegistry.parseMarketplaceManifest(manifest)
        assertEquals(listOf("skills/xlsx", "skills/pdf", "skills/canvas-design"), paths)
    }

    @Test
    fun `skips plugins sourced from a different repo`() {
        val manifest = """
            {
              "plugins": [
                { "name": "local", "source": "./", "skills": ["./skills/a"] },
                { "name": "remote", "source": "https://github.com/other/repo", "skills": ["./skills/b"] }
              ]
            }
        """.trimIndent()

        val paths = SkillRegistry.parseMarketplaceManifest(manifest)
        assertEquals(listOf("skills/a"), paths)
    }

    @Test
    fun `tolerates absent source as same-repo`() {
        val manifest = """{ "plugins": [ { "name": "x", "skills": ["skills/a"] } ] }"""
        assertEquals(listOf("skills/a"), SkillRegistry.parseMarketplaceManifest(manifest))
    }

    @Test
    fun `de-duplicates repeated paths`() {
        val manifest = """
            { "plugins": [
                { "source": "./", "skills": ["./skills/a", "./skills/a"] },
                { "source": "./", "skills": ["skills/a"] }
            ] }
        """.trimIndent()
        assertEquals(listOf("skills/a"), SkillRegistry.parseMarketplaceManifest(manifest))
    }

    @Test
    fun `returns empty on missing plugins or malformed json`() {
        assertTrue(SkillRegistry.parseMarketplaceManifest("{}").isEmpty())
        assertTrue(SkillRegistry.parseMarketplaceManifest("not json").isEmpty())
        assertTrue(SkillRegistry.parseMarketplaceManifest("""{ "plugins": [ { "name": "x" } ] }""").isEmpty())
    }

    private val tree = setOf(
        "skills/pdf/SKILL.md",
        "skills/pdf/reference.md",
        "skills/docx/SKILL.md",
        "skills/nested/sub/SKILL.md",
        "README.md",
    )

    @Test
    fun `allowlist takes precedence and keeps only folders with a SKILL_md`() {
        val dirs = SkillRegistry.selectSkillDirs(
            treePaths = tree,
            allowlist = listOf("skills/pdf", "skills/missing"),
            manifestPaths = listOf("skills/docx"),
            root = "skills",
        )
        assertEquals(listOf("skills/pdf"), dirs)
    }

    @Test
    fun `manifest used when no allowlist`() {
        val dirs = SkillRegistry.selectSkillDirs(tree, null, listOf("skills/pdf", "skills/docx"), "skills")
        assertEquals(listOf("skills/pdf", "skills/docx"), dirs)
    }

    @Test
    fun `folder scrape finds direct children under root only`() {
        val dirs = SkillRegistry.selectSkillDirs(tree, null, null, "skills").sorted()
        assertEquals(listOf("skills/docx", "skills/pdf"), dirs)
    }

    @Test
    fun `exclude drops skills by folder name`() {
        val dirs = SkillRegistry.selectSkillDirs(
            treePaths = tree,
            allowlist = null,
            manifestPaths = listOf("skills/pdf", "skills/docx"),
            root = "skills",
            exclude = setOf("docx"),
        )
        assertEquals(listOf("skills/pdf"), dirs)
    }
}
