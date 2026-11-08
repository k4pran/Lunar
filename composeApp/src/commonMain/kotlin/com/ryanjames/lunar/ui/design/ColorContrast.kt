package com.ryanjames.lunar.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/**
 * WCAG 2.x contrast ratio between two colours, from 1.0 (identical) to 21.0 (black on white).
 * A translucent foreground is composited over the background first.
 */
fun contrastRatio(foreground: Color, background: Color): Double {
    val opaqueBackground = background.copy(alpha = 1f)
    val opaqueForeground = foreground.compositeOver(opaqueBackground)
    val first = opaqueForeground.luminance().toDouble()
    val second = opaqueBackground.luminance().toDouble()
    return (max(first, second) + 0.05) / (min(first, second) + 0.05)
}
