package com.inspiredandroid.kai

import android.content.Context
import android.content.Intent
import android.net.Uri
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
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}
