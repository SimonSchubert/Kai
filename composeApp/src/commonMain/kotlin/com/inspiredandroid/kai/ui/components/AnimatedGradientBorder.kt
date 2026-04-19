package com.inspiredandroid.kai.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
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

private const val STOP_A = 0f
private const val STOP_B = 0.33f
private const val STOP_C = 0.66f

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
    // Reuse the same color-stops array across frames; only the pair positions
    // are mutated per frame so no allocations happen in the animated draw loop.
    val colorStops = remember {
        arrayOf(
            0f to Color.Transparent,
            0f to gradientPurple,
            0f to gradientViolet,
            0f to gradientMagenta,
            1f to Color.Transparent,
        )
    }
    return this.drawWithCache {
        val borderPx = borderWidth.toPx()
        val cr = CornerRadius(cornerRadius.toPx())
        val strokeStyle = Stroke(width = borderPx)
        onDrawWithContent {
            if (backgroundColor != null) {
                drawRoundRect(color = backgroundColor, cornerRadius = cr)
            }
            drawContent()

            // Shift the three base stops by `progress`, wrap into [0,1), then
            // sort ascending. Three comparisons ≪ the previous list map+sort.
            val p = progress
            var posA = (STOP_A - p + 1f) % 1f
            var posB = (STOP_B - p + 1f) % 1f
            var posC = (STOP_C - p + 1f) % 1f
            var colA = gradientPurple
            var colB = gradientViolet
            var colC = gradientMagenta
            if (posA > posB) {
                val tp = posA
                posA = posB
                posB = tp
                val tc = colA
                colA = colB
                colB = tc
            }
            if (posB > posC) {
                val tp = posB
                posB = posC
                posC = tp
                val tc = colB
                colB = colC
                colC = tc
            }
            if (posA > posB) {
                val tp = posA
                posA = posB
                posB = tp
                val tc = colA
                colA = colB
                colB = tc
            }

            // Boundary color so the wrap-around at 0 and 1 matches seamlessly.
            val wrapDist = 1f - posC + posA
            val t = if (wrapDist > 0f) (1f - posC) / wrapDist else 0f
            val boundary = lerp(colC, colA, t)

            colorStops[0] = 0f to boundary
            colorStops[1] = posA to colA
            colorStops[2] = posB to colB
            colorStops[3] = posC to colC
            colorStops[4] = 1f to boundary

            drawRoundRect(
                brush = Brush.sweepGradient(colorStops = colorStops),
                cornerRadius = cr,
                style = strokeStyle,
            )
        }
    }
}
