package com.ryanjames.lunar.notation.layout

import com.ryanjames.lunar.notation.model.Clef
import com.ryanjames.lunar.notation.model.Note
import com.ryanjames.lunar.notation.model.NoteValue
import com.ryanjames.lunar.notation.model.NotationMeasure
import com.ryanjames.lunar.notation.model.NotationStaff
import com.ryanjames.lunar.notation.model.Pitch
import com.ryanjames.lunar.notation.model.PitchStep
import com.ryanjames.lunar.notation.model.Rest
import com.ryanjames.lunar.notation.model.TimeSignature

data class NotationStaffLayoutMetrics(
    val lineSpacingPx: Float = 14f,
    val leadingInsetPx: Float = lineSpacingPx * 3.6f,
    val trailingInsetPx: Float = lineSpacingPx * 1.2f,
    val measurePaddingPx: Float = lineSpacingPx * 1.1f,
    val minimumMeasureWidthPx: Float = lineSpacingPx * 6.5f,
    val noteHeadWidthPx: Float = lineSpacingPx * 1.25f,
    val noteHeadHeightPx: Float = lineSpacingPx * 0.92f,
    val stemHeightPx: Float = lineSpacingPx * 3.4f,
    val ledgerLineWidthPx: Float = lineSpacingPx * 1.7f,
) {
    val staffHeightPx: Float
        get() = lineSpacingPx * 4f
}

data class NotationStaffLayout(
    val totalWidthPx: Float,
    val measureLayouts: List<NotationMeasureLayout>,
)

data class NotationMeasureLayout(
    val measureNumber: Int,
    val startX: Float,
    val endX: Float,
    val occupiedBeats: Double,
    val capacityBeats: Double,
    val eventLayouts: List<NotationEventLayout>,
) {
    val fillFraction: Float
        get() = if (capacityBeats <= 0.0) 0f else (occupiedBeats / capacityBeats).coerceIn(0.0, 1.0).toFloat()
}

sealed interface NotationEventLayout {
    val eventIndex: Int
    val centerX: Float
    val voice: Int
}

data class NoteEventLayout(
    override val eventIndex: Int,
    override val centerX: Float,
    override val voice: Int,
    val staffStep: Int,
    val ledgerLineSteps: List<Int>,
    val noteValue: NoteValue,
    val dots: Int,
    val stemDirection: StemDirection,
) : NotationEventLayout

data class RestEventLayout(
    override val eventIndex: Int,
    override val centerX: Float,
    override val voice: Int,
    val noteValue: NoteValue,
    val dots: Int,
    val centerStaffStep: Int = 4,
) : NotationEventLayout

enum class StemDirection {
    UP,
    DOWN,
}

fun layoutStaffForWidth(
    staff: NotationStaff,
    defaultTimeSignature: TimeSignature,
    availableWidth: Float,
    metrics: NotationStaffLayoutMetrics = NotationStaffLayoutMetrics(),
): NotationStaffLayout {
    require(availableWidth > 0f) { "Available width must be positive." }

    val measureCount = staff.measures.size.coerceAtLeast(1)
    val measureWidth = ((availableWidth - metrics.leadingInsetPx - metrics.trailingInsetPx) / measureCount.toFloat())
        .coerceAtLeast(metrics.minimumMeasureWidthPx)
    val totalWidth = metrics.leadingInsetPx + metrics.trailingInsetPx + measureWidth * measureCount

    val measureLayouts = staff.measures.mapIndexed { index, measure ->
        val startX = metrics.leadingInsetPx + measureWidth * index
        val endX = startX + measureWidth
        layoutMeasure(
            measure = measure,
            clef = staff.clef,
            defaultTimeSignature = defaultTimeSignature,
            startX = startX,
            endX = endX,
            metrics = metrics,
        )
    }

    return NotationStaffLayout(
        totalWidthPx = totalWidth,
        measureLayouts = measureLayouts,
    )
}

fun staffStepIndexForPitch(pitch: Pitch, clef: Clef): Int =
    pitch.toDiatonicIndex() - clef.bottomLinePitch().toDiatonicIndex()

fun ledgerLineStepsForNote(staffStep: Int): List<Int> = when {
    staffStep < 0 -> {
        val lowestLedgerStep = if (staffStep % 2 == 0) staffStep else staffStep + 1
        generateSequence(-2) { it - 2 }
            .takeWhile { it >= lowestLedgerStep }
            .toList()
    }

    staffStep > 8 -> {
        val highestLedgerStep = if (staffStep % 2 == 0) staffStep else staffStep - 1
        generateSequence(10) { it + 2 }
            .takeWhile { it <= highestLedgerStep }
            .toList()
    }

    else -> emptyList()
}

fun stemDirectionForStaffStep(staffStep: Int): StemDirection =
    if (staffStep >= 4) StemDirection.DOWN else StemDirection.UP

private fun layoutMeasure(
    measure: NotationMeasure,
    clef: Clef,
    defaultTimeSignature: TimeSignature,
    startX: Float,
    endX: Float,
    metrics: NotationStaffLayoutMetrics,
): NotationMeasureLayout {
    val contentStartX = startX + metrics.measurePaddingPx
    val contentEndX = endX - metrics.measurePaddingPx
    val layoutEvents = measure.events.mapIndexed { index, event ->
        val centerX = if (measure.events.size == 1) {
            (contentStartX + contentEndX) / 2f
        } else {
            val fraction = (index + 1).toFloat() / (measure.events.size + 1).toFloat()
            contentStartX + (contentEndX - contentStartX) * fraction
        }

        when (event) {
            is Note -> {
                val staffStep = staffStepIndexForPitch(event.pitch, clef)
                NoteEventLayout(
                    eventIndex = index,
                    centerX = centerX,
                    voice = event.voice,
                    staffStep = staffStep,
                    ledgerLineSteps = ledgerLineStepsForNote(staffStep),
                    noteValue = event.duration.value,
                    dots = event.duration.dots,
                    stemDirection = stemDirectionForStaffStep(staffStep),
                )
            }

            is Rest -> RestEventLayout(
                eventIndex = index,
                centerX = centerX,
                voice = event.voice,
                noteValue = event.duration.value,
                dots = event.duration.dots,
            )
        }
    }

    val timeSignature = measure.effectiveTimeSignature(defaultTimeSignature)

    return NotationMeasureLayout(
        measureNumber = measure.number,
        startX = startX,
        endX = endX,
        occupiedBeats = measure.occupiedBeats(defaultTimeSignature),
        capacityBeats = timeSignature.beatsPerMeasure.toDouble(),
        eventLayouts = layoutEvents,
    )
}

private fun Clef.bottomLinePitch(): Pitch = when (this) {
    Clef.TREBLE -> Pitch(step = PitchStep.E, octave = 4)
    Clef.BASS -> Pitch(step = PitchStep.G, octave = 2)
    Clef.ALTO -> Pitch(step = PitchStep.F, octave = 3)
    Clef.TENOR -> Pitch(step = PitchStep.D, octave = 3)
}

private fun Pitch.toDiatonicIndex(): Int = octave * DIATONIC_STEPS_PER_OCTAVE + step.diatonicIndex

private val PitchStep.diatonicIndex: Int
    get() = when (this) {
        PitchStep.C -> 0
        PitchStep.D -> 1
        PitchStep.E -> 2
        PitchStep.F -> 3
        PitchStep.G -> 4
        PitchStep.A -> 5
        PitchStep.B -> 6
    }

private const val DIATONIC_STEPS_PER_OCTAVE = 7
