package com.inspiredandroid.kai.build.runtime

import android.content.Context
import java.io.File

/**
 * Storage layout for Kai Build. Completely separate from the chat Alpine sandbox
 * under filesDir/linux-sandbox.
 */
class BuildPaths(context: Context) {
    private val appContext = context.applicationContext

    val buildDir: File get() = File(appContext.filesDir, "kai-build")
    val rootfsDir: File get() = File(buildDir, "rootfs")
    val tmpDir: File get() = File(buildDir, "tmp")

    /** Written once Debian is bootstrapped; its absence means "partial install". */
    val readyMarker: File get() = File(buildDir, "ready")

    val archiveFile: File get() = File(buildDir, "rootfs.tar.xz")

    /**
     * Host folder for project sources. Bind-mounted to `/root/projects` only
     * (not all of `/root`), so agent binaries can live on the executable rootfs
     * while code stays USB/MTP reachable under external files.
     */
    val homeDir: File by lazy {
        File(appContext.getExternalFilesDir(null) ?: buildDir, "kai-build-home")
    }

    /** Every Kai Build project is a folder in here — `/root/projects` inside Debian. */
    val projectsDir: File get() = File(homeDir, "projects")

    /** Mount point inside the rootfs for the projects bind. Must exist before proot runs. */
    val rootfsProjectsMount: File get() = File(rootfsDir, "root/projects")

    val nativeLibDir: String get() = appContext.applicationInfo.nativeLibraryDir
    val prootPath: String get() = File(nativeLibDir, "libproot.so").absolutePath
    val tallocTarget: File get() = File(buildDir, "libtalloc.so.2")

    fun ensureLayout() {
        listOf(buildDir, tmpDir, homeDir, projectsDir, rootfsProjectsMount).forEach { it.mkdirs() }
        // Agent installers drop binaries under ~/.local/bin on the rootfs (executable).
        File(rootfsDir, "root/.local/bin").mkdirs()
    }
}
