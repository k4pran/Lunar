package com.ryanjames.lunar.ui.design

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryanjames.lunar.settings.AppSkin

/**
 * Colour roles for the Engraved design language. Screens should reach for these roles rather than
 * Material colour names so both skins stay consistent.
 */
@Immutable
data class LunarColors(
    val bg: Color,
    val panel: Color,
    val field: Color,
    val ink: Color,
    val ink2: Color,
    val muted: Color,
    val rule: Color,
    val strong: Color,
    val accent: Color,
    val onAccent: Color,
    val selection: Color,
    val danger: Color,
    val onDanger: Color,
    val sheet: Color,
    val sheetEdge: Color,
    val leader: Color,
    val faint: Color,
    val scrim: Color,
    val isDark: Boolean,
)

@Immutable
data class LunarTypography(
    val display: TextStyle,
    val title: TextStyle,
    val heading: TextStyle,
    val subtitle: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    val label: TextStyle,
    val button: TextStyle,
)

@Immutable
data class LunarSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp,
)

/** Near-square corners: the Engraved direction avoids pill shapes. */
@Immutable
data class LunarShapes(
    val extraSmall: CornerBasedShape = RoundedCornerShape(2.dp),
    val small: CornerBasedShape = RoundedCornerShape(3.dp),
    val medium: CornerBasedShape = RoundedCornerShape(4.dp),
    val large: CornerBasedShape = RoundedCornerShape(6.dp),
    val extraLarge: CornerBasedShape = RoundedCornerShape(8.dp),
)

/** Durations, in milliseconds, shared by every animated surface so motion feels of a piece. */
@Immutable
data class LunarMotion(
    /** Popovers, sheets and focus feedback. */
    val quickMillis: Int = 120,
    /** Viewer chrome and other overlays that fade over content. */
    val standardMillis: Int = 200,
    /** Layout changes such as the sidebar opening or closing. */
    val layoutMillis: Int = 240,
)

/** Keyboard focus indication. The ring is drawn in [LunarColors.accent]. */
@Immutable
data class LunarFocus(
    val ringWidth: Dp = 2.dp,
    /** Smallest hit area for anything tappable, per the platform accessibility guidelines. */
    val minTouchTarget: Dp = 44.dp,
)

@Immutable
data class LunarTokens(
    val skin: AppSkin,
    val colors: LunarColors,
    val typography: LunarTypography,
    val spacing: LunarSpacing = LunarSpacing(),
    val shapes: LunarShapes = LunarShapes(),
    val motion: LunarMotion = LunarMotion(),
    val focus: LunarFocus = LunarFocus(),
)

internal val LocalLunarTokens = staticCompositionLocalOf<LunarTokens> {
    error("LunarTokens were not provided. Wrap content in LunarTheme.")
}

@Composable
@ReadOnlyComposable
fun lunarTokens(): LunarTokens = LocalLunarTokens.current

private val PaperColors = LunarColors(
    bg = Color(0xFFF3EEE3),
    panel = Color(0xFFFAF7F0),
    field = Color(0xFFFFFDF8),
    ink = Color(0xFF1F1B16),
    ink2 = Color(0xFF433D35),
    muted = Color(0xFF6A6256),
    rule = Color(0xFFD8CFBE),
    strong = Color(0xFF2B261F),
    accent = Color(0xFF8C2F22),
    onAccent = Color(0xFFFFFFFF),
    selection = Color(0xFFE6DAC2),
    danger = Color(0xFFA12A1C),
    onDanger = Color(0xFFFFFFFF),
    sheet = Color(0xFFFFFFFF),
    sheetEdge = Color(0xFFD2C8B4),
    leader = Color(0xFFB3A893),
    faint = Color(0xFFEAE3D4),
    scrim = Color(0xFF1F1B16).copy(alpha = 0.45f),
    isDark = false,
)

private val MoonlightColors = LunarColors(
    bg = Color(0xFF14171C),
    panel = Color(0xFF1B1F26),
    field = Color(0xFF232830),
    ink = Color(0xFFECE7DC),
    ink2 = Color(0xFFC8C2B5),
    muted = Color(0xFF9D978B),
    rule = Color(0xFF2F343D),
    strong = Color(0xFF59606B),
    accent = Color(0xFFD2B87F),
    onAccent = Color(0xFF15171B),
    selection = Color(0xFF2C3240),
    danger = Color(0xFFE58A7B),
    onDanger = Color(0xFF15171B),
    sheet = Color(0xFFEDE8DC),
    sheetEdge = Color(0xFF3A404A),
    leader = Color(0xFF4F5560),
    faint = Color(0xFF1F232A),
    scrim = Color(0xFF000000).copy(alpha = 0.6f),
    isDark = true,
)

/** Pure colour lookup so palettes can be checked without a composition. */
fun lunarColors(skin: AppSkin): LunarColors = when (skin) {
    AppSkin.PAPER -> PaperColors
    AppSkin.MOONLIGHT -> MoonlightColors
}

/**
 * Builds the type scale: Cormorant Garamond for display sizes, Newsreader for body copy, and
 * letter-spaced labels that [SmallCapsLabel] renders in capitals.
 */
fun lunarTypography(
    display: FontFamily,
    body: FontFamily,
): LunarTypography = LunarTypography(
    display = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
    ),
    title = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
    ),
    heading = TextStyle(
        fontFamily = display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    subtitle = TextStyle(
        fontFamily = body,
        fontStyle = FontStyle.Italic,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    body = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    label = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.4.sp,
    ),
    button = TextStyle(
        fontFamily = body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.3.sp,
    ),
)
