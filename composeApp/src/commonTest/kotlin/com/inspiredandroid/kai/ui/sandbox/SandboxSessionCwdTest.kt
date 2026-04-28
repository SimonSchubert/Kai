package com.inspiredandroid.kai.ui.sandbox

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SandboxSessionCwdTest {

    private val marker = "__KAI_CWD_test__:"

    @Test
    fun wrapperPrependsCdToTrackedCwdAndAppendsMarker() {
        val wrapped = wrapCommandForCwdTracking("ls", "/root/work", marker)
        assertTrue(wrapped.startsWith("cd '/root/work' 2>/dev/null"))
        assertTrue(wrapped.contains("printf '%s%s\\n' '__KAI_CWD_test__:'"))
        assertTrue(wrapped.contains("\"\$(pwd)\""))
        assertTrue(wrapped.contains("exit \$__kai_st"))
    }

    @Test
    fun wrapperFallsBackToRootIfCwdMissing() {
        val wrapped = wrapCommandForCwdTracking("ls", "/gone", marker)
        assertTrue(wrapped.contains("|| cd '/root'"))
    }

    @Test
    fun wrapperEscapesSingleQuotesInCwd() {
        val wrapped = wrapCommandForCwdTracking("ls", "/it's a path", marker)
        assertTrue(wrapped.contains("cd '/it'\\''s a path'"))
    }

    @Test
    fun markerHandlerCapturesNewWorkingDir() {
        var captured: String? = null
        val handled = handleCwdMarker("$marker/root/newdir", marker) { captured = it }
        assertTrue(handled)
        assertEquals("/root/newdir", captured)
    }

    @Test
    fun markerHandlerIgnoresNonMatchingLines() {
        var captured: String? = null
        val handled = handleCwdMarker("regular output", marker) { captured = it }
        assertFalse(handled)
        assertEquals(null, captured)
    }

    @Test
    fun markerHandlerRejectsEmptyOrRelativePath() {
        var captured: String? = null
        val handled = handleCwdMarker(marker, marker) { captured = it }
        assertTrue(handled)
        assertEquals(null, captured)
    }
}
