package com.inspiredandroid.kai.sandbox

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.delay
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopLinuxSandboxManagerTest {

    private lateinit var baseDir: File

    @BeforeTest
    fun setUp() {
        baseDir = Files.createTempDirectory("desktop-sandbox-manager-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        baseDir.deleteRecursively()
    }

    private fun managerWithFakeDownload(bytes: ByteArray = byteArrayOf(1, 2, 3, 4)): DesktopLinuxSandboxManager {
        val engine = MockEngine {
            respond(
                content = bytes,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentLength, bytes.size.toString()),
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
    fun setupDownloadsBinaryAndReachesReady() = kotlinx.coroutines.runBlocking {
        val manager = managerWithFakeDownload()
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
