package com.inspiredandroid.kai.linux

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LinuxDistroTest {

    @Test
    fun `unknown or missing id falls back to the default rather than throwing`() {
        assertEquals(LinuxDistro.DEFAULT, LinuxDistro.fromId(null))
        assertEquals(LinuxDistro.DEFAULT, LinuxDistro.fromId(""))
        assertEquals(LinuxDistro.DEFAULT, LinuxDistro.fromId("gentoo"))
    }

    @Test
    fun `ids round-trip so a stored setting survives`() {
        LinuxDistro.entries.forEach { distro ->
            assertEquals(distro, LinuxDistro.fromId(distro.id))
        }
    }

    @Test
    fun `debian is the default and legacy installs are alpine`() {
        // A rootfs with no marker predates the distro choice, and Alpine was the
        // only thing the chat sandbox could have been.
        assertEquals(LinuxDistro.DEBIAN, LinuxDistro.DEFAULT)
        assertEquals(LinuxDistro.ALPINE, LinuxDistro.LEGACY)
    }

    @Test
    fun `each distro uses its own package manager`() {
        assertSame(AptPackageManager, LinuxDistro.DEBIAN.packageManager)
        assertSame(ApkPackageManager, LinuxDistro.ALPINE.packageManager)
    }

    @Test
    fun `bash is always present because every shell session execs it`() {
        LinuxDistro.entries.forEach { distro ->
            assertTrue("bash" in distro.basePackages, "${distro.id} must ship bash")
            assertTrue("bash" in distro.protectedPackages, "${distro.id} must never offer to remove bash")
        }
    }

    @Test
    fun `base and optional sets do not overlap`() {
        LinuxDistro.entries.forEach { distro ->
            val overlap = distro.basePackages.intersect(distro.optionalPackages.toSet())
            assertTrue(overlap.isEmpty(), "${distro.id} lists ${overlap.joinToString()} twice")
        }
    }

    @Test
    fun `optional bundle carries the remote-server tooling the shell tool advertises`() {
        LinuxDistro.entries.forEach { distro ->
            listOf("openssh-client", "lftp", "rsync").forEach { pkg ->
                assertTrue(pkg in distro.optionalPackages, "${distro.id} is missing $pkg")
            }
        }
    }

    @Test
    fun `optional packages are never protected so the Packages tab can remove them`() {
        LinuxDistro.entries.forEach { distro ->
            distro.optionalPackages.forEach { pkg ->
                assertFalse(pkg in distro.protectedPackages, "${distro.id} protects optional $pkg")
            }
        }
    }

    @Test
    fun `debian base carries what the coding-agent installers need`() {
        // tar: OpenCode's installer extracts a .tar.gz. coreutils: Claude's checks
        // a sha256sum. ca-certificates/curl: every vendor script is a curl | bash.
        listOf("tar", "coreutils", "ca-certificates", "curl", "python3").forEach {
            assertTrue(it in LinuxDistro.DEBIAN.basePackages, "Debian base is missing $it")
        }
    }
}
