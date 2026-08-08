package com.inspiredandroid.kai.linux

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AptPackageManagerTest {

    @Test
    fun `parses dpkg-query output into name and version`() {
        val raw = listOf(
            "ii \tbash\t5.2.15-2+b7",
            "ii \tcoreutils\t9.1-1",
            "ii \tpython3\t3.11.2-1+b1",
        ).joinToString("\n")

        assertEquals(
            listOf(
                PackageEntry("bash", "5.2.15-2+b7"),
                PackageEntry("coreutils", "9.1-1"),
                PackageEntry("python3", "3.11.2-1+b1"),
            ),
            AptPackageManager.parseInstalled(raw),
        )
    }

    @Test
    fun `skips packages that are removed but keep their config`() {
        // `rc` = removed, config files remain. dpkg-query -W lists these, but they
        // are not usable and offering to uninstall them again is nonsense.
        val raw = listOf(
            "ii \tgit\t1:2.39.5-0+deb12u2",
            "rc \tnano\t7.2-1",
            "iU \thalf-configured\t1.0",
        ).joinToString("\n")

        assertEquals(listOf(PackageEntry("git", "1:2.39.5-0+deb12u2")), AptPackageManager.parseInstalled(raw))
    }

    @Test
    fun `installed list is sorted by name regardless of dpkg order`() {
        val raw = "ii \tzlib1g\t1:1.2.13\nii \tbash\t5.2.15"

        assertEquals(listOf("bash", "zlib1g"), AptPackageManager.parseInstalled(raw).map { it.name })
    }

    @Test
    fun `parses apt-cache search into name and description with no version`() {
        val raw = """
            fastfetch - Fast system information tool
            fastjar - Jar creation utility
        """.trimIndent()

        assertEquals(
            listOf(
                PackageEntry("fastfetch", "", "Fast system information tool"),
                PackageEntry("fastjar", "", "Jar creation utility"),
            ),
            AptPackageManager.parseSearch(raw),
        )
    }

    @Test
    fun `search skips apt diagnostics and continuation lines`() {
        val raw = """
            W: Target Packages is configured multiple times
            fastfetch - Fast system information tool
            E: Unable to locate package nope
              indented continuation text
        """.trimIndent()

        assertEquals(listOf("fastfetch"), AptPackageManager.parseSearch(raw).map { it.name })
    }

    @Test
    fun `only the E prefix counts as a failure`() {
        assertFalse(AptPackageManager.hasErrors("Reading package lists...", "W: unsigned repository"))
        assertTrue(AptPackageManager.hasErrors("", "E: Unable to locate package sshpazz"))
    }

    @Test
    fun `reads the upgraded count off apt's summary line`() {
        val stdout = """
            Reading package lists...
            Setting up libc6:arm64 (2.36-9+deb12u10) ...
            4 upgraded, 0 newly installed, 0 to remove and 0 not upgraded.
        """.trimIndent()

        assertEquals(4, AptPackageManager.countUpgraded(stdout))
        assertEquals(
            0,
            AptPackageManager.countUpgraded("0 upgraded, 0 newly installed, 0 to remove and 0 not upgraded."),
        )
        assertEquals(0, AptPackageManager.countUpgraded("Reading package lists..."))
    }

    @Test
    fun `install avoids recommends so a phone rootfs stays small`() {
        assertEquals(
            "apt-get install -y --no-install-recommends 'python3-pip'",
            AptPackageManager.installCommand("python3-pip"),
        )
        assertEquals("apt-get remove -y 'python3-pip'", AptPackageManager.removeCommand("python3-pip"))
    }

    @Test
    fun `dpkg format asks for the status field parseInstalled filters on`() {
        assertTrue(AptPackageManager.listInstalledCommand.contains("\${db:Status-Abbrev}"))
        assertTrue(AptPackageManager.listInstalledCommand.contains("\${Package}"))
        assertTrue(AptPackageManager.listInstalledCommand.contains("\${Version}"))
    }
}
