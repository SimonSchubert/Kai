package com.inspiredandroid.kai.sandbox

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Builds a real `.tar.bz2` archive containing a trivial `bin/micromamba` script, matching the real release layout. */
fun buildFakeMicromambaArchive(stagingDir: File, echoText: String = "fake-micromamba"): ByteArray {
    val binDir = File(stagingDir, "bin")
    binDir.mkdirs()
    val fakeBinary = File(binDir, "micromamba")
    fakeBinary.writeText("#!/bin/sh\necho $echoText \"\$@\"\n")
    fakeBinary.setExecutable(true, false)
    val archiveFile = File(stagingDir, "fake-micromamba.tar.bz2")
    val process = ProcessBuilder(
        "tar", "-cjf", archiveFile.absolutePath,
        "-C", stagingDir.absolutePath,
        "bin/micromamba",
    ).redirectErrorStream(true).start()
    check(process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0) {
        "Failed to build test fixture archive: ${process.inputStream.bufferedReader().readText()}"
    }
    return archiveFile.readBytes()
}

class MicromambaDownloaderTest {

    private lateinit var targetDir: File

    @BeforeTest
    fun setUp() {
        targetDir = Files.createTempDirectory("micromamba-downloader-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        targetDir.deleteRecursively()
    }

    @Test
    fun downloadsBytesToTargetFile() = runTest {
        val fakeBytes = "fake-archive-contents".toByteArray()
        val engine = MockEngine { request ->
            respond(
                content = fakeBytes,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentLength, fakeBytes.size.toString()),
            )
        }
        val downloader = MicromambaDownloader(HttpClient(engine))
        val target = File(targetDir, "micromamba.tar.bz2")

        var lastProgress = 0f
        downloader.download(target) { progress -> lastProgress = progress }

        assertTrue(target.exists())
        assertEquals(fakeBytes.toList(), target.readBytes().toList())
        assertEquals(1f, lastProgress)
    }

    @Test
    fun throwsOnNonSuccessStatus() = runTest {
        val engine = MockEngine { respond(content = "", status = HttpStatusCode.NotFound) }
        val downloader = MicromambaDownloader(HttpClient(engine))
        val target = File(targetDir, "micromamba.tar.bz2")

        assertFailsWith<java.io.IOException> {
            downloader.download(target) { }
        }
        assertTrue(!target.exists())
    }

    @Test
    fun extractBinaryProducesARealExecutableFromTheArchive() {
        val stagingDir = File(targetDir, "staging").apply { mkdirs() }
        val archiveBytes = buildFakeMicromambaArchive(stagingDir, echoText = "hello-from-extracted-binary")
        val archiveFile = File(targetDir, "micromamba.tar.bz2").apply { writeBytes(archiveBytes) }
        val baseDir = File(targetDir, "install")

        val downloader = MicromambaDownloader(HttpClient(MockEngine { respond("") }))
        downloader.extractBinary(archiveFile, baseDir)

        val binaryFile = File(baseDir, "bin/micromamba")
        assertTrue(binaryFile.exists())
        assertTrue(binaryFile.canExecute())

        // The whole point: actually run it, not just check exists()/canExecute().
        val process = ProcessBuilder(binaryFile.absolutePath).redirectErrorStream(true).start()
        val finished = process.waitFor(10, TimeUnit.SECONDS)
        val output = process.inputStream.bufferedReader().readText()
        assertTrue(finished, "extracted binary did not exit within timeout")
        assertEquals(0, process.exitValue())
        assertTrue(output.contains("hello-from-extracted-binary"), "unexpected output: $output")
    }

    @Test
    fun extractBinaryThrowsWhenArchiveHasNoBinMicromamba() {
        val stagingDir = File(targetDir, "staging-empty").apply { mkdirs() }
        val otherFile = File(stagingDir, "not-micromamba.txt").apply { writeText("nope") }
        val archiveFile = File(targetDir, "empty.tar.bz2")
        val process = ProcessBuilder(
            "tar", "-cjf", archiveFile.absolutePath,
            "-C", stagingDir.absolutePath,
            otherFile.name,
        ).start()
        check(process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0)

        val downloader = MicromambaDownloader(HttpClient(MockEngine { respond("") }))
        assertFailsWith<java.io.IOException> {
            downloader.extractBinary(archiveFile, File(targetDir, "install-empty"))
        }
    }
}
