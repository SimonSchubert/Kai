package com.inspiredandroid.kai.linux

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeMigrationTest {

    private lateinit var root: File
    private lateinit var source: File
    private lateinit var target: File

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("home-migration").toFile()
        source = File(root, "source").apply { mkdirs() }
        target = File(root, "target").apply { mkdirs() }
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun write(dir: File, path: String, content: String): File {
        val file = File(dir, path)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return file
    }

    @Test
    fun `survey counts only what the target is missing`() {
        write(source, "notes.txt", "abcde")
        write(source, ".ssh/id_rsa", "key")
        write(target, "notes.txt", "already here")

        val survey = HomeMigration.survey(source, target)

        assertEquals(1, survey.fileCount)
        assertEquals(3, survey.bytes)
        assertFalse(survey.isEmpty)
    }

    @Test
    fun `copy merges without touching what the target already has`() {
        write(source, "notes.txt", "from source")
        write(source, ".ssh/id_rsa", "key")
        write(source, "skills/greet/SKILL.md", "hello")
        write(target, "notes.txt", "from target")

        val copied = HomeMigration.copy(source, target)

        assertEquals(2, copied)
        assertEquals("from target", File(target, "notes.txt").readText())
        assertEquals("key", File(target, ".ssh/id_rsa").readText())
        assertEquals("hello", File(target, "skills/greet/SKILL.md").readText())
        // The source is a fallback until the user removes it, so it stays whole.
        assertEquals("from source", File(source, "notes.txt").readText())
    }

    @Test
    fun `copy merges into a directory the target already has`() {
        write(source, ".ssh/id_rsa", "key")
        write(source, ".ssh/config", "Host x")
        write(target, ".ssh/config", "kept")

        HomeMigration.copy(source, target)

        assertEquals("key", File(target, ".ssh/id_rsa").readText())
        assertEquals("kept", File(target, ".ssh/config").readText())
    }

    @Test
    fun `excluded entries are neither surveyed nor copied`() {
        write(source, "projects/app/main.kt", "fun main() {}")
        write(source, ".cache/pip/wheel", "junk")
        write(source, ".bashrc", "alias ll='ls -l'")
        write(source, ".opencode/bin/opencode", "binary")
        write(source, ".claude/settings.json", "{}")
        write(source, ".local/share/claude/versions/1.0", "binary")
        write(source, ".local/bin/myscript", "#!/bin/sh")

        assertEquals(1, HomeMigration.survey(source, target).fileCount)
        assertEquals(1, HomeMigration.copy(source, target))

        assertTrue(File(target, ".local/bin/myscript").isFile)
        assertFalse(File(target, "projects").exists())
        assertFalse(File(target, ".cache").exists())
        assertFalse(File(target, ".bashrc").exists())
        assertFalse(File(target, ".opencode").exists())
        assertFalse(File(target, ".claude").exists())
        assertFalse(File(target, ".local/share/claude").exists())
    }

    @Test
    fun `a migrated home has nothing left to migrate`() {
        write(source, "notes.txt", "abc")
        write(source, ".ssh/id_rsa", "key")

        HomeMigration.copy(source, target)

        assertTrue(HomeMigration.survey(source, target).isEmpty)
    }

    @Test
    fun `symlinks come across as links and are never followed`() {
        write(source, "real.txt", "content")
        Files.createSymbolicLink(File(source, "link.txt").toPath(), File("real.txt").toPath())
        // A loop is what makes following links unsafe on a real rootfs.
        Files.createSymbolicLink(File(source, "loop").toPath(), File(source, "loop").toPath())

        val copied = HomeMigration.copy(source, target)

        assertEquals(3, copied)
        assertTrue(Files.isSymbolicLink(File(target, "link.txt").toPath()))
        assertTrue(Files.isSymbolicLink(File(target, "loop").toPath()))
        assertEquals("content", File(target, "link.txt").readText())
    }

    @Test
    fun `private key permissions survive the copy`() {
        val key = write(source, ".ssh/id_rsa", "key")
        Files.setPosixFilePermissions(key.toPath(), setOf(java.nio.file.attribute.PosixFilePermission.OWNER_READ))

        HomeMigration.copy(source, target)

        assertEquals(
            setOf(java.nio.file.attribute.PosixFilePermission.OWNER_READ),
            Files.getPosixFilePermissions(File(target, ".ssh/id_rsa").toPath()),
        )
    }

    @Test
    fun `an empty or missing source is nothing to offer`() {
        assertTrue(HomeMigration.survey(source, target).isEmpty)
        assertTrue(HomeMigration.survey(File(root, "nope"), target).isEmpty)
        assertEquals(0, HomeMigration.copy(File(root, "nope"), target))
    }
}
