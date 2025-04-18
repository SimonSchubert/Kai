package com.inspiredandroid.kai

import androidx.compose.ui.draganddrop.DragAndDropEvent
import io.github.vinceglb.filekit.core.PlatformFile
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.js.Js
import kotlinx.browser.window
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

actual fun httpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(Js) {
    config(this)
}

actual fun getBackgroundDispatcher(): CoroutineContext = EmptyCoroutineContext

actual fun openUrl(url: String) {
    window.open(url, "_blank")
}

actual fun onDragAndDropEventDropped(event: DragAndDropEvent): PlatformFile? = null
