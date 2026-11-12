package com.ryanjames.lunar.ui.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Line icons for the app shell, drawn on a 24-unit grid. */
internal enum class ShellIcon {
    LIBRARY,
    SETLISTS,
    SONGBOOKS,
    COMPOSE,
    SOURCES,
    SETTINGS,
    MENU,
    COLLAPSE,
    EXPAND,
}

@Composable
internal fun ShellIconGlyph(
    icon: ShellIcon,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val unit = this.size.minDimension / 24f
        val stroke = Stroke(width = 1.8f * unit, cap = StrokeCap.Round)
        fun p(x: Float, y: Float) = Offset(x * unit, y * unit)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, p(x1, y1), p(x2, y2), strokeWidth = stroke.width, cap = StrokeCap.Round)
        fun rect(x: Float, y: Float, w: Float, h: Float) = drawRoundRect(
            color = color,
            topLeft = p(x, y),
            size = Size(w * unit, h * unit),
            cornerRadius = CornerRadius(1.5f * unit),
            style = stroke,
        )

        when (icon) {
            ShellIcon.LIBRARY -> {
                rect(4f, 4f, 4f, 16f)
                rect(10f, 4f, 4f, 16f)
                line(16.5f, 5f, 20f, 19.5f)
            }

            ShellIcon.SETLISTS -> {
                listOf(6f, 12f, 18f).forEach { y ->
                    drawCircle(color, radius = 1.2f * unit, center = p(4.5f, y))
                    line(8.5f, y, 20f, y)
                }
            }

            ShellIcon.SONGBOOKS -> {
                line(12f, 6f, 12f, 20f)
                line(3f, 5f, 12f, 6f)
                line(12f, 6f, 21f, 5f)
                line(3f, 5f, 3f, 19f)
                line(21f, 5f, 21f, 19f)
                line(3f, 19f, 12f, 20f)
                line(12f, 20f, 21f, 19f)
            }

            ShellIcon.COMPOSE -> {
                drawCircle(color, radius = 3f * unit, center = p(8f, 17.5f), style = stroke)
                line(11f, 17.5f, 11f, 4f)
                line(11f, 4f, 18f, 7f)
            }

            ShellIcon.SOURCES -> {
                line(3f, 7f, 3f, 19f)
                line(3f, 19f, 21f, 19f)
                line(21f, 19f, 21f, 9f)
                line(21f, 9f, 12f, 9f)
                line(12f, 9f, 10f, 6f)
                line(10f, 6f, 3f, 6f)
                line(3f, 6f, 3f, 7f)
            }

            ShellIcon.SETTINGS -> settingsGlyph(color, stroke, unit)

            ShellIcon.MENU -> {
                line(4f, 7f, 20f, 7f)
                line(4f, 12f, 20f, 12f)
                line(4f, 17f, 20f, 17f)
            }

            ShellIcon.COLLAPSE -> {
                rect(3f, 4f, 18f, 16f)
                line(9f, 4f, 9f, 20f)
                line(16f, 9f, 13f, 12f)
                line(13f, 12f, 16f, 15f)
            }

            ShellIcon.EXPAND -> {
                rect(3f, 4f, 18f, 16f)
                line(9f, 4f, 9f, 20f)
                line(13f, 9f, 16f, 12f)
                line(16f, 12f, 13f, 15f)
            }
        }
    }
}

private fun DrawScope.settingsGlyph(color: Color, stroke: Stroke, unit: Float) {
    val center = Offset(12f * unit, 12f * unit)
    drawCircle(color, radius = 3f * unit, center = center, style = stroke)
    drawCircle(color, radius = 7f * unit, center = center, style = stroke)
    repeat(8) { index ->
        val angle = index * (kotlin.math.PI / 4).toFloat()
        val dx = kotlin.math.cos(angle)
        val dy = kotlin.math.sin(angle)
        drawLine(
            color = color,
            start = Offset(center.x + dx * 7f * unit, center.y + dy * 7f * unit),
            end = Offset(center.x + dx * 9.5f * unit, center.y + dy * 9.5f * unit),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
    }
}
