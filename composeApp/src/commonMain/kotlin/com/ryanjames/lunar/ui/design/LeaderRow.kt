package com.ryanjames.lunar.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/** Minimum width kept for the dotted leader so trailing content is never pushed out of the row. */
val LeaderMinWidth: Dp = 24.dp

/** Horizontal gap between the title, the leader and the trailing content. */
private val LeaderGap: Dp = 8.dp

/** Widths, in pixels, allotted to each part of a [LeaderRow]. */
data class LeaderRowWidths(
    val title: Int,
    val leader: Int,
    val trailing: Int,
)

/**
 * Splits [totalWidth] between title, leader and trailing content. Trailing content keeps its
 * natural width first, the leader keeps at least [leaderMinWidth], and the title takes what is
 * left (and is truncated if it needs more). Any spare space goes to the leader.
 */
fun allocateLeaderRow(
    totalWidth: Int,
    titleWidth: Int,
    trailingWidth: Int,
    leaderMinWidth: Int,
    gap: Int,
): LeaderRowWidths {
    val gaps = gap * 2
    val trailing = min(trailingWidth, max(0, totalWidth - leaderMinWidth - gaps))
    val titleBudget = max(0, totalWidth - trailing - leaderMinWidth - gaps)
    val title = min(titleWidth, titleBudget)
    val leader = max(0, totalWidth - title - trailing - gaps)
    return LeaderRowWidths(title = title, leader = leader, trailing = trailing)
}

/**
 * A contents-page style row: title, dotted leader, then trailing content such as a page count or
 * an action. The title should be single-line (for example [TruncatedText]) so it can shrink.
 */
@Composable
fun LeaderRow(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leaderColor: Color = lunarTokens().colors.leader,
    trailing: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            Box(contentAlignment = Alignment.CenterStart) { title() }
            DottedLeader(color = leaderColor)
            Box(contentAlignment = Alignment.CenterEnd) { trailing() }
        },
    ) { measurables, constraints ->
        val (titleMeasurable, leaderMeasurable, trailingMeasurable) = measurables
        val gap = LeaderGap.roundToPx()
        val leaderMin = LeaderMinWidth.roundToPx()
        val titleWidth = titleMeasurable.maxIntrinsicWidth(constraints.maxHeight)
        val trailingWidth = trailingMeasurable.maxIntrinsicWidth(constraints.maxHeight)
        val totalWidth = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            titleWidth + trailingWidth + leaderMin + gap * 2
        }
        val widths = allocateLeaderRow(
            totalWidth = totalWidth,
            titleWidth = titleWidth,
            trailingWidth = trailingWidth,
            leaderMinWidth = leaderMin,
            gap = gap,
        )
        val trailingPlaceable = trailingMeasurable.measure(
            Constraints(maxWidth = widths.trailing, maxHeight = constraints.maxHeight),
        )
        val titlePlaceable = titleMeasurable.measure(
            Constraints(maxWidth = widths.title, maxHeight = constraints.maxHeight),
        )
        val height = max(titlePlaceable.height, trailingPlaceable.height)
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        val leaderPlaceable = leaderMeasurable.measure(Constraints.fixed(widths.leader, height))

        layout(totalWidth, height) {
            titlePlaceable.place(0, (height - titlePlaceable.height) / 2)
            leaderPlaceable.place(titlePlaceable.width + gap, 0)
            trailingPlaceable.place(totalWidth - trailingPlaceable.width, (height - trailingPlaceable.height) / 2)
        }
    }
}

@Composable
private fun DottedLeader(color: Color) {
    Canvas(modifier = Modifier) {
        val dot = 1.5.dp.toPx()
        val step = 5.dp.toPx()
        // Sit the dots near the text baseline rather than the vertical centre.
        val y = size.height * 0.68f
        var x = dot
        while (x < size.width - dot) {
            drawCircle(color = color, radius = dot / 2f, center = Offset(x, y))
            x += step
        }
    }
}
