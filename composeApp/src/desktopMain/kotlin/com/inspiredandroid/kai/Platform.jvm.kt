@file:OptIn(ExperimentalComposeUiApi::class)

package com.inspiredandroid.kai

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.data.EmailStore
import com.inspiredandroid.kai.data.EncryptedFileSettings
import com.inspiredandroid.kai.data.MemoryStore
import com.inspiredandroid.kai.data.TaskStore
import com.inspiredandroid.kai.mcp.McpServerManager
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.tools.CommonTools
import com.inspiredandroid.kai.tools.EmailTools
import com.inspiredandroid.kai.tools.HeartbeatTools
import com.inspiredandroid.kai.tools.ProcessManagerTool
import com.inspiredandroid.kai.tools.SchedulingTools
import com.inspiredandroid.kai.tools.ShellCommandTool
import com.russhwolf.settings.Settings
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.write
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.Dispatchers
import org.koin.java.KoinJavaComponent.inject
import java.io.File
import java.net.URI
import kotlin.coroutines.CoroutineContext

actual fun httpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(CIO) {
    config(this)
}

actual fun getBackgroundDispatcher(): CoroutineContext = Dispatchers.IO

actual fun onDragAndDropEventDropped(event: DragAndDropEvent): PlatformFile? {
    if (event.dragData() is DragData.FilesList) {
        val dragData = event.dragData() as DragData.FilesList
        val filePath = dragData.readFiles().firstOrNull()
        if (filePath != null) {
            try {
                val fileUri = URI(filePath)
                val file = File(fileUri)

                if (file.exists()) {
                    return PlatformFile(file)
                }
            } catch (_: Exception) {
            }
        }
        return null
    } else {
        return null
    }
}

actual val BackIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack

actual val currentPlatform: Platform = run {
    val osName = System.getProperty("os.name", "").lowercase()
    when {
        "mac" in osName || "darwin" in osName -> Platform.Desktop.Mac
        "win" in osName -> Platform.Desktop.Windows
        else -> Platform.Desktop.Linux
    }
}

actual val defaultUiScale: Float = if (currentPlatform is Platform.Desktop.Linux) 1.1f else 1.0f

actual val isEmailSupported: Boolean = true

actual val isSmsSupported: Boolean = false

actual val isNotificationsSupported: Boolean = false

actual val isSplinterlandsSupported: Boolean = true

actual suspend fun compressImageBytes(bytes: ByteArray, mimeType: String): ByteArray {
    if (!mimeType.startsWith("image/")) return bytes
    return try {
        val inputStream = java.io.ByteArrayInputStream(bytes)
        val image = javax.imageio.ImageIO.read(inputStream) ?: return bytes
        val maxDim = 1024
        val scaled = if (image.width > maxDim || image.height > maxDim) {
            val scale = maxDim.toDouble() / maxOf(image.width, image.height)
            val newWidth = (image.width * scale).toInt()
            val newHeight = (image.height * scale).toInt()
            val resized = java.awt.image.BufferedImage(newWidth, newHeight, java.awt.image.BufferedImage.TYPE_INT_RGB)
            val g2d = resized.createGraphics()
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g2d.drawImage(image, 0, 0, newWidth, newHeight, null)
            g2d.dispose()
            resized
        } else {
            // Still need to convert to RGB for JPEG encoding (original might have alpha)
            val rgb = java.awt.image.BufferedImage(image.width, image.height, java.awt.image.BufferedImage.TYPE_INT_RGB)
            val g2d = rgb.createGraphics()
            g2d.drawImage(image, 0, 0, null)
            g2d.dispose()
            rgb
        }
        val outputStream = java.io.ByteArrayOutputStream()
        javax.imageio.ImageIO.write(scaled, "jpg", outputStream)
        outputStream.toByteArray()
    } catch (_: Exception) {
        bytes
    }
}

actual fun getAppFilesDirectory(): String {
    val userHome = System.getProperty("user.home")
    val kaiDir = File("$userHome/.kai")
    if (!kaiDir.exists()) {
        kaiDir.mkdirs()
    }
    return kaiDir.absolutePath
}

actual fun createSecureSettings(): Settings = EncryptedFileSettings()

