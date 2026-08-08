package com.inspiredandroid.kai.linux

private val ALPINE_REVISION_SUFFIX = Regex("-r\\d+$")

private val UPGRADE_PROGRESS_LINE = Regex("""^\(\d+/\d+\)\s+Upgrading\s""")

/** Alpine's `apk`. */
object ApkPackageManager : PackageManagerSpec {

    override val listInstalledCommand = "apk info -v | sort"

    override val updateCommand = "apk update"

    override val upgradeCommand = "apk upgrade"

    override fun searchCommand(query: String, limit: Int): String = "apk search -v ${shellQuote(query)} | head -n $limit"

    override fun installCommand(name: String): String = "apk add --no-cache ${shellQuote(name)}"

    override fun removeCommand(name: String): String = "apk del ${shellQuote(name)}"

    override fun parseInstalled(raw: String): List<PackageEntry> = raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("WARNING:") && !it.startsWith("ERROR:") }
        .mapNotNull { line -> parseNameVersion(line)?.let { PackageEntry(it.first, it.second) } }
        .distinctBy { "${it.name}@${it.version}" }
        .toList()

    override fun parseSearch(raw: String): List<PackageEntry> = raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("WARNING:") && !it.startsWith("ERROR:") }
        .mapNotNull { line ->
            val sepIdx = line.indexOf(" - ")
            val nameVer = if (sepIdx >= 0) line.substring(0, sepIdx) else line
            val description = if (sepIdx >= 0) line.substring(sepIdx + 3).trim() else null
            parseNameVersion(nameVer)?.let { (n, v) -> PackageEntry(n, v, description?.takeIf { it.isNotEmpty() }) }
        }
        .distinctBy { "${it.name}@${it.version}" }
        .toList()

    // apk's exit code is polluted by a cumulative DB error count under proot, and
    // the lowercase "errors" in its summary line is that same count rather than a
    // per-run failure. Only the "ERROR:" prefix marks a real one.
    override fun hasErrors(stdout: String, stderr: String): Boolean = stdout.lineSequence().any { it.startsWith("ERROR:") } ||
        stderr.lineSequence().any { it.startsWith("ERROR:") }

    // apk upgrade emits one progress line per package: `(N/M) Upgrading <pkg> (...)`.
    // No matches → nothing was actually upgraded (e.g. system already up to date).
    override fun countUpgraded(stdout: String): Int = stdout.lineSequence()
        .count { UPGRADE_PROGRESS_LINE.containsMatchIn(it) }

    // Alpine package idents are `<name>-<version>-r<rev>`, but names themselves
    // can contain hyphen-digit segments (e.g. `webkit2gtk-4.1`, `glib-2.0`).
    // Strategy: peel off the trailing `-r<digits>` revision, then split at the
    // *last* `-<digit>` boundary — that's the version, anything before it is name.
    private fun parseNameVersion(s: String): Pair<String, String>? {
        if (s.isEmpty()) return null
        val revision = ALPINE_REVISION_SUFFIX.find(s)?.value.orEmpty()
        val withoutRev = if (revision.isNotEmpty()) s.dropLast(revision.length) else s
        var splitAt = -1
        for (i in withoutRev.length - 1 downTo 1) {
            if (withoutRev[i - 1] == '-' && withoutRev[i].isDigit()) {
                splitAt = i - 1
                break
            }
        }
        if (splitAt < 0) {
            return if (revision.isNotEmpty()) withoutRev to revision.trimStart('-') else s to ""
        }
        val name = withoutRev.substring(0, splitAt)
        val version = withoutRev.substring(splitAt + 1) + revision
        if (name.isEmpty()) return null
        return name to version
    }
}
