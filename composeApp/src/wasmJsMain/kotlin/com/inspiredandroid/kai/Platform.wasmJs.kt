package com.inspiredandroid.kai

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.graphics.vector.ImageVector
import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.tools.CommonTools
import com.russhwolf.settings.Settings
import com.russhwolf.settings.StorageSettings
import io.github.vinceglb.filekit.PlatformFile
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
}

actual fun getAvailableTools(): List<Tool> = CommonTools.getCommonTools(WebKoinHelper.appSettings)
