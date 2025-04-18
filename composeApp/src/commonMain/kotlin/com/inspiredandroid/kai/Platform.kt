package com.inspiredandroid.kai

import androidx.compose.ui.draganddrop.DragAndDropEvent
import io.github.vinceglb.filekit.core.PlatformFile
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import kotlin.coroutines.CoroutineContext

expect fun httpClient(config: HttpClientConfig<*>.() -> Unit = {}): HttpClient

expect fun getBackgroundDispatcher(): CoroutineContext

expect fun openUrl(url: String)

expect fun onDragAndDropEventDropped(event: DragAndDropEvent): PlatformFile?
