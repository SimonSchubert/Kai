package com.kainext.duo

import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.graphics.scale
import androidx.core.net.toUri
import com.kainext.duo.data.AppSettings
import com.kainext.duo.data.EmailStore
import com.kainext.duo.data.MemoryStore
import com.kainext.duo.data.NotificationStore
import com.kainext.duo.data.SmsDraftStore
import com.kainext.duo.data.SmsStore
import com.kainext.duo.data.TaskStore
import com.kainext.duo.mcp.McpServerManager
import com.kainext.duo.network.tools.Tool
import com.kainext.duo.network.tools.ToolInfo
import com.kainext.duo.notifications.NotificationReader
import com.kainext.duo.notifications.declaresNotificationListener
import com.kainext.duo.sandbox.LinuxSandboxManager
import com.kainext.duo.sandbox.SandboxState
import com.kainext.duo.sandbox.openFileWithIntent
import com.kainext.duo.sandbox.resolveSandboxFile
import com.kainext.duo.sms.SmsReader
import com.kainext.duo.sms.SmsSender
import com.kainext.duo.sms.declaresReadSms
import com.kainext.duo.tools.AndroidContextTool
import com.kainext.duo.tools.AndroidFileTool
import com.kainext.duo.tools.AndroidShellTool
import com.kainext.duo.tools.AndroidUiTool
import com.kainext.duo.tools.AppPermission
import com.kainext.duo.tools.CalendarRepository
import com.kainext.duo.tools.CommonTools
import com.kainext.duo.tools.CreateCalendarEventTool
import com.kainext.duo.tools.NotificationHelper
import com.kainext.duo.tools.NotificationTools
import com.kainext.duo.tools.OpenFileTool
import com.kainext.duo.tools.PermissionController
import com.kainext.duo.tools.PrivilegedMode
import com.kainext.duo.tools.PrivilegedShell
import com.kainext.duo.tools.ProcessManagerTool
import com.kainext.duo.tools.SendNotificationTool
import com.kainext.duo.tools.SetAlarmTool
import com.kainext.duo.tools.ShellCommandTool
import com.kainext.duo.tools.SmsTools
import com.kainext.duo.tools.SshConfigureHostTool
import com.kainext.duo.tools.buildAgentToolSet
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import dev.spght.encryptedprefs.EncryptedSharedPreferences
import dev.spght.encryptedprefs.MasterKey
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.write
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import org.koin.java.KoinJavaComponent
import org.koin.java.KoinJavaComponent.inject
import kotlin.coroutines.CoroutineContext

actual fun httpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(OkHttp) {
    config(this)
}

actual fun getBackgroundDispatcher(): CoroutineContext = Dispatchers.IO

actual fun onDragAndDropEventDropped(event: DragAndDropEvent): PlatformFile? = null

actual val BackIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack

actual val currentPlatform: Platform = Platform.Mobile.Android

actual val defaultUiScale: Float = 1.0f

actual val isEmailSupported: Boolean = true

// Evaluated lazily because we need the Koin-injected Context. Whether READ_SMS
// is declared in the merged manifest is a build-time property (foss flavor adds
// it, playStore does not), so caching the first result is safe for the process
// lifetime. The try/catch guards screenshot / unit-test environments that may
// call `getPlatformToolDefinitions()` before Koin has been started.
actual val isSmsSupported: Boolean by lazy {
    try {
        val context: Context by inject(Context::class.java)
        context.declaresReadSms()
    } catch (_: Throwable) {
        false
    }
}

// Same lazy pattern as `isSmsSupported`: probe the merged manifest for the listener
// service. Foss flavor declares it, playStore does not.
actual val isNotificationsSupported: Boolean by lazy {
    try {
        val context: Context by inject(Context::class.java)
        context.declaresNotificationListener()
    } catch (_: Throwable) {
        false
    }
}

actual val isSplinterlandsSupported: Boolean = true

actual suspend fun compressImageBytes(bytes: ByteArray, mimeType: String): ByteArray {
    if (!mimeType.startsWith("image/")) return bytes
    return try {
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        val maxDim = 1024
        val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            bitmap.scale(newWidth, newHeight)
        } else {
            bitmap
        }
        val outputStream = java.io.ByteArrayOutputStream()
        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, outputStream)
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        outputStream.toByteArray()
    } catch (_: Exception) {
        bytes
    }
}

