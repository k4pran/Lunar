package com.ryanjames.lunar.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt
import kotlin.time.Clock

data class MetronomeTimeSignature(
    val beatsPerMeasure: Int,
    val beatUnit: Int,
    val label: String = "$beatsPerMeasure/$beatUnit",
) {
    companion object {
        val COMMON = listOf(
            MetronomeTimeSignature(2, 4),
            MetronomeTimeSignature(3, 4),
            MetronomeTimeSignature(4, 4),
            MetronomeTimeSignature(5, 4),
            MetronomeTimeSignature(6, 8),
            MetronomeTimeSignature(7, 8),
            MetronomeTimeSignature(9, 8),
            MetronomeTimeSignature(12, 8),
        )
    }
}

private const val MIN_BPM = 20
private const val MAX_BPM = 300
private const val DEFAULT_BPM = 120
private const val TAP_RESET_MILLIS = 2_000L

internal data class MetronomeRestartState(
    val beatIndex: Int,
    val shouldAccentImmediately: Boolean,
)

/**
 * Tempo, metre and playback state for the metronome. It lives outside [MetronomePanel] so the
 * viewer can close the popover without losing the tempo or stopping the click.
 */
@Stable
class MetronomeState(
    initialBpm: Int = DEFAULT_BPM,
    initialTimeSignature: MetronomeTimeSignature = MetronomeTimeSignature.COMMON[2],
) {
    var bpm by mutableIntStateOf(initialBpm.coerceIn(MIN_BPM, MAX_BPM))
        private set
    var timeSignature by mutableStateOf(initialTimeSignature)
    var isPlaying by mutableStateOf(false)
    var currentBeat by mutableIntStateOf(-1)
        internal set

    private var lastTapMillis = 0L
    private var tapCount = 0
    private var tapBpmAccumulator = 0.0

    fun updateBpm(value: Int) {
        bpm = value.coerceIn(MIN_BPM, MAX_BPM)
    }

    fun adjustBpm(delta: Int) = updateBpm(bpm + delta)

    fun togglePlaying() {
        isPlaying = !isPlaying
    }

    /** Registers a tap at [nowMillis]; two or more taps within two seconds set the tempo. */
    fun tap(nowMillis: Long) {
        if (tapCount == 0 || nowMillis - lastTapMillis > TAP_RESET_MILLIS) {
            tapCount = 1
            tapBpmAccumulator = 0.0
        } else {
            val interval = (nowMillis - lastTapMillis).coerceAtLeast(1L)
            tapBpmAccumulator += 60_000.0 / interval
            tapCount++
            updateBpm((tapBpmAccumulator / (tapCount - 1)).roundToInt())
        }
        lastTapMillis = nowMillis
    }
}

@Composable
fun rememberMetronomeState(): MetronomeState = remember { MetronomeState() }

/** Drives the click for [state] for as long as this effect stays in the composition. */
@Composable
fun MetronomePlaybackEffect(state: MetronomeState) {
    val clickPlayer = rememberMetronomeClickPlayer()
    DisposableEffect(clickPlayer) {
        onDispose {
            clickPlayer.stop()
        }
    }

    LaunchedEffect(state, state.isPlaying) {
        clickPlayer.stop()
        if (!state.isPlaying) {
            state.currentBeat = -1
            return@LaunchedEffect
        }
        try {
            runMetronomePlayback(
                clickPlayer = clickPlayer,
                initialBeat = state.currentBeat,
                currentBpm = { state.bpm },
                currentTimeSignature = { state.timeSignature },
                onBeatChanged = { beatIndex -> state.currentBeat = beatIndex },
                shouldContinue = { isActive },
            )
        } finally {
            clickPlayer.stop()
        }
    }
}

/**
 * Standalone metronome outside the viewer (opened from quick find). The click keeps going while
 * the dialog is open and stops when it closes.
 */
@Composable
fun MetronomeDialog(
    onDismiss: () -> Unit,
) {
    val state = rememberMetronomeState()
    MetronomePlaybackEffect(state)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.widthIn(min = 320.dp, max = 460.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 10.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Metronome",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
                MetronomePanel(state = state)
            }
        }
    }
}

