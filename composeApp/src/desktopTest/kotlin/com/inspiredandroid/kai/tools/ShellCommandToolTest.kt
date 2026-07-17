package com.inspiredandroid.kai.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ShellCommandToolTest {

    @Test
    fun noWorkingDirOrEnvLeavesCommandUnwrapped() {
        assertEquals("echo hi", buildWrappedCommand("echo hi", null, emptyMap()))
    }

    @Test
    fun envWrapsCommandInNestedBashC() {
        // Regression test: an earlier version emitted a same-line prefix
        // (FOO='hello' echo $FOO), which exports FOO into the executed
        // command's real environment -- fine for a subprocess like printenv,
        // but bash expands $FOO in the command's own arguments BEFORE that
        // same-line assignment takes effect, so `echo $FOO` saw the old
        // (unset) value. Wrapping in a nested `bash -c` means $FOO is
        // expanded by the child shell, after it has inherited FOO for real.
        val wrapped = buildWrappedCommand("echo \$FOO", null, mapOf("FOO" to "hello"))
        assertEquals("FOO='hello' bash -c 'echo \$FOO'", wrapped)
    }

    @Test
    fun envKeyIsNotQuoted() {
        // 'FOO'='hello' is not a valid assignment word (bash requires an
        // unquoted name before '='); it parses as a command literally named
        // "FOO=hello" instead, failing with "command not found".
        val wrapped = buildWrappedCommand("printenv FOO", null, mapOf("FOO" to "hello"))
        assertEquals("FOO='hello' bash -c 'printenv FOO'", wrapped)
    }

    @Test
    fun envValueIsSingleQuoted() {
        val wrapped = buildWrappedCommand("echo hi", null, mapOf("FOO" to "it's a test"))
        assertEquals("FOO='it'\\''s a test' bash -c 'echo hi'", wrapped)
    }

    @Test
    fun commandContainingSingleQuotesIsEscaped() {
        val wrapped = buildWrappedCommand("echo 'hello world'", null, mapOf("FOO" to "bar"))
        assertEquals("FOO='bar' bash -c 'echo '\\''hello world'\\'''", wrapped)
    }

    @Test
    fun workingDirIsQuotedAndPrependedWithCd() {
        val wrapped = buildWrappedCommand("pwd", "/tmp/some dir", emptyMap())
        assertEquals("cd '/tmp/some dir' && pwd", wrapped)
    }

    @Test
    fun workingDirAndEnvCombine() {
        // cd stays outside the nested bash -c -- it's documented to persist
        // in the *persistent* shell across calls, which only holds if it
        // runs directly rather than inside a throwaway child shell.
        val wrapped = buildWrappedCommand("pwd", "/tmp", mapOf("FOO" to "bar"))
        assertEquals("cd '/tmp' && FOO='bar' bash -c 'pwd'", wrapped)
    }

    @Test
    fun multipleEnvVarsAllUnquotedKeys() {
        val wrapped = buildWrappedCommand("echo hi", null, linkedMapOf("FOO" to "1", "BAR" to "2"))
        assertEquals("FOO='1' BAR='2' bash -c 'echo hi'", wrapped)
    }

    @Test
    fun invalidEnvKeyIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            buildWrappedCommand("echo hi", null, mapOf("FOO; rm -rf /" to "hello"))
        }
    }

    @Test
    fun envKeyStartingWithDigitIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            buildWrappedCommand("echo hi", null, mapOf("1FOO" to "hello"))
        }
    }

    @Test
    fun envKeyWithSpaceIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            buildWrappedCommand("echo hi", null, mapOf("FOO BAR" to "hello"))
        }
    }
}
