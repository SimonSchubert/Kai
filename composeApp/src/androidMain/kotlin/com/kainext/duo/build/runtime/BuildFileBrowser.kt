package com.kainext.duo.build.runtime

import android.content.Context
import com.kainext.duo.FileBrowserSource
import com.kainext.duo.MAX_BROWSER_TEXT_BYTES
import com.kainext.duo.SandboxFileEntry
import com.kainext.duo.TextFileResult
import com.kainext.duo.linux.GuestFileMap
import com.kainext.duo.linux.InstallMarker
import com.kainext.duo.linux.LinuxDistro
import com.kainext.duo.linux.LinuxPaths
import com.kainext.duo.sandbox.importFileInto
import com.kainext.duo.sandbox.nonCollidingChild
import com.kainext.duo.sandbox.openFileWithIntent
import com.kainext.duo.sandbox.readFileAsText
import com.kainext.duo.sandbox.toFileEntry
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The Files tab's view of Kai Build's Debian system.
 *
 * Paths are guest paths (`/root/projects/app/src/Main.kt`) translated through
 * [GuestFileMap] with the same binds the shell runs with, so what the browser
 * shows is what a command in the terminal sees — including `/root/projects`,
 * which lives outside the rootfs on purpose.
 */
class BuildFileBrowser(
    private val context: Context,
    private val paths: LinuxPaths,
) : FileBrowserSource {

    /**
     * Rebuilt per call rather than cached: the bind layout follows the install
     * marker, which changes when Linux is installed, removed, or reset from the
     * other surface while this screen is open.
     */
    private fun fileMap(): GuestFileMap = GuestFileMap(
        rootfsDir = paths.rootfsDir,
        homeDir = paths.homeDir(paths.readMarker() ?: defaultMarker()),
        projectsDir = paths.projectsDir,
        tmpDir = paths.tmpDir,
    )

    private fun defaultMarker() = InstallMarker(LinuxDistro.DEBIAN, homeOnRootfs = true)

    override suspend fun listDirectory(path: String): List<SandboxFileEntry> = withContext(Dispatchers.IO) {
        val dir = fileMap().resolve(path) ?: return@withContext emptyList()
        if (!dir.isDirectory) return@withContext emptyList()
        // `exists()` filters dangling symlinks out of the listing instead of
        // handing the UI rows that fail the moment they are opened.
        (dir.listFiles() ?: emptyArray())
            .filter { runCatching { it.exists() }.getOrDefault(false) }
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            .map { it.toFileEntry(path) }
    }

    override suspend fun readTextFile(path: String, maxBytes: Int, force: Boolean): TextFileResult =
        withContext(Dispatchers.IO) {
            val file = fileMap().resolve(path) ?: return@withContext TextFileResult.Unreadable
            if (!file.isFile) return@withContext TextFileResult.Unreadable
            readFileAsText(file, maxBytes.coerceAtLeast(1), force)
        }

    override suspend fun writeTextFile(path: String, content: String): Boolean = withContext(Dispatchers.IO) {
        val file = fileMap().resolve(path) ?: return@withContext false
        try {
            file.parentFile?.mkdirs()
            file.writeText(content)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun openFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        val file = fileMap().resolve(path) ?: return@withContext Result.failure(
            IllegalArgumentException("No such file: $path"),
        )
        if (!file.isFile) return@withContext Result.failure(IllegalArgumentException("Not a file: $path"))
        val result = openFileWithIntent(context, file)
        if (result.success) Result.success(Unit) else Result.failure(IllegalStateException(result.error ?: "No app opened it"))
    }

    override suspend fun deleteEntry(path: String, recursive: Boolean): Boolean = withContext(Dispatchers.IO) {
        val map = fileMap()
        val file = map.resolve(path) ?: return@withContext false
        // The bind roots are the mount structure: deleting one would empty the
        // whole guest-side directory without the user asking for that.
        if (map.isRoot(file)) return@withContext false
        try {
            if (file.isDirectory) {
                if (!recursive) file.delete() else file.deleteRecursively()
            } else {
                file.delete()
            }
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun renameEntry(path: String, newName: String): Result<String> = withContext(Dispatchers.IO) {
        val map = fileMap()
        val file = map.resolve(path) ?: return@withContext Result.failure(
            IllegalArgumentException("No such entry: $path"),
        )
        if (map.isRoot(file)) return@withContext Result.failure(
            IllegalStateException("This folder cannot be renamed"),
        )
        val parent = file.parentFile ?: return@withContext Result.failure(IllegalStateException("No parent folder"))
        val target = nonCollidingChild(parent, newName)
        try {
            if (file.renameTo(target)) Result.success(target.name) else Result.failure(
                IllegalStateException("Rename failed"),
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun importFile(directoryPath: String, source: PlatformFile): Result<String> =
        withContext(Dispatchers.IO) {
            val dir = fileMap().resolve(directoryPath) ?: return@withContext Result.failure(
                IllegalArgumentException("No such folder: $directoryPath"),
            )
            try {
                val file = importFileInto(dir, source)
                Result.success(file.name)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

/** The browser's own copy of the cap, so callers default to the same budget. */
internal const val BUILD_BROWSER_TEXT_BYTES: Int = MAX_BROWSER_TEXT_BYTES
