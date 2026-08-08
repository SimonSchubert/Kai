package com.inspiredandroid.kai.linux

/**
 * `${db:Status-Abbrev}` is dpkg's two-letter want/state pair (plus a padding
 * character), which is the only way to tell a really-installed package from one
 * that was removed with its config files left behind — `dpkg-query -W` lists both.
 */
private const val DPKG_FORMAT =
    """${'$'}{db:Status-Abbrev}\t${'$'}{Package}\t${'$'}{Version}\n"""

/** `N upgraded, M newly installed, K to remove and L not upgraded.` */
private val UPGRADE_SUMMARY = Regex("""(\d+)\s+upgraded""")

/** Installed and configured. Anything else (`rc`, `iU`, …) is not usable. */
private const val STATUS_INSTALLED = "ii"

object AptPackageManager : PackageManagerSpec {

    override val listInstalledCommand = "dpkg-query -W -f='$DPKG_FORMAT' 2>/dev/null"

    override val updateCommand = "apt-get update -y"

    override val upgradeCommand = "apt-get upgrade -y"

    override fun searchCommand(query: String, limit: Int): String = "apt-cache search ${shellQuote(query)} | head -n $limit"

    // --no-install-recommends keeps a phone-sized rootfs from pulling in docs,
    // X11 and systemd dependencies it can never use.
    override fun installCommand(name: String): String = "apt-get install -y --no-install-recommends ${shellQuote(name)}"

    override fun removeCommand(name: String): String = "apt-get remove -y ${shellQuote(name)}"

    override fun parseInstalled(raw: String): List<PackageEntry> = raw.lineSequence()
        .mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 3) return@mapNotNull null
            if (parts[0].trim() != STATUS_INSTALLED) return@mapNotNull null
            val name = parts[1].trim()
            if (name.isEmpty()) return@mapNotNull null
            PackageEntry(name, parts[2].trim())
        }
        .distinctBy { "${it.name}@${it.version}" }
        .sortedBy { it.name.lowercase() }
        .toList()

    // `apt-cache search` prints `name - short description` and no version. The
    // Packages list renders a blank version as no version at all, and the install
    // action only needs the name.
    override fun parseSearch(raw: String): List<PackageEntry> = raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("E:") && !it.startsWith("W:") }
        .mapNotNull { line ->
            val sepIdx = line.indexOf(" - ")
            val name = (if (sepIdx >= 0) line.substring(0, sepIdx) else line).trim()
            if (name.isEmpty() || name.contains(' ')) return@mapNotNull null
            val description = if (sepIdx >= 0) line.substring(sepIdx + 3).trim() else null
            PackageEntry(name, version = "", description = description?.takeIf { it.isNotEmpty() })
        }
        .distinctBy { it.name }
        .toList()

    // apt marks real failures with an `E:` prefix. `W:` warnings (unsigned repo,
    // missing translation index) are routine inside a proot rootfs.
    override fun hasErrors(stdout: String, stderr: String): Boolean = stdout.lineSequence().any { it.startsWith("E:") } ||
        stderr.lineSequence().any { it.startsWith("E:") }

    override fun countUpgraded(stdout: String): Int = stdout.lineSequence()
        .firstNotNullOfOrNull { line -> UPGRADE_SUMMARY.find(line)?.groupValues?.get(1)?.toIntOrNull() }
        ?: 0
}
