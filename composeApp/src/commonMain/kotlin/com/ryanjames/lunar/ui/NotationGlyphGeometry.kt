package com.ryanjames.lunar.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.ryanjames.lunar.notation.layout.NoteEventLayout
import com.ryanjames.lunar.notation.layout.NotationStaffLayoutMetrics
import com.ryanjames.lunar.notation.layout.StemDirection
import com.ryanjames.lunar.notation.model.NoteValue

internal data class NoteGlyphGeometry(
    val noteHeadTopLeft: Offset,
    val noteHeadSize: Size,
    val noteHeadRotationDegrees: Float,
    val noteStrokeWidth: Float,
    val stem: StemGeometry?,
    val flagCount: Int,
    val dotAnchor: Offset,
)

internal data class StemGeometry(
    val start: Offset,
    val end: Offset,
    val direction: StemDirection,
)

internal fun calculateNoteGlyphGeometry(
    layout: NoteEventLayout,
    centerY: Float,
    metrics: NotationStaffLayoutMetrics,
): NoteGlyphGeometry {
    val noteHeadSize = noteHeadSizeFor(layout.noteValue, metrics)
    val noteHeadTopLeft = Offset(
        x = layout.centerX - noteHeadSize.width / 2f,
        y = centerY - noteHeadSize.height / 2f,
    )
    val stem = stemGeometryFor(
        centerX = layout.centerX,
        centerY = centerY,
        noteValue = layout.noteValue,
        direction = layout.stemDirection,
        noteHeadSize = noteHeadSize,
        metrics = metrics,
    )

    return NoteGlyphGeometry(
        noteHeadTopLeft = noteHeadTopLeft,
        noteHeadSize = noteHeadSize,
        noteHeadRotationDegrees = noteHeadRotationFor(layout.noteValue),
        noteStrokeWidth = metrics.lineSpacingPx * 0.14f,
        stem = stem,
        flagCount = flagCountFor(layout.noteValue),
        dotAnchor = Offset(
            x = layout.centerX + noteHeadSize.width * 0.96f,
            y = centerY - metrics.lineSpacingPx * 0.1f,
        ),
    )
}

internal fun flagCountFor(noteValue: NoteValue): Int = when (noteValue) {
    NoteValue.EIGHTH -> 1
    NoteValue.SIXTEENTH -> 2
    NoteValue.THIRTY_SECOND -> 3
    NoteValue.SIXTY_FOURTH -> 4
    else -> 0
}

internal fun noteHeadTargetSize(
    noteValue: NoteValue,
    metrics: NotationStaffLayoutMetrics,
): Size = when (noteValue) {
    NoteValue.WHOLE -> Size(
        width = metrics.lineSpacingPx * 1.54f,
        height = metrics.lineSpacingPx * 0.94f,
    )

    NoteValue.HALF -> Size(
        width = metrics.lineSpacingPx * 1.42f,
        height = metrics.lineSpacingPx * 1.01f,
    )

    else -> Size(
        width = metrics.lineSpacingPx * 1.36f,
        height = metrics.lineSpacingPx * 1.01f,
    )
}

internal fun noteHeadTargetRect(
    centerX: Float,
    centerY: Float,
    noteValue: NoteValue,
    metrics: NotationStaffLayoutMetrics,
): Rect {
    val size = noteHeadTargetSize(noteValue, metrics)
    return Rect(
        left = centerX - size.width / 2f,
        top = centerY - size.height / 2f,
        right = centerX + size.width / 2f,
        bottom = centerY + size.height / 2f,
    )
}

internal fun isStaffLineStep(staffStep: Int): Boolean = staffStep % 2 == 0

internal fun stemGeometryForRenderedNotehead(
    noteHeadBounds: Rect,
    centerY: Float,
    noteValue: NoteValue,
    direction: StemDirection,
    metrics: NotationStaffLayoutMetrics,
): StemGeometry? {
    if (noteValue == NoteValue.WHOLE) {
        return null
    }

    val stemLength = metrics.stemHeightPx * when (noteValue) {
        NoteValue.EIGHTH -> 1.03f
        NoteValue.SIXTEENTH -> 1.08f
        NoteValue.THIRTY_SECOND -> 1.13f
        NoteValue.SIXTY_FOURTH -> 1.18f
        else -> 1f
    }
    val horizontalInset = noteHeadBounds.width * 0.08f
    val verticalInset = noteHeadBounds.height * when (noteValue) {
        NoteValue.HALF -> 0.05f
        else -> 0.08f
    }

    return if (direction == StemDirection.UP) {
        val start = Offset(
            x = noteHeadBounds.right - horizontalInset,
            y = centerY - verticalInset,
        )
        StemGeometry(
            start = start,
            end = Offset(start.x, start.y - stemLength),
            direction = direction,
        )
    } else {
        val start = Offset(
            x = noteHeadBounds.left + horizontalInset,
            y = centerY + verticalInset,
        )
        StemGeometry(
            start = start,
            end = Offset(start.x, start.y + stemLength),
            direction = direction,
        )
    }
}

internal fun dotAnchorForRenderedNotehead(
    noteHeadBounds: Rect,
    centerY: Float,
    metrics: NotationStaffLayoutMetrics,
): Offset = Offset(
    x = noteHeadBounds.right + metrics.lineSpacingPx * 0.34f,
    y = centerY - metrics.lineSpacingPx * 0.08f,
)

private fun noteHeadSizeFor(
    noteValue: NoteValue,
    metrics: NotationStaffLayoutMetrics,
): Size = when (noteValue) {
    NoteValue.WHOLE -> Size(
        width = metrics.noteHeadWidthPx * 1.32f,
        height = metrics.noteHeadHeightPx * 0.82f,
    )

    else -> Size(
        width = metrics.noteHeadWidthPx,
        height = metrics.noteHeadHeightPx,
    )
}

private fun noteHeadRotationFor(noteValue: NoteValue): Float = when (noteValue) {
    NoteValue.WHOLE -> -12f
    else -> -24f
}

private fun stemGeometryFor(
    centerX: Float,
    centerY: Float,
    noteValue: NoteValue,
    direction: StemDirection,
    noteHeadSize: Size,
    metrics: NotationStaffLayoutMetrics,
): StemGeometry? {
    if (noteValue == NoteValue.WHOLE) {
        return null
    }

    val stemLength = metrics.stemHeightPx * when (noteValue) {
        NoteValue.EIGHTH -> 1.03f
        NoteValue.SIXTEENTH -> 1.08f
        NoteValue.THIRTY_SECOND -> 1.13f
        NoteValue.SIXTY_FOURTH -> 1.18f
        else -> 1f
    }

    return if (direction == StemDirection.UP) {
        val start = Offset(
            x = centerX + noteHeadSize.width * 0.42f,
            y = centerY - noteHeadSize.height * 0.12f,
        )
        StemGeometry(
            start = start,
            end = Offset(start.x, start.y - stemLength),
            direction = direction,
        )
    } else {
        val start = Offset(
            x = centerX - noteHeadSize.width * 0.42f,
            y = centerY + noteHeadSize.height * 0.12f,
        )
        StemGeometry(
            start = start,
            end = Offset(start.x, start.y + stemLength),
            direction = direction,
        )
    }
}