actual fun getAppFilesDirectory(): String {
    val context: Context by inject(Context::class.java)
    return context.filesDir.absolutePath
}

// Uses dev.spght:encryptedprefs-ktx — a maintained community fork of the deprecated
// androidx.security:security-crypto. We keep application-level encryption because
// secure settings store API keys, email passwords, and conversation encryption keys.
actual fun createSecureSettings(): Settings {
    val context: Context by inject(Context::class.java)
    return try {
        SharedPreferencesSettings(createEncryptedPrefs(context))
    } catch (_: Exception) {
        // AEADBadTagException occurs when Android Auto Backup restores the encrypted
        // prefs file but the Keystore key is hardware-bound and doesn't transfer.
        // Delete the corrupted file and recreate fresh encrypted prefs.
        context.deleteSharedPreferences("kai_secure_prefs")
        SharedPreferencesSettings(createEncryptedPrefs(context))
    }
}

private fun createEncryptedPrefs(context: Context): android.content.SharedPreferences {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    return EncryptedSharedPreferences.create(
        context,
        "kai_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
}

actual fun createLegacySettings(): Settings? {
    val context: Context by inject(Context::class.java)
    // Plain (unencrypted) prefs. The file name carries the applicationId so a rename can never
    // silently reuse another app's settings. Note the app was renamed to com.kainext.duo, which
    // makes this a separate install: Android gives it its own sandbox, so there is no old data to
    // migrate from and none is read here.
    val prefs = context.getSharedPreferences("com.kainext.duo_preferences", Context.MODE_PRIVATE)
    return SharedPreferencesSettings(prefs)
}

// Tool definitions for Android platform. Every tool Android can execute is listed, including the
// ones no per-tool switch controls — chat resolves display names from here, and an omission shows
// the raw tool id instead. Tools driven by a master toggle carry `userToggleable = false`, which
// is what keeps them out of the Tools tab.
actual fun getPlatformToolDefinitions(): List<ToolInfo> = CommonTools.commonToolDefinitions +
    listOf(
        SendNotificationTool.toolInfo,
        CreateCalendarEventTool.toolInfo,
        SetAlarmTool.toolInfo,
        OpenFileTool.toolInfo,
        ShellCommandTool.toolInfo,
        ProcessManagerTool.toolInfo,
        SshConfigureHostTool.toolInfo,
        AndroidShellTool.toolInfo,
        AndroidFileTool.toolInfo,
        AndroidUiTool.toolInfo,
        AndroidContextTool.toolInfo,
    )

actual fun getAvailableTools(): List<Tool> {
    val context: Context by inject(Context::class.java)
    val appSettings: AppSettings by inject(AppSettings::class.java)
    val memoryStore: MemoryStore by inject(MemoryStore::class.java)
    val taskStore: TaskStore by inject(TaskStore::class.java)
    val emailStore: EmailStore by inject(EmailStore::class.java)
    val mcpServerManager: McpServerManager by inject(McpServerManager::class.java)

    return buildAgentToolSet(
        appSettings = appSettings,
        memoryStore = memoryStore,
        taskStore = taskStore,
        mcpServerManager = mcpServerManager,
        emailStore = emailStore,
    ) {
        if (appSettings.isToolEnabled(SendNotificationTool.ID)) {
            val notificationPermissionController: PermissionController =
                KoinJavaComponent.get(PermissionController::class.java, permissionQualifier(AppPermission.POST_NOTIFICATIONS))
            add(SendNotificationTool.create(NotificationHelper(context, notificationPermissionController)))
        }

        if (appSettings.isToolEnabled(CreateCalendarEventTool.ID)) {
            val calendarPermissionController: PermissionController =
                KoinJavaComponent.get(PermissionController::class.java, permissionQualifier(AppPermission.CALENDAR))
            add(CreateCalendarEventTool.create(CalendarRepository(context, calendarPermissionController)))
        }

        if (appSettings.isToolEnabled(SetAlarmTool.ID)) {
            add(SetAlarmTool.create(context))
        }

        if (appSettings.isToolEnabled(OpenFileTool.schema.name)) {
            add(OpenFileTool)
        }

        if (appSettings.isSandboxEnabled()) {
            val sandboxManager: LinuxSandboxManager by inject(LinuxSandboxManager::class.java)
            if (sandboxManager.state.value is SandboxState.Ready) {
                add(ShellCommandTool)
                add(ProcessManagerTool)
                add(SshConfigureHostTool)
            }
        }

        // Device control: privileged shell, /sdcard file access and UI automation.
        // Master toggle lives in Settings → Device control; each area has its own switch.
        if (appSettings.isDeviceControlEnabled()) {
            val privilegedShell = PrivilegedShell {
                PrivilegedMode.fromId(appSettings.getPrivilegedMode())
            }
            if (appSettings.isDeviceShellEnabled()) {
                add(AndroidShellTool(privilegedShell))
            }
            if (appSettings.isSdcardAccessEnabled()) {
                add(AndroidFileTool(privilegedShell))
            }
            if (appSettings.isUiAutomationEnabled()) {
                add(AndroidUiTool(privilegedShell))
            }
            // Personal context: clipboard, photos, usage. Needs no shell, but it does read
            // device data, so it follows the same master switch as the rest of device control.
            add(AndroidContextTool())
        }

        // SMS read tools: triple-gated. `isSmsSupported` is only true on FOSS builds
        // (READ_SMS declared in merged manifest). `isSmsEnabled()` is the user toggle.
        // `hasPermission()` catches runtime revocation.
        val smsReaderForTools: SmsReader? = if (isSmsSupported) {
            val smsReader: SmsReader by inject(SmsReader::class.java)
            smsReader
        } else {
            null
        }
        if (smsReaderForTools != null && appSettings.isSmsEnabled() && smsReaderForTools.hasPermission()) {
            val smsStore: SmsStore by inject(SmsStore::class.java)
            addAll(SmsTools.getSmsReadTools(smsStore, smsReaderForTools))
        }

        // SMS send tools: independently gated on the Send toggle + SEND_SMS permission.
        // These only *stage* drafts — actual sending is user-triggered via the review banner.
        if (smsReaderForTools != null && appSettings.isSmsSendEnabled()) {
            val smsSender: SmsSender by inject(SmsSender::class.java)
            if (smsSender.hasPermission()) {
                val smsDraftStore: SmsDraftStore by inject(SmsDraftStore::class.java)
                addAll(SmsTools.getSmsSendTools(smsDraftStore, smsReaderForTools, smsSender))
            }
        }

        // Notification tools: triple-gated. `isNotificationsSupported` is FOSS-only
        // (listener service declared in merged manifest). `isNotificationsEnabled()`
        // is the user toggle. `hasAccess()` catches system-level revocation.
        if (isNotificationsSupported && appSettings.isNotificationsEnabled()) {
            val notificationReader: NotificationReader by inject(NotificationReader::class.java)
            if (notificationReader.hasAccess()) {
                val notificationStore: NotificationStore by inject(NotificationStore::class.java)
                addAll(NotificationTools.getNotificationTools(notificationStore, notificationReader))
            }
        }
    }
}

actual fun openUrl(url: String): Boolean = try {
    val context: Context by inject(Context::class.java)
    val parsedUri = url.toUri()
    if (parsedUri.scheme == "file") {
        // Only sandbox files (the guest's /root), resolved exactly like open_file. A raw path would
        // let the model hand any app-private file — settings with API keys, databases — to an
        // external app through a FileProvider read grant.
        val sandboxManager: LinuxSandboxManager by inject(LinuxSandboxManager::class.java)
        val rel = parsedUri.path?.removePrefix("/root")?.takeIf { it.startsWith("/") }?.trimStart('/')
        val file = rel?.let { resolveSandboxFile(sandboxManager.homePath, it) }
        file != null && file.isFile && openFileWithIntent(context, file).success
    } else {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, parsedUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        true
    }
} catch (_: Exception) {
    false
}

actual fun decodeToImageBitmap(bytes: ByteArray): ImageBitmap? = try {
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
} catch (_: Exception) {
    null
}

@androidx.compose.runtime.Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}

actual suspend fun saveFileToDevice(bytes: ByteArray, baseName: String, extension: String) {
    val file = FileKit.openFileSaver(suggestedName = baseName, defaultExtension = extension)
    file?.write(bytes)
}
