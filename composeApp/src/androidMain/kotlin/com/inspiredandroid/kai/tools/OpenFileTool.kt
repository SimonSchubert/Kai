package com.inspiredandroid.kai.tools

import android.content.Context
import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolSchema
import com.inspiredandroid.kai.sandbox.LinuxSandboxManager
import com.inspiredandroid.kai.sandbox.openFileWithIntent
import com.inspiredandroid.kai.sandbox.resolveSandboxFile
import org.koin.java.KoinJavaComponent.inject

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

        val result = openFileWithIntent(context, file)
        return if (result.success) {
            mapOf(
                "success" to true,
                "path" to path,
                "mime_type" to result.mimeType,
                "content_uri" to (result.contentUri ?: ""),
            )
        } else {
            mapOf("success" to false, "error" to (result.error ?: "Failed to open file"))
        }
    }
}
