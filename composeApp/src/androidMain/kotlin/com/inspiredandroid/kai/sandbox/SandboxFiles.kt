package com.inspiredandroid.kai.sandbox

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import com.inspiredandroid.kai.SandboxFileEntry
import com.inspiredandroid.kai.TextFileResult
import com.inspiredandroid.kai.data.FileCategory
import com.inspiredandroid.kai.data.classifyFile
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.source
import io.github.vinceglb.filekit.withScopedAccess
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.io.Buffer
import kotlinx.io.asSink
import kotlinx.io.buffered
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

internal fun resolveSandboxFile(homeRoot: String, rel: String): File? {
    if (rel.isBlank() || rel.startsWith("/") || rel.startsWith("\\")) return null
    val parts = rel.split("/", "\\").filter { it.isNotEmpty() }
    if (parts.any { it == ".." }) return null
    return safeChild(File(homeRoot), parts)
}

internal fun resolveSandboxAbsolute(rootfsPath: String, homePath: String, sandboxPath: String): File? {
    val normalized = sandboxPath.trim().ifEmpty { "/" }
    if (!normalized.startsWith("/")) return null
    val parts = normalized.split("/").filter { it.isNotEmpty() }
    if (parts.any { it == ".." }) return null
    val (rootDir, remainder) = if (parts.firstOrNull() == "root") {
        File(homePath) to parts.drop(1)
    } else {
        File(rootfsPath) to parts
    }
    return safeChild(rootDir, remainder)
}

/** Blocks path traversal: the resolved child must stay under [root]. */
internal fun safeChild(root: File, parts: List<String>): File? {
    val candidate = if (parts.isEmpty()) root else File(root, parts.joinToString(File.separator))
    val rootCanon = root.canonicalPath
    val candidateCanon = candidate.canonicalPath
    if (candidateCanon != rootCanon && !candidateCanon.startsWith(rootCanon + File.separator)) return null
    return candidate
}

/** One row of a directory listing, with the guest path built from [parent]. */
internal fun File.toFileEntry(parent: String): SandboxFileEntry = SandboxFileEntry(
    name = name,
    path = if (parent.isEmpty()) "/$name" else "$parent/$name",
    isDirectory = isDirectory,
    sizeBytes = if (isFile) length() else 0,
    lastModifiedMs = lastModified(),
)

// Extensions Android's own map either has no entry for, or resolves to something that is
// wrong for a Linux sandbox — `.ts` is TypeScript here far more often than an MPEG
// transport stream. Without `apk` in particular the intent goes out as a wildcard type
// and the package installer never appears in the chooser.
private val mimeOverrides = mapOf(
    "apk" to "application/vnd.android.package-archive",
    "ts" to "text/plain",
    "bz2" to "application/x-bzip2",
    "zst" to "application/zstd",
)

internal fun guessMimeType(filename: String): String {
    val ext = filename.substringAfterLast('.', "").lowercase()
    mimeOverrides[ext]?.let { return it }
    android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
    // Android's map covers media, documents and archives but almost no source or config
    // extension. text/plain at least offers a viewer where `*/*` offers nothing.
    return if (classifyFile(mimeType = null, fileName = filename) == FileCategory.TEXT) "text/plain" else "*/*"
}

/**
 * Shared by both Linux environments' browsers. Separates "too big" from "not text"
 * from "unreadable" so the UI can say which, and refuses to hand back an editable
 * buffer that would not survive a round trip to disk.
 */
internal fun readFileAsText(file: File, maxBytes: Int, force: Boolean): TextFileResult {
    if (!file.isFile) return TextFileResult.Unreadable
    val length = file.length()
    if (length > maxBytes) return TextFileResult.TooLarge(length)
    val bytes = try {
        file.readBytes()
    } catch (e: IOException) {
        return TextFileResult.Unreadable
    }
    // A NUL byte is legal UTF-8 but effectively never appears in real text, so it is the
    // cheapest binary tell available before attempting a decode.
    val strict = if (bytes.any { it == 0.toByte() }) null else decodeUtf8Strict(bytes)
    if (strict != null) return TextFileResult.Text(strict, editable = true)
    if (!force) return TextFileResult.Binary
    // Forced: decode lossily so the user can at least look at it. Not editable — the
    // replacement characters would overwrite the original bytes on save.
    return TextFileResult.Text(bytes.toString(Charsets.UTF_8), editable = false)
}

/** Null when [bytes] is not valid UTF-8, rather than silently substituting U+FFFD. */
private fun decodeUtf8Strict(bytes: ByteArray): String? = try {
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
} catch (e: CharacterCodingException) {
    null
}

private const val COPY_BUFFER_BYTES = 64L * 1024

/**
 * Streams [source] into [dir] under a name that does not already exist, and returns the
 * file created. Streamed rather than read into a ByteArray so importing a multi-gigabyte
 * file cannot OOM the process.
 */
internal suspend fun importFileInto(dir: File, source: PlatformFile): File {
    if (!dir.isDirectory) throw IOException("Not a directory: $dir")
    val dest = nonCollidingChild(dir, source.name)
    source.withScopedAccess {
        dest.outputStream().use { out ->
            val sink = out.asSink().buffered()
            source.source().use { raw ->
                val buffer = Buffer()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = raw.readAtMostTo(buffer, COPY_BUFFER_BYTES)
                    if (read == -1L) break
                    sink.write(buffer, read)
                }
                sink.flush()
            }
        }
    }
    return dest
}

/** Strips any path from [rawName] and suffixes `-1`, `-2`, … until the name is free. */
internal fun nonCollidingChild(dir: File, rawName: String): File {
    val safe = rawName.substringAfterLast('/').substringAfterLast('\\')
        .let { if (it.isBlank() || it == "." || it == "..") "imported" else it }
    var candidate = File(dir, safe)
    if (!candidate.exists()) return candidate
    val base = safe.substringBeforeLast('.', safe)
    val ext = safe.substringAfterLast('.', "")
    var index = 1
    while (candidate.exists()) {
        candidate = File(dir, if (ext.isEmpty()) "$base-$index" else "$base-$index.$ext")
        index++
    }
    return candidate
}

internal data class FileOpenResult(
    val success: Boolean,
    val mimeType: String,
    val contentUri: String? = null,
    val error: String? = null,
)

private const val APK_MIME = "application/vnd.android.package-archive"

// REQUEST_INSTALL_PACKAGES is declared by the `foss` flavor only — Play policy restricts
// it. Without it the installer opens and then refuses, so report that up front instead.
private fun Context.declaresRequestInstallPackages(): Boolean = try {
    packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        .requestedPermissions?.contains(Manifest.permission.REQUEST_INSTALL_PACKAGES) == true
} catch (_: Exception) {
    false
}

internal fun openFileWithIntent(context: Context, file: File): FileOpenResult {
    val mime = guessMimeType(file.name)
    if (mime == APK_MIME && !context.declaresRequestInstallPackages()) {
        return FileOpenResult(false, mime, error = "This build cannot install apps")
    }
    val authority = "${context.packageName}.fileprovider"

    val uri = try {
        FileProvider.getUriForFile(context, authority, file)
    } catch (e: IllegalArgumentException) {
        return FileOpenResult(false, mime, error = "FileProvider can't expose this path: ${e.message}")
    }

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    return try {
        context.startActivity(intent)
        FileOpenResult(true, mime, contentUri = uri.toString())
    } catch (e: ActivityNotFoundException) {
        FileOpenResult(false, mime, error = "No app available to open $mime files")
    } catch (e: Exception) {
        FileOpenResult(false, mime, error = e.message ?: "Failed to open file")
    }
}
