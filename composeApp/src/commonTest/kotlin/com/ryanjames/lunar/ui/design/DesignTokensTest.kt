package com.ryanjames.lunar.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ryanjames.lunar.settings.AppSkin
import com.ryanjames.lunar.ui.toColorScheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesignTokensTest {

    @Test
    fun contrastRatioMatchesWcagReferenceValues() {
        assertClose(21.0, contrastRatio(Color.Black, Color.White))
        assertClose(1.0, contrastRatio(Color.White, Color.White))
        // #767676 on white is the classic "just passes AA" grey.
        assertClose(4.54, contrastRatio(Color(0xFF767676), Color.White), tolerance = 0.01)
        assertClose(contrastRatio(Color.White, Color.Black), contrastRatio(Color.Black, Color.White))
    }

    @Test
    fun textRolesMeetWcagAaOnBackgroundsInEverySkin() {
        AppSkin.entries.forEach { skin ->
            val colors = lunarColors(skin)
            val foregrounds = mapOf("ink" to colors.ink, "ink2" to colors.ink2, "muted" to colors.muted)
            val backgrounds = mapOf("bg" to colors.bg, "panel" to colors.panel)
            foregrounds.forEach { (fgName, fg) ->
                backgrounds.forEach { (bgName, bg) ->
                    val ratio = contrastRatio(fg, bg)
                    assertTrue(ratio >= 4.5, "$skin: $fgName on $bgName is $ratio:1, below 4.5:1")
                }
            }
            assertTrue(contrastRatio(colors.onAccent, colors.accent) >= 4.5, "$skin: onAccent on accent")
            assertTrue(contrastRatio(colors.onDanger, colors.danger) >= 4.5, "$skin: onDanger on danger")
        }
    }

    @Test
    fun skinsMapOntoMaterialColorScheme() {
        AppSkin.entries.forEach { skin ->
            val colors = lunarColors(skin)
            val scheme = colors.toColorScheme()
            assertEquals(colors.bg, scheme.background)
            assertEquals(colors.panel, scheme.surface)
            assertEquals(colors.ink, scheme.onSurface)
            assertEquals(colors.accent, scheme.primary)
            assertEquals(colors.onAccent, scheme.onPrimary)
            assertEquals(colors.danger, scheme.error)
            assertEquals(colors.rule, scheme.outlineVariant)
            assertTrue(contrastRatio(scheme.onTertiary, scheme.tertiary) >= 4.5, "$skin: onTertiary on tertiary")
        }
        assertEquals(false, lunarColors(AppSkin.PAPER).isDark)
        assertEquals(true, lunarColors(AppSkin.MOONLIGHT).isDark)
    }

    @Test
    fun collectionColorSlotIsStableAndNormalizesCase() {
        val names = listOf("Jazz standards", "Bach", "Recital 2026", "", "Chopin etudes")
        names.forEach { name ->
            val slot = collectionColorSlot(name)
            assertEquals(slot, collectionColorSlot(name))
            assertTrue(slot in 0 until CollectionColorSlotCount)
        }
        assertEquals(collectionColorSlot("Jazz"), collectionColorSlot("  jazz "))
    }

    @Test
    fun collectionColorSlotSpreadsDistinctNamesAcrossSlots() {
        val names = (1..64).map { "Collection $it" }
        val usage = names.groupingBy { collectionColorSlot(it) }.eachCount()

        assertEquals(CollectionColorSlotCount, usage.size, "Every slot should be used: $usage")
        assertTrue(usage.values.max() <= 16, "No slot should hoard names: $usage")
    }

    @Test
    fun collectionPalettesCoverEverySlot() {
        AppSkin.entries.forEach { skin ->
            val palette = collectionPalette(skin)
            assertEquals(CollectionColorSlotCount, palette.size)
            assertEquals(CollectionColorSlotCount, palette.toSet().size)
            assertEquals(palette[collectionColorSlot("Bach")], collectionColor(skin, "Bach"))
        }
    }

    @Test
    fun leaderRowKeepsTrailingContentAndMinimumLeader() {
        // Plenty of room: everything gets its natural width and the leader takes the rest.
        assertEquals(
            LeaderRowWidths(title = 100, leader = 164, trailing = 40),
            allocateLeaderRow(totalWidth = 320, titleWidth = 100, trailingWidth = 40, leaderMinWidth = 24, gap = 8),
        )
        // Long title: it is truncated so the leader keeps its minimum and trailing stays whole.
        assertEquals(
            LeaderRowWidths(title = 240, leader = 24, trailing = 40),
            allocateLeaderRow(totalWidth = 320, titleWidth = 900, trailingWidth = 40, leaderMinWidth = 24, gap = 8),
        )
        // Pathologically narrow: nothing goes negative.
        val tiny = allocateLeaderRow(totalWidth = 10, titleWidth = 50, trailingWidth = 50, leaderMinWidth = 24, gap = 8)
        assertTrue(tiny.title >= 0 && tiny.leader >= 0 && tiny.trailing >= 0)
    }

    @Test
    fun popoverOpensBelowAnchorAndFlipsWhenOutOfRoom() {
        val provider = AnchoredBelowPositionProvider(gap = 4)
        val window = IntSize(800, 600)
        val popup = IntSize(200, 150)

        assertEquals(
            IntOffset(100, 144),
            provider.calculatePosition(IntRect(100, 100, 180, 140), window, LayoutDirection.Ltr, popup),
        )
        assertEquals(
            IntOffset(100, 396),
            provider.calculatePosition(IntRect(100, 550, 180, 590), window, LayoutDirection.Ltr, popup),
        )
        // Clamped horizontally so it never spills off the right edge.
        assertEquals(
            600,
            provider.calculatePosition(IntRect(760, 100, 790, 140), window, LayoutDirection.Ltr, popup).x,
        )
    }

    @Test
    fun focusRingIsVisibleAgainstEverySurface() {
        AppSkin.entries.forEach { skin ->
            val colors = lunarColors(skin)
            listOf("bg" to colors.bg, "panel" to colors.panel, "field" to colors.field).forEach { (name, surface) ->
                val ratio = contrastRatio(colors.accent, surface)
                // WCAG 1.4.11: focus indicators need 3:1 against what surrounds them.
                assertTrue(ratio >= 3.0, "$skin: focus ring on $name is $ratio:1, below 3:1")
            }
        }
        val focus = LunarFocus()
        assertTrue(focus.ringWidth.value >= 2f, "Focus ring should be at least 2dp")
        assertTrue(focus.minTouchTarget.value >= 44f, "Touch targets should be at least 44dp")
    }

    @Test
    fun motionDurationsStayShortAndOrdered() {
        val motion = LunarMotion()
        assertTrue(motion.quickMillis in 1..motion.standardMillis)
        assertTrue(motion.standardMillis <= motion.layoutMillis)
        assertTrue(motion.layoutMillis <= 300, "Layout motion should not drag: ${motion.layoutMillis}ms")
    }

    @Test
    fun compactWidthStopsAtTabletBreakpoint() {
        assertTrue(isCompactWidth(360.dp))
        assertTrue(isCompactWidth(599.dp))
        assertEquals(false, isCompactWidth(600.dp))
        assertEquals(false, isCompactWidth(1280.dp))
    }

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 0.001) {
        assertTrue(abs(expected - actual) <= tolerance, "Expected $expected but was $actual")
    }
}
