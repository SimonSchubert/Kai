package com.inspiredandroid.kai.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun LogoAnimation(
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
) {
    val animatable = remember { Animatable(1f) }
    var drawDarkFirst by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            animatable.animateTo(-1f, tween(767, easing = EaseInOut))
            drawDarkFirst = !drawDarkFirst
            animatable.animateTo(1f, tween(767, easing = EaseInOut))
            drawDarkFirst = !drawDarkFirst
        }
    }
    Canvas(modifier = modifier.size(size)) {
        val center = this.center
        val radius = center.y
        val displacement = radius * animatable.value
        val darkCenter = Offset(center.x + displacement, center.y)
        val lightCenter = Offset(center.x - displacement, center.y)
        if (drawDarkFirst) {
            drawCircle(Color(0xFF582FB7), radius, darkCenter)
            drawCircle(Color(0xFF8063C5), radius, lightCenter)
        } else {
            drawCircle(Color(0xFF8063C5), radius, lightCenter)
            drawCircle(Color(0xFF582FB7), radius, darkCenter)
        }
    }
}
