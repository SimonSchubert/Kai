package com.inspiredandroid.kai.build.runtime

import android.content.Context
import com.inspiredandroid.kai.FileBrowserSource
import com.inspiredandroid.kai.SandboxFileEntry
import com.inspiredandroid.kai.TextFileResult
import com.inspiredandroid.kai.linux.GuestFileMap
import com.inspiredandroid.kai.linux.LinuxPaths
import com.inspiredandroid.kai.sandbox.importFileInto
import com.inspiredandroid.kai.sandbox.openFileWithIntent
import com.inspiredandroid.kai.sandbox.readFileAsText
import com.inspiredandroid.kai.sandbox.toFileEntry
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Kai Build's Debian seen as a file tree. Everything the user can browse is an
 * ordinary host file — the rootfs is app-private storage and project folders are
 * an external-files directory — so this needs no proot round-trip.
 */
class BuildFileBrowser(
    private val context: Context,
    private val paths: LinuxPaths,
) : FileBrowserSource {

    /**
     * Guest path to host file, following the same binds proot is started with:
     * `/root/projects` and `/tmp` are bind-mounted, everything else — including
     * `/root` itself — is the rootfs.
     */
    private val files = GuestFileMap(
        rootfsDir = paths.rootfsDir,
        homeDir = File(paths.rootfsDir, "root"),
        projectsDir = paths.projectsDir,
        tmpDir = paths.tmpDir,
    )

    private fun resolve(guestPath: String): File? = files.resolve(guestPath)

    private fun isRoot(file: File): Boolean = files.isRoot(file)

    override suspend fun listDirectory(path: String): List<SandboxFileEntry> = withContext(Dispatchers.IO) {
        val dir = resolve(path) ?: return@withContext emptyList()
        if (!dir.isDirectory) return@withContext emptyList()
        val parent = if (path.endsWith("/")) path.dropLast(1) else path
        dir.listFiles().orEmpty()
            .map { it.toFileEntry(parent = if (parent == "/") "" else parent) }
            .sortedWith(
                compareByDescending<SandboxFileEntry> { it.isDirectory }
                    .thenBy { it.name.lowercase() },
            )
    }

    override suspend fun readTextFile(path: String, maxBytes: Int, force: Boolean): TextFileResult = withContext(Dispatchers.IO) {
        val file = resolve(path) ?: return@withContext TextFileResult.Unreadable
        readFileAsText(file, maxBytes, force)
    }

    override suspend fun importFile(directoryPath: String, source: PlatformFile): Result<String> = withContext(Dispatchers.IO) {
        val dir = resolve(directoryPath)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid path: $directoryPath"))
        try {
            val created = importFileInto(dir, source)
            val parent = directoryPath.trimEnd('/')
            Result.success(if (parent.isEmpty()) "/${created.name}" else "$parent/${created.name}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun writeTextFile(path: String, content: String): Boolean = withContext(Dispatchers.IO) {
        val file = resolve(path) ?: return@withContext false
        if (file.exists() && !file.isFile) return@withContext false
        try {
            file.parentFile?.mkdirs()
            file.writeBytes(content.toByteArray(Charsets.UTF_8))
            true
        } catch (e: IOException) {
            false
        }
    }

    override suspend fun openFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        val file = resolve(path)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid path: $path"))
        if (!file.isFile) return@withContext Result.failure(IllegalArgumentException("Not a file: $path"))
        val result = openFileWithIntent(context, file)
        if (result.success) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(result.error ?: "Open failed"))
        }
    }

    override suspend fun deleteEntry(path: String, recursive: Boolean): Boolean = withContext(Dispatchers.IO) {
        val file = resolve(path) ?: return@withContext false
        if (!file.exists() || isRoot(file)) return@withContext false
        when {
            file.isDirectory && !recursive -> {
                val empty = file.list()?.isEmpty() != false
                if (empty) file.delete() else false
            }

            file.isDirectory -> file.deleteRecursively()

            else -> file.delete()
        }
    }

    override suspend fun renameEntry(path: String, newName: String): Result<String> = withContext(Dispatchers.IO) {
        if (newName.isBlank() || newName.contains('/') || newName.contains('\\') ||
            newName == "." || newName == ".."
        ) {
            return@withContext Result.failure(IllegalArgumentException("Invalid name"))
        }
        val source = resolve(path)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid path"))
        if (!source.exists()) return@withContext Result.failure(IllegalArgumentException("Not found"))
        if (isRoot(source)) return@withContext Result.failure(IllegalArgumentException("Cannot rename this folder"))
        val parent = path.substringBeforeLast('/', "")
        val newPath = if (parent.isEmpty()) "/$newName" else "$parent/$newName"
        val destination = resolve(newPath)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid destination"))
        // The browser's rename dialog shows a dedicated message for this exact text.
        if (destination.exists()) return@withContext Result.failure(IllegalStateException("collision"))
        if (source.renameTo(destination)) {
            Result.success(newPath)
        } else {
            Result.failure(IllegalStateException("rename failed"))
        }
    }
}
