package com.inspiredandroid.kai

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.data.HeartbeatManager
import com.inspiredandroid.kai.data.MemoryStore
import com.inspiredandroid.kai.data.TaskStore
import com.inspiredandroid.kai.mcp.McpServerManager
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.tools.CommonTools
import com.inspiredandroid.kai.tools.HeartbeatTools
import com.inspiredandroid.kai.tools.SchedulingTools
import com.russhwolf.settings.Settings
import com.russhwolf.settings.StorageSettings
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.download
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.js.Js
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

actual fun httpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(Js) {
    config(this)
}

actual fun getBackgroundDispatcher(): CoroutineContext = EmptyCoroutineContext

actual fun onDragAndDropEventDropped(event: DragAndDropEvent): PlatformFile? = null

actual val BackIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack

actual val isMobilePlatform: Boolean = false

actual val isDesktopPlatform: Boolean = false

actual val defaultUiScale: Float = 1.0f

actual val isEmailSupported: Boolean = false

actual val isSplinterlandsSupported: Boolean = false

actual suspend fun compressImageBytes(bytes: ByteArray, mimeType: String): ByteArray = bytes

actual val platformName: String = "Web"

actual fun getAppFilesDirectory(): String {
    // Web uses localStorage, return empty string as no file path is needed
    return ""
}

actual fun createSecureSettings(): Settings {
    // Web has no secure storage - using localStorage
    return StorageSettings()
}

actual fun createLegacySettings(): Settings? = null // Same storage location, no migration needed

actual fun getPlatformToolDefinitions(): List<ToolInfo> = CommonTools.commonToolDefinitions

private object WebKoinHelper : KoinComponent {
    val appSettings: AppSettings by inject()
    val memoryStore: MemoryStore by inject()
    val taskStore: TaskStore by inject()
    val heartbeatManager: HeartbeatManager by inject()
    val mcpServerManager: McpServerManager by inject()
}

actual fun getAvailableTools(): List<Tool> = buildList {
    addAll(CommonTools.getCommonTools(WebKoinHelper.appSettings))
    if (WebKoinHelper.appSettings.isMemoryEnabled()) {
        addAll(CommonTools.getMemoryTools(WebKoinHelper.memoryStore))
    }
    if (WebKoinHelper.appSettings.isSchedulingEnabled()) {
        addAll(SchedulingTools.getSchedulingTools(WebKoinHelper.taskStore))
        addAll(HeartbeatTools.getHeartbeatTools(WebKoinHelper.heartbeatManager, WebKoinHelper.memoryStore, WebKoinHelper.appSettings))
    }
    addAll(WebKoinHelper.mcpServerManager.getEnabledMcpTools())
}

actual fun openUrl(url: String): Boolean = try {
    kotlinx.browser.window.open(url, "_blank")
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
    // No system back gesture on web
}

actual suspend fun saveFileToDevice(bytes: ByteArray, baseName: String, extension: String) {
    FileKit.download(bytes = bytes, fileName = "$baseName.$extension")
}

actual fun appendParseErrorLog(entry: String) {
}
