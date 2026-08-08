package com.inspiredandroid.kai.linux

import android.content.Context
import java.io.File

/** Directory under `filesDir` holding the chat sandbox's Linux. */
const val SANDBOX_DIR_NAME = "linux-sandbox"

/** Directory under `filesDir` holding Kai Build's own Linux, when it needs one. */
const val BUILD_DIR_NAME = "kai-build"

/** Pre-marker Kai Build installs recorded completion in this file. */
const val LEGACY_READY_FILE = "ready"

/**
 * Every Alpine rootfs ships this and no Debian one does, which makes it proof
 * that a marker-less chat sandbox is a finished pre-unification install rather
 * than a Debian that is still being extracted.
 */
const val ALPINE_RELEASE_FILE = "rootfs/etc/alpine-release"

private const val MARKER_FILE = "install"
private const val KEY_DISTRO = "distro"
private const val KEY_HOME = "home"
private const val HOME_ROOTFS = "rootfs"
private const val HOME_EXTERNAL = "external"

/**
 * What an install recorded about itself. Written only once the install fully
 * succeeds, so a partial rootfs can never present itself as ready.
 */
data class InstallMarker(
    val distro: LinuxDistro,
    /**
     * True when `/root` lives on the rootfs (executable, which agent binaries
     * need). False is the pre-unification chat layout, where `/root` was bound
     * to external app storage — kept as-is for installs that already have it.
     */
    val homeOnRootfs: Boolean,
)

/**
 * Storage layout for one Linux install. The chat sandbox and Kai Build each
 * point one of these at their own directory — or at the *same* directory, which
 * is how a Debian chat sandbox and Kai Build end up sharing a single rootfs.
 *
 * Directory names are deliberately unchanged from before the two stacks merged,
 * so existing installs are found rather than orphaned and `file_paths.xml` keeps
 * working.
 */
class LinuxPaths(
    context: Context,
    dirName: String,
    /** Adopted when a rootfs predates markers. */
    private val legacyMarker: InstallMarker,
    /**
     * Path under [root] whose presence proves a marker-less install *finished*.
     * "A rootfs directory exists" is not enough: an install in progress has one
     * too, and adopting a legacy marker over a half-extracted Debian would
     * record it as a complete Alpine.
     */
    private val legacyEvidence: String,
) {
    private val appContext = context.applicationContext

    val root: File = File(appContext.filesDir, dirName)
    val rootfsDir: File get() = File(root, "rootfs")
    val tmpDir: File get() = File(root, "tmp")

    private val markerFile: File get() = File(root, MARKER_FILE)

    fun archiveFile(spec: DistroSpec): File = File(root, spec.archiveName)

    val nativeLibDir: String get() = appContext.applicationInfo.nativeLibraryDir

    /** proot runs straight out of nativeLibraryDir — the one place Android grants exec. */
    val prootPath: String get() = File(nativeLibDir, "libproot.so").absolutePath

    private val tallocTarget: File get() = File(root, "libtalloc.so.2")

    /** proot resolves `libtalloc.so.2` relative to this. */
    val libDir: String get() = root.absolutePath

    /**
     * The pre-unification chat home. Still bound to `/root` for installs that
     * recorded [InstallMarker.homeOnRootfs] as false.
     */
    private val legacyExternalHome: File by lazy {
        val external = appContext.getExternalFilesDir(null)
        val target = if (external != null) File(external, "sandbox-home") else File(root, "home")
        target.mkdirs()
        target
    }

    /**
     * Bound to `/root/projects`. Lives in external app files so project code stays
     * USB/MTP reachable, and is shared by whichever Debian exists so switching the
     * chat sandbox's distro never strands a project.
     */
    val projectsDir: File by lazy {
        val external = appContext.getExternalFilesDir(null) ?: root
        File(external, "kai-build-home/projects")
    }

    /** Host directory backing `/root` for this install. */
    fun homeDir(marker: InstallMarker): File = if (marker.homeOnRootfs) File(rootfsDir, "root") else legacyExternalHome

    fun ensureLayout() {
        listOf(root, tmpDir, projectsDir).forEach { it.mkdirs() }
    }

    /** Mount points must exist inside the rootfs before proot binds over them. */
    fun ensureMountPoints() {
        File(rootfsDir, "root/projects").mkdirs()
        File(rootfsDir, "root/.local/bin").mkdirs()
    }

    /**
     * Android strips the `.so.2` suffix from jniLibs, so proot cannot find the
     * soname it was linked against until we put a correctly named copy somewhere
     * on its library path.
     */
    fun copyLibtalloc() {
        if (tallocTarget.exists()) return
        val source = File(nativeLibDir, "libtalloc.so")
        if (source.exists()) source.copyTo(tallocTarget, overwrite = true)
    }

    /**
     * The install on disk, or null when there is none. Adopts and persists the
     * legacy marker for installs made before markers existed, so every caller
     * downstream sees one shape.
     */
    fun readMarker(): InstallMarker? {
        if (!rootfsDir.isDirectory) return null
        parseMarker()?.let { return it }
        if (!File(root, legacyEvidence).exists()) return null
        writeMarker(legacyMarker)
        return legacyMarker
    }

    fun writeMarker(marker: InstallMarker) {
        root.mkdirs()
        val home = if (marker.homeOnRootfs) HOME_ROOTFS else HOME_EXTERNAL
        markerFile.writeText("$KEY_DISTRO=${marker.distro.id}\n$KEY_HOME=$home\n")
    }

    private fun parseMarker(): InstallMarker? {
        if (!markerFile.isFile) return null
        val values = runCatching {
            markerFile.readLines()
                .mapNotNull { line ->
                    val idx = line.indexOf('=')
                    if (idx <= 0) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
                }
                .toMap()
        }.getOrNull() ?: return null
        val distroId = values[KEY_DISTRO] ?: return null
        return InstallMarker(
            distro = LinuxDistro.fromId(distroId),
            homeOnRootfs = values[KEY_HOME] != HOME_EXTERNAL,
        )
    }

    /** Wipes the install, leaving project folders (they live outside [root]) alone. */
    fun deleteInstall() {
        markerFile.delete()
        File(root, LEGACY_READY_FILE).delete()
        rootfsDir.deleteRecursively()
        tmpDir.deleteRecursively()
        LinuxDistro.entries.forEach { File(root, DistroSpec.of(it).archiveName).delete() }
    }

    companion object {
        /**
         * The chat sandbox's install. It never wrote a completion file, so a
         * marker-less rootfs is recognised as a finished pre-unification Alpine
         * by Alpine's own release file.
         */
        fun forSandbox(context: Context) = LinuxPaths(
            context = context,
            dirName = SANDBOX_DIR_NAME,
            legacyMarker = InstallMarker(LinuxDistro.LEGACY, homeOnRootfs = false),
            legacyEvidence = ALPINE_RELEASE_FILE,
        )

        /** Kai Build's own install, used only when the chat sandbox is not Debian. */
        fun forBuild(context: Context) = LinuxPaths(
            context = context,
            dirName = BUILD_DIR_NAME,
            legacyMarker = InstallMarker(LinuxDistro.DEBIAN, homeOnRootfs = true),
            legacyEvidence = LEGACY_READY_FILE,
        )
    }
}
