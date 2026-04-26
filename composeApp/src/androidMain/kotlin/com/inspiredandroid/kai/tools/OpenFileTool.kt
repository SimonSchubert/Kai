package com.inspiredandroid.kai.tools

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolSchema
import com.inspiredandroid.kai.sandbox.LinuxSandboxManager
import org.koin.java.KoinJavaComponent.inject
import java.io.File

private const val OPEN_FILE_DESCRIPTION = """Open a file from the sandbox /root directory in the user's default Android app — browser for HTML, image viewer for PNG/JPG, PDF viewer for PDF, markdown viewer for .md, etc. This is how you show finished work to the user.

Path is relative to /root. What the shell tool calls /root/page.html, this tool takes as path="page.html".

Write self-contained files — for HTML, inline all CSS and JavaScript in the same file (no external <link rel="stylesheet"> or <script src=...>), since the file is opened in isolation."""

object OpenFileTool : Tool {
    private val context: Context by inject(Context::class.java)
    private val sandboxManager: LinuxSandboxManager by inject(LinuxSandboxManager::class.java)

    override val schema = ToolSchema(
        name = "open_file",
        description = OPEN_FILE_DESCRIPTION,
        parameters = mapOf(
            "path" to ParameterSchema(
                "string",
                "Path relative to /root, e.g. site/index.html or notes.md",
                true,
            ),
        ),
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val path = (args["path"] as? String)?.trim()
            ?: return mapOf("success" to false, "error" to "path is required")

        val file = resolveSandboxFile(sandboxManager.homePath, path)
            ?: return mapOf("success" to false, "error" to "Invalid path: must be relative to /root, no leading / or .. segments")

        if (!file.exists()) {
            return mapOf("success" to false, "error" to "File not found: $path")
        }
        if (!file.isFile) {
            return mapOf("success" to false, "error" to "Not a file: $path")
        }

        val mime = guessMimeType(file.name)
        val authority = "${context.packageName}.fileprovider"

        val uri = try {
            FileProvider.getUriForFile(context, authority, file)
        } catch (e: IllegalArgumentException) {
            return mapOf("success" to false, "error" to "FileProvider can't expose this path: ${e.message}")
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // No resolveActivity probe: on Android 11+ package visibility rules make it
        // return null for activities in other apps even when startActivity will
        // succeed. Just launch and translate ActivityNotFoundException into a
        // friendly error.
        return try {
            context.startActivity(intent)
            mapOf(
                "success" to true,
                "path" to path,
                "mime_type" to mime,
                "content_uri" to uri.toString(),
            )
        } catch (e: android.content.ActivityNotFoundException) {
            mapOf("success" to false, "error" to "No app available to open $mime files")
        } catch (e: Exception) {
            mapOf("success" to false, "error" to (e.message ?: "Failed to open file"))
        }
    }
}

private fun resolveSandboxFile(homeRoot: String, rel: String): File? {
    if (rel.isBlank() || rel.startsWith("/") || rel.startsWith("\\")) return null
    val parts = rel.split("/", "\\").filter { it.isNotEmpty() }
    if (parts.any { it == ".." }) return null
    val root = File(homeRoot)
    val candidate = File(root, parts.joinToString(File.separator))
    val rootCanon = root.canonicalPath
    val candidateCanon = candidate.canonicalPath
    if (candidateCanon != rootCanon && !candidateCanon.startsWith(rootCanon + File.separator)) return null
    return candidate
}

private fun guessMimeType(filename: String): String {
    val ext = filename.substringAfterLast('.', "").lowercase()
    return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
}
