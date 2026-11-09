package com.ryanjames.lunar

import com.ryanjames.lunar.library.data.LibrarySnapshot
import com.ryanjames.lunar.settings.AppSettings
import com.ryanjames.lunar.settings.AppSkin
import com.ryanjames.lunar.settings.ViewerKeybindings
import com.ryanjames.lunar.settings.ViewerPageModePreference
import com.ryanjames.lunar.ui.AppSection
import com.ryanjames.lunar.ui.QuickFindAction
import com.ryanjames.lunar.ui.QuickFindEntry
import com.ryanjames.lunar.ui.QuickFindGroup
import com.ryanjames.lunar.ui.ViewerTarget
import com.ryanjames.lunar.ui.availableQuickFindActions
import com.ryanjames.lunar.ui.buildQuickFindEntries
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuickFindStateTest {
    @Test
    fun entriesAreGroupedAsScoresSetlistsSongbooksThenActions() {
        val snapshot = LibrarySnapshot(
            items = listOf(
                testSheetMusicItem(id = "moon_river", title = "Moon River"),
                testSheetMusicItem(id = "hidden_moon", title = "Moon Hidden", isHidden = true),
                testSheetMusicItem(id = "blue_bossa", title = "Blue Bossa"),
            ),
            setlists = listOf(testLibrarySetlist(id = "moon_gig", name = "Moon gig", itemIds = listOf("moon_river"))),
            songbooks = listOf(testLibrarySongbook(id = "moon_book", name = "Moonbook", itemIds = emptyList())),
        )

        val entries = buildQuickFindEntries(
            snapshot = snapshot,
            query = "moon",
            actions = availableQuickFindActions(hasFocusedScore = false),
            keybindings = ViewerKeybindings(),
        )

        assertEquals(
            listOf("score_moon_river", "setlist_moon_gig", "songbook_moon_book"),
            entries.map(QuickFindEntry::key),
        )
        assertEquals("1 score", entries[1].detail)
    }

    @Test
    fun actionsShowTheirCurrentKeybinding() {
        val entries = buildQuickFindEntries(
            snapshot = LibrarySnapshot(),
            query = "",
            actions = availableQuickFindActions(hasFocusedScore = true),
            keybindings = ViewerKeybindings(togglePageViewMode = "T", openRandomScore = "R"),
        )

        val actions = entries.filterIsInstance<QuickFindEntry.Action>()
        assertTrue(entries.all { it.group == QuickFindGroup.ACTIONS })
        assertEquals(QuickFindAction.entries, actions.map(QuickFindEntry.Action::action))
        assertEquals("T", actions.single { it.action == QuickFindAction.TOGGLE_TWO_PAGE_VIEW }.detail)
        assertEquals("R", actions.single { it.action == QuickFindAction.OPEN_RANDOM_SCORE }.detail)
        assertEquals(null, actions.single { it.action == QuickFindAction.SWITCH_SKIN }.detail)
    }

    @Test
    fun addToSetlistActionNeedsAFocusedScore() {
        assertFalse(QuickFindAction.ADD_FOCUSED_SCORE_TO_SETLIST in availableQuickFindActions(hasFocusedScore = false))
        assertTrue(QuickFindAction.ADD_FOCUSED_SCORE_TO_SETLIST in availableQuickFindActions(hasFocusedScore = true))
    }

    @Test
    fun openingAScoreClosesThePaletteAndKeepsFullscreen() = runBlocking {
        val first = testSheetMusicItem(id = "moon_river", title = "Moon River")
        val second = testSheetMusicItem(id = "blue_bossa", title = "Blue Bossa")
        val appState = createTestLunarAppState(this, createTestPlatformRuntime(initialItems = listOf(first, second)))

        appState.openQuickFind()
        appState.openScoreFromQuickFind(first)
        assertFalse(appState.quickFindVisible)
        assertEquals(ViewerTarget.Score(first.id), appState.fullscreenTarget)

        appState.openQuickFind()
        appState.openScoreFromQuickFind(second)
        assertEquals(ViewerTarget.Score(second.id), appState.fullscreenTarget)
        assertEquals(second.id, appState.focusedScoreId)

        appState.closeFullscreen()
        assertEquals(null, appState.fullscreenTarget)
    }

    @Test
    fun openingASongbookFromTheViewerReplacesTheOpenScore() = runBlocking {
        val item = testSheetMusicItem(id = "moon_river", title = "Moon River")
        val appState = createTestLunarAppState(this, createTestPlatformRuntime(initialItems = listOf(item)))
        appState.openScore(item)
        appState.openQuickFind()

        appState.openSongbookFromQuickFind("book")

        assertEquals(ViewerTarget.Songbook("book"), appState.fullscreenTarget)
        assertEquals(null, appState.viewerStartPage)
        appState.closeFullscreen()
        assertEquals(null, appState.fullscreenTarget)
    }

    @Test
    fun openingASetlistClosesTheViewerAndShowsItInSetlists() = runBlocking {
        val item = testSheetMusicItem(id = "moon_river", title = "Moon River")
        val appState = createTestLunarAppState(this, createTestPlatformRuntime(initialItems = listOf(item)))
        appState.openScore(item)
        appState.selectSection(AppSection.SETTINGS)
        appState.openQuickFind()

        appState.openSetlistFromQuickFind("gig")

        assertFalse(appState.quickFindVisible)
        assertEquals(null, appState.fullscreenTarget)
        assertEquals(AppSection.SETLISTS, appState.selectedSection)
        assertEquals("gig", appState.selectedSetlistId)
    }

    @Test
    fun openingASongbookOpensItInTheViewer() = runBlocking {
        val appState = createTestLunarAppState(this)
        appState.openQuickFind()

        appState.openSongbookFromQuickFind("book")

        assertFalse(appState.quickFindVisible)
        assertEquals(ViewerTarget.Songbook("book"), appState.fullscreenTarget)
    }

    @Test
    fun actionsRunAndRoundTripSettingsThroughTheStore() = runBlocking {
        val item = testSheetMusicItem(id = "moon_river", title = "Moon River")
        val runtime = createTestPlatformRuntime(
            initialItems = listOf(item),
            initialSettings = AppSettings(skin = AppSkin.PAPER),
        )
        val appState = createTestLunarAppState(this, runtime)

        appState.runQuickFindAction(QuickFindAction.SWITCH_SKIN, emptyList())
        appState.runQuickFindAction(QuickFindAction.TOGGLE_TWO_PAGE_VIEW, emptyList())
        yield()
        assertEquals(AppSkin.MOONLIGHT, runtime.settingsStore.settings.value.skin)
        assertEquals(ViewerPageModePreference.TWO_PAGE, runtime.settingsStore.settings.value.defaultViewerPageMode)

        appState.runQuickFindAction(QuickFindAction.START_METRONOME, emptyList())
        assertTrue(appState.metronomeVisible)
        appState.dismissMetronome()
        assertFalse(appState.metronomeVisible)

        appState.runQuickFindAction(QuickFindAction.OPEN_RANDOM_SCORE, listOf(item))
        assertEquals(ViewerTarget.Score(item.id), appState.fullscreenTarget)
        assertEquals(item.id, appState.quickFindTargetScoreId)

        appState.runQuickFindAction(QuickFindAction.ADD_FOCUSED_SCORE_TO_SETLIST, emptyList())
        assertTrue(appState.setlistPickerVisible)
        assertEquals(setOf(item.id), appState.pickerTargetIds)
        assertEquals(item.id, appState.focusedScoreId)
    }
}
