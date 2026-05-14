package com.inspiredandroid.kai.sandbox

import java.io.File

/**
 * Manages the sandbox user's ~/.ssh directory: a seeded defaults block
 * (ControlMaster + keepalives) plus per-host alias blocks added by
 * [com.inspiredandroid.kai.tools.SshConfigureHostTool].
 *
 * Pure JVM file IO — no Android dependencies — so it can be unit-tested.
 * Lives in jvmShared but is only consumed on Android (the sandbox is
 * Android-only); desktop just doesn't reference it.
 *
 * All blocks we own are bracketed with `# kai:<marker>:start` /
 * `# kai:<marker>:end` so upserts are idempotent without disturbing
 * anything the user wrote outside the markers.
 */
class SshConfigManager(private val homeDir: File) {

    private val sshDir: File get() = File(homeDir, ".ssh")
    private val configFile: File get() = File(sshDir, "config")
    private val knownHostsFile: File get() = File(sshDir, "known_hosts")

    fun ensureDefaults() {
        ensureSshDir()
        val current = readConfig()
        val updated = upsertBlock(current, DEFAULTS_MARKER, defaultsBody())
        if (updated != current) writeConfig(updated)
    }

    /**
     * Upsert a Host alias block. Returns true if the file changed.
     * Always re-seeds the defaults block as a side effect so a first-time
     * caller doesn't have to remember to do it separately.
     */
    fun upsertHost(
        alias: String,
        hostname: String,
        user: String? = null,
        port: Int? = null,
        identityFile: String? = null,
    ): Boolean {
        require(alias.isNotBlank()) { "alias must not be blank" }
        require(alias.none { it.isWhitespace() }) { "alias must not contain whitespace" }
        require(hostname.isNotBlank()) { "hostname must not be blank" }
        require(hostname.none { it.isWhitespace() }) { "hostname must not contain whitespace" }
        if (port != null) require(port in 1..65535) { "port must be in 1..65535" }

        ensureSshDir()
        val current = readConfig()
        val withDefaults = upsertBlock(current, DEFAULTS_MARKER, defaultsBody())
        val body = buildString {
            appendLine("Host $alias")
            appendLine("    HostName $hostname")
            if (user != null) appendLine("    User $user")
            if (port != null) appendLine("    Port $port")
            if (identityFile != null) {
                appendLine("    IdentityFile ${resolveIdentity(identityFile)}")
                append("    IdentitiesOnly yes")
            }
        }.trimEnd()
        val updated = upsertBlock(withDefaults, hostMarker(alias), body)
        if (updated == current) return false
        writeConfig(updated)
        return true
    }

    /** Append a known_hosts line if not already present (exact-line dedupe). */
    fun appendKnownHostLine(line: String): Boolean {
        require(line.isNotBlank()) { "known_host line must not be blank" }
        val trimmed = line.trim()
        require(!trimmed.contains('\n')) { "known_host line must be a single line" }
        ensureSshDir()
        val existing = if (knownHostsFile.isFile) knownHostsFile.readText() else ""
        if (existing.lineSequence().any { it.trim() == trimmed }) return false
        val joined = buildString {
            append(existing)
            if (existing.isNotEmpty() && !existing.endsWith("\n")) append('\n')
            appendLine(trimmed)
        }
        knownHostsFile.writeText(joined)
        lockDown(knownHostsFile, executable = false)
        return true
    }

    /** Aliases currently bracketed by our markers. */
    fun listAliases(): List<String> {
        val regex = Regex("""# kai:host:([^:\s]+):start""")
        return regex.findAll(readConfig()).map { it.groupValues[1] }.toList()
    }

    private fun ensureSshDir() {
        if (!sshDir.isDirectory) sshDir.mkdirs()
        lockDown(sshDir, executable = true)
    }

    private fun readConfig(): String = if (configFile.isFile) configFile.readText() else ""

    private fun writeConfig(content: String) {
        configFile.writeText(content)
        lockDown(configFile, executable = false)
    }

    private fun resolveIdentity(path: String): String = when {
        path.startsWith("/") || path.startsWith("~") -> path
        else -> "~/.ssh/$path"
    }

    private fun lockDown(file: File, executable: Boolean) {
        // openssh's StrictModes refuses to load configs/keys with group/world
        // perms. Java's setX(false, false) clears the other/group bits;
        // setX(true, true) sets the owner bit. Net result is 600 (or 700 for
        // dirs). Each call is wrapped because filesystems that don't honor
        // POSIX modes (rare on Android internal storage but possible on FAT)
        // would otherwise throw and abort the whole write.
        runCatching { file.setReadable(false, false) }
        runCatching { file.setReadable(true, true) }
        runCatching { file.setWritable(false, false) }
        runCatching { file.setWritable(true, true) }
        runCatching { file.setExecutable(false, false) }
        if (executable) runCatching { file.setExecutable(true, true) }
    }

    private fun defaultsBody(): String = """
        Host *
            ServerAliveInterval 30
            ServerAliveCountMax 3
            StrictHostKeyChecking accept-new
    """.trimIndent()
    // Note: ControlMaster/ControlPath/ControlPersist were intentionally removed.
    // openssh's mux protocol creates the control socket via link() for atomic
    // create-or-fail. Android's protected_hardlinks sysctl (plus SELinux policy
    // for the untrusted_app domain) refuses hard-link creation from app
    // processes regardless of the file's ownership or mode. proot can't fake
    // around this because the kernel enforces against the real Android uid.
    // Multiplexing simply doesn't work in this sandbox; leaving the config in
    // produces a muxserver_listen: link ... Permission denied error on every
    // call. The alias config is still useful as a single source of truth for
    // host/user/port/identity per remote — just without held connections.

    private fun hostMarker(alias: String) = "host:$alias"

    private fun upsertBlock(text: String, marker: String, body: String): String {
        val startTag = "# kai:$marker:start"
        val endTag = "# kai:$marker:end"
        val newBlock = "$startTag\n$body\n$endTag"

        // Match the block plus any whitespace immediately before/after so
        // repeated upserts don't accumulate blank lines.
        val pattern = Regex(
            """\n*${Regex.escape(startTag)}.*?${Regex.escape(endTag)}\n*""",
            RegexOption.DOT_MATCHES_ALL,
        )
        return if (pattern.containsMatchIn(text)) {
            val replaced = pattern.replace(text, "\n\n$newBlock\n\n")
            normalize(replaced)
        } else {
            val base = text.trimEnd('\n')
            val joiner = if (base.isEmpty()) "" else "\n\n"
            normalize("$base$joiner$newBlock\n")
        }
    }

    private fun normalize(text: String): String {
        // Collapse runs of >2 blank lines to a single blank line and ensure a
        // single trailing newline. Leaves single blank-line block separators
        // alone — those are intentional.
        val collapsed = Regex("""\n{3,}""").replace(text, "\n\n")
        val trimmedLeading = collapsed.trimStart('\n')
        return trimmedLeading.trimEnd('\n') + "\n"
    }

    companion object {
        private const val DEFAULTS_MARKER = "defaults"
    }
}
