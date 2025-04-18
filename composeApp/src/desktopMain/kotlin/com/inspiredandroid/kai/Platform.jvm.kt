@file:OptIn(ExperimentalComposeUiApi::class)

package com.inspiredandroid.kai

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import io.github.vinceglb.filekit.core.PlatformFile
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.Dispatchers
import java.awt.Desktop
import java.io.File
import java.net.URI
import kotlin.coroutines.CoroutineContext

actual fun httpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(CIO) {
    config(this)
}

actual fun getBackgroundDispatcher(): CoroutineContext = Dispatchers.IO

actual fun openUrl(url: String) {
    if (Desktop.isDesktopSupported()) {
        val desktop = Desktop.getDesktop()
        try {
            val uri = URI(url)
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                desktop.browse(uri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    } else {
        return null
    }
}
