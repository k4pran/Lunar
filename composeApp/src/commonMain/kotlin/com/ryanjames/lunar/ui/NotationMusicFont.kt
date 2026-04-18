package com.ryanjames.lunar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import com.ryanjames.lunar.composeapp.generated.resources.Bravura
import com.ryanjames.lunar.composeapp.generated.resources.Res
import com.ryanjames.lunar.notation.layout.StemDirection
import com.ryanjames.lunar.notation.model.Clef
import com.ryanjames.lunar.notation.model.NoteValue
import org.jetbrains.compose.resources.Font

@Composable
internal fun rememberBravuraFontFamily(): FontFamily {
    val bravuraFont = Font(resource = Res.font.Bravura)
    return remember(bravuraFont) {
        FontFamily(bravuraFont)
    }
}

internal fun noteHeadGlyphFor(noteValue: NoteValue): String = when (noteValue) {
    NoteValue.WHOLE -> smuflGlyph(0xE0A2)
    NoteValue.HALF -> smuflGlyph(0xE0A3)
    else -> smuflGlyph(0xE0A4)
}

internal fun restGlyphFor(noteValue: NoteValue): String = when (noteValue) {
    NoteValue.WHOLE -> smuflGlyph(0xE4E3)
    NoteValue.HALF -> smuflGlyph(0xE4E4)
    NoteValue.QUARTER -> smuflGlyph(0xE4E5)
    NoteValue.EIGHTH -> smuflGlyph(0xE4E6)
    NoteValue.SIXTEENTH -> smuflGlyph(0xE4E7)
    NoteValue.THIRTY_SECOND -> smuflGlyph(0xE4E8)
    NoteValue.SIXTY_FOURTH -> smuflGlyph(0xE4E9)
}

internal fun clefGlyphFor(clef: Clef): String = when (clef) {
    Clef.TREBLE -> smuflGlyph(0xE050)
    Clef.BASS -> smuflGlyph(0xE062)
    Clef.ALTO, Clef.TENOR -> smuflGlyph(0xE05C)
}

internal fun flagGlyphFor(
    noteValue: NoteValue,
    direction: StemDirection,
): String? {
    val codePoint = when (noteValue) {
        NoteValue.EIGHTH -> if (direction == StemDirection.UP) 0xE240 else 0xE241
        NoteValue.SIXTEENTH -> if (direction == StemDirection.UP) 0xE242 else 0xE243
        NoteValue.THIRTY_SECOND -> if (direction == StemDirection.UP) 0xE244 else 0xE245
        NoteValue.SIXTY_FOURTH -> if (direction == StemDirection.UP) 0xE246 else 0xE247
        else -> null
    }
    return codePoint?.let(::smuflGlyph)
}

private fun smuflGlyph(codePoint: Int): String = codePoint.toChar().toString()
