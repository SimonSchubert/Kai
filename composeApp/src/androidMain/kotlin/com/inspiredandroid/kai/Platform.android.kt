package com.inspiredandroid.kai

import android.content.Context
import android.content.Intent
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.core.net.toUri
import io.github.vinceglb.filekit.core.PlatformFile
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.Dispatchers
import org.koin.java.KoinJavaComponent.inject
import kotlin.coroutines.CoroutineContext

actual fun httpClient(config: HttpClientConfig<*>.() -> Unit): HttpClient = HttpClient(CIO) {
    config(this)
}

actual fun getBackgroundDispatcher(): CoroutineContext = Dispatchers.IO

actual fun openUrl(url: String) {
    val context: Context by inject(Context::class.java)
    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
    context.startActivity(intent)
}

actual fun onDragAndDropEventDropped(event: DragAndDropEvent): PlatformFile? = null
