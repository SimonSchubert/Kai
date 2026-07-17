package com.inspiredandroid.kai.sandbox

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopLinuxSandboxManagerTest {

    private lateinit var baseDir: File
    private lateinit var stagingDir: File

    @BeforeTest
    fun setUp() {
        baseDir = Files.createTempDirectory("desktop-sandbox-manager-test").toFile()
        stagingDir = Files.createTempDirectory("desktop-sandbox-manager-test-staging").toFile()
    }

    @AfterTest
    fun tearDown() {
        baseDir.deleteRecursively()
        stagingDir.deleteRecursively()
    }

    // Serves a real .tar.bz2 archive (built via the system tar, same shape as
    // the real micromamba release) so setup() exercises real download +
    // real extraction, not just byte-copying. Using arbitrary bytes here
    // previously hid a Critical bug: the downloaded archive was never
    // extracted, so the "binary" on disk was actually a compressed tarball.
    private fun managerWithFakeDownload(echoText: String = "fake-micromamba"): DesktopLinuxSandboxManager {
        val archiveBytes = buildFakeMicromambaArchive(stagingDir, echoText)
        val engine = MockEngine {
            respond(
                content = archiveBytes,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentLength, archiveBytes.size.toString()),
            )
        }
        return DesktopLinuxSandboxManager(baseDir, MicromambaDownloader(HttpClient(engine)))
    }

    @Test
    fun startsNotInstalled() {
        val manager = managerWithFakeDownload()
        assertIs<SandboxState.NotInstalled>(manager.state.value)
    }

    @Test
    fun setupDownloadsExtractsAndReachesReadyWithARealRunnableBinary() = kotlinx.coroutines.runBlocking {
        val manager = managerWithFakeDownload(echoText = "hello-from-setup")
        manager.setup()
        // setup() launches on its own scope; poll briefly for completion.
        var attempts = 0
        while (manager.state.value !is SandboxState.Ready && attempts < 50) {
            delay(20)
            attempts++
        }
        assertIs<SandboxState.Ready>(manager.state.value)
        assertTrue(manager.layout.binaryFile.exists())
        assertTrue(manager.layout.binaryFile.canExecute())

        // The whole point of this test: the file at binaryFile must actually
        // run, not just exist with the executable bit set. A copied-but-not-
        // extracted .tar.bz2 would pass exists()/canExecute() and fail here.
        val process = ProcessBuilder(manager.layout.binaryFile.absolutePath).redirectErrorStream(true).start()
        val finished = process.waitFor(10, TimeUnit.SECONDS)
        val output = process.inputStream.bufferedReader().readText()
        assertTrue(finished, "extracted binary did not exit within timeout")
        assertEquals(0, process.exitValue())
        assertTrue(output.contains("hello-from-setup"), "unexpected output: $output")

        // The intermediate archive must not be left behind next to the binary.
        assertFalse(File(baseDir, "micromamba-download.tar.bz2").exists())
    }

    @Test
    fun resetDeletesEverythingAndReturnsToNotInstalled() = kotlinx.coroutines.runBlocking {
        val manager = managerWithFakeDownload()
        manager.setup()
        var attempts = 0
        while (manager.state.value !is SandboxState.Ready && attempts < 50) {
            delay(20)
            attempts++
        }
        manager.reset()
        attempts = 0
        while (manager.state.value !is SandboxState.NotInstalled && attempts < 50) {
            delay(20)
            attempts++
        }
        assertIs<SandboxState.NotInstalled>(manager.state.value)
        assertFalse(manager.layout.binaryFile.exists())
    }

    @Test
    fun arePackagesInstalledFalseWhenNotReady() {
        val manager = managerWithFakeDownload()
        assertFalse(manager.arePackagesInstalled())
    }

    @Test
    fun diskUsageZeroWhenNotInstalled() {
        val manager = managerWithFakeDownload()
        assertEquals(0L, manager.getDiskUsageMB())
    }
}
