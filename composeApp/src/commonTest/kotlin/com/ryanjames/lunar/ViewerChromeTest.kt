package com.ryanjames.lunar

import com.ryanjames.lunar.settings.AppSettings
import com.ryanjames.lunar.settings.AppSettingsJson
import com.ryanjames.lunar.settings.DefaultViewerChromeAutoHideSeconds
import com.ryanjames.lunar.settings.ViewerKeybindings
import com.ryanjames.lunar.settings.ViewerShortcutAction
import com.ryanjames.lunar.ui.tooltipFor
import com.ryanjames.lunar.ui.viewerPageLabel
import com.ryanjames.lunar.ui.viewerVisiblePages
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ViewerChromeTest {

    @Test
    fun viewerChromeAutoHideDefaultsToThreeSeconds() {
        val settings = AppSettings()

        assertEquals(DefaultViewerChromeAutoHideSeconds, settings.viewerChromeAutoHideSeconds)
        assertEquals(3_000L, settings.viewerChromeAutoHideMillis)
    }

    @Test
    fun viewerChromeAutoHideNullMeansNeverAndOutOfRangeValuesAreClamped() {
        assertNull(AppSettings(viewerChromeAutoHideSeconds = null).viewerChromeAutoHideMillis)
        assertEquals(1_000L, AppSettings(viewerChromeAutoHideSeconds = 0).viewerChromeAutoHideMillis)
        assertEquals(60_000L, AppSettings(viewerChromeAutoHideSeconds = 600).viewerChromeAutoHideMillis)
    }

    @Test
    fun viewerChromeAutoHideRoundTripsThroughSettingsStore() = runBlocking {
        val runtime = createTestPlatformRuntime()
        val appState = createTestLunarAppState(scope = this, runtime = runtime)

        appState.updateViewerChromeAutoHideSeconds(10)
        yield()
        assertEquals(10, runtime.settingsStore.settings.value.viewerChromeAutoHideSeconds)

        appState.updateViewerChromeAutoHideSeconds(null)
        yield()
        assertNull(runtime.settingsStore.settings.value.viewerChromeAutoHideSeconds)
        assertNull(runtime.settingsStore.settings.value.viewerChromeAutoHideMillis)

        runtime.settingsStore.reset()
        assertEquals(3, runtime.settingsStore.settings.value.viewerChromeAutoHideSeconds)
    }

    @Test
    fun viewerChromeAutoHideKeepsExplicitNullInJsonAndDefaultsWhenMissing() {
        val never = AppSettings(viewerChromeAutoHideSeconds = null)
        val encoded = AppSettingsJson.encodeToString(AppSettings.serializer(), never)

        assertEquals(never, AppSettingsJson.decodeFromString(AppSettings.serializer(), encoded))

        val legacy = AppSettingsJson.decodeFromString(
            AppSettings.serializer(),
            """{ "defaultViewerPageMode": "TWO_PAGE" }""",
        )
        assertEquals(DefaultViewerChromeAutoHideSeconds, legacy.viewerChromeAutoHideSeconds)
    }

    @Test
    fun viewerPageLabelDescribesSinglePagesAndSpreads() {
        assertEquals("Page 1 of 6", viewerPageLabel(currentPage = 0, pageCount = 6, twoPageMode = false))
        assertEquals("Pages 3 and 4 of 6", viewerPageLabel(currentPage = 2, pageCount = 6, twoPageMode = true))
        assertEquals("Page 5 of 5", viewerPageLabel(currentPage = 4, pageCount = 5, twoPageMode = true))
        assertEquals("Page 2", viewerPageLabel(currentPage = 1, pageCount = null, twoPageMode = false))
    }

    @Test
    fun viewerVisiblePagesClipsSpreadsToTheDocument() {
        assertEquals(2..3, viewerVisiblePages(currentPage = 2, pageCount = 6, twoPageMode = true))
        assertEquals(5..5, viewerVisiblePages(currentPage = 5, pageCount = 6, twoPageMode = true))
        assertEquals(0..0, viewerVisiblePages(currentPage = 0, pageCount = 1, twoPageMode = true))
        assertEquals(3..3, viewerVisiblePages(currentPage = 3, pageCount = 6, twoPageMode = false))
    }

    @Test
    fun viewerTooltipsNameTheBoundKey() {
        val keybindings = ViewerKeybindings(zoomIn = "I", openRandomScore = null)

        assertEquals("Zoom in (I)", keybindings.tooltipFor(ViewerShortcutAction.ZOOM_IN))
        assertEquals("Zoom out (Down Arrow)", keybindings.tooltipFor(ViewerShortcutAction.ZOOM_OUT))
        assertEquals("Open random score", keybindings.tooltipFor(ViewerShortcutAction.OPEN_RANDOM_SCORE))
        assertEquals(
            "Mark as favorite (F)",
            keybindings.tooltipFor(ViewerShortcutAction.TOGGLE_FAVORITE, label = "Mark as favorite"),
        )
    }
}
