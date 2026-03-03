package com.inspiredandroid.kai

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.graphics.vector.ImageVector
import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.data.MemoryStore
import com.inspiredandroid.kai.data.TaskStore
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.tools.CommonTools
import com.inspiredandroid.kai.tools.SchedulingTools
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import io.github.vinceglb.filekit.PlatformFile
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext

actual fun httpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(Darwin) {
    config(this)
}

actual fun getBackgroundDispatcher(): CoroutineContext = Dispatchers.IO

actual fun onDragAndDropEventDropped(event: DragAndDropEvent): PlatformFile? = null

actual val BackIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBackIos

actual val isMobilePlatform: Boolean = true

actual val platformName: String = "iOS"

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
}

actual fun getAvailableTools(): List<Tool> = buildList {
    addAll(CommonTools.getCommonTools(IosKoinHelper.appSettings))
    addAll(CommonTools.getMemoryTools(IosKoinHelper.memoryStore))
    if (IosKoinHelper.appSettings.isSchedulingEnabled()) {
        addAll(SchedulingTools.getSchedulingTools(IosKoinHelper.taskStore))
    }
}
