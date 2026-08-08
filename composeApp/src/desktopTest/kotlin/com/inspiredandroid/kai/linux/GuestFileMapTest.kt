package com.inspiredandroid.kai.linux

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuestFileMapTest {

    private lateinit var base: File
    private lateinit var rootfs: File
    private lateinit var projects: File
    private lateinit var tmp: File
    private lateinit var externalHome: File

    @BeforeTest
    fun setUp() {
        base = Files.createTempDirectory("guest-map").toFile()
        rootfs = File(base, "rootfs").apply { mkdirs() }
        projects = File(base, "projects").apply { mkdirs() }
        tmp = File(base, "tmp").apply { mkdirs() }
        externalHome = File(base, "sandbox-home").apply { mkdirs() }
        File(rootfs, "root").mkdirs()
    }

    @AfterTest
    fun tearDown() {
        base.deleteRecursively()
    }

    /** The current layout: /root on the rootfs, projects bound in beneath it. */
    private fun sharedMap() = GuestFileMap(
        rootfsDir = rootfs,
        homeDir = File(rootfs, "root"),
        projectsDir = projects,
        tmpDir = tmp,
    )

    /** Pre-unification chat sandbox: /root bound from external storage, no projects. */
    private fun legacyMap() = GuestFileMap(
        rootfsDir = rootfs,
        homeDir = externalHome,
        projectsDir = null,
        tmpDir = tmp,
    )

    @Test
    fun `paths outside the binds resolve into the rootfs`() {
        assertEquals(File(rootfs, "etc/hosts"), sharedMap().resolve("/etc/hosts"))
        assertEquals(rootfs, sharedMap().resolve("/"))
    }

    @Test
    fun `root maps to the home directory of the layout`() {
        assertEquals(File(rootfs, "root/.ssh/config"), sharedMap().resolve("/root/.ssh/config"))
        assertEquals(File(externalHome, ".ssh/config"), legacyMap().resolve("/root/.ssh/config"))
    }

    @Test
    fun `projects wins over root because it is the more specific bind`() {
        // /root/projects is a mount point inside the rootfs; resolving it there
        // would list an empty directory instead of the user's project folders.
        assertEquals(File(projects, "demo/main.py"), sharedMap().resolve("/root/projects/demo/main.py"))
        assertEquals(projects, sharedMap().resolve("/root/projects"))
    }

    @Test
    fun `without a projects bind the path stays under home`() {
        assertEquals(File(externalHome, "projects/demo"), legacyMap().resolve("/root/projects/demo"))
    }

    @Test
    fun `tmp maps to the bound tmp directory`() {
        assertEquals(File(tmp, "kai-pid"), sharedMap().resolve("/tmp/kai-pid"))
    }

    @Test
    fun `traversal and relative paths are rejected`() {
        val map = sharedMap()
        assertNull(map.resolve("/root/../../etc/passwd"))
        assertNull(map.resolve("/etc/../../escape"))
        assertNull(map.resolve("etc/hosts"))
        assertNull(map.resolve("../etc"))
    }

    @Test
    fun `a symlink pointing out of its root does not resolve`() {
        val outside = File(base, "outside").apply { mkdirs() }
        File(outside, "secret").writeText("nope")
        Files.createSymbolicLink(File(rootfs, "escape").toPath(), outside.toPath())

        assertNull(sharedMap().resolve("/escape/secret"))
    }

    @Test
    fun `bind roots are protected from rename and delete`() {
        val map = sharedMap()
        assertTrue(map.isRoot(rootfs))
        assertTrue(map.isRoot(projects))
        assertTrue(map.isRoot(tmp))
        assertTrue(map.isRoot(File(rootfs, "root")))
        assertFalse(map.isRoot(File(rootfs, "etc")))
        assertFalse(map.isRoot(File(projects, "demo")))
    }

    @Test
    fun `an empty path is the filesystem root`() {
        assertEquals(rootfs, sharedMap().resolve(""))
        assertEquals(rootfs, sharedMap().resolve("   "))
    }
}
