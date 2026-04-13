package com.ryanjames.lunar.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.ryanjames.lunar.settings.AppColorTheme

@Immutable
data class LunarThemePalette(
    val appBackgroundGradientBottom: Color,
    val headerGradientStart: Color,
    val headerGradientEnd: Color,
    val headerForeground: Color,
    val accentGradientStart: Color,
    val accentGradientEnd: Color,
    val viewerBackdrop: Color,
    val viewerOverlay: Color,
    val viewerScrim: Color,
    val viewerForeground: Color,
)

private data class LunarThemeSpec(
    val colorScheme: ColorScheme,
    val palette: LunarThemePalette,
)

private val LocalLunarThemePalette = staticCompositionLocalOf<LunarThemePalette> {
    error("LunarThemePalette was not provided.")
}

@Composable
@ReadOnlyComposable
fun lunarThemePalette(): LunarThemePalette = LocalLunarThemePalette.current

@Composable
fun LunarTheme(
    theme: AppColorTheme,
    content: @Composable () -> Unit,
) {
    val spec = theme.toSpec()

    CompositionLocalProvider(LocalLunarThemePalette provides spec.palette) {
        MaterialTheme(
            colorScheme = spec.colorScheme,
            typography = MaterialTheme.typography.copy(
                displayLarge = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 57.sp,
                    lineHeight = 64.sp,
                ),
                headlineMedium = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                ),
                titleLarge = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                ),
                bodyLarge = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.SansSerif,
                ),
                bodyMedium = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.SansSerif,
                ),
                labelLarge = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = FontFamily.SansSerif,
                ),
            ),
            content = content,
        )
    }
}

