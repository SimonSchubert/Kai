@file:OptIn(ExperimentalComposeUiApi::class)

package com.inspiredandroid.kai

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.graphics.vector.ImageVector
import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.data.MemoryStore
import com.inspiredandroid.kai.data.TaskStore
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.tools.CommonTools
import com.inspiredandroid.kai.tools.SchedulingTools
import com.inspiredandroid.kai.tools.ShellCommandTool
import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import io.github.vinceglb.filekit.PlatformFile
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.Dispatchers
import org.koin.java.KoinJavaComponent.inject
import java.io.File
import java.net.URI
import java.util.prefs.Preferences
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

actual fun createSecureSettings(): Settings {
    // Desktop has no built-in secure storage - using standard Preferences
    val preferences = Preferences.userRoot().node("com.inspiredandroid.kai")
    return PreferencesSettings(preferences)
}

actual fun createLegacySettings(): Settings? = null // Same storage location, no migration needed

actual fun getPlatformToolDefinitions(): List<ToolInfo> = CommonTools.commonToolDefinitions + ShellCommandTool.toolInfo

actual fun getAvailableTools(): List<Tool> {
    val appSettings: AppSettings by inject(AppSettings::class.java)
    val memoryStore: MemoryStore by inject(MemoryStore::class.java)
    val taskStore: TaskStore by inject(TaskStore::class.java)
    return buildList {
        addAll(CommonTools.getCommonTools(appSettings))
        addAll(CommonTools.getMemoryTools(memoryStore))
        if (appSettings.isSchedulingEnabled()) {
            addAll(SchedulingTools.getSchedulingTools(taskStore))
        }
        if (appSettings.isToolEnabled(ShellCommandTool.schema.name, defaultEnabled = false)) {
            add(ShellCommandTool)
        }
    }
}
