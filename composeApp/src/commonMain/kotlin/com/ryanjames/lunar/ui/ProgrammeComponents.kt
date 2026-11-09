package com.ryanjames.lunar.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * Title, dotted leader, trailing content. The leader keeps a minimum width so the trailing
 * content is never squeezed out; the title truncates instead.
 */
@Composable
internal fun LeaderRow(
    title: String,
    modifier: Modifier = Modifier,
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val leaderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
    Layout(
        modifier = modifier,
        content = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = leading,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Canvas(modifier = Modifier.height(2.dp)) {
                drawLine(
                    color = leaderColor,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 6f)),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = trailing,
            )
        },
    ) { measurables, constraints ->
        val gap = 8.dp.roundToPx()
        val minLeader = 24.dp.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val leading = measurables[0].measure(loose)
        val totalWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
        val trailing: Placeable
        val titleMax: Int
        if (constraints.hasBoundedWidth) {
            val shared = (totalWidth - leading.width - minLeader - gap * 3).coerceAtLeast(0)
            val titleWanted = measurables[1].maxIntrinsicWidth(constraints.maxHeight)
            // The trailing content takes what the title leaves, but never more than its share,
            // so a long composer or a narrow pane can't push the title out of sight.
            val trailingMax = maxOf(
                shared - titleWanted,
                (shared * MAX_TRAILING_SHARE).toInt(),
            ).coerceIn(0, shared)
            trailing = measurables[3].measure(loose.copy(maxWidth = trailingMax))
            titleMax = (shared - trailing.width).coerceAtLeast(0)
        } else {
            trailing = measurables[3].measure(loose)
            titleMax = Constraints.Infinity
        }
        val title = measurables[1].measure(loose.copy(maxWidth = titleMax))
        val width = if (constraints.hasBoundedWidth) {
            totalWidth
        } else {
            leading.width + title.width + trailing.width + minLeader + gap * 3
        }
        val leaderWidth = (width - leading.width - title.width - trailing.width - gap * 3)
            .coerceAtLeast(0)
        val leader = measurables[2].measure(Constraints.fixed(leaderWidth, 2.dp.roundToPx()))
        val height = maxOf(leading.height, title.height, trailing.height)
        layout(width, height) {
            var x = 0
            leading.placeRelative(x, (height - leading.height) / 2)
            x += leading.width + if (leading.width > 0) gap else 0
            title.placeRelative(x, (height - title.height) / 2)
            x += title.width + gap
            // Sit the leader just under the title's centre, like a printed programme.
            leader.placeRelative(x, (height + title.height / 3) / 2)
            trailing.placeRelative(width - trailing.width, (height - trailing.height) / 2)
        }
    }
}

/** Largest fraction of a [LeaderRow]'s free width its trailing content may take from the title. */
private const val MAX_TRAILING_SHARE = 0.45f

/** A small floating panel anchored under [anchor], dismissed by clicking outside it. */
@Composable
internal fun AnchoredPopover(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    anchor: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier) {
        anchor()
        if (expanded) {
            val positionProvider = remember { BelowAnchorPositionProvider(gapPx = 6) }
            Popup(
                popupPositionProvider = positionProvider,
                onDismissRequest = onDismiss,
                properties = PopupProperties(focusable = true),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(min = 240.dp, max = 320.dp)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        content = content,
                    )
                }
            }
        }
    }
}

private class BelowAnchorPositionProvider(
    private val gapPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left
            .coerceAtMost(windowSize.width - popupContentSize.width)
            .coerceAtLeast(0)
        val below = anchorBounds.bottom + gapPx
        val y = if (below + popupContentSize.height <= windowSize.height) {
            below
        } else {
            (anchorBounds.top - gapPx - popupContentSize.height).coerceAtLeast(0)
        }
        return IntOffset(x, y)
    }
}

/** A "- n +" stepper clamped to [range]. */
@Composable
internal fun CountStepper(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    label: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = { onValueChange((value - 1).coerceIn(range)) },
            enabled = value > range.first,
            modifier = Modifier.semantics { contentDescription = "Fewer scores" },
        ) {
            Text("-")
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif),
            modifier = Modifier
                .widthIn(min = 32.dp)
                .semantics { contentDescription = "$label: $value" },
            textAlign = TextAlign.Center,
        )
        OutlinedButton(
            onClick = { onValueChange((value + 1).coerceIn(range)) },
            enabled = value < range.last,
            modifier = Modifier.semantics { contentDescription = "More scores" },
        ) {
            Text("+")
        }
    }
}

@Composable
internal fun DragHandleGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp)) {
        val stroke = 2.dp.toPx()
        listOf(0.3f, 0.5f, 0.7f).forEach { fraction ->
            drawLine(
                color = color,
                start = Offset(size.width * 0.15f, size.height * fraction),
                end = Offset(size.width * 0.85f, size.height * fraction),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** A cloth-bound cover: a tinted board with the title stamped on it. */
@Composable
internal fun ClothCover(
    title: String,
    width: Dp,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
) {
    val (cloth, ink) = clothColors(title)
    Box(
        modifier = modifier
            .size(width = width, height = width * 1.32f)
            .background(cloth, MaterialTheme.shapes.extraSmall)
            .border(1.dp, ink.copy(alpha = 0.25f), MaterialTheme.shapes.extraSmall)
            .padding(width * 0.08f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, ink.copy(alpha = 0.45f), MaterialTheme.shapes.extraSmall),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (showTitle) title else title.take(1).uppercase(),
                style = if (showTitle) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.labelLarge
                }.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
                color = ink,
                textAlign = TextAlign.Center,
                maxLines = if (showTitle) 4 else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}

@Composable
private fun clothColors(title: String): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    val swatches = listOf(
        scheme.primary to scheme.onPrimary,
        scheme.secondary to scheme.onSecondary,
        scheme.tertiary to scheme.onTertiary,
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
    )
    return swatches[clothSwatchIndex(title, swatches.size)]
}

/** Stable swatch slot for a songbook name. */
internal fun clothSwatchIndex(name: String, slotCount: Int): Int {
    if (slotCount <= 0) return 0
    val hash = name.trim().lowercase().fold(17) { acc, char -> acc * 31 + char.code }
    return ((hash % slotCount) + slotCount) % slotCount
}