actual fun createLegacySettings(): Settings? = null // Same storage location, no migration needed

actual fun getPlatformToolDefinitions(): List<ToolInfo> = listOf(ShellCommandTool.toolInfo, ProcessManagerTool.toolInfo) + CommonTools.commonToolDefinitions

actual fun getAvailableTools(): List<Tool> {
    val appSettings: AppSettings by inject(AppSettings::class.java)
    val memoryStore: MemoryStore by inject(MemoryStore::class.java)
    val taskStore: TaskStore by inject(TaskStore::class.java)
    val emailStore: EmailStore by inject(EmailStore::class.java)
    return buildList {
        addAll(CommonTools.getCommonTools(appSettings))
        if (appSettings.isMemoryEnabled()) {
            addAll(CommonTools.getMemoryTools(memoryStore))
        }
        if (appSettings.isSchedulingEnabled()) {
            addAll(SchedulingTools.getSchedulingTools(taskStore))
            addAll(HeartbeatTools.getHeartbeatTools(memoryStore, appSettings))
        }
        if (appSettings.isToolEnabled(ShellCommandTool.schema.name, defaultEnabled = false)) {
            add(ShellCommandTool)
            add(ProcessManagerTool)
        }
        if (appSettings.isEmailEnabled()) {
            addAll(EmailTools.getEmailTools(emailStore))
        }

        val mcpServerManager: McpServerManager by inject(McpServerManager::class.java)
        addAll(mcpServerManager.getEnabledMcpTools())
    }
}

actual fun openUrl(url: String): Boolean = try {
    java.awt.Desktop.getDesktop().browse(URI(url))
    true
} catch (_: Exception) {
    false
}

actual fun decodeToImageBitmap(bytes: ByteArray): ImageBitmap? = try {
    org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
} catch (_: Exception) {
    null
}

@androidx.compose.runtime.Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No system back gesture on desktop
}

actual suspend fun saveFileToDevice(bytes: ByteArray, baseName: String, extension: String) {
    val file = FileKit.openFileSaver(suggestedName = baseName, extension = extension)
    file?.write(bytes)
}

/**
 * Posts a native OS notification. Each platform has its own surface:
 *   - macOS: `osascript` invokes the user-facing Notification Center.
 *   - Linux: `notify-send` (libnotify) is the freedesktop standard and ships in most distros.
 *   - Windows: AWT [java.awt.SystemTray] briefly registers a tray icon to display a balloon
 *     toast, then removes it so we don't leave a persistent tray entry.
 * All paths swallow failures — if the OS hook is missing the in-app heartbeat banner still fires.
 */
actual fun sendHeartbeatNotification(title: String, body: String) {
    try {
        when (currentPlatform as Platform.Desktop) {
            Platform.Desktop.Mac -> {
                // AppleScript string literals: backslash and double-quote need escaping.
                val safeTitle = title.replace("\\", "\\\\").replace("\"", "\\\"")
                val safeBody = body.replace("\\", "\\\\").replace("\"", "\\\"")
                ProcessBuilder("osascript", "-e", "display notification \"$safeBody\" with title \"$safeTitle\"")
                    .start()
            }

            Platform.Desktop.Windows -> {
                if (!java.awt.SystemTray.isSupported()) return
                val tray = java.awt.SystemTray.getSystemTray()
                // 1×1 transparent placeholder — Windows auto-supplies a fallback icon for the toast.
                val image = java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                val trayIcon = java.awt.TrayIcon(image, "Kai")
                trayIcon.isImageAutoSize = true
                tray.add(trayIcon)
                trayIcon.displayMessage(title, body, java.awt.TrayIcon.MessageType.INFO)
                java.util.Timer(true).schedule(
                    object : java.util.TimerTask() {
                        override fun run() = tray.remove(trayIcon)
                    },
                    5_000,
                )
            }

            Platform.Desktop.Linux -> {
                // `--` terminator prevents a title or body starting with `-` from being parsed as a flag.
                ProcessBuilder("notify-send", "--", title, body).start()
            }
        }
    } catch (_: Exception) {
        // notify-send missing, AWT headless, sandboxed osascript, etc. — fall back silently.
    }
}