private fun AppColorTheme.toSpec(): LunarThemeSpec = when (this) {
    AppColorTheme.OCEAN -> LunarThemeSpec(
        colorScheme = lightColorScheme(
            primary = Color(0xFF1F4F6B),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFA7C6ED),
            onPrimaryContainer = Color(0xFF0D2B36),
            secondary = Color(0xFF4D7C99),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFF6A9BD1),
            onSecondaryContainer = Color(0xFF0D2B36),
            tertiary = Color(0xFF176A8A),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFB8D8EC),
            onTertiaryContainer = Color(0xFF0D2B36),
            background = Color(0xFFE8F2FA),
            onBackground = Color(0xFF0D2B36),
            surface = Color(0xFFF0F7FC),
            onSurface = Color(0xFF1A3E4F),
            surfaceVariant = Color(0xFFCCDDE9),
            onSurfaceVariant = Color(0xFF2A6E7C),
            outline = Color(0xFF4C8FD5),
            error = Color(0xFFB42318),
            onError = Color.White,
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),
        ),
        palette = LunarThemePalette(
            appBackgroundGradientBottom = Color(0xFFCCDDE9),
            headerGradientStart = Color(0xFF1F4F6B),
            headerGradientEnd = Color(0xFF176A8A),
            headerForeground = Color.White,
            accentGradientStart = Color(0xFF176A8A),
            accentGradientEnd = Color(0xFF4D7C99),
            viewerBackdrop = Color(0xFF172433),
            viewerOverlay = Color(0xFF123346).copy(alpha = 0.96f),
            viewerScrim = Color(0xFF050A10).copy(alpha = 0.88f),
            viewerForeground = Color.White,
        ),
    )

    AppColorTheme.FOREST -> LunarThemeSpec(
        colorScheme = lightColorScheme(
            primary = Color(0xFF27543B),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFC6E3CF),
            onPrimaryContainer = Color(0xFF10311F),
            secondary = Color(0xFF5E7B62),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFD4E6D2),
            onSecondaryContainer = Color(0xFF203425),
            tertiary = Color(0xFF8E6A2C),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFF0DEB8),
            onTertiaryContainer = Color(0xFF3C2800),
            background = Color(0xFFF1F6F0),
            onBackground = Color(0xFF173021),
            surface = Color(0xFFF7FBF6),
            onSurface = Color(0xFF1E3527),
            surfaceVariant = Color(0xFFDCE7DC),
            onSurfaceVariant = Color(0xFF4A6250),
            outline = Color(0xFF7D9A84),
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
        ),
        palette = LunarThemePalette(
            appBackgroundGradientBottom = Color(0xFFDCE7DC),
            headerGradientStart = Color(0xFF27543B),
            headerGradientEnd = Color(0xFF8E6A2C),
            headerForeground = Color.White,
            accentGradientStart = Color(0xFF5E7B62),
            accentGradientEnd = Color(0xFF27543B),
            viewerBackdrop = Color(0xFF122018),
            viewerOverlay = Color(0xFF1C2F24).copy(alpha = 0.96f),
            viewerScrim = Color(0xFF040A06).copy(alpha = 0.88f),
            viewerForeground = Color.White,
        ),
    )

    AppColorTheme.SUNSET -> LunarThemeSpec(
        colorScheme = lightColorScheme(
            primary = Color(0xFF8A3E24),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFF7C6AE),
            onPrimaryContainer = Color(0xFF3A1308),
            secondary = Color(0xFF9B5A33),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFF0D1BF),
            onSecondaryContainer = Color(0xFF41210E),
            tertiary = Color(0xFF7A4F7E),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFE7D0E8),
            onTertiaryContainer = Color(0xFF311538),
            background = Color(0xFFFEF3EC),
            onBackground = Color(0xFF3A2318),
            surface = Color(0xFFFFF8F4),
            onSurface = Color(0xFF44291E),
            surfaceVariant = Color(0xFFF2DDD0),
            onSurfaceVariant = Color(0xFF6C5348),
            outline = Color(0xFFC08361),
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
        ),
        palette = LunarThemePalette(
            appBackgroundGradientBottom = Color(0xFFF2DDD0),
            headerGradientStart = Color(0xFF8A3E24),
            headerGradientEnd = Color(0xFF7A4F7E),
            headerForeground = Color.White,
            accentGradientStart = Color(0xFF9B5A33),
            accentGradientEnd = Color(0xFF8A3E24),
            viewerBackdrop = Color(0xFF251915),
            viewerOverlay = Color(0xFF3A241D).copy(alpha = 0.96f),
            viewerScrim = Color(0xFF0C0605).copy(alpha = 0.88f),
            viewerForeground = Color.White,
        ),
    )

    AppColorTheme.AURORA -> LunarThemeSpec(
        colorScheme = lightColorScheme(
            primary = Color(0xFF006D77),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFA8E7E4),
            onPrimaryContainer = Color(0xFF002023),
            secondary = Color(0xFF8C5E2A),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFF8D9B4),
            onSecondaryContainer = Color(0xFF341B00),
            tertiary = Color(0xFF9B3D5C),
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFF7C4D4),
            onTertiaryContainer = Color(0xFF3C0B1C),
            background = Color(0xFFF9F7F2),
            onBackground = Color(0xFF1F2A2A),
            surface = Color(0xFFFEFCF8),
            onSurface = Color(0xFF1E2A29),
            surfaceVariant = Color(0xFFE2ECEA),
            onSurfaceVariant = Color(0xFF486361),
            outline = Color(0xFF6F8A87),
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
        ),
        palette = LunarThemePalette(
            appBackgroundGradientBottom = Color(0xFFE2ECEA),
            headerGradientStart = Color(0xFF006D77),
            headerGradientEnd = Color(0xFF9B3D5C),
            headerForeground = Color.White,
            accentGradientStart = Color(0xFF8C5E2A),
            accentGradientEnd = Color(0xFF006D77),
            viewerBackdrop = Color(0xFF182225),
            viewerOverlay = Color(0xFF213136).copy(alpha = 0.96f),
            viewerScrim = Color(0xFF060A0C).copy(alpha = 0.88f),
            viewerForeground = Color.White,
        ),
    )

    AppColorTheme.DARCULA -> LunarThemeSpec(
        colorScheme = darkColorScheme(
            primary = Color(0xFF80A0C8),
            onPrimary = Color(0xFF122033),
            primaryContainer = Color(0xFF304861),
            onPrimaryContainer = Color(0xFFD6E4F7),
            secondary = Color(0xFF7FB787),
            onSecondary = Color(0xFF0F2A14),
            secondaryContainer = Color(0xFF254231),
            onSecondaryContainer = Color(0xFFCDEED0),
            tertiary = Color(0xFFE39B4D),
            onTertiary = Color(0xFF402000),
            tertiaryContainer = Color(0xFF6A471E),
            onTertiaryContainer = Color(0xFFFFDEB8),
            background = Color(0xFF1E1F22),
            onBackground = Color(0xFFE3E6EA),
            surface = Color(0xFF2B2D30),
            onSurface = Color(0xFFD5D9E0),
            surfaceVariant = Color(0xFF33363A),
            onSurfaceVariant = Color(0xFFAFB6C2),
            outline = Color(0xFF7C8595),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
        ),
        palette = LunarThemePalette(
            appBackgroundGradientBottom = Color(0xFF2E3136),
            headerGradientStart = Color(0xFF304861),
            headerGradientEnd = Color(0xFF6A471E),
            headerForeground = Color(0xFFF3F5F7),
            accentGradientStart = Color(0xFF80A0C8),
            accentGradientEnd = Color(0xFF7FB787),
            viewerBackdrop = Color(0xFF16181B),
            viewerOverlay = Color(0xFF25292D).copy(alpha = 0.96f),
            viewerScrim = Color(0xFF000000).copy(alpha = 0.88f),
            viewerForeground = Color(0xFFF3F5F7),
        ),
    )

    AppColorTheme.MIDNIGHT -> LunarThemeSpec(
        colorScheme = darkColorScheme(
            primary = Color(0xFF6DD3FF),
            onPrimary = Color(0xFF00273A),
            primaryContainer = Color(0xFF004D6D),
            onPrimaryContainer = Color(0xFFC8EFFF),
            secondary = Color(0xFF88B1FF),
            onSecondary = Color(0xFF0A2C63),
            secondaryContainer = Color(0xFF20457D),
            onSecondaryContainer = Color(0xFFD9E2FF),
            tertiary = Color(0xFF7EF3C7),
            onTertiary = Color(0xFF003828),
            tertiaryContainer = Color(0xFF00513A),
            onTertiaryContainer = Color(0xFFA0FFD9),
            background = Color(0xFF06131D),
            onBackground = Color(0xFFD7E5F0),
            surface = Color(0xFF0D1B24),
            onSurface = Color(0xFFD7E5F0),
            surfaceVariant = Color(0xFF1D2B36),
            onSurfaceVariant = Color(0xFF9FB2C1),
            outline = Color(0xFF6A8090),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
        ),
        palette = LunarThemePalette(
            appBackgroundGradientBottom = Color(0xFF10212D),
            headerGradientStart = Color(0xFF004D6D),
            headerGradientEnd = Color(0xFF00513A),
            headerForeground = Color(0xFFF4FAFF),
            accentGradientStart = Color(0xFF88B1FF),
            accentGradientEnd = Color(0xFF6DD3FF),
            viewerBackdrop = Color(0xFF020A0F),
            viewerOverlay = Color(0xFF0E1B24).copy(alpha = 0.96f),
            viewerScrim = Color(0xFF000000).copy(alpha = 0.88f),
            viewerForeground = Color(0xFFF4FAFF),
        ),
    )

    AppColorTheme.EMBER -> LunarThemeSpec(
        colorScheme = darkColorScheme(
            primary = Color(0xFFFFB37A),
            onPrimary = Color(0xFF4E2600),
            primaryContainer = Color(0xFF6E3A0D),
            onPrimaryContainer = Color(0xFFFFDCC3),
            secondary = Color(0xFFE08A67),
            onSecondary = Color(0xFF52210A),
            secondaryContainer = Color(0xFF70371C),
            onSecondaryContainer = Color(0xFFFFDCCD),
            tertiary = Color(0xFFDDBB5A),
            onTertiary = Color(0xFF3B2F00),
            tertiaryContainer = Color(0xFF564500),
            onTertiaryContainer = Color(0xFFFBE18C),
            background = Color(0xFF1A120E),
            onBackground = Color(0xFFF0DED6),
            surface = Color(0xFF211814),
            onSurface = Color(0xFFF0DED6),
            surfaceVariant = Color(0xFF3A2B24),
            onSurfaceVariant = Color(0xFFD6C2B8),
            outline = Color(0xFFA98C7F),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
        ),
        palette = LunarThemePalette(
            appBackgroundGradientBottom = Color(0xFF31231D),
            headerGradientStart = Color(0xFF6E3A0D),
            headerGradientEnd = Color(0xFF564500),
            headerForeground = Color(0xFFFFF1E8),
            accentGradientStart = Color(0xFFE08A67),
            accentGradientEnd = Color(0xFFFFB37A),
            viewerBackdrop = Color(0xFF140D09),
            viewerOverlay = Color(0xFF2A1B13).copy(alpha = 0.96f),
            viewerScrim = Color(0xFF000000).copy(alpha = 0.88f),
            viewerForeground = Color(0xFFFFF1E8),
        ),
    )
}
