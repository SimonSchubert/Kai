package com.inspiredandroid.kai

import io.github.vinceglb.filekit.PlatformFile

/** Largest file the built-in editor will pull into memory. */
const val MAX_BROWSER_TEXT_BYTES: Int = 512_000

/**
 * Why a file could — or could not — be shown as text. A bare null would collapse
 * "too big", "not text" and "gone" into one indistinguishable outcome, which is
 * what the browser used to report to the user.
 */
sealed interface TextFileResult {
    /**
     * Decoded content. [editable] is false when the bytes are not valid UTF-8 and were
     * decoded lossily on request: the buffer contains replacement characters, so writing
     * it back would destroy the original bytes.
     */
    data class Text(val content: String, val editable: Boolean = true) : TextFileResult

    /** Not valid UTF-8 text. A forced read still decodes it, read-only. */
    data object Binary : TextFileResult

    /** Beyond the editor's cap; [sizeBytes] is the real size, for the message. */
    data class TooLarge(val sizeBytes: Long) : TextFileResult

    /** Missing, not a regular file, or the read failed. */
    data object Unreadable : TextFileResult
}

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

    /**
     * Reads [path] as text. [force] decodes non-UTF-8 bytes lossily instead of
     * reporting [TextFileResult.Binary]; it does not lift [maxBytes].
     */
    suspend fun readTextFile(
        path: String,
        maxBytes: Int = MAX_BROWSER_TEXT_BYTES,
        force: Boolean = false,
    ): TextFileResult

    suspend fun writeTextFile(path: String, content: String): Boolean

    /** Hands the file to another app on the device. */
    suspend fun openFile(path: String): Result<Unit>

    suspend fun deleteEntry(path: String, recursive: Boolean): Boolean

    /** Renames within the same directory; returns the new path. Fails with `"collision"` if taken. */
    suspend fun renameEntry(path: String, newName: String): Result<String>

    /**
     * Streams a file picked from device storage into [directoryPath]. Returns the new
     * guest path; the name is suffixed rather than overwriting an existing file.
     */
    suspend fun importFile(directoryPath: String, source: PlatformFile): Result<String>
}

/** Used by platforms that have no Linux environment at all. */
object NoOpFileBrowserSource : FileBrowserSource {
    override suspend fun listDirectory(path: String): List<SandboxFileEntry> = emptyList()
    override suspend fun readTextFile(path: String, maxBytes: Int, force: Boolean): TextFileResult = TextFileResult.Unreadable
    override suspend fun writeTextFile(path: String, content: String): Boolean = false
    override suspend fun openFile(path: String): Result<Unit> = Result.failure(UnsupportedOperationException("File browsing is Android-only"))

    override suspend fun deleteEntry(path: String, recursive: Boolean): Boolean = false
    override suspend fun renameEntry(path: String, newName: String): Result<String> = Result.failure(UnsupportedOperationException("File browsing is Android-only"))
    override suspend fun importFile(directoryPath: String, source: PlatformFile): Result<String> = Result.failure(UnsupportedOperationException("File browsing is Android-only"))
}