@Composable
fun MetronomePanel(
    state: MetronomeState,
    modifier: Modifier = Modifier,
) {
    var showTimeSignatureMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "${state.bpm} BPM",
            style = MaterialTheme.typography.displaySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            text = tempoMarking(state.bpm),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Slider(
            value = state.bpm.toFloat(),
            onValueChange = { state.updateBpm(it.roundToInt()) },
            valueRange = MIN_BPM.toFloat()..MAX_BPM.toFloat(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(-5, -1, 1, 5).forEach { delta ->
                OutlinedButton(onClick = { state.adjustBpm(delta) }) {
                    Text(if (delta > 0) "+$delta" else "$delta")
                }
            }
        }

        FilledTonalButton(
            onClick = { state.tap(Clock.System.now().toEpochMilliseconds()) },
        ) {
            Text("Tap Tempo")
        }

        Box {
            OutlinedButton(onClick = { showTimeSignatureMenu = true }) {
                Text("Time: ${state.timeSignature.label}")
            }
            DropdownMenu(
                expanded = showTimeSignatureMenu,
                onDismissRequest = { showTimeSignatureMenu = false },
            ) {
                MetronomeTimeSignature.COMMON.forEach { ts ->
                    DropdownMenuItem(
                        text = { Text(ts.label) },
                        onClick = {
                            state.timeSignature = ts
                            showTimeSignatureMenu = false
                        },
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            repeat(state.timeSignature.beatsPerMeasure) { beatIndex ->
                val isActive = beatIndex == state.currentBeat
                val isAccent = beatIndex == 0
                val targetColor = when {
                    isActive && isAccent -> MaterialTheme.colorScheme.error
                    isActive -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val color by animateColorAsState(
                    targetValue = targetColor,
                    animationSpec = tween(durationMillis = 80),
                )
                Box(
                    modifier = Modifier
                        .size(if (isAccent) 28.dp else 22.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }

        Button(
            onClick = state::togglePlaying,
            colors = if (state.isPlaying) {
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            } else {
                ButtonDefaults.buttonColors()
            },
            modifier = Modifier.fillMaxWidth(0.6f).height(52.dp),
        ) {
            Text(
                text = if (state.isPlaying) "Stop" else "Start",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

private fun tempoMarking(bpm: Int): String = when {
    bpm < 40 -> "Grave"
    bpm < 55 -> "Largo"
    bpm < 65 -> "Larghetto"
    bpm < 73 -> "Adagio"
    bpm < 78 -> "Andante"
    bpm < 86 -> "Andantino"
    bpm < 98 -> "Moderato"
    bpm < 109 -> "Allegretto"
    bpm < 132 -> "Allegro"
    bpm < 140 -> "Vivace"
    bpm < 178 -> "Presto"
    else -> "Prestissimo"
}

internal fun prepareMetronomeRestart(
    currentBeat: Int,
    beatsPerMeasure: Int,
): MetronomeRestartState {
    require(beatsPerMeasure > 0) { "beatsPerMeasure must be positive." }
    return if (currentBeat < 0) {
        MetronomeRestartState(
            beatIndex = 0,
            shouldAccentImmediately = true,
        )
    } else {
        MetronomeRestartState(
            beatIndex = currentBeat.mod(beatsPerMeasure),
            shouldAccentImmediately = false,
        )
    }
}

internal suspend fun runMetronomePlayback(
    clickPlayer: MetronomeClickPlayer,
    initialBeat: Int,
    currentBpm: () -> Int,
    currentTimeSignature: () -> MetronomeTimeSignature,
    onBeatChanged: (Int) -> Unit,
    shouldContinue: () -> Boolean,
    delayMillis: suspend (Long) -> Unit = { delay(it) },
) {
    val restartState = prepareMetronomeRestart(
        currentBeat = initialBeat,
        beatsPerMeasure = currentTimeSignature().beatsPerMeasure,
    )
    var beatIndex = restartState.beatIndex
    onBeatChanged(beatIndex)
    if (restartState.shouldAccentImmediately) {
        clickPlayer.playClick(accented = true)
    }
    while (shouldContinue()) {
        delayMillis(intervalMillisForBpm(currentBpm()))
        if (!shouldContinue()) {
            break
        }
        beatIndex = nextMetronomeBeat(
            currentBeat = beatIndex,
            beatsPerMeasure = currentTimeSignature().beatsPerMeasure,
        )
        onBeatChanged(beatIndex)
        clickPlayer.playClick(accented = beatIndex == 0)
    }
}

internal fun intervalMillisForBpm(bpm: Int): Long =
    (60_000.0 / bpm).toLong().coerceAtLeast(1L)

internal fun nextMetronomeBeat(
    currentBeat: Int,
    beatsPerMeasure: Int,
): Int {
    require(beatsPerMeasure > 0) { "beatsPerMeasure must be positive." }
    return when {
        currentBeat < 0 -> 0
        currentBeat >= beatsPerMeasure - 1 -> 0
        else -> currentBeat + 1
    }
}
