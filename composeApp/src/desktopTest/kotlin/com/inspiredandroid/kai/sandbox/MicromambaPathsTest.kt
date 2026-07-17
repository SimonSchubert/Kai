package com.inspiredandroid.kai.sandbox

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MicromambaPathsTest {

    private lateinit var baseDir: File

    @BeforeTest
    fun setUp() {
        baseDir = Files.createTempDirectory("micromamba-paths-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        baseDir.deleteRecursively()
    }

    @Test
    fun downloadUrlForX86_64() {
        val url = micromambaDownloadUrl(osArch = "amd64")
        assertEquals(
            "https://micro.mamba.pm/api/micromamba/linux-64/latest",
            url,
        )
    }

    @Test
    fun downloadUrlForAarch64() {
        val url = micromambaDownloadUrl(osArch = "aarch64")
        assertEquals(
            "https://micro.mamba.pm/api/micromamba/linux-aarch64/latest",
            url,
        )
    }

    @Test
    fun layoutPointsBinaryUnderBinDir() {
        val layout = MicromambaLayout(baseDir)
        assertEquals(File(baseDir, "bin/micromamba"), layout.binaryFile)
    }

    @Test
    fun layoutPointsRootPrefixUnderRootDir() {
        val layout = MicromambaLayout(baseDir)
        assertEquals(File(baseDir, "root"), layout.rootPrefix)
    }

    @Test
    fun layoutDoesNotCreateDirsOnConstruction() {
        MicromambaLayout(baseDir)
        assertTrue(baseDir.listFiles()?.isEmpty() != false)
    }
}
