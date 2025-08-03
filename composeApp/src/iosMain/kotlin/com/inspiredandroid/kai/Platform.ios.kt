package com.inspiredandroid.kai

import androidx.compose.ui.draganddrop.DragAndDropEvent
import io.github.vinceglb.filekit.PlatformFile
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import kotlin.coroutines.CoroutineContext

actual fun httpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(Darwin) {
    config(this)
}

actual fun getBackgroundDispatcher(): CoroutineContext = Dispatchers.IO

actual fun openUrl(url: String) {
    UIApplication.sharedApplication.openURL(NSURL(string = url))
}

actual fun onDragAndDropEventDropped(event: DragAndDropEvent): PlatformFile? = null
