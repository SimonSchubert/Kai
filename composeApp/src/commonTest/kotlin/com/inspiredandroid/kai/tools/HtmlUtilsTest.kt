package com.inspiredandroid.kai.tools

import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlUtilsTest {

    @Test
    fun decodeHtmlEntities_replacesNbsp() {
        assertEquals("Hello World", "Hello&nbsp;World".decodeHtmlEntities())
    }

    @Test
    fun decodeHtmlEntities_replacesAmp() {
        assertEquals("Tom & Jerry", "Tom &amp; Jerry".decodeHtmlEntities())
    }

    @Test
    fun decodeHtmlEntities_replacesLt() {
        assertEquals("5 < 10", "5 &lt; 10".decodeHtmlEntities())
    }

    @Test
    fun decodeHtmlEntities_replacesGt() {
        assertEquals("10 > 5", "10 &gt; 5".decodeHtmlEntities())
    }

    @Test
    fun decodeHtmlEntities_replacesQuot() {
        assertEquals("\"Hello\"", "&quot;Hello&quot;".decodeHtmlEntities())
    }

    @Test
    fun decodeHtmlEntities_replacesApos() {
        assertEquals("It's fine", "It&#39;s fine".decodeHtmlEntities())
    }

    @Test
    fun decodeHtmlEntities_replacesMultipleEntities() {
        val input = "&quot;Tom &amp; Jerry&quot; is &lt; 100&nbsp;years old, isn&#39;t it &gt; 50?"
        val expected = "\"Tom & Jerry\" is < 100 years old, isn't it > 50?"
        assertEquals(expected, input.decodeHtmlEntities())
    }

    @Test
    fun decodeHtmlEntities_noEntities_returnsSameString() {
        val input = "Just a normal string with no HTML entities at all."
        assertEquals(input, input.decodeHtmlEntities())
    }

    @Test
    fun decodeHtmlEntities_emptyString_returnsEmptyString() {
        assertEquals("", "".decodeHtmlEntities())
    }
}
