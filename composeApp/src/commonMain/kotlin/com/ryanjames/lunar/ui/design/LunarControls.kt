package com.ryanjames.lunar.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

enum class LunarButtonStyle {
    /** Filled accent button for the main action in a region. */
    PRIMARY,

    /** Outlined button for secondary actions. */
    SECONDARY,

    /** Borderless accent text for low-emphasis actions. */
    TEXT,
}

@Composable
fun LunarButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: LunarButtonStyle = LunarButtonStyle.PRIMARY,
    enabled: Boolean = true,
    icon: LunarIconKind? = null,
) {
    val tokens = lunarTokens()
    val colors = tokens.colors
    val container = when (style) {
        LunarButtonStyle.PRIMARY -> colors.accent
        LunarButtonStyle.SECONDARY, LunarButtonStyle.TEXT -> Color.Transparent
    }
    val content = when (style) {
        LunarButtonStyle.PRIMARY -> colors.onAccent
        LunarButtonStyle.SECONDARY -> colors.ink
        LunarButtonStyle.TEXT -> colors.accent
    }
    val border = if (style == LunarButtonStyle.SECONDARY) BorderStroke(1.dp, colors.strong) else null

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .defaultMinSize(minHeight = tokens.focus.minTouchTarget)
            .lunarFocusRing(),
        shape = tokens.shapes.medium,
        color = container,
        contentColor = content,
        border = border,
    ) {
        Row(
            modifier = Modifier
                .alpha(if (enabled) 1f else 0.45f)
                .padding(
                    horizontal = if (style == LunarButtonStyle.TEXT) tokens.spacing.sm else tokens.spacing.lg,
                    vertical = tokens.spacing.sm,
                ),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let { LunarIcon(kind = it, contentDescription = null, size = 16.dp) }
            Text(text = text, style = tokens.typography.button, color = content)
        }
    }
}

/** An option for [TextToggle]: a short label and the value it selects. */
data class TextToggleOption<T>(
    val label: String,
    val value: T,
)

/** A compact text-only segmented control, rendered as "List | Gallery". */
@Composable
fun <T> TextToggle(
    options: List<TextToggleOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = lunarTokens()
    Row(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, option ->
            if (index > 0) {
                Text(text = "|", style = tokens.typography.bodySmall, color = tokens.colors.rule)
            }
            val isSelected = option.value == selected
            Text(
                text = option.label,
                style = tokens.typography.bodySmall.copy(
                    textDecoration = if (isSelected) TextDecoration.Underline else null,
                ),
                color = if (isSelected) tokens.colors.ink else tokens.colors.muted,
                modifier = Modifier
                    .defaultMinSize(minHeight = tokens.focus.minTouchTarget)
                    .lunarFocusRing(tokens.shapes.small)
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelect(option.value) },
                    )
                    .padding(horizontal = tokens.spacing.xs, vertical = tokens.spacing.md),
            )
        }
    }
}

/** A small square marking a collection, coloured from the skin's collection palette. */
@Composable
fun CollectionSwatch(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
) {
    val tokens = lunarTokens()
    Box(
        modifier = modifier
            .size(size)
            .background(color = collectionColor(tokens.skin, name), shape = tokens.shapes.extraSmall),
    )
}

/**
 * A panel anchored below its parent layout (flipping above when there is no room), dismissed by
 * clicking outside or pressing Escape. Place it next to the anchor inside the same Box. It fades in
 * and out, and on compact widths (see [LocalLunarCompactLayout]) it is shown as a bottom sheet.
 */
@Composable
fun Popover(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalLunarCompactLayout.current) {
        if (expanded) PopoverSheet(onDismissRequest = onDismissRequest, modifier = modifier, content = content)
        return
    }
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = expanded
    if (!visibility.currentState && !visibility.targetState) return

    val tokens = lunarTokens()
    val gap = with(LocalDensity.current) { tokens.spacing.xs.roundToPx() }
    Popup(
        popupPositionProvider = AnchoredBelowPositionProvider(gap),
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        AnimatedVisibility(
            visibleState = visibility,
            enter = fadeIn(tween(durationMillis = tokens.motion.quickMillis)),
            exit = fadeOut(tween(durationMillis = tokens.motion.quickMillis)),
        ) {
            Surface(
                modifier = modifier
                    .testTag(LunarTestTags.POPOVER)
                    .widthIn(min = 160.dp, max = 360.dp),
                shape = tokens.shapes.large,
                color = tokens.colors.panel,
                contentColor = tokens.colors.ink,
                border = BorderStroke(1.dp, tokens.colors.rule),
                shadowElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.padding(vertical = tokens.spacing.sm),
                    content = content,
                )
            }
        }
    }
}

/** The phone form of [Popover]: the same content in a modal bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PopoverSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = lunarTokens()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.testTag(LunarTestTags.POPOVER_SHEET),
        shape = tokens.shapes.extraLarge,
        containerColor = tokens.colors.panel,
        contentColor = tokens.colors.ink,
        scrimColor = tokens.colors.scrim,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = tokens.spacing.lg),
            content = content,
        )
    }
}

/** Positions a popup under its anchor, aligned to the start edge, and keeps it on screen. */
internal class AnchoredBelowPositionProvider(
    private val gap: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val preferredX = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.left
        } else {
            anchorBounds.right - popupContentSize.width
        }
        val x = preferredX.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val below = anchorBounds.bottom + gap
        val above = anchorBounds.top - gap - popupContentSize.height
        val y = when {
            below + popupContentSize.height <= windowSize.height -> below
            above >= 0 -> above
            else -> (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        }
        return IntOffset(x, y)
    }
}

/**
 * A modal dialog in the Engraved style: a heading, body content, and a trailing row of actions
 * (typically [LunarButton]s).
 */
@Composable
fun LunarDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = lunarTokens()
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = modifier
                .widthIn(min = 280.dp, max = 560.dp)
                .semantics { paneTitle = title },
            shape = tokens.shapes.large,
            color = tokens.colors.panel,
            contentColor = tokens.colors.ink,
            border = BorderStroke(1.dp, tokens.colors.rule),
        ) {
            Column(
                modifier = Modifier.padding(tokens.spacing.xl),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
            ) {
                Text(text = title, style = tokens.typography.heading, color = tokens.colors.ink)
                content()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = tokens.spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        }
    }
}
