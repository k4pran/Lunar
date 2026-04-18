package com.ryanjames.lunar

import androidx.compose.ui.geometry.Rect
import com.ryanjames.lunar.notation.layout.NoteEventLayout
import com.ryanjames.lunar.notation.layout.NotationStaffLayoutMetrics
import com.ryanjames.lunar.notation.layout.StemDirection
import com.ryanjames.lunar.notation.model.NoteValue
import com.ryanjames.lunar.ui.calculateNoteGlyphGeometry
import com.ryanjames.lunar.ui.dotAnchorForRenderedNotehead
import com.ryanjames.lunar.ui.isStaffLineStep
import com.ryanjames.lunar.ui.noteHeadTargetRect
import com.ryanjames.lunar.ui.noteHeadTargetSize
import com.ryanjames.lunar.ui.stemGeometryForRenderedNotehead
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotationGlyphGeometryTest {

    @Test
    fun noteGlyphGeometryAnchorsUpStemToNoteheadEdge() {
        val layout = NoteEventLayout(
            eventIndex = 0,
            centerX = 100f,
            voice = 1,
            staffStep = 2,
            ledgerLineSteps = emptyList(),
            noteValue = NoteValue.QUARTER,
            dots = 0,
            stemDirection = StemDirection.UP,
        )
        val metrics = NotationStaffLayoutMetrics(lineSpacingPx = 14f)

        val geometry = calculateNoteGlyphGeometry(
            layout = layout,
            centerY = 120f,
            metrics = metrics,
        )

        val stem = assertNotNull(geometry.stem)
        assertTrue(stem.start.x > layout.centerX)
        assertTrue(stem.start.y < 120f)
        assertTrue(stem.end.y < stem.start.y)
        assertEquals(0, geometry.flagCount)
    }

    @Test
    fun noteGlyphGeometryAddsFlagsForShorterValuesAndOmitsWholeNoteStem() {
        val eighthGeometry = calculateNoteGlyphGeometry(
            layout = testNoteLayout(noteValue = NoteValue.EIGHTH, stemDirection = StemDirection.DOWN),
            centerY = 80f,
            metrics = NotationStaffLayoutMetrics(lineSpacingPx = 12f),
        )
        val wholeGeometry = calculateNoteGlyphGeometry(
            layout = testNoteLayout(noteValue = NoteValue.WHOLE, stemDirection = StemDirection.UP),
            centerY = 80f,
            metrics = NotationStaffLayoutMetrics(lineSpacingPx = 12f),
        )

        assertEquals(1, eighthGeometry.flagCount)
        assertNotNull(eighthGeometry.stem)
        assertNull(wholeGeometry.stem)
        assertEquals(0, wholeGeometry.flagCount)
    }

    @Test
    fun wholeNoteUsesBroaderMoreHorizontalHeadThanQuarterNote() {
        val metrics = NotationStaffLayoutMetrics(lineSpacingPx = 12f)

        val wholeGeometry = calculateNoteGlyphGeometry(
            layout = testNoteLayout(noteValue = NoteValue.WHOLE, stemDirection = StemDirection.UP),
            centerY = 64f,
            metrics = metrics,
        )
        val quarterGeometry = calculateNoteGlyphGeometry(
            layout = testNoteLayout(noteValue = NoteValue.QUARTER, stemDirection = StemDirection.UP),
            centerY = 64f,
            metrics = metrics,
        )

        assertTrue(wholeGeometry.noteHeadSize.width > quarterGeometry.noteHeadSize.width)
        assertTrue(wholeGeometry.noteHeadSize.height < quarterGeometry.noteHeadSize.height)
        assertTrue(wholeGeometry.noteHeadRotationDegrees > quarterGeometry.noteHeadRotationDegrees)
    }

    @Test
    fun renderedNoteheadStemGeometryAttachesInsideGlyphBounds() {
        val noteHeadBounds = Rect(left = 90f, top = 70f, right = 112f, bottom = 86f)

        val stem = assertNotNull(
            stemGeometryForRenderedNotehead(
                noteHeadBounds = noteHeadBounds,
                centerY = 78f,
                noteValue = NoteValue.QUARTER,
                direction = StemDirection.UP,
                metrics = NotationStaffLayoutMetrics(lineSpacingPx = 12f),
            )
        )

        assertTrue(stem.start.x < noteHeadBounds.right)
        assertTrue(stem.start.x > noteHeadBounds.center.x)
        assertTrue(stem.start.y < 78f)
        assertTrue(stem.end.y < stem.start.y)
    }

    @Test
    fun renderedNoteheadDotAnchorSitsJustToTheRightOfGlyph() {
        val noteHeadBounds = Rect(left = 90f, top = 70f, right = 112f, bottom = 86f)

        val dotAnchor = dotAnchorForRenderedNotehead(
            noteHeadBounds = noteHeadBounds,
            centerY = 78f,
            metrics = NotationStaffLayoutMetrics(lineSpacingPx = 12f),
        )

        assertTrue(dotAnchor.x > noteHeadBounds.right)
        assertTrue(dotAnchor.y < 78f)
    }

    @Test
    fun quarterNoteTargetSizeMatchesStaffSpaceHeight() {
        val metrics = NotationStaffLayoutMetrics(lineSpacingPx = 12f)

        val targetSize = noteHeadTargetSize(
            noteValue = NoteValue.QUARTER,
            metrics = metrics,
        )

        assertTrue(targetSize.height > metrics.noteHeadHeightPx)
        assertEquals(12.12f, targetSize.height, absoluteTolerance = 0.001f)
    }

    @Test
    fun noteHeadTargetRectStaysCenteredOnStaffPosition() {
        val targetRect = noteHeadTargetRect(
            centerX = 100f,
            centerY = 72f,
            noteValue = NoteValue.QUARTER,
            metrics = NotationStaffLayoutMetrics(lineSpacingPx = 12f),
        )

        assertEquals(100f, targetRect.center.x, absoluteTolerance = 0.001f)
        assertEquals(72f, targetRect.center.y, absoluteTolerance = 0.001f)
    }

    @Test
    fun detectsWhetherStepFallsOnStaffLine() {
        assertTrue(isStaffLineStep(0))
        assertTrue(isStaffLineStep(4))
        assertTrue(!isStaffLineStep(1))
    }
}

private fun testNoteLayout(
    noteValue: NoteValue,
    stemDirection: StemDirection,
): NoteEventLayout = NoteEventLayout(
    eventIndex = 0,
    centerX = 100f,
    voice = 1,
    staffStep = 4,
    ledgerLineSteps = emptyList(),
    noteValue = noteValue,
    dots = 0,
    stemDirection = stemDirection,
)
