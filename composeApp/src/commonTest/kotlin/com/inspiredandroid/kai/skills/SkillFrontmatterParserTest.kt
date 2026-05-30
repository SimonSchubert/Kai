package com.inspiredandroid.kai.skills

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SkillFrontmatterParserTest {

    @Test
    fun `parses name description and body`() {
        val source = """
            ---
            name: pdf-tools
            description: Extract and manipulate PDF files.
            ---
            Use the bundled script to extract text.
        """.trimIndent()

        val result = SkillFrontmatterParser.parse(source)
        assertIs<SkillFrontmatterParser.Result.Ok>(result)
        assertEquals("pdf-tools", result.id)
        assertEquals("Extract and manipulate PDF files.", result.description)
        assertEquals("Use the bundled script to extract text.", result.body)
    }

    @Test
    fun `tolerates CRLF and quoted values`() {
        val source = "---\r\nname: \"my-skill\"\r\ndescription: 'A thing'\r\n---\r\nBody line"
        val result = SkillFrontmatterParser.parse(source)
        assertIs<SkillFrontmatterParser.Result.Ok>(result)
        assertEquals("my-skill", result.id)
        assertEquals("A thing", result.description)
        assertEquals("Body line", result.body)
    }

    @Test
    fun `rejects missing frontmatter`() {
        val result = SkillFrontmatterParser.parse("just some text without frontmatter")
        assertIs<SkillFrontmatterParser.Result.Err>(result)
    }

    @Test
    fun `rejects unclosed frontmatter`() {
        val result = SkillFrontmatterParser.parse("---\nname: x\ndescription: y\n")
        assertIs<SkillFrontmatterParser.Result.Err>(result)
    }

    @Test
    fun `rejects invalid id characters`() {
        val source = "---\nname: Bad_Name\ndescription: ok\n---\nbody"
        val result = SkillFrontmatterParser.parse(source)
        assertIs<SkillFrontmatterParser.Result.Err>(result)
    }

    @Test
    fun `rejects missing description`() {
        val source = "---\nname: ok-name\n---\nbody"
        val result = SkillFrontmatterParser.parse(source)
        assertIs<SkillFrontmatterParser.Result.Err>(result)
    }

    @Test
    fun `rejects overlong id`() {
        val longName = "a".repeat(65)
        val result = SkillFrontmatterParser.parse("---\nname: $longName\ndescription: ok\n---\nbody")
        assertIs<SkillFrontmatterParser.Result.Err>(result)
    }

    @Test
    fun `displayName titlecases hyphenated id`() {
        assertEquals("Pdf Tools", SkillFrontmatterParser.displayName("pdf-tools"))
        assertEquals("Single", SkillFrontmatterParser.displayName("single"))
    }

    @Test
    fun `ignores unknown frontmatter keys`() {
        val source = "---\nname: ok-name\nlicense: MIT\ndescription: ok\nversion: 1.0\n---\nbody"
        val result = SkillFrontmatterParser.parse(source)
        assertIs<SkillFrontmatterParser.Result.Ok>(result)
        assertTrue(result.id == "ok-name")
    }
}
