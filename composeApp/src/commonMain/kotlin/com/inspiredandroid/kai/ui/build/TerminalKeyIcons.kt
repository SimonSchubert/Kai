package com.inspiredandroid.kai.ui.build

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Glyphs for the terminal key row.
//
// The row used to print the arrow characters (←↑↓→⏎) in the monospace face the
// terminal itself draws with. Those are text, not icons: they come out
// hairline-thin, sit at whatever weight the platform's fallback font happens to
// have, and are sized by the cap's font size rather than an icon size — next to
// the Material icons in the input bar below they read as stray characters
// rather than keys. These are drawn instead, in the round-capped stroke style
// the app's other hand-drawn icons use, heavy enough to stay legible at arm's
// length on a phone.

private const val ArrowStrokeWidth = 3f

/** Size the arrows are drawn at; the stroke scales with it. */
internal val TerminalKeyIconSize = 22.dp

/** Base glyph points left; every other direction is the same path rotated. */
private fun arrowIcon(name: String, rotationDegrees: Float): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        group(rotate = rotationDegrees, pivotX = 12f, pivotY = 12f) {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = ArrowStrokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(13.5f, 5.5f)
                lineTo(7f, 12f)
                lineTo(13.5f, 18.5f)
            }
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = ArrowStrokeWidth,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(7.5f, 12f)
                lineTo(19f, 12f)
            }
        }
    }.build()

internal val TerminalArrowLeft: ImageVector by lazy { arrowIcon("TerminalArrowLeft", 0f) }
internal val TerminalArrowUp: ImageVector by lazy { arrowIcon("TerminalArrowUp", 90f) }
internal val TerminalArrowRight: ImageVector by lazy { arrowIcon("TerminalArrowRight", 180f) }
internal val TerminalArrowDown: ImageVector by lazy { arrowIcon("TerminalArrowDown", 270f) }

/** Enter sits alone at the end of the row, so it is drawn a size up. */
internal val TerminalEnterIconSize = 24.dp

/**
 * Return arrow: the hook every keyboard prints on its Enter key.
 *
 * Its head is a filled triangle rather than a third stroke — a stroked head has
 * to be small enough to fit under the hook, and at cap size that came out too
 * faint to read as an arrow at all.
 */
internal val TerminalEnter: ImageVector by lazy {
    ImageVector.Builder(
        name = "TerminalEnter",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 3.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(19f, 6f)
            lineTo(19f, 13f)
            lineTo(9f, 13f)
        }
        path(fill = SolidColor(Color.White)) {
            moveTo(4.5f, 13f)
            lineTo(11.5f, 8.5f)
            lineTo(11.5f, 17.5f)
            close()
        }
    }.build()
}
