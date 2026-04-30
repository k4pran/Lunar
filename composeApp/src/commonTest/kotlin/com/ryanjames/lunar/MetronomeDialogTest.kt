package com.ryanjames.lunar

import com.ryanjames.lunar.ui.MetronomeClickPlayer
import com.ryanjames.lunar.ui.MetronomeTimeSignature
import com.ryanjames.lunar.ui.nextMetronomeBeat
import com.ryanjames.lunar.ui.prepareMetronomeRestart
import com.ryanjames.lunar.ui.runMetronomePlayback
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MetronomeDialogTest {

    @Test
    fun metronomeRestartFromStoppedAccentsBeatOneImmediately() {
        val restartState = prepareMetronomeRestart(
            currentBeat = -1,
            beatsPerMeasure = 4,
        )

        assertEquals(0, restartState.beatIndex)
        assertTrue(restartState.shouldAccentImmediately)
    }

    @Test
    fun metronomeRestartWhilePlayingKeepsCurrentBeatWithoutImmediateAccent() {
        val restartState = prepareMetronomeRestart(
            currentBeat = 5,
            beatsPerMeasure = 4,
        )

        assertEquals(1, restartState.beatIndex)
        assertFalse(restartState.shouldAccentImmediately)
    }

    @Test
    fun nextMetronomeBeatWrapsToDownbeatWhenMeasureShrinksPastCurrentBeat() {
        assertEquals(
            0,
            nextMetronomeBeat(
                currentBeat = 3,
                beatsPerMeasure = 3,
            ),
        )
    }

    @Test
    fun metronomePlaybackUsesLatestTempoWithoutReTriggeringStartAccent() = runBlocking {
        val clicks = mutableListOf<Boolean>()
        val beatSequence = mutableListOf<Int>()
        val intervals = mutableListOf<Long>()
        var bpm = 120
        var timeSignature = MetronomeTimeSignature(4, 4)
        var keepRunning = true

        runMetronomePlayback(
            clickPlayer = object : MetronomeClickPlayer {
                override fun playClick(accented: Boolean) {
                    clicks += accented
                }
            },
            initialBeat = -1,
            currentBpm = { bpm },
            currentTimeSignature = { timeSignature },
            onBeatChanged = { beatIndex ->
                beatSequence += beatIndex
                if (beatSequence.size >= 4) {
                    keepRunning = false
                }
            },
            shouldContinue = { keepRunning },
            delayMillis = { interval ->
                intervals += interval
                if (intervals.size == 1) {
                    bpm = 180
                    timeSignature = MetronomeTimeSignature(3, 4)
                }
            },
        )

        assertEquals(listOf(0, 1, 2, 0), beatSequence)
        assertEquals(listOf(true, false, false, true), clicks)
        assertEquals(listOf(500L, 333L, 333L), intervals)
    }
}
