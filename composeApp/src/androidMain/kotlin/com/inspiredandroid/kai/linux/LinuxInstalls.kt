package com.inspiredandroid.kai.linux

import android.content.Context
import java.io.File

/**
 * The Linux installs a device can hold — at most one per distribution — and which
 * directory each of them lives in.
 *
 * There are two directories, both named before the chat sandbox and Kai Build
 * shared anything, and neither belongs to a feature any more. A distribution
 * claims whichever one already holds it, which is what lets the chat sandbox be
 * pointed from one distribution to the other and back without either install
 * being downloaded again — and what puts a Debian chat sandbox in the same
 * directory Kai Build works in, making them one install.
 */
class LinuxInstalls(context: Context) {

    private val sandboxDir = LinuxPaths.forSandbox(context)
    private val buildDir = LinuxPaths.forBuild(context)

    /**
     * Where [distro] is installed, or where it would be installed. Debian prefers
     * Kai Build's directory and Alpine the chat sandbox's, so a device that ends
     * up with both keeps each where its legacy detection expects to find it.
     */
    fun pathsFor(distro: LinuxDistro): LinuxPaths {
        val candidates = if (distro == LinuxDistro.DEBIAN) {
            listOf(buildDir, sandboxDir)
        } else {
            listOf(sandboxDir, buildDir)
        }
        candidates.firstOrNull { it.readMarker()?.distro == distro }?.let { return it }
        // Never hand back a directory holding the other distribution: installing
        // into it would delete an install the user can still switch back to.
        return candidates.firstOrNull { it.readMarker() == null } ?: candidates.first()
    }

    /**
     * The distribution in the directory the chat sandbox used before there was
     * anywhere else for one to live, if it still holds an install. This is how a
     * sandbox that predates the picker is recognised as the user's real choice.
     */
    fun distroInSandboxDir(): LinuxDistro? = sandboxDir.readMarker()?.distro

    /** Distributions with a finished install on disk. */
    fun installed(): Set<LinuxDistro> = listOfNotNull(
        sandboxDir.readMarker()?.distro,
        buildDir.readMarker()?.distro,
    ).toSet()

    /**
     * Host directory backing `/root` for [distro], or null when it has no
     * install. Each install records where its own home lives, so this is also
     * how a pre-unification sandbox's external-storage home is found.
     */
    fun homeDirFor(distro: LinuxDistro): File? {
        val paths = pathsFor(distro)
        val marker = paths.readMarker() ?: return null
        return paths.homeDir(marker)
    }
}
