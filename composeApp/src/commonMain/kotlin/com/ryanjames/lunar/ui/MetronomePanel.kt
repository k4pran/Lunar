package com.ryanjames.lunar.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt
import kotlin.time.Clock
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

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

internal data class MetronomeRestartState(
    val beatIndex: Int,
    val shouldAccentImmediately: Boolean,
)

@Composable
fun MetronomeContent(
    modifier: Modifier = Modifier,
) {
    val clickPlayer = rememberMetronomeClickPlayer()
    var bpm by remember { mutableIntStateOf(DEFAULT_BPM) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentBeat by remember { mutableIntStateOf(-1) }
    var selectedTimeSignature by remember { mutableStateOf(MetronomeTimeSignature.COMMON[2]) }
    var showTimeSignatureMenu by remember { mutableStateOf(false) }
    var lastTapTime by remember { mutableStateOf(0L) }
    var tapCount by remember { mutableIntStateOf(0) }
    var tapBpmAccumulator by remember { mutableStateOf(0.0) }
    val latestBpm by rememberUpdatedState(bpm)
    val latestTimeSignature by rememberUpdatedState(selectedTimeSignature)
    DisposableEffect(clickPlayer) {
        onDispose {
            clickPlayer.stop()
        }
    }

    LaunchedEffect(isPlaying) {
        clickPlayer.stop()
        if (!isPlaying) {
            currentBeat = -1
            return@LaunchedEffect
        }
        try {
            runMetronomePlayback(
                clickPlayer = clickPlayer,
                initialBeat = currentBeat,
                currentBpm = { latestBpm },
                currentTimeSignature = { latestTimeSignature },
                onBeatChanged = { beatIndex -> currentBeat = beatIndex },
                shouldContinue = { isActive },
            )
        } finally {
            clickPlayer.stop()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "$bpm BPM",
            style = MaterialTheme.typography.displaySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            text = tempoMarking(bpm),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Slider(
            value = bpm.toFloat(),
            onValueChange = { bpm = it.roundToInt() },
            valueRange = MIN_BPM.toFloat()..MAX_BPM.toFloat(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { bpm = (bpm - 5).coerceAtLeast(MIN_BPM) }) {
                Text("-5")
            }
            OutlinedButton(onClick = { bpm = (bpm - 1).coerceAtLeast(MIN_BPM) }) {
                Text("-1")
            }
            OutlinedButton(onClick = { bpm = (bpm + 1).coerceAtMost(MAX_BPM) }) {
                Text("+1")
            }
            OutlinedButton(onClick = { bpm = (bpm + 5).coerceAtMost(MAX_BPM) }) {
                Text("+5")
            }
        }

        FilledTonalButton(
            onClick = {
                val now = Clock.System.now().toEpochMilliseconds()
                if (now - lastTapTime > 2000) {
                    tapCount = 1
                    tapBpmAccumulator = 0.0
                } else {
                    val interval = now - lastTapTime
                    tapBpmAccumulator += 60_000.0 / interval
                    tapCount++
                    if (tapCount >= 2) {
                        bpm = (tapBpmAccumulator / (tapCount - 1)).roundToInt().coerceIn(MIN_BPM, MAX_BPM)
                    }
                }
                lastTapTime = now
            },
        ) {
            Text("Tap Tempo")
        }

        Box {
            OutlinedButton(onClick = { showTimeSignatureMenu = true }) {
                Text("Time: ${selectedTimeSignature.label}")
            }
            DropdownMenu(
                expanded = showTimeSignatureMenu,
                onDismissRequest = { showTimeSignatureMenu = false },
            ) {
                MetronomeTimeSignature.COMMON.forEach { ts ->
                    DropdownMenuItem(
                        text = { Text(ts.label) },
                        onClick = {
                            selectedTimeSignature = ts
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
            repeat(selectedTimeSignature.beatsPerMeasure) { beatIndex ->
                val isActive = beatIndex == currentBeat
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
            onClick = { isPlaying = !isPlaying },
            colors = if (isPlaying) {
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            } else {
                ButtonDefaults.buttonColors()
            },
            modifier = Modifier.fillMaxWidth(0.6f).height(52.dp),
        ) {
            Text(
                text = if (isPlaying) "Stop" else "Start",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
fun MetronomeDialog(
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val horizontalPadding = 20.dp
            val verticalPadding = 20.dp
            val dialogWidth = (maxWidth - horizontalPadding * 2).coerceAtMost(460.dp).coerceAtLeast(320.dp)
            val dialogMaxHeight = (maxHeight - verticalPadding * 2).coerceAtLeast(360.dp)

            Surface(
                modifier = Modifier
                    .width(dialogWidth)
                    .heightIn(max = dialogMaxHeight),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 10.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
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

                    MetronomeContent(
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
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
