package com.inspiredandroid.kai.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import com.inspiredandroid.kai.SandboxController
import io.ktor.http.decodeURLPart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A [UriHandler] that routes links pointing at sandbox files through
 * [SandboxController.openFile] instead of the platform's `startActivity`.
 *
 * The model often emits a `file:///root/foo.gif` link after creating a file in
 * the Linux sandbox. Handing a raw `file://` URI to Android's `startActivity`
 * throws `FileUriExposedException` and crashes the app, so such links must never
 * reach the platform handler — `openFile` serves them via a FileProvider
 * `content://` URI instead. Every non-file link (http/https/mailto/…) is passed
 * straight through to [delegate].
 */
internal class SandboxAwareUriHandler(
    private val delegate: UriHandler,
    private val sandboxController: SandboxController,
    private val scope: CoroutineScope,
) : UriHandler {
    override fun openUri(uri: String) {
        val sandboxPath = toSandboxPath(uri)
        if (sandboxPath != null) {
            scope.launch { sandboxController.openFile(sandboxPath) }
        } else {
            delegate.openUri(uri)
        }
    }
}

/**
 * Maps a link URL to an absolute sandbox path, or null when it isn't a local-file
 * link (http/https/mailto/… all return null and are handled by the platform). A
 * `file:` scheme or a bare absolute path both count as sandbox files. Percent
 * escapes (e.g. `%20`) are decoded so the resolved path matches the real file.
 */
internal fun toSandboxPath(uri: String): String? {
    val raw = when {
        uri.startsWith("file://") -> uri.removePrefix("file://")
        uri.startsWith("file:") -> uri.removePrefix("file:")
        uri.startsWith("/") -> uri
        else -> return null
    }
    if (!raw.startsWith("/")) return null
    return runCatching { raw.decodeURLPart() }.getOrDefault(raw)
}

@Composable
internal fun rememberSandboxAwareUriHandler(sandboxController: SandboxController): UriHandler {
    // Captured before this handler is provided, so it is the platform default.
    val delegate = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    return remember(delegate, sandboxController, scope) {
        SandboxAwareUriHandler(delegate, sandboxController, scope)
    }
}
