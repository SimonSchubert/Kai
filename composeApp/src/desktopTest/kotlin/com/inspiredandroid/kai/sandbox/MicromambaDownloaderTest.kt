package com.inspiredandroid.kai.sandbox

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
        val fakeBytes = "fake-micromamba-binary-contents".toByteArray()
        val engine = MockEngine { request ->
            respond(
                content = fakeBytes,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentLength, fakeBytes.size.toString()),
            )
        }
        val downloader = MicromambaDownloader(HttpClient(engine))
        val target = File(targetDir, "micromamba")

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
        val target = File(targetDir, "micromamba")

        assertFailsWith<java.io.IOException> {
            downloader.download(target) { }
        }
        assertTrue(!target.exists())
    }
}
