package com.ryanjames.lunar.notation.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class NotationModelsTest {

    private val json = Json {
        encodeDefaults = true
    }

    @Test
    fun emptyDocumentStartsWithSingleTrebleStaffAndOpeningMeasure() {
        val document = NotationDocument.empty(
            id = "score-1",
            title = "Sketch",
            composer = "Lunar",
        )

        assertEquals("Sketch", document.title)
        assertEquals("Lunar", document.composer)
        assertEquals(1, document.staves.size)
        assertEquals(Clef.TREBLE, document.staves.single().clef)
        assertEquals(listOf(1), document.staves.single().measures.map { it.number })
        assertEquals(TimeSignature.commonTime(), document.metadata.defaultTimeSignature)
    }

    @Test
    fun measureOccupancyUsesEffectiveTimeSignatureAndDottedDurations() {
        val measure = NotationMeasure(
            number = 3,
            events = listOf(
                Note(
                    pitch = Pitch(PitchStep.C, octave = 4),
                    duration = NoteDuration(value = NoteValue.QUARTER, dots = 1),
                ),
                Rest(
                    duration = NoteDuration(value = NoteValue.EIGHTH),
                ),
            ),
        )

        val occupiedBeats = measure.occupiedBeats(TimeSignature(beatsPerMeasure = 6, beatUnit = 8))

        assertEquals(4.0, occupiedBeats)
    }

    @Test
    fun notationDocumentRoundTripsThroughSerialization() {
        val document = NotationDocument(
            id = "score-2",
            title = "Nocturne",
            composer = "Example",
            metadata = NotationMetadata(
                subtitle = "Draft",
                tempoText = "Andante",
                defaultTimeSignature = TimeSignature(beatsPerMeasure = 3, beatUnit = 4),
                defaultKeySignature = KeySignature(fifths = -3, mode = TonalMode.MINOR),
            ),
            staves = listOf(
                NotationStaff(
                    id = "piano-rh",
                    name = "Piano",
                    shortName = "Pno.",
                    measures = listOf(
                        NotationMeasure(
                            number = 1,
                            events = listOf(
                                Note(
                                    pitch = Pitch(PitchStep.E, octave = 5, accidental = Accidental.FLAT),
                                    duration = NoteDuration(value = NoteValue.HALF, dots = 1),
                                    tie = TieMark.START,
                                ),
                                Rest(duration = NoteDuration(value = NoteValue.QUARTER)),
                            ),
                        )
                    ),
                )
            ),
        )

        val encoded = json.encodeToString(NotationDocument.serializer(), document)
        val decoded = json.decodeFromString(NotationDocument.serializer(), encoded)

        assertEquals(document, decoded)
    }
}
