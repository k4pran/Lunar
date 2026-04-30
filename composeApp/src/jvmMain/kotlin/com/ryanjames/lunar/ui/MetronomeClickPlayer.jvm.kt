package com.ryanjames.lunar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import java.awt.Toolkit
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

private const val MetronomeSampleRate = 44_100

@Composable
actual fun rememberMetronomeClickPlayer(): MetronomeClickPlayer {
    val player = remember { JvmMetronomeClickPlayer() }
    DisposableEffect(player) {
        onDispose {
            player.close()
        }
    }
    return player
}

internal fun buildMetronomeClickPcm(
    frequencyHz: Double,
    durationMillis: Int,
    peakAmplitude: Double,
    sampleRate: Int = MetronomeSampleRate,
): ByteArray {
    val totalSamples = (sampleRate * (durationMillis / 1000.0)).toInt().coerceAtLeast(1)
    val pcm = ByteArray(totalSamples * 2)
    for (sampleIndex in 0 until totalSamples) {
        val timeSeconds = sampleIndex.toDouble() / sampleRate.toDouble()
        val envelope = (1.0 - sampleIndex.toDouble() / totalSamples.toDouble()).coerceIn(0.0, 1.0)
        val waveform = kotlin.math.sin(2.0 * Math.PI * frequencyHz * timeSeconds)
        val value = (waveform * envelope * peakAmplitude * Short.MAX_VALUE)
            .toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
        val byteIndex = sampleIndex * 2
        pcm[byteIndex] = (value.toInt() and 0xFF).toByte()
        pcm[byteIndex + 1] = ((value.toInt() shr 8) and 0xFF).toByte()
    }
    return pcm
}

private class JvmMetronomeClickPlayer : MetronomeClickPlayer {
    private val generation = AtomicLong(0)
    private val executor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(),
        { runnable -> Thread(runnable, "lunar-metronome").apply { isDaemon = true } },
    )
    private val audioFormat = AudioFormat(
        MetronomeSampleRate.toFloat(),
        16,
        1,
        true,
        false,
    )
    private val line = runCatching {
        AudioSystem.getSourceDataLine(audioFormat).apply {
            open(audioFormat)
            start()
        }
    }.getOrNull()
    private val accentedClick = buildMetronomeClickPcm(
        frequencyHz = 1_280.0,
        durationMillis = 38,
        peakAmplitude = 0.72,
    )
    private val regularClick = buildMetronomeClickPcm(
        frequencyHz = 920.0,
        durationMillis = 28,
        peakAmplitude = 0.48,
    )

    override fun playClick(accented: Boolean) {
        val pcm = if (accented) accentedClick else regularClick
        val activeLine = line
        val scheduledGeneration = generation.get()
        executor.execute {
            if (scheduledGeneration != generation.get()) {
                return@execute
            }
            if (activeLine != null && activeLine.isOpen) {
                synchronized(activeLine) {
                    if (scheduledGeneration != generation.get()) {
                        return@synchronized
                    }
                    runCatching {
                        activeLine.write(pcm, 0, pcm.size)
                    }.onFailure {
                        if (scheduledGeneration == generation.get()) {
                            runCatching { Toolkit.getDefaultToolkit().beep() }
                        }
                    }
                }
            } else {
                if (scheduledGeneration == generation.get()) {
                    runCatching { Toolkit.getDefaultToolkit().beep() }
                }
            }
        }
    }

    override fun stop() {
        generation.incrementAndGet()
        executor.queue.clear()
        line?.let { activeLine ->
            synchronized(activeLine) {
                runCatching { activeLine.flush() }
            }
        }
    }

    fun close() {
        stop()
        executor.shutdownNow()
        line?.runCatching {
            stop()
            close()
        }
    }
}
