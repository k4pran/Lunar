package com.ryanjames.lunar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryanjames.lunar.notation.model.Clef
import com.ryanjames.lunar.notation.model.KeySignature
import com.ryanjames.lunar.notation.model.Note
import com.ryanjames.lunar.notation.model.NoteDuration
import com.ryanjames.lunar.notation.model.NoteValue
import com.ryanjames.lunar.notation.model.NotationDocument
import com.ryanjames.lunar.notation.model.NotationMeasure
import com.ryanjames.lunar.notation.model.NotationMetadata
import com.ryanjames.lunar.notation.model.NotationStaff
import com.ryanjames.lunar.notation.model.Pitch
import com.ryanjames.lunar.notation.model.PitchStep
import com.ryanjames.lunar.notation.model.Rest
import com.ryanjames.lunar.notation.model.TimeSignature

@Composable
fun ComposeScreen(
    appState: LunarAppState,
    modifier: Modifier = Modifier,
) {
    val themePalette = lunarThemePalette()
    val prototypeDocument = remember { buildPrototypeNotationDocument() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                themePalette.headerGradientStart,
                                themePalette.headerGradientEnd,
                            )
                        )
                    )
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Compose",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = themePalette.headerForeground,
                )
                Text(
                    text = "The notation workspace now has a first-pass renderer: shared layout logic feeding a live Compose canvas, which is the path we can keep layering interaction onto.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = themePalette.headerForeground.copy(alpha = 0.84f),
                )
            }
        }

        NotationPreviewPanel(
            document = prototypeDocument,
            modifier = Modifier.fillMaxWidth(),
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Why this step matters",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "We now control stave geometry ourselves, which is the prerequisite for selection, hit testing, note entry, and drag-editing later.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "A later LilyPond pipeline can still give us beautiful engraved output, but this renderer is the part that can become interactive.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            OutlinedButton(
                onClick = { appState.selectSection(AppSection.LIBRARY) },
            ) {
                Text("Back to library")
            }
        }
    }
}

private fun buildPrototypeNotationDocument(): NotationDocument = NotationDocument(
    id = "compose-prototype",
    title = "Renderer Study",
    composer = "Lunar",
    metadata = NotationMetadata(
        subtitle = "Prototype",
        tempoText = "Moderato",
        defaultTimeSignature = TimeSignature(beatsPerMeasure = 4, beatUnit = 4),
        defaultKeySignature = KeySignature(),
    ),
    staves = listOf(
        NotationStaff(
            id = "lead-staff",
            name = "Lead",
            clef = Clef.TREBLE,
            measures = listOf(
                NotationMeasure(
                    number = 1,
                    events = listOf(
                        Note(Pitch(PitchStep.G, 4), NoteDuration(NoteValue.QUARTER)),
                        Note(Pitch(PitchStep.A, 4), NoteDuration(NoteValue.QUARTER)),
                        Note(Pitch(PitchStep.B, 4), NoteDuration(NoteValue.QUARTER)),
                        Note(Pitch(PitchStep.C, 5), NoteDuration(NoteValue.QUARTER)),
                    ),
                ),
                NotationMeasure(
                    number = 2,
                    events = listOf(
                        Note(Pitch(PitchStep.E, 5), NoteDuration(NoteValue.HALF)),
                        Rest(NoteDuration(NoteValue.QUARTER)),
                        Note(Pitch(PitchStep.B, 4), NoteDuration(NoteValue.QUARTER)),
                    ),
                ),
                NotationMeasure(
                    number = 3,
                    events = listOf(
                        Note(Pitch(PitchStep.A, 4), NoteDuration(NoteValue.EIGHTH)),
                        Note(Pitch(PitchStep.G, 4), NoteDuration(NoteValue.EIGHTH)),
                        Note(Pitch(PitchStep.F, 4), NoteDuration(NoteValue.QUARTER)),
                        Note(Pitch(PitchStep.E, 4), NoteDuration(NoteValue.QUARTER)),
                        Note(Pitch(PitchStep.D, 4), NoteDuration(NoteValue.QUARTER)),
                    ),
                ),
                NotationMeasure(
                    number = 4,
                    events = listOf(
                        Note(Pitch(PitchStep.A, 5), NoteDuration(NoteValue.HALF)),
                        Note(Pitch(PitchStep.C, 4), NoteDuration(NoteValue.HALF)),
                    ),
                ),
            ),
        )
    ),
)
