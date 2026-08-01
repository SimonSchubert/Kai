package com.inspiredandroid.kai.build.runtime

import android.content.Context
import com.inspiredandroid.kai.FileBrowserSource
import com.inspiredandroid.kai.SandboxFileEntry
import com.inspiredandroid.kai.sandbox.openFileWithIntent
import com.inspiredandroid.kai.sandbox.safeChild
import com.inspiredandroid.kai.sandbox.toFileEntry
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
    private val paths: BuildPaths,
) : FileBrowserSource {

    /**
     * Guest path to host file, following the same binds proot is started with:
     * `/root/projects` and `/tmp` are bind-mounted, everything else is the rootfs.
     * Returns null for anything that would escape its root.
     */
    private fun resolve(guestPath: String): File? {
        val normalized = guestPath.trim().ifEmpty { "/" }
        if (!normalized.startsWith("/")) return null
        val parts = normalized.split("/").filter { it.isNotEmpty() }
        if (parts.any { it == ".." }) return null
        return when {
            parts.size >= 2 && parts[0] == "root" && parts[1] == "projects" ->
                safeChild(paths.projectsDir, parts.drop(2))

            parts.firstOrNull() == "tmp" -> safeChild(paths.tmpDir, parts.drop(1))

            else -> safeChild(paths.rootfsDir, parts)
        }
    }

    /** The roots themselves are structure, not content: they can never be renamed or deleted. */
    private fun isRoot(file: File): Boolean {
        val canonical = file.canonicalPath
        return canonical == paths.projectsDir.canonicalPath ||
            canonical == paths.rootfsDir.canonicalPath ||
            canonical == paths.tmpDir.canonicalPath
    }

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

    override suspend fun readTextFile(path: String, maxBytes: Int): String? = withContext(Dispatchers.IO) {
        val file = resolve(path) ?: return@withContext null
        if (!file.isFile || file.length() > maxBytes) return@withContext null
        val bytes = try {
            file.readBytes()
        } catch (e: IOException) {
            return@withContext null
        }
        if (bytes.any { it == 0.toByte() }) return@withContext null
        bytes.toString(Charsets.UTF_8)
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
