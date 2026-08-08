package com.inspiredandroid.kai.linux

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApkPackageManagerTest {

    @Test
    fun `parses apk info -v output into name and version`() {
        val raw = """
            bash-5.2.37-r0
            busybox-1.37.0-r12
            curl-8.12.1-r1
        """.trimIndent()

        assertEquals(
            listOf(
                PackageEntry("bash", "5.2.37-r0"),
                PackageEntry("busybox", "1.37.0-r12"),
                PackageEntry("curl", "8.12.1-r1"),
            ),
            ApkPackageManager.parseInstalled(raw),
        )
    }

    @Test
    fun `keeps hyphen-digit segments that belong to the package name`() {
        val parsed = ApkPackageManager.parseInstalled("webkit2gtk-4.1-2.46.5-r0\nglib-2.0-2.82.4-r0")

        assertEquals(listOf("webkit2gtk-4.1", "glib-2.0"), parsed.map { it.name })
        assertEquals(listOf("2.46.5-r0", "2.82.4-r0"), parsed.map { it.version })
    }

    @Test
    fun `drops apk warning and error lines`() {
        val raw = """
            WARNING: opening from cache https://dl-cdn.alpinelinux.org: No such file
            bash-5.2.37-r0
            ERROR: unable to select packages
        """.trimIndent()

        assertEquals(listOf(PackageEntry("bash", "5.2.37-r0")), ApkPackageManager.parseInstalled(raw))
    }

    @Test
    fun `search output splits description off the name-version`() {
        val raw = "fastfetch-2.36.1-r0 - Neofetch-like system information tool"

        assertEquals(
            listOf(PackageEntry("fastfetch", "2.36.1-r0", "Neofetch-like system information tool")),
            ApkPackageManager.parseSearch(raw),
        )
    }

    @Test
    fun `only the ERROR prefix counts as a failure`() {
        // The lowercase "errors" in apk's summary is a cumulative DB counter under
        // proot, not a failure of this run.
        assertFalse(ApkPackageManager.hasErrors("OK: 12 MiB in 20 packages", "2 errors; 5 MiB"))
        assertTrue(ApkPackageManager.hasErrors("", "ERROR: unable to select packages"))
    }

    @Test
    fun `counts one upgrade per progress line`() {
        val stdout = """
            (1/3) Upgrading busybox (1.37.0-r11 -> 1.37.0-r12)
            (2/3) Upgrading ssl_client (1.37.0-r11 -> 1.37.0-r12)
            (3/3) Upgrading musl (1.2.5-r8 -> 1.2.5-r9)
            OK: 8 MiB in 18 packages
        """.trimIndent()

        assertEquals(3, ApkPackageManager.countUpgraded(stdout))
        assertEquals(0, ApkPackageManager.countUpgraded("OK: 8 MiB in 18 packages"))
    }

    @Test
    fun `commands single-quote the package name`() {
        assertEquals("apk add --no-cache 'py3-pip'", ApkPackageManager.installCommand("py3-pip"))
        assertEquals("apk del 'py3-pip'", ApkPackageManager.removeCommand("py3-pip"))
        assertEquals("apk search -v 'fast' | head -n 50", ApkPackageManager.searchCommand("fast", 50))
    }
}
