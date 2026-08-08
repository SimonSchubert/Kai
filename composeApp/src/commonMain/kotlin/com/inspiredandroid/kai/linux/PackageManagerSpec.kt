package com.inspiredandroid.kai.linux

import androidx.compose.runtime.Immutable

@Immutable
data class PackageEntry(
    val name: String,
    val version: String,
    val description: String? = null,
)

/**
 * Everything that differs between `apk` and `apt` when the Packages tab, the
 * sandbox installer and the Kai Build installer drive a package manager: the
 * commands to run and how to read what they printed.
 *
 * Exit codes are deliberately not part of this contract — under proot both
 * package managers return codes that do not mean what they normally mean, so
 * callers verify by re-reading the installed list or by [hasErrors].
 */
interface PackageManagerSpec {

    /** Prints one installed package per line for [parseInstalled]. */
    val listInstalledCommand: String

    /** Refreshes the package index. Safe to run repeatedly. */
    val updateCommand: String

    /** Upgrades every installed package. Assumes [updateCommand] ran first. */
    val upgradeCommand: String

    /** Searches names *and* descriptions, capped at [limit] lines, for [parseSearch]. */
    fun searchCommand(query: String, limit: Int): String

    fun installCommand(name: String): String

    fun removeCommand(name: String): String

    fun parseInstalled(raw: String): List<PackageEntry>

    fun parseSearch(raw: String): List<PackageEntry>

    /** True when the streams carry a real failure rather than routine noise. */
    fun hasErrors(stdout: String, stderr: String): Boolean

    /** How many packages [upgradeCommand] actually replaced. */
    fun countUpgraded(stdout: String): Int
}

/** Single-quotes [s] for `sh -c`, escaping any embedded quote. */
internal fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
