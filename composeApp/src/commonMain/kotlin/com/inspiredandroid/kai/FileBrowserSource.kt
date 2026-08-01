package com.inspiredandroid.kai

/**
 * A browsable file tree behind an absolute-path API. Both Linux environments
 * expose one — the chat Alpine sandbox and Kai Build's Debian — so the file
 * browser UI can be pointed at either without knowing whose files it shows.
 *
 * Paths are the ones the user sees in that environment (guest paths); the
 * implementation maps them to wherever the files actually live on the host.
 */
interface FileBrowserSource {
    /** Directories first, then case-insensitive by name. Empty when [path] is not a readable directory. */
    suspend fun listDirectory(path: String): List<SandboxFileEntry>

    /** Null when the file is missing, larger than [maxBytes], or binary. */
    suspend fun readTextFile(path: String, maxBytes: Int = 512_000): String?

    suspend fun writeTextFile(path: String, content: String): Boolean

    /** Hands the file to another app on the device. */
    suspend fun openFile(path: String): Result<Unit>

    suspend fun deleteEntry(path: String, recursive: Boolean): Boolean

    /** Renames within the same directory; returns the new path. Fails with `"collision"` if taken. */
    suspend fun renameEntry(path: String, newName: String): Result<String>
}

/** Used by platforms that have no Linux environment at all. */
object NoOpFileBrowserSource : FileBrowserSource {
    override suspend fun listDirectory(path: String): List<SandboxFileEntry> = emptyList()
    override suspend fun readTextFile(path: String, maxBytes: Int): String? = null
    override suspend fun writeTextFile(path: String, content: String): Boolean = false
    override suspend fun openFile(path: String): Result<Unit> = Result.failure(UnsupportedOperationException("File browsing is Android-only"))

    override suspend fun deleteEntry(path: String, recursive: Boolean): Boolean = false
    override suspend fun renameEntry(path: String, newName: String): Result<String> = Result.failure(UnsupportedOperationException("File browsing is Android-only"))
}
