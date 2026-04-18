package com.ryanjames.lunar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryanjames.lunar.notation.layout.NoteEventLayout
import com.ryanjames.lunar.notation.layout.NotationStaffLayoutMetrics
import com.ryanjames.lunar.notation.layout.RestEventLayout
import com.ryanjames.lunar.notation.layout.StemDirection
import com.ryanjames.lunar.notation.layout.layoutStaffForWidth
import com.ryanjames.lunar.notation.model.Clef
import com.ryanjames.lunar.notation.model.KeySignature
import com.ryanjames.lunar.notation.model.NoteValue
import com.ryanjames.lunar.notation.model.NotationDocument
import com.ryanjames.lunar.notation.model.TonalMode
import kotlin.math.max

@Composable
internal fun NotationPreviewPanel(
    document: NotationDocument,
    modifier: Modifier = Modifier,
) {
    val primaryStaff = document.staves.first()
    val totalMeasures = primaryStaff.measures.size
    val totalEvents = primaryStaff.measures.sumOf { it.events.size }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Internal renderer preview",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "This is the editable in-app rendering path: shared layout math plus a Compose canvas, now with Bravura glyphs for the music symbols.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PreviewStat(label = "Staff", value = "${document.staves.size}")
                PreviewStat(label = "Measures", value = "$totalMeasures")
                PreviewStat(label = "Events", value = "$totalEvents")
            }

            NotationPreviewCanvas(
                document = document,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "Previewing ${primaryStaff.clef.name.lowercase().replaceFirstChar { it.uppercase() }} clef, ${document.metadata.defaultTimeSignature.beatsPerMeasure}/${document.metadata.defaultTimeSignature.beatUnit}, ${document.metadata.defaultKeySignature.displayLabel()}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PreviewStat(
    label: String,
    value: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.76f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun NotationPreviewCanvas(
    document: NotationDocument,
    modifier: Modifier = Modifier,
) {
    val palette = lunarThemePalette()
    val inkColor = MaterialTheme.colorScheme.onSurface
    val guideColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    val accentColor = palette.accentGradientEnd.copy(alpha = 0.22f)
    val paperColor = MaterialTheme.colorScheme.surface
    val textMeasurer = rememberTextMeasurer()
    val musicFontFamily = rememberBravuraFontFamily()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .semantics {
                contentDescription = "Notation preview canvas"
            },
    ) {
        val staff = document.staves.firstOrNull() ?: return@Canvas
        val lineSpacing = (size.height / 15f).coerceIn(11f, 18f)
        val metrics = NotationStaffLayoutMetrics(
            lineSpacingPx = lineSpacing,
            leadingInsetPx = lineSpacing * 3.6f,
            trailingInsetPx = lineSpacing * 1.1f,
            measurePaddingPx = lineSpacing * 0.95f,
            minimumMeasureWidthPx = lineSpacing * 6.3f,
            noteHeadWidthPx = lineSpacing * 1.2f,
            noteHeadHeightPx = lineSpacing * 0.9f,
            stemHeightPx = lineSpacing * 3.4f,
            ledgerLineWidthPx = lineSpacing * 1.75f,
        )
        val layout = layoutStaffForWidth(
            staff = staff,
            defaultTimeSignature = document.metadata.defaultTimeSignature,
            availableWidth = size.width - lineSpacing * 1.5f,
            metrics = metrics,
        )
        val staffTop = (size.height - metrics.staffHeightPx) / 2f
        val horizontalOffset = max((size.width - layout.totalWidthPx) / 2f, 0f)
        val staffStartX = horizontalOffset + lineSpacing * 0.5f
        val staffEndX = horizontalOffset + layout.totalWidthPx - metrics.trailingInsetPx * 0.35f
        val strokeWidth = lineSpacing * 0.12f

        repeat(5) { lineIndex ->
            val y = staffTop + lineIndex * metrics.lineSpacingPx
            drawLine(
                color = guideColor,
                start = Offset(staffStartX, y),
                end = Offset(staffEndX, y),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }

        drawNotationClef(
            clef = staff.clef,
            staffStartX = staffStartX,
            staffTop = staffTop,
            metrics = metrics,
            textMeasurer = textMeasurer,
            musicFontFamily = musicFontFamily,
            color = inkColor,
        )

        layout.measureLayouts.forEachIndexed { index, measureLayout ->
            val startX = horizontalOffset + measureLayout.startX
            val endX = horizontalOffset + measureLayout.endX
            val fillTop = staffTop - lineSpacing * 0.9f
            val fillHeight = lineSpacing * 0.55f

            if (index == 0) {
                drawLine(
                    color = guideColor.copy(alpha = 0.95f),
                    start = Offset(startX, staffTop - lineSpacing * 0.2f),
                    end = Offset(startX, staffTop + metrics.staffHeightPx + lineSpacing * 0.2f),
                    strokeWidth = strokeWidth * 1.4f,
                    cap = StrokeCap.Round,
                )
            }

            drawLine(
                color = guideColor.copy(alpha = 0.72f),
                start = Offset(endX, staffTop - lineSpacing * 0.2f),
                end = Offset(endX, staffTop + metrics.staffHeightPx + lineSpacing * 0.2f),
                strokeWidth = strokeWidth * 1.2f,
                cap = StrokeCap.Round,
            )

            val fillWidth = (endX - startX - lineSpacing * 0.36f) * measureLayout.fillFraction.coerceIn(0f, 1f)
            if (fillWidth > 0f) {
                drawRect(
                    color = accentColor,
                    topLeft = Offset(startX + lineSpacing * 0.18f, fillTop),
                    size = Size(
                        width = fillWidth,
                        height = fillHeight,
                    ),
                )
            }

            measureLayout.eventLayouts.forEach { eventLayout ->
                when (eventLayout) {
                    is NoteEventLayout -> drawNotationNote(
                        layout = eventLayout,
                        staffTop = staffTop,
                        metrics = metrics,
                        color = inkColor,
                        paperColor = paperColor,
                        textMeasurer = textMeasurer,
                        musicFontFamily = musicFontFamily,
                    )

                    is RestEventLayout -> drawNotationRest(
                        layout = eventLayout,
                        staffTop = staffTop,
                        metrics = metrics,
                        color = inkColor,
                        textMeasurer = textMeasurer,
                        musicFontFamily = musicFontFamily,
                    )
                }
            }
        }

        val finalBarX = horizontalOffset + layout.measureLayouts.last().endX + lineSpacing * 0.18f
        drawLine(
            color = guideColor,
            start = Offset(finalBarX, staffTop - lineSpacing * 0.2f),
            end = Offset(finalBarX, staffTop + metrics.staffHeightPx + lineSpacing * 0.2f),
            strokeWidth = strokeWidth * 1.6f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawNotationNote(
    layout: NoteEventLayout,
    staffTop: Float,
    metrics: NotationStaffLayoutMetrics,
    color: Color,
    paperColor: Color,
    textMeasurer: TextMeasurer,
    musicFontFamily: FontFamily,
) {
    val centerY = staffYForStep(layout.staffStep, staffTop, metrics.lineSpacingPx)
    val ledgerStroke = metrics.lineSpacingPx * 0.14f

    layout.ledgerLineSteps.forEach { ledgerStep ->
        val ledgerY = staffYForStep(ledgerStep, staffTop, metrics.lineSpacingPx)
        drawLine(
            color = color,
            start = Offset(layout.centerX - metrics.ledgerLineWidthPx / 2f, ledgerY),
            end = Offset(layout.centerX + metrics.ledgerLineWidthPx / 2f, ledgerY),
            strokeWidth = ledgerStroke,
            cap = StrokeCap.Round,
        )
    }

    val geometry = calculateNoteGlyphGeometry(
        layout = layout,
        centerY = centerY,
        metrics = metrics,
    )
    val noteHeadPlacement = measureMusicGlyph(
        glyph = noteHeadGlyphFor(layout.noteValue),
        fontSizePx = noteHeadFontSizePx(layout.noteValue, metrics),
        color = color,
        fontFamily = musicFontFamily,
        textMeasurer = textMeasurer,
    )
    val noteHeadBounds = measuredMusicGlyphBounds(
        measuredGlyph = noteHeadPlacement,
        center = Offset(layout.centerX, centerY),
        yNudgePx = noteHeadVerticalNudgePx(
            noteValue = layout.noteValue,
            staffStep = layout.staffStep,
            metrics = metrics,
        ),
    )

    if (layout.noteValue == NoteValue.WHOLE || layout.noteValue == NoteValue.HALF || isStaffLineStep(layout.staffStep)) {
        drawOval(
            color = paperColor,
            topLeft = Offset(
                x = noteHeadBounds.left - metrics.lineSpacingPx * 0.07f,
                y = noteHeadBounds.top - metrics.lineSpacingPx * 0.05f,
            ),
            size = Size(
                width = noteHeadBounds.width + metrics.lineSpacingPx * 0.14f,
                height = noteHeadBounds.height + metrics.lineSpacingPx * 0.1f,
            ),
        )
    }

    drawMeasuredMusicGlyph(
        measuredGlyph = noteHeadPlacement,
        center = Offset(layout.centerX, centerY),
        yNudgePx = noteHeadVerticalNudgePx(
            noteValue = layout.noteValue,
            staffStep = layout.staffStep,
            metrics = metrics,
        ),
    )

    stemGeometryForRenderedNotehead(
        noteHeadBounds = noteHeadBounds,
        centerY = centerY,
        noteValue = layout.noteValue,
        direction = layout.stemDirection,
        metrics = metrics,
    )?.let { stem ->
        drawLine(
            color = color,
            start = stem.start,
            end = stem.end,
            strokeWidth = geometry.noteStrokeWidth,
            cap = StrokeCap.Round,
        )
        drawStemFlagGlyph(
            noteValue = layout.noteValue,
            stem = stem,
            metrics = metrics,
            color = color,
            textMeasurer = textMeasurer,
            musicFontFamily = musicFontFamily,
        )
    }

    val dotAnchor = dotAnchorForRenderedNotehead(
        noteHeadBounds = noteHeadBounds,
        centerY = centerY,
        metrics = metrics,
    )

    drawDots(
        dots = layout.dots,
        centerX = dotAnchor.x,
        centerY = dotAnchor.y,
        color = color,
        lineSpacingPx = metrics.lineSpacingPx,
    )
}

private fun DrawScope.drawNotationRest(
    layout: RestEventLayout,
    staffTop: Float,
    metrics: NotationStaffLayoutMetrics,
    color: Color,
    textMeasurer: TextMeasurer,
    musicFontFamily: FontFamily,
) {
    val centerY = staffYForStep(layout.centerStaffStep, staffTop, metrics.lineSpacingPx)
    val yNudgePx = when (layout.noteValue) {
        NoteValue.WHOLE -> -metrics.lineSpacingPx * 0.24f
        NoteValue.HALF -> -metrics.lineSpacingPx * 0.04f
        else -> metrics.lineSpacingPx * 0.04f
    }

    drawCenteredMusicGlyph(
        glyph = restGlyphFor(layout.noteValue),
        center = Offset(layout.centerX, centerY),
        fontSizePx = restFontSizePx(layout.noteValue, metrics),
        color = color,
        fontFamily = musicFontFamily,
        textMeasurer = textMeasurer,
        yNudgePx = yNudgePx,
    )

    drawDots(
        dots = layout.dots,
        centerX = layout.centerX + metrics.noteHeadWidthPx * 0.95f,
        centerY = centerY - metrics.lineSpacingPx * 0.15f,
        color = color,
        lineSpacingPx = metrics.lineSpacingPx,
    )
}

private fun DrawScope.drawNotationClef(
    clef: Clef,
    staffStartX: Float,
    staffTop: Float,
    metrics: NotationStaffLayoutMetrics,
    textMeasurer: TextMeasurer,
    musicFontFamily: FontFamily,
    color: Color,
) {
    val centerStep = when (clef) {
        Clef.TREBLE -> 2
        Clef.BASS -> 6
        Clef.ALTO -> 4
        Clef.TENOR -> 6
    }
    val centerY = staffYForStep(centerStep, staffTop, metrics.lineSpacingPx)
    val centerX = staffStartX + metrics.lineSpacingPx * 0.98f
    val fontSizePx = when (clef) {
        Clef.TREBLE -> metrics.lineSpacingPx * 4.9f
        Clef.BASS -> metrics.lineSpacingPx * 3.45f
        else -> metrics.lineSpacingPx * 3.8f
    }
    val yNudgePx = when (clef) {
        Clef.TREBLE -> metrics.lineSpacingPx * 0.34f
        Clef.BASS -> metrics.lineSpacingPx * 0.08f
        else -> metrics.lineSpacingPx * 0.12f
    }

    drawCenteredMusicGlyph(
        glyph = clefGlyphFor(clef),
        center = Offset(centerX, centerY),
        fontSizePx = fontSizePx,
        color = color,
        fontFamily = musicFontFamily,
        textMeasurer = textMeasurer,
        yNudgePx = yNudgePx,
    )
}

private fun DrawScope.drawDots(
    dots: Int,
    centerX: Float,
    centerY: Float,
    color: Color,
    lineSpacingPx: Float,
) {
    repeat(dots) { index ->
        drawCircle(
            color = color,
            radius = lineSpacingPx * 0.12f,
            center = Offset(
                x = centerX + index * lineSpacingPx * 0.42f,
                y = centerY,
            ),
        )
    }
}

private fun DrawScope.drawCenteredMusicGlyph(
    glyph: String,
    center: Offset,
    fontSizePx: Float,
    color: Color,
    fontFamily: FontFamily,
    textMeasurer: TextMeasurer,
    xNudgePx: Float = 0f,
    yNudgePx: Float = 0f,
): Rect {
    val measuredGlyph = measureMusicGlyph(
        glyph = glyph,
        fontSizePx = fontSizePx,
        color = color,
        fontFamily = fontFamily,
        textMeasurer = textMeasurer,
    )
    return drawMeasuredMusicGlyph(
        measuredGlyph = measuredGlyph,
        center = center,
        xNudgePx = xNudgePx,
        yNudgePx = yNudgePx,
    )
}

private fun DrawScope.drawMeasuredMusicGlyph(
    measuredGlyph: MeasuredMusicGlyph,
    center: Offset,
    xNudgePx: Float = 0f,
    yNudgePx: Float = 0f,
): Rect {
    val topLeft = measuredMusicGlyphTopLeft(
        measuredGlyph = measuredGlyph,
        center = center,
        xNudgePx = xNudgePx,
        yNudgePx = yNudgePx,
    )
    drawText(
        textLayoutResult = measuredGlyph.layoutResult,
        topLeft = topLeft,
    )
    return Rect(
        left = topLeft.x + measuredGlyph.bounds.left,
        top = topLeft.y + measuredGlyph.bounds.top,
        right = topLeft.x + measuredGlyph.bounds.right,
        bottom = topLeft.y + measuredGlyph.bounds.bottom,
    )
}

private fun measuredMusicGlyphBounds(
    measuredGlyph: MeasuredMusicGlyph,
    center: Offset,
    xNudgePx: Float = 0f,
    yNudgePx: Float = 0f,
): Rect {
    val topLeft = measuredMusicGlyphTopLeft(
        measuredGlyph = measuredGlyph,
        center = center,
        xNudgePx = xNudgePx,
        yNudgePx = yNudgePx,
    )
    return Rect(
        left = topLeft.x + measuredGlyph.bounds.left,
        top = topLeft.y + measuredGlyph.bounds.top,
        right = topLeft.x + measuredGlyph.bounds.right,
        bottom = topLeft.y + measuredGlyph.bounds.bottom,
    )
}

private fun measuredMusicGlyphTopLeft(
    measuredGlyph: MeasuredMusicGlyph,
    center: Offset,
    xNudgePx: Float = 0f,
    yNudgePx: Float = 0f,
): Offset {
    val topLeft = Offset(
        x = center.x - measuredGlyph.bounds.center.x + xNudgePx,
        y = center.y - measuredGlyph.bounds.center.y + yNudgePx,
    )
    return topLeft
}

private fun DrawScope.drawStemFlagGlyph(
    noteValue: NoteValue,
    stem: StemGeometry,
    metrics: NotationStaffLayoutMetrics,
    color: Color,
    textMeasurer: TextMeasurer,
    musicFontFamily: FontFamily,
) {
    val glyph = flagGlyphFor(noteValue, stem.direction) ?: return
    val measuredGlyph = measureMusicGlyph(
        glyph = glyph,
        fontSizePx = metrics.lineSpacingPx * 2.7f,
        color = color,
        fontFamily = musicFontFamily,
        textMeasurer = textMeasurer,
    )
    val topLeft = if (stem.direction == StemDirection.UP) {
        Offset(
            x = stem.end.x - measuredGlyph.bounds.left - metrics.lineSpacingPx * 0.04f,
            y = stem.end.y - measuredGlyph.bounds.top - metrics.lineSpacingPx * 0.06f,
        )
    } else {
        Offset(
            x = stem.end.x - measuredGlyph.bounds.left - metrics.lineSpacingPx * 0.96f,
            y = stem.end.y - measuredGlyph.bounds.top - metrics.lineSpacingPx * 1.32f,
        )
    }
    drawText(
        textLayoutResult = measuredGlyph.layoutResult,
        topLeft = topLeft,
    )
}

private fun DrawScope.measureMusicGlyph(
    glyph: String,
    fontSizePx: Float,
    color: Color,
    fontFamily: FontFamily,
    textMeasurer: TextMeasurer,
): MeasuredMusicGlyph {
    val textLayoutResult = textMeasurer.measure(
        text = glyph,
        style = TextStyle(
            color = color,
            fontFamily = fontFamily,
            fontSize = (fontSizePx / density / fontScale).sp,
            lineHeight = (fontSizePx / density / fontScale).sp,
        ),
        maxLines = 1,
        softWrap = false,
    )
    return MeasuredMusicGlyph(
        layoutResult = textLayoutResult,
        bounds = textLayoutResult.getBoundingBox(0),
    )
}

private fun staffYForStep(
    staffStep: Int,
    staffTop: Float,
    lineSpacingPx: Float,
): Float = staffTop + (4f - staffStep / 2f) * lineSpacingPx

private fun noteHeadFontSizePx(
    noteValue: NoteValue,
    metrics: NotationStaffLayoutMetrics,
): Float = when (noteValue) {
    NoteValue.WHOLE -> metrics.lineSpacingPx * 3.5f
    NoteValue.HALF -> metrics.lineSpacingPx * 3.4f
    else -> metrics.lineSpacingPx * 3.28f
}

private fun noteHeadVerticalNudgePx(
    noteValue: NoteValue,
    staffStep: Int,
    metrics: NotationStaffLayoutMetrics,
): Float = when {
    noteValue == NoteValue.WHOLE -> metrics.lineSpacingPx * 0.06f
    isStaffLineStep(staffStep) -> metrics.lineSpacingPx * 0.015f
    else -> 0f
}

private fun restFontSizePx(
    noteValue: NoteValue,
    metrics: NotationStaffLayoutMetrics,
): Float = when (noteValue) {
    NoteValue.WHOLE, NoteValue.HALF -> metrics.lineSpacingPx * 2.18f
    NoteValue.QUARTER -> metrics.lineSpacingPx * 2.72f
    NoteValue.EIGHTH -> metrics.lineSpacingPx * 2.56f
    NoteValue.SIXTEENTH -> metrics.lineSpacingPx * 2.62f
    NoteValue.THIRTY_SECOND -> metrics.lineSpacingPx * 2.72f
    NoteValue.SIXTY_FOURTH -> metrics.lineSpacingPx * 2.82f
}

private data class MeasuredMusicGlyph(
    val layoutResult: TextLayoutResult,
    val bounds: Rect,
)

private fun KeySignature.displayLabel(): String = when {
    fifths == 0 && mode == TonalMode.MAJOR -> "C major"
    fifths == 0 && mode == TonalMode.MINOR -> "A minor"
    else -> "${if (fifths >= 0) "+" else ""}$fifths fifths ${mode.name.lowercase()}"
}
