package com.ryanjames.lunar.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ryanjames.lunar.ui.LunarTooltip

/** Widths below this are treated as a phone: popovers become bottom sheets. */
val CompactLayoutMaxWidth: Dp = 600.dp

fun isCompactWidth(width: Dp): Boolean = width < CompactLayoutMaxWidth

/** True when the app is laid out for a phone-sized window. Provided by [LunarAdaptiveLayout]. */
val LocalLunarCompactLayout = staticCompositionLocalOf { false }

/** Measures the available width once and tells descendants whether to use the compact layout. */
@Composable
fun LunarAdaptiveLayout(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        CompositionLocalProvider(
            LocalLunarCompactLayout provides isCompactWidth(maxWidth),
            content = content,
        )
    }
}

/** Grows the hit area to the token minimum touch target without changing how the control looks. */
fun Modifier.lunarTouchTarget(): Modifier = composed {
    val minSize = lunarTokens().focus.minTouchTarget
    sizeIn(minWidth = minSize, minHeight = minSize)
}

/**
 * Draws a visible ring while this element or a child has keyboard focus. Place it before the
 * focusable modifier (for example `clickable`) so it observes that element's focus.
 */
fun Modifier.lunarFocusRing(shape: Shape? = null): Modifier = composed {
    val tokens = lunarTokens()
    var focused by remember { mutableStateOf(false) }
    val ringColor by animateColorAsState(
        targetValue = if (focused) tokens.colors.accent else Color.Transparent,
        animationSpec = tween(durationMillis = tokens.motion.quickMillis),
        label = "focus-ring",
    )
    onFocusChanged { focused = it.hasFocus }
        .border(width = tokens.focus.ringWidth, color = ringColor, shape = shape ?: tokens.shapes.medium)
}

/**
 * A button that shows only an icon. The [contentDescription] is required: it labels the button for
 * screen readers and doubles as its tooltip. The hit area is at least the token touch target.
 */
@Composable
fun LunarIconButton(
    icon: LunarIconKind,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = lunarTokens().colors.ink2,
    iconSize: Dp = 18.dp,
) {
    val tokens = lunarTokens()
    LunarTooltip(text = contentDescription, modifier = modifier) {
        Box(
            modifier = Modifier
                .lunarTouchTarget()
                .lunarFocusRing()
                .clip(tokens.shapes.medium)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center,
        ) {
            LunarIcon(
                kind = icon,
                contentDescription = null,
                size = iconSize,
                tint = tint,
                modifier = Modifier.alpha(if (enabled) 1f else 0.4f),
            )
        }
    }
}
