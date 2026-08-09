package com.inspiredandroid.kai.linux

import java.io.File
import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption

/** What moving one install's `/root` into another's would actually carry over. */
data class HomeSurvey(val fileCount: Int, val bytes: Long) {
    val isEmpty: Boolean get() = fileCount == 0
}

/**
 * Copies a user's home directory from one Linux install into another, so
 * switching the shell integration's distribution does not mean leaving SSH keys,
 * skills and files behind — and so the distribution being left can then be
 * removed without losing them.
 *
 * The copy **merges**: anything already at the destination wins and is never
 * touched, and the source is never modified. That makes it safe to run twice,
 * and makes "nothing left to copy" the natural end state — a second survey of a
 * migrated home comes back empty, which is how the offer stops being made.
 */
object HomeMigration {

    /**
     * Home entries that are never worth carrying across, matched against the
     * path relative to the home directory:
     *
     * - `projects` is a bind mount both installs already share, so copying it
     *   would duplicate Kai Build's project folders into themselves.
     * - Shell startup files ship with every rootfs and are written for that
     *   distribution's own layout; Debian's would send an Alpine shell looking
     *   for tools that are not there.
     * - `.cache` is regenerable and can be larger than everything that matters.
     * - The coding-agent dirs hold binaries compiled against the source
     *   distribution's libc, which cannot run on the target, and the state those
     *   binaries keep. Kai Build only ever runs in Debian and installs them per
     *   environment, so there is nothing here another distribution can use.
     */
    val EXCLUDED = setOf(
        "projects",
        ".cache",
        ".bashrc",
        ".bash_logout",
        ".bash_profile",
        ".profile",
        ".grok",
        ".opencode",
        ".claude",
        ".local/share/claude",
    )

    /**
     * Counts what [copy] would write — the entries the destination does not
     * already have. Walks the source tree, so call it off the main thread.
     * Directories are not counted: they carry no data of their own, and a home
     * that only needs empty folders is one with nothing to migrate.
     */
    fun survey(source: File, target: File): HomeSurvey {
        var files = 0
        var bytes = 0L
        walk(source, target) { entry, _ ->
            if (entry.isDirectory) return@walk
            files++
            // A symlink's own size is meaningless — it is whatever it points at
            // that has one, and that is either copied separately or already there.
            if (!entry.isSymlink) bytes += entry.file.length()
        }
        return HomeSurvey(files, bytes)
    }

    /**
     * Merges [source] into [target] and returns how many entries were written.
     * [onProgress] reports the running count so a long copy can say where it is.
     *
     * A single unreadable entry — a socket, a FIFO, a file whose mode the app
     * cannot open — is skipped rather than abandoning the rest of someone's home.
     */
    fun copy(source: File, target: File, onProgress: (Int) -> Unit = {}): Int {
        var copied = 0
        walk(source, target) { entry, destination ->
            val written = runCatching {
                destination.parentFile?.mkdirs()
                val options = mutableListOf<CopyOption>(StandardCopyOption.COPY_ATTRIBUTES)
                if (entry.isSymlink) options += LinkOption.NOFOLLOW_LINKS
                Files.copy(entry.file.toPath(), destination.toPath(), *options.toTypedArray())
                // COPY_ATTRIBUTES carries the mode on a Unix filesystem, but this
                // is not the place to depend on best-effort: a migrated `~/.ssh`
                // or private key that lands world-readable is one openssh refuses
                // to use.
                if (!entry.isSymlink) copyPermissions(entry.file, destination)
            }.isSuccess
            // Directories are scaffolding, not content — leaving them out keeps
            // this count the same one [survey] reported.
            if (written && !entry.isDirectory) {
                copied++
                onProgress(copied)
            }
        }
        return copied
    }

    private fun copyPermissions(from: File, to: File) {
        runCatching {
            Files.setPosixFilePermissions(to.toPath(), Files.getPosixFilePermissions(from.toPath()))
        }
    }

    private class Entry(val file: File, val isDirectory: Boolean, val isSymlink: Boolean)

    /**
     * Visits every source entry the target is missing, paired with where it would
     * go. A missing directory is reported before its children, so a consumer that
     * creates it has somewhere to put them; a directory the target already has is
     * recursed into instead, so it still receives the files it lacks.
     *
     * Symlinks are reported as entries and never followed: a rootfs contains link
     * loops, and a symlink's value is the link itself.
     */
    private fun walk(source: File, target: File, visit: (Entry, File) -> Unit) {
        if (!source.isDirectory) return
        val stack = ArrayDeque<String>()
        stack.addLast("")
        while (stack.isNotEmpty()) {
            val prefix = stack.removeLast()
            val children = runCatching { File(source, prefix).listFiles() }.getOrNull() ?: continue
            for (child in children) {
                val relative = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
                if (relative in EXCLUDED) continue
                val isSymlink = Files.isSymbolicLink(child.toPath())
                val isDirectory = !isSymlink && child.isDirectory
                val destination = File(target, relative)
                // Whatever is already there wins — including a destination that is
                // a plain file where the source has a directory, or the reverse.
                val present = Files.exists(destination.toPath(), LinkOption.NOFOLLOW_LINKS)
                if (!present) visit(Entry(child, isDirectory, isSymlink), destination)
                if (isDirectory && (!present || destination.isDirectory)) stack.addLast(relative)
            }
        }
    }
}
