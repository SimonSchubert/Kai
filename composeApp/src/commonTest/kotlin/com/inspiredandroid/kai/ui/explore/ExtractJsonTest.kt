package com.inspiredandroid.kai.ui.explore

import kotlin.test.Test
import kotlin.test.assertEquals

class ExtractJsonTest {

    @Test
    fun `plain JSON array without wrapping`() {
        val input = """[{"title":"A","description":"B"}]"""
        val result = extractJson(input)
        assertEquals(input, result)
    }

    @Test
    fun `JSON wrapped in code fences`() {
        val input = """
Some text before
```json
[{"title":"A","description":"B"}]
```
Some text after
        """.trimIndent()

        val result = extractJson(input)
        assertEquals("""[{"title":"A","description":"B"}]""", result)
    }

    @Test
    fun `JSON with surrounding text`() {
        val input = """Here is the result: [{"title":"A","description":"B"}] hope that helps!"""
        val result = extractJson(input)
        assertEquals("""[{"title":"A","description":"B"}]""", result)
    }

    @Test
    fun `handles unescaped inner quotes via sanitizeJsonQuotes`() {
        val input = """[{"title":"A "cool" thing","description":"B"}]"""
        val result = extractJson(input)
        assertEquals("""[{"title":"A \"cool\" thing","description":"B"}]""", result)
    }
}
