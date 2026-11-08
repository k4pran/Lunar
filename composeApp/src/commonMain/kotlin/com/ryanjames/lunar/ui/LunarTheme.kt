package com.ryanjames.lunar.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.ryanjames.lunar.settings.AppSkin
import com.ryanjames.lunar.ui.design.LocalLunarTokens
import com.ryanjames.lunar.ui.design.LunarColors
import com.ryanjames.lunar.ui.design.LunarShapes
import com.ryanjames.lunar.ui.design.LunarTokens
import com.ryanjames.lunar.ui.design.LunarTypography
import com.ryanjames.lunar.ui.design.cormorantGaramondFamily
import com.ryanjames.lunar.ui.design.lunarColors
import com.ryanjames.lunar.ui.design.lunarTypography
import com.ryanjames.lunar.ui.design.newsreaderFamily

/**
 * Gradient and viewer colours used by screens that have not moved onto [LunarTokens] yet. Derived
 * from the active skin so they stay in step with it.
 */
@Immutable
data class LunarThemePalette(
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

private val LocalLunarThemePalette = staticCompositionLocalOf<LunarThemePalette> {
    error("LunarThemePalette was not provided.")
}

@Composable
@ReadOnlyComposable
fun lunarThemePalette(): LunarThemePalette = LocalLunarThemePalette.current

@Composable
fun LunarTheme(
    skin: AppSkin,
    content: @Composable () -> Unit,
) {
    val displayFamily = cormorantGaramondFamily()
    val bodyFamily = newsreaderFamily()
    val colors = lunarColors(skin)
    val tokens = remember(skin, displayFamily, bodyFamily) {
        LunarTokens(
            skin = skin,
            colors = colors,
            typography = lunarTypography(display = displayFamily, body = bodyFamily),
        )
    }

    CompositionLocalProvider(
        LocalLunarTokens provides tokens,
        LocalLunarThemePalette provides colors.toLegacyPalette(),
    ) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(),
            typography = tokens.typography.toMaterialTypography(),
            shapes = tokens.shapes.toMaterialShapes(),
            content = content,
        )
    }
}

/** A deeper accent for gradient ends; still legible under [LunarColors.onAccent]. */
private val LunarColors.accentDeep: Color
    get() = lerp(accent, if (isDark) ink else strong, 0.25f)

internal fun LunarColors.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = selection,
        onPrimaryContainer = ink,
        inversePrimary = accent,
        secondary = ink2,
        onSecondary = bg,
        secondaryContainer = selection,
        onSecondaryContainer = ink,
        tertiary = accentDeep,
        onTertiary = onAccent,
        tertiaryContainer = faint,
        onTertiaryContainer = ink,
        background = bg,
        onBackground = ink,
        surface = panel,
        onSurface = ink,
        surfaceVariant = faint,
        onSurfaceVariant = ink2,
        surfaceTint = Color.Transparent,
        inverseSurface = ink,
        inverseOnSurface = bg,
        error = danger,
        onError = onDanger,
        errorContainer = lerp(panel, danger, 0.18f),
        onErrorContainer = ink,
        outline = strong,
        outlineVariant = rule,
        scrim = scrim,
        surfaceBright = field,
        surfaceDim = bg,
        surfaceContainerLowest = field,
        surfaceContainerLow = panel,
        surfaceContainer = panel,
        surfaceContainerHigh = faint,
        surfaceContainerHighest = faint,
    )
}

private fun LunarColors.toLegacyPalette(): LunarThemePalette = LunarThemePalette(
    headerGradientStart = accent,
    headerGradientEnd = accentDeep,
    headerForeground = onAccent,
    accentGradientStart = accent,
    accentGradientEnd = accentDeep,
    viewerBackdrop = if (isDark) Color(0xFF0E1014) else Color(0xFF3A352E),
    viewerOverlay = (if (isDark) panel else Color(0xFF2A2620)).copy(alpha = 0.96f),
    viewerScrim = Color(0xFF000000).copy(alpha = 0.88f),
    viewerForeground = if (isDark) ink else Color(0xFFF7F3EA),
)

private fun LunarTypography.toMaterialTypography(): Typography = Typography(
    displayLarge = display.copy(fontSize = display.fontSize * 1.3f, lineHeight = display.lineHeight * 1.3f),
    displayMedium = display,
    displaySmall = title,
    headlineLarge = title,
    headlineMedium = title.copy(fontSize = heading.fontSize * 1.25f, lineHeight = heading.lineHeight * 1.2f),
    headlineSmall = heading,
    titleLarge = heading,
    titleMedium = heading.copy(fontSize = body.fontSize * 1.1f, lineHeight = body.lineHeight),
    titleSmall = button,
    bodyLarge = body,
    bodyMedium = bodySmall.copy(fontSize = bodySmall.fontSize * 1.05f),
    bodySmall = bodySmall.copy(fontSize = bodySmall.fontSize * 0.9f, lineHeight = bodySmall.lineHeight * 0.9f),
    labelLarge = button,
    labelMedium = button.copy(fontSize = button.fontSize * 0.9f),
    labelSmall = label,
)

private fun LunarShapes.toMaterialShapes(): Shapes = Shapes(
    extraSmall = extraSmall,
    small = small,
    medium = medium,
    large = large,
    extraLarge = extraLarge,
)
