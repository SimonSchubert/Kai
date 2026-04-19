package com.inspiredandroid.kai.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import com.inspiredandroid.kai.PlatformBackHandler
import com.inspiredandroid.kai.ui.chat.composables.FullScreenImageViewerOverlay

val LocalShowFullScreenImage = staticCompositionLocalOf<(ImageBitmap) -> Unit> { { } }

@Composable
fun FullScreenImageHost(content: @Composable () -> Unit) {
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    val show = remember { { bitmap: ImageBitmap -> image = bitmap } }
    val dismiss = remember { { image = null } }

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalShowFullScreenImage provides show) {
            content()
        }
        image?.let { bmp ->
            FullScreenImageViewerOverlay(bitmap = bmp, onDismiss = dismiss)
            PlatformBackHandler(enabled = true, onBack = dismiss)
        }
    }
}
