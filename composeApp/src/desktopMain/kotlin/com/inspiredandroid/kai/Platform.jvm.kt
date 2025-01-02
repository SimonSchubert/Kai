package com.inspiredandroid.kai

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.awt.Desktop
import java.net.URI

actual fun httpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(CIO) {
    config(this)
}

actual fun getBackgroundDispatcher(): CoroutineDispatcher = Dispatchers.IO

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
