package com.inspiredandroid.kai.sandbox

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun runsSimpleCommandAndCapturesStdout() = runTest {
        val result = shell.run("echo hello-desktop-shell", timeoutSeconds = 10)
        assertEquals(true, result["success"])
        assertEquals("hello-desktop-shell", result["stdout"])
        assertEquals(0, result["exit_code"])
    }

    @Test
    fun preservesCwdAcrossCommands() = runTest {
        shell.run("cd /tmp", timeoutSeconds = 10)
        val result = shell.run("pwd", timeoutSeconds = 10)
        assertEquals("/tmp", result["stdout"])
    }

    @Test
    fun reportsNonZeroExitCode() = runTest {
        val result = shell.run("exit 7", timeoutSeconds = 10)
        assertEquals(false, result["success"])
        assertEquals(7, result["exit_code"])
    }

    @Test
    fun pathIncludesMicromambaRootBin() = runTest {
        val result = shell.run("echo \$PATH", timeoutSeconds = 10)
        val stdout = result["stdout"] as String
        assertTrue(stdout.contains(File(baseDir, "root/bin").absolutePath))
    }
}
