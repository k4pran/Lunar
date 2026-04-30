package com.ryanjames.lunar

import com.ryanjames.lunar.ui.buildMetronomeClickPcm
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MetronomeClickPlayerJvmTest {

    @Test
    fun generatedMetronomeClickPcmContainsAudibleSamples() {
        val pcm = buildMetronomeClickPcm(
            frequencyHz = 880.0,
            durationMillis = 32,
            peakAmplitude = 0.5,
        )

        assertTrue(pcm.isNotEmpty())
        assertTrue(pcm.any { it.toInt() != 0 })
    }

    @Test
    fun accentedAndRegularClicksProduceDifferentWaveforms() {
        val regular = buildMetronomeClickPcm(
            frequencyHz = 920.0,
            durationMillis = 28,
            peakAmplitude = 0.48,
        )
        val accented = buildMetronomeClickPcm(
            frequencyHz = 1_280.0,
            durationMillis = 38,
            peakAmplitude = 0.72,
        )

        assertTrue(accented.size > regular.size)
        assertFalse(regular.contentEquals(accented))
    }
}
