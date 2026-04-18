package com.ryanjames.lunar.notation.layout

import com.ryanjames.lunar.notation.model.Clef
import com.ryanjames.lunar.notation.model.Note
import com.ryanjames.lunar.notation.model.NoteDuration
import com.ryanjames.lunar.notation.model.NoteValue
import com.ryanjames.lunar.notation.model.NotationMeasure
import com.ryanjames.lunar.notation.model.NotationStaff
import com.ryanjames.lunar.notation.model.Pitch
import com.ryanjames.lunar.notation.model.PitchStep
import com.ryanjames.lunar.notation.model.Rest
import com.ryanjames.lunar.notation.model.TimeSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NotationLayoutTest {

    @Test
    fun pitchMappingUsesCorrectBottomLineForEachClef() {
        assertEquals(0, staffStepIndexForPitch(Pitch(PitchStep.E, 4), Clef.TREBLE))
        assertEquals(8, staffStepIndexForPitch(Pitch(PitchStep.F, 5), Clef.TREBLE))
        assertEquals(10, staffStepIndexForPitch(Pitch(PitchStep.A, 5), Clef.TREBLE))
        assertEquals(-2, staffStepIndexForPitch(Pitch(PitchStep.C, 4), Clef.TREBLE))

        assertEquals(0, staffStepIndexForPitch(Pitch(PitchStep.G, 2), Clef.BASS))
        assertEquals(8, staffStepIndexForPitch(Pitch(PitchStep.A, 3), Clef.BASS))
        assertEquals(-2, staffStepIndexForPitch(Pitch(PitchStep.E, 2), Clef.BASS))
    }

    @Test
    fun ledgerLineMappingOnlyAddsNeededLedgerLines() {
        assertEquals(emptyList(), ledgerLineStepsForNote(-1))
        assertEquals(listOf(-2), ledgerLineStepsForNote(-3))
        assertEquals(listOf(10), ledgerLineStepsForNote(11))
        assertEquals(listOf(10, 12), ledgerLineStepsForNote(12))
    }

    @Test
    fun staffLayoutSpreadsEventsAcrossMeasuresAndCapturesNoteGeometry() {
        val staff = NotationStaff(
            id = "staff-1",
            clef = Clef.TREBLE,
            measures = listOf(
                NotationMeasure(
                    number = 1,
                    events = listOf(
                        Note(
                            pitch = Pitch(PitchStep.C, 4),
                            duration = NoteDuration(NoteValue.HALF),
                        ),
                        Rest(
                            duration = NoteDuration(NoteValue.QUARTER),
                        ),
                        Note(
                            pitch = Pitch(PitchStep.A, 5),
                            duration = NoteDuration(NoteValue.QUARTER),
                        ),
                    ),
                )
            ),
        )

        val layout = layoutStaffForWidth(
            staff = staff,
            defaultTimeSignature = TimeSignature.commonTime(),
            availableWidth = 320f,
        )

        assertEquals(1, layout.measureLayouts.size)
        val measureLayout = layout.measureLayouts.single()
        assertEquals(4.0, measureLayout.occupiedBeats)
        assertEquals(4.0, measureLayout.capacityBeats)
        assertTrue(measureLayout.fillFraction > 0.99f)
        assertEquals(3, measureLayout.eventLayouts.size)
        assertTrue(measureLayout.eventLayouts[0].centerX < measureLayout.eventLayouts[1].centerX)
        assertTrue(measureLayout.eventLayouts[1].centerX < measureLayout.eventLayouts[2].centerX)

        val firstNote = assertIs<NoteEventLayout>(measureLayout.eventLayouts.first())
        assertEquals(-2, firstNote.staffStep)
        assertEquals(listOf(-2), firstNote.ledgerLineSteps)
        assertEquals(StemDirection.UP, firstNote.stemDirection)

        val finalNote = assertIs<NoteEventLayout>(measureLayout.eventLayouts.last())
        assertEquals(10, finalNote.staffStep)
        assertEquals(listOf(10), finalNote.ledgerLineSteps)
        assertEquals(StemDirection.DOWN, finalNote.stemDirection)
    }
}
