package com.ryanjames.lunar

import com.ryanjames.lunar.notation.layout.StemDirection
import com.ryanjames.lunar.notation.model.Clef
import com.ryanjames.lunar.notation.model.NoteValue
import com.ryanjames.lunar.ui.clefGlyphFor
import com.ryanjames.lunar.ui.flagGlyphFor
import com.ryanjames.lunar.ui.noteHeadGlyphFor
import com.ryanjames.lunar.ui.restGlyphFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotationMusicFontTest {

    @Test
    fun mapsCoreNoteheadsToStandardSmuflGlyphs() {
        assertEquals("\uE0A2", noteHeadGlyphFor(NoteValue.WHOLE))
        assertEquals("\uE0A3", noteHeadGlyphFor(NoteValue.HALF))
        assertEquals("\uE0A4", noteHeadGlyphFor(NoteValue.QUARTER))
    }

    @Test
    fun mapsClefsAndRestsToBravuraGlyphs() {
        assertEquals("\uE050", clefGlyphFor(Clef.TREBLE))
        assertEquals("\uE062", clefGlyphFor(Clef.BASS))
        assertEquals("\uE4E5", restGlyphFor(NoteValue.QUARTER))
        assertEquals("\uE4E8", restGlyphFor(NoteValue.THIRTY_SECOND))
    }

    @Test
    fun mapsFlagGlyphsByStemDirection() {
        assertEquals("\uE240", flagGlyphFor(NoteValue.EIGHTH, StemDirection.UP))
        assertEquals("\uE243", flagGlyphFor(NoteValue.SIXTEENTH, StemDirection.DOWN))
        assertNull(flagGlyphFor(NoteValue.QUARTER, StemDirection.UP))
    }
}
