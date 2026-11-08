package com.ryanjames.lunar.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.ryanjames.lunar.composeapp.generated.resources.Res
import com.ryanjames.lunar.composeapp.generated.resources.cormorant_garamond_semibold
import com.ryanjames.lunar.composeapp.generated.resources.cormorant_garamond_semibold_italic
import com.ryanjames.lunar.composeapp.generated.resources.newsreader_italic
import com.ryanjames.lunar.composeapp.generated.resources.newsreader_medium
import com.ryanjames.lunar.composeapp.generated.resources.newsreader_regular
import com.ryanjames.lunar.composeapp.generated.resources.newsreader_semibold
import org.jetbrains.compose.resources.Font

/** Cormorant Garamond (SIL OFL 1.1), used for display and heading sizes. */
@Composable
internal fun cormorantGaramondFamily(): FontFamily = FontFamily(
    Font(Res.font.cormorant_garamond_semibold, FontWeight.SemiBold, FontStyle.Normal),
    Font(Res.font.cormorant_garamond_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
)

/** Newsreader (SIL OFL 1.1), used for body copy, labels and buttons. */
@Composable
internal fun newsreaderFamily(): FontFamily = FontFamily(
    Font(Res.font.newsreader_regular, FontWeight.Normal, FontStyle.Normal),
    Font(Res.font.newsreader_italic, FontWeight.Normal, FontStyle.Italic),
    Font(Res.font.newsreader_medium, FontWeight.Medium, FontStyle.Normal),
    Font(Res.font.newsreader_semibold, FontWeight.SemiBold, FontStyle.Normal),
)
