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
import com.inspiredandroid.kai.data.HeartbeatManager
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

actual val isMobilePlatform: Boolean = false

actual val isDesktopPlatform: Boolean = true

actual val defaultUiScale: Float = run {
    val osName = System.getProperty("os.name", "").lowercase()
    if ("linux" in osName) 1.1f else 1.0f
}

actual val isEmailSupported: Boolean = true

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

actual val platformName: String = run {
    val osName = System.getProperty("os.name", "").lowercase()
    when {
        "mac" in osName || "darwin" in osName -> "macOS"
        "win" in osName -> "Windows"
        else -> "Linux"
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
    val heartbeatManager: HeartbeatManager by inject(HeartbeatManager::class.java)
    val emailStore: EmailStore by inject(EmailStore::class.java)
    return buildList {
        addAll(CommonTools.getCommonTools(appSettings))
        if (appSettings.isMemoryEnabled()) {
            addAll(CommonTools.getMemoryTools(memoryStore))
        }
        if (appSettings.isSchedulingEnabled()) {
            addAll(SchedulingTools.getSchedulingTools(taskStore))
            addAll(HeartbeatTools.getHeartbeatTools(heartbeatManager, memoryStore, appSettings))
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

// Desktop has no system push-notification surface wired up; the in-app banner suffices.
actual fun sendHeartbeatNotification(title: String, body: String) = Unit
