package com.inspiredandroid.kai.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.ui.gradientMagenta
import com.inspiredandroid.kai.ui.gradientPurple
import com.inspiredandroid.kai.ui.gradientViolet

private val baseStops = listOf(0f to gradientPurple, 0.33f to gradientViolet, 0.66f to gradientMagenta)

@Composable
fun Modifier.animatedGradientBorder(
    cornerRadius: Dp,
    borderWidth: Dp = 2.dp,
    backgroundColor: Color? = null,
): Modifier {
    val infiniteTransition = rememberInfiniteTransition()
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
        ),
    )
    return this.drawWithContent {
        val borderPx = borderWidth.toPx()
        val cr = CornerRadius(cornerRadius.toPx())
        if (backgroundColor != null) {
            drawRoundRect(color = backgroundColor, cornerRadius = cr)
        }
        drawContent()
        val shifted = baseStops.map { (pos, color) ->
            ((pos - progress + 1f) % 1f) to color
        }.sortedBy { it.first }
        val last = shifted.last()
        val first = shifted.first()
        val wrapDist = 1f - last.first + first.first
        val t = if (wrapDist > 0f) (1f - last.first) / wrapDist else 0f
        val boundary = lerp(last.second, first.second, t)
        val colorStops = arrayOf(
            0f to boundary,
            shifted[0].first to shifted[0].second,
            shifted[1].first to shifted[1].second,
            shifted[2].first to shifted[2].second,
            1f to boundary,
        )
        drawRoundRect(
            brush = Brush.sweepGradient(colorStops = colorStops),
            cornerRadius = cr,
            style = Stroke(width = borderPx),
        )
    }
}
