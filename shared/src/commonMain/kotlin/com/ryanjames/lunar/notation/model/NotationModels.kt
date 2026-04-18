package com.ryanjames.lunar.notation.model

import kotlinx.serialization.Serializable

const val DEFAULT_NOTATION_DOCUMENT_TITLE = "Untitled score"
const val DEFAULT_NOTATION_STAFF_ID = "staff-1"

@Serializable
data class NotationDocument(
    val id: String,
    val title: String = DEFAULT_NOTATION_DOCUMENT_TITLE,
    val composer: String? = null,
    val metadata: NotationMetadata = NotationMetadata(),
    val staves: List<NotationStaff> = listOf(NotationStaff(id = DEFAULT_NOTATION_STAFF_ID)),
) {
    init {
        require(staves.isNotEmpty()) { "A notation document must contain at least one staff." }
    }

    companion object {
        fun empty(
            id: String,
            title: String = DEFAULT_NOTATION_DOCUMENT_TITLE,
            composer: String? = null,
            clef: Clef = Clef.TREBLE,
        ): NotationDocument = NotationDocument(
            id = id,
            title = title,
            composer = composer,
            staves = listOf(
                NotationStaff(
                    id = DEFAULT_NOTATION_STAFF_ID,
                    clef = clef,
                    measures = listOf(NotationMeasure(number = 1)),
                )
            ),
        )
    }
}

@Serializable
data class NotationMetadata(
    val subtitle: String? = null,
    val copyright: String? = null,
    val tempoText: String? = null,
    val defaultTimeSignature: TimeSignature = TimeSignature.commonTime(),
    val defaultKeySignature: KeySignature = KeySignature(),
)

@Serializable
data class NotationStaff(
    val id: String,
    val name: String? = null,
    val shortName: String? = null,
    val clef: Clef = Clef.TREBLE,
    val measures: List<NotationMeasure> = listOf(NotationMeasure(number = 1)),
) {
    init {
        require(id.isNotBlank()) { "A staff id is required." }
        require(measures.isNotEmpty()) { "A staff must contain at least one measure." }
    }
}

@Serializable
data class NotationMeasure(
    val number: Int,
    val timeSignature: TimeSignature? = null,
    val keySignature: KeySignature? = null,
    val events: List<NotationEvent> = emptyList(),
) {
    init {
        require(number >= 1) { "Measure numbers must start at 1." }
    }

    fun effectiveTimeSignature(defaultTimeSignature: TimeSignature): TimeSignature =
        timeSignature ?: defaultTimeSignature

    fun occupiedBeats(defaultTimeSignature: TimeSignature): Double {
        val activeTimeSignature = effectiveTimeSignature(defaultTimeSignature)
        return events.sumOf { event -> event.beatsIn(activeTimeSignature) }
    }
}

@Serializable
sealed interface NotationEvent {
    val voice: Int

    fun beatsIn(timeSignature: TimeSignature): Double
}

@Serializable
data class Note(
    val pitch: Pitch,
    val duration: NoteDuration = NoteDuration(),
    override val voice: Int = 1,
    val tie: TieMark = TieMark.NONE,
    val accidentalDisplay: AccidentalDisplay = AccidentalDisplay.AUTOMATIC,
) : NotationEvent {
    init {
        require(voice >= 1) { "Voices are 1-based." }
    }

    override fun beatsIn(timeSignature: TimeSignature): Double = duration.beatsIn(timeSignature)
}

@Serializable
data class Rest(
    val duration: NoteDuration = NoteDuration(),
    override val voice: Int = 1,
) : NotationEvent {
    init {
        require(voice >= 1) { "Voices are 1-based." }
    }

    override fun beatsIn(timeSignature: TimeSignature): Double = duration.beatsIn(timeSignature)
}

@Serializable
data class Pitch(
    val step: PitchStep,
    val octave: Int,
    val accidental: Accidental? = null,
) {
    init {
        require(octave in 0..8) { "Octave must stay in the supported engraving range of 0..8." }
    }
}

@Serializable
data class NoteDuration(
    val value: NoteValue = NoteValue.QUARTER,
    val dots: Int = 0,
) {
    init {
        require(dots >= 0) { "Dots cannot be negative." }
    }

    fun beatsIn(timeSignature: TimeSignature): Double {
        val dotMultiplier = (0..dots).sumOf { dotIndex -> 1.0 / (1 shl dotIndex) }
        return (timeSignature.beatUnit.toDouble() / value.denominator.toDouble()) * dotMultiplier
    }
}

@Serializable
data class TimeSignature(
    val beatsPerMeasure: Int,
    val beatUnit: Int,
) {
    init {
        require(beatsPerMeasure >= 1) { "A time signature must contain at least one beat." }
        require(beatUnit in SUPPORTED_BEAT_UNITS) { "Beat unit must be one of $SUPPORTED_BEAT_UNITS." }
    }

    companion object {
        fun commonTime(): TimeSignature = TimeSignature(beatsPerMeasure = 4, beatUnit = 4)
    }
}

@Serializable
data class KeySignature(
    val fifths: Int = 0,
    val mode: TonalMode = TonalMode.MAJOR,
) {
    init {
        require(fifths in -7..7) { "Key signatures currently support -7 to +7 fifths." }
    }
}

@Serializable
enum class Clef {
    TREBLE,
    BASS,
    ALTO,
    TENOR,
}

@Serializable
enum class PitchStep {
    C,
    D,
    E,
    F,
    G,
    A,
    B,
}

@Serializable
enum class Accidental {
    DOUBLE_FLAT,
    FLAT,
    NATURAL,
    SHARP,
    DOUBLE_SHARP,
}

@Serializable
enum class AccidentalDisplay {
    AUTOMATIC,
    FORCE_SHOW,
    HIDE,
}

@Serializable
enum class NoteValue(val denominator: Int) {
    WHOLE(1),
    HALF(2),
    QUARTER(4),
    EIGHTH(8),
    SIXTEENTH(16),
    THIRTY_SECOND(32),
    SIXTY_FOURTH(64),
}

@Serializable
enum class TieMark {
    NONE,
    START,
    STOP,
    CONTINUE,
}

@Serializable
enum class TonalMode {
    MAJOR,
    MINOR,
}

private val SUPPORTED_BEAT_UNITS = setOf(1, 2, 4, 8, 16, 32, 64)
