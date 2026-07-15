package com.inspiredandroid.kai.sandbox

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DesktopPersistentShellTest {

    private lateinit var baseDir: File
    private lateinit var shell: DesktopPersistentShell

    @BeforeTest
    fun setUp() {
        baseDir = Files.createTempDirectory("desktop-persistent-shell-test").toFile()
        shell = DesktopPersistentShell(MicromambaLayout(baseDir))
    }

    @AfterTest
    fun tearDown() {
        shell.reset()
        baseDir.deleteRecursively()
    }

    @Test
    fun runsSimpleCommandAndCapturesStdout() = runBlocking {
        val result = shell.run("echo hello-desktop-shell", timeoutSeconds = 10)
        assertEquals(true, result["success"])
        assertEquals("hello-desktop-shell", result["stdout"])
        assertEquals(0, result["exit_code"])
    }

    @Test
    fun preservesCwdAcrossCommands() = runBlocking {
        shell.run("cd /tmp", timeoutSeconds = 10)
        val result = shell.run("pwd", timeoutSeconds = 10)
        assertEquals("/tmp", result["stdout"])
    }

    @Test
    fun reportsNonZeroExitCode() = runBlocking {
        // Subshell: a bare `exit 7` would terminate the persistent shell itself
        // (commands run un-wrapped so `cd` persists), reporting shell death, not code 7.
        val result = shell.run("(exit 7)", timeoutSeconds = 10)
        assertEquals(false, result["success"])
        assertEquals(7, result["exit_code"])
    }

    @Test
    fun pathIncludesMicromambaRootBin() = runBlocking {
        val result = shell.run("echo \$PATH", timeoutSeconds = 10)
        val stdout = result["stdout"] as String
        assertTrue(stdout.contains(File(baseDir, "root/bin").absolutePath))
    }

    @Test
    fun cancelForegroundKillsChildProcessWithoutResettingShell() = runBlocking {
        val pending = async { shell.run("sleep 30", timeoutSeconds = 20) }
        delay(300.milliseconds) // let `sleep` actually start as bash's child
        shell.cancelForeground()

        val result = withTimeout(5.seconds) { pending.await() }
        assertEquals(false, result["success"])
        assertEquals(false, result["shell_died"]) // child was signaled, not the shell reset
        assertEquals(130, result["exit_code"]) // 128 + SIGINT

        // Shell itself must still be alive and usable after the cancel.
        val next = shell.run("echo still-alive", timeoutSeconds = 10)
        assertEquals("still-alive", next["stdout"])
    }
}
