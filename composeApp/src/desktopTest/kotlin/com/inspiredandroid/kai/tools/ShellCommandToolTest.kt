package com.inspiredandroid.kai.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ShellCommandToolTest {

    @Test
    fun noWorkingDirOrEnvProducesEmptyPrefix() {
        assertEquals("", buildCommandPrefix(null, emptyMap()))
    }

    @Test
    fun envKeyIsNotQuoted() {
        // Regression test: an earlier version quoted the key too, producing
        // 'FOO'='hello' -- which bash parses as a command named "FOO=hello"
        // (command not found) rather than an assignment, since bash only
        // recognizes NAME=value as an assignment when NAME is unquoted.
        val prefix = buildCommandPrefix(null, mapOf("FOO" to "hello"))
        assertEquals("FOO='hello' ", prefix)
    }

    @Test
    fun envValueIsSingleQuoted() {
        val prefix = buildCommandPrefix(null, mapOf("FOO" to "it's a test"))
        assertEquals("FOO='it'\\''s a test' ", prefix)
    }

    @Test
    fun workingDirIsQuotedAndPrependedWithCd() {
        val prefix = buildCommandPrefix("/tmp/some dir", emptyMap())
        assertEquals("cd '/tmp/some dir' && ", prefix)
    }

    @Test
    fun workingDirAndEnvCombine() {
        val prefix = buildCommandPrefix("/tmp", mapOf("FOO" to "bar"))
        assertEquals("cd '/tmp' && FOO='bar' ", prefix)
    }

    @Test
    fun multipleEnvVarsAllUnquotedKeys() {
        val prefix = buildCommandPrefix(null, linkedMapOf("FOO" to "1", "BAR" to "2"))
        assertEquals("FOO='1' BAR='2' ", prefix)
    }

    @Test
    fun invalidEnvKeyIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            buildCommandPrefix(null, mapOf("FOO; rm -rf /" to "hello"))
        }
    }

    @Test
    fun envKeyStartingWithDigitIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            buildCommandPrefix(null, mapOf("1FOO" to "hello"))
        }
    }

    @Test
    fun envKeyWithSpaceIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            buildCommandPrefix(null, mapOf("FOO BAR" to "hello"))
        }
    }
}
