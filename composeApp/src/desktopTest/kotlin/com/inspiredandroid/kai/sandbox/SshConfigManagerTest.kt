package com.inspiredandroid.kai.sandbox

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SshConfigManagerTest {

    private lateinit var home: File
    private lateinit var mgr: SshConfigManager

    @BeforeTest
    fun setUp() {
        home = Files.createTempDirectory("ssh-cfg-test").toFile()
        mgr = SshConfigManager(home)
    }

    @AfterTest
    fun tearDown() {
        home.deleteRecursively()
    }

    private fun configText(): String = File(home, ".ssh/config").readText()
    private fun knownHostsText(): String = File(home, ".ssh/known_hosts").readText()

    @Test
    fun ensureDefaultsCreatesBlockOnce() {
        mgr.ensureDefaults()
        val first = configText()
        assertContains(first, "# kai:defaults:start")
        assertContains(first, "ServerAliveInterval 30")
        assertContains(first, "ServerAliveCountMax 3")
        assertContains(first, "StrictHostKeyChecking accept-new")
        assertContains(first, "# kai:defaults:end")
        // ControlMaster was intentionally removed — Android blocks the link()
        // syscall openssh uses to create its mux socket, so multiplexing
        // produces an error and zero benefit.
        assertFalse(first.contains("ControlMaster"))
        assertFalse(first.contains("ControlPath"))

        mgr.ensureDefaults()
        // Second call is a no-op on content.
        assertEquals(first, configText())
    }

    @Test
    fun upsertHostAddsBlockAndSeedsDefaults() {
        val changed = mgr.upsertHost(alias = "prod", hostname = "1.2.3.4")
        assertTrue(changed)
        val text = configText()
        assertContains(text, "# kai:defaults:start")
        assertContains(text, "# kai:host:prod:start")
        assertContains(text, "Host prod")
        assertContains(text, "HostName 1.2.3.4")
        assertContains(text, "# kai:host:prod:end")
    }

    @Test
    fun upsertHostIsIdempotentForSameArgs() {
        mgr.upsertHost("prod", "1.2.3.4", user = "deploy", port = 2222)
        val first = configText()
        val changed = mgr.upsertHost("prod", "1.2.3.4", user = "deploy", port = 2222)
        assertFalse(changed, "second identical upsert should report no change")
        assertEquals(first, configText())
    }

    @Test
    fun upsertHostReplacesPreviousBlock() {
        mgr.upsertHost("prod", "old.example.com", user = "root")
        mgr.upsertHost("prod", "new.example.com", user = "deploy", port = 2222)
        val text = configText()
        // Only one host block for the alias.
        val startCount = "# kai:host:prod:start".toRegex().findAll(text).count()
        val endCount = "# kai:host:prod:end".toRegex().findAll(text).count()
        assertEquals(1, startCount)
        assertEquals(1, endCount)
        // New values present, old gone.
        assertContains(text, "HostName new.example.com")
        assertContains(text, "User deploy")
        assertContains(text, "Port 2222")
        assertFalse(text.contains("old.example.com"))
        assertFalse(text.contains("User root"))
    }

    @Test
    fun upsertHostKeepsNonKaiContent() {
        File(home, ".ssh").mkdirs()
        val userPreamble = "# my hand-written notes\nHost legacy\n    HostName legacy.example\n\n"
        File(home, ".ssh/config").writeText(userPreamble)
        mgr.upsertHost("prod", "1.2.3.4")
        val text = configText()
        assertContains(text, "# my hand-written notes")
        assertContains(text, "Host legacy")
        assertContains(text, "HostName legacy.example")
        assertContains(text, "Host prod")
    }

    @Test
    fun upsertHostResolvesRelativeIdentity() {
        mgr.upsertHost("prod", "1.2.3.4", identityFile = "prod_id")
        assertContains(configText(), "IdentityFile ~/.ssh/prod_id")
        assertContains(configText(), "IdentitiesOnly yes")
    }

    @Test
    fun upsertHostKeepsAbsoluteIdentity() {
        mgr.upsertHost("prod", "1.2.3.4", identityFile = "/etc/ssh/somekey")
        assertContains(configText(), "IdentityFile /etc/ssh/somekey")
    }

    @Test
    fun upsertHostKeepsTildeIdentity() {
        mgr.upsertHost("prod", "1.2.3.4", identityFile = "~/keys/prod_id")
        assertContains(configText(), "IdentityFile ~/keys/prod_id")
    }

    @Test
    fun blankAliasRejected() {
        assertFailsWith<IllegalArgumentException> {
            mgr.upsertHost(alias = " ", hostname = "1.2.3.4")
        }
    }

    @Test
    fun whitespaceInAliasRejected() {
        assertFailsWith<IllegalArgumentException> {
            mgr.upsertHost(alias = "my host", hostname = "1.2.3.4")
        }
    }

    @Test
    fun blankHostnameRejected() {
        assertFailsWith<IllegalArgumentException> {
            mgr.upsertHost(alias = "prod", hostname = "")
        }
    }

    @Test
    fun invalidPortRejected() {
        assertFailsWith<IllegalArgumentException> {
            mgr.upsertHost(alias = "prod", hostname = "1.2.3.4", port = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            mgr.upsertHost(alias = "prod", hostname = "1.2.3.4", port = 70000)
        }
    }

    @Test
    fun listAliasesReturnsRegisteredHosts() {
        mgr.upsertHost("prod", "1.2.3.4")
        mgr.upsertHost("staging", "5.6.7.8")
        val aliases = mgr.listAliases()
        assertEquals(setOf("prod", "staging"), aliases.toSet())
    }

    @Test
    fun appendKnownHostLineDedupes() {
        val line = "example.com ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAITESTKEY"
        assertTrue(mgr.appendKnownHostLine(line))
        assertFalse(mgr.appendKnownHostLine(line), "duplicate exact line should not be re-appended")
        val text = knownHostsText()
        assertEquals(1, text.lines().count { it.trim() == line.trim() })
    }

    @Test
    fun appendKnownHostLineAppendsDistinctLines() {
        mgr.appendKnownHostLine("a.example ssh-ed25519 AAAA1")
        mgr.appendKnownHostLine("b.example ssh-ed25519 AAAA2")
        val text = knownHostsText()
        assertContains(text, "a.example")
        assertContains(text, "b.example")
        assertTrue(text.endsWith("\n"))
    }

    @Test
    fun appendKnownHostLineRejectsMultiline() {
        assertFailsWith<IllegalArgumentException> {
            mgr.appendKnownHostLine("a.example AAAA\nb.example AAAA")
        }
    }

    @Test
    fun repeatedUpsertsDoNotGrowBlankLines() {
        mgr.upsertHost("prod", "1.2.3.4")
        mgr.upsertHost("prod", "1.2.3.4", user = "deploy")
        mgr.upsertHost("prod", "1.2.3.4", user = "deploy", port = 2222)
        val text = configText()
        // No run of 3+ consecutive newlines should remain.
        assertFalse(text.contains("\n\n\n"), "should not accumulate blank lines: <<<$text>>>")
    }
}
