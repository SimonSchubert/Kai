package com.inspiredandroid.kai

import androidx.compose.material.icons.Icons
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.data.EmailStore
import com.inspiredandroid.kai.data.HeartbeatManager
import com.inspiredandroid.kai.data.MemoryStore
import com.inspiredandroid.kai.data.TaskStore
import com.inspiredandroid.kai.mcp.McpServerManager
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.tools.CommonTools
import com.inspiredandroid.kai.tools.EmailTools
import com.inspiredandroid.kai.tools.HeartbeatTools
import com.inspiredandroid.kai.tools.SchedulingTools
import com.inspiredandroid.kai.ui.icons.ArrowBackIos
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.write
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import kotlin.coroutines.CoroutineContext

actual fun httpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(Darwin) {
    config(this)
}

actual fun getBackgroundDispatcher(): CoroutineContext = Dispatchers.IO

actual fun onDragAndDropEventDropped(event: DragAndDropEvent): PlatformFile? = null

actual val BackIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBackIos

actual val currentPlatform: Platform = Platform.Mobile.Ios

actual val defaultUiScale: Float = 1.0f

actual val isEmailSupported: Boolean = true

actual val isSplinterlandsSupported: Boolean = false

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
actual suspend fun compressImageBytes(bytes: ByteArray, mimeType: String): ByteArray {
    if (!mimeType.startsWith("image/")) return bytes
    return try {
        val nsData = bytes.usePinned { pinned ->
            NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
        }
        val image = platform.UIKit.UIImage(data = nsData)
        val maxDim = 1024.0
        val imgWidth = image.size.useContents { width }
        val imgHeight = image.size.useContents { height }
        val scaled = if (imgWidth > maxDim || imgHeight > maxDim) {
            val scale = maxDim / maxOf(imgWidth, imgHeight)
            val newWidth = imgWidth * scale
            val newHeight = imgHeight * scale
            val newSize = kotlinx.cinterop.cValue<platform.CoreGraphics.CGSize> {
                width = newWidth
                height = newHeight
            }
            platform.UIKit.UIGraphicsBeginImageContextWithOptions(newSize, false, 1.0)
            image.drawInRect(
                kotlinx.cinterop.cValue<platform.CoreGraphics.CGRect> {
                    origin.x = 0.0
                    origin.y = 0.0
                    size.width = newWidth
                    size.height = newHeight
                },
            )
            val resized = platform.UIKit.UIGraphicsGetImageFromCurrentImageContext()
            platform.UIKit.UIGraphicsEndImageContext()
            resized ?: image
        } else {
            image
        }
        val jpegData = platform.UIKit.UIImageJPEGRepresentation(scaled, 0.8) ?: return bytes
        jpegData.toByteArray()
    } catch (_: Exception) {
        bytes
    }
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val result = ByteArray(size)
    result.usePinned { pinned ->
        platform.posix.memcpy(pinned.addressOf(0), bytes, length)
    }
    return result
}

actual fun getAppFilesDirectory(): String {
    val paths = platform.Foundation.NSSearchPathForDirectoriesInDomains(
        platform.Foundation.NSDocumentDirectory,
        platform.Foundation.NSUserDomainMask,
        true,
    )
    return paths.first() as String
}

@OptIn(ExperimentalSettingsImplementation::class)
actual fun createSecureSettings(): Settings = KeychainSettings(service = "com.inspiredandroid.kai")

actual fun createLegacySettings(): Settings? = NSUserDefaultsSettings(platform.Foundation.NSUserDefaults.standardUserDefaults)

actual fun getPlatformToolDefinitions(): List<ToolInfo> = CommonTools.commonToolDefinitions

private object IosKoinHelper : KoinComponent {
    val appSettings: AppSettings by inject()
    val memoryStore: MemoryStore by inject()
    val taskStore: TaskStore by inject()
    val heartbeatManager: HeartbeatManager by inject()
    val emailStore: EmailStore by inject()
    val mcpServerManager: McpServerManager by inject()
}

actual fun getAvailableTools(): List<Tool> = buildList {
    addAll(CommonTools.getCommonTools(IosKoinHelper.appSettings))
    if (IosKoinHelper.appSettings.isMemoryEnabled()) {
        addAll(CommonTools.getMemoryTools(IosKoinHelper.memoryStore))
    }
    if (IosKoinHelper.appSettings.isSchedulingEnabled()) {
        addAll(SchedulingTools.getSchedulingTools(IosKoinHelper.taskStore))
        addAll(HeartbeatTools.getHeartbeatTools(IosKoinHelper.heartbeatManager, IosKoinHelper.memoryStore, IosKoinHelper.appSettings))
    }
    if (IosKoinHelper.appSettings.isEmailEnabled()) {
        addAll(EmailTools.getEmailTools(IosKoinHelper.emailStore))
    }
    addAll(IosKoinHelper.mcpServerManager.getEnabledMcpTools())
}

@Suppress("CAST_NEVER_SUCCEEDS")
actual fun openUrl(url: String): Boolean = try {
    val nsUrl = platform.Foundation.NSURL.URLWithString(url)
    if (nsUrl != null) {
        platform.UIKit.UIApplication.sharedApplication.openURL(nsUrl)
    } else {
        false
    }
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
    // iOS swipe-back is handled by the navigation controller
}

actual suspend fun saveFileToDevice(bytes: ByteArray, baseName: String, extension: String) {
    val file = FileKit.openFileSaver(suggestedName = baseName, extension = extension)
    file?.write(bytes)
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual fun sendHeartbeatNotification(title: String, body: String) {
    // The authorization completion runs asynchronously on a system queue, so it's outside the
    // outer try/catch's scope and needs its own guard. Heartbeat delivery must never throw.
    try {
        val center = platform.UserNotifications.UNUserNotificationCenter.currentNotificationCenter()
        val options = platform.UserNotifications.UNAuthorizationOptionAlert or
            platform.UserNotifications.UNAuthorizationOptionSound or
            platform.UserNotifications.UNAuthorizationOptionBadge
        center.requestAuthorizationWithOptions(options) { granted, _ ->
            if (!granted) return@requestAuthorizationWithOptions
            try {
                val content = platform.UserNotifications.UNMutableNotificationContent().apply {
                    setTitle(title)
                    setBody(body)
                    setSound(platform.UserNotifications.UNNotificationSound.defaultSound())
                }
                // iOS rejects nil triggers for non-scheduled notifications and 0 for time-interval
                // triggers, so use a tiny delay to fire effectively immediately.
                val trigger = platform.UserNotifications.UNTimeIntervalNotificationTrigger
                    .triggerWithTimeInterval(timeInterval = 0.1, repeats = false)
                val request = platform.UserNotifications.UNNotificationRequest.requestWithIdentifier(
                    identifier = platform.Foundation.NSUUID().UUIDString,
                    content = content,
                    trigger = trigger,
                )
                center.addNotificationRequest(request, null)
            } catch (_: Throwable) {
            }
        }
    } catch (_: Throwable) {
    }
}
