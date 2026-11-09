package com.ryanjames.lunar

import com.ryanjames.lunar.library.model.LibraryGroup
import com.ryanjames.lunar.settings.AppSettings
import com.ryanjames.lunar.settings.LibraryLayoutPreference
import com.ryanjames.lunar.ui.LibraryBrowseMode
import com.ryanjames.lunar.ui.LibraryLayoutMode
import com.ryanjames.lunar.ui.collectionSwatchSlot
import com.ryanjames.lunar.ui.galleryGroupPositions
import com.ryanjames.lunar.ui.galleryOpenLabel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LibraryGalleryStateTest {

    @Test
    fun layoutChoiceRoundTripsThroughSettingsStore() = runBlocking {
        val runtime = createTestPlatformRuntime(
            initialSettings = AppSettings(defaultLibraryLayout = LibraryLayoutPreference.LIST),
        )
        val appState = createTestLunarAppState(scope = this, runtime = runtime)
        assertEquals(LibraryLayoutMode.LIST, appState.layoutMode)

        appState.updateLayoutMode(LibraryLayoutMode.GRID)
        yield()

        assertEquals(LibraryLayoutPreference.GRID, runtime.settingsStore.settings.value.defaultLibraryLayout)
        assertEquals(LibraryLayoutMode.GRID, createTestLunarAppState(scope = this, runtime = runtime).layoutMode)

        appState.updateLayoutMode(LibraryLayoutMode.LIST)
        yield()

        assertEquals(LibraryLayoutPreference.LIST, runtime.settingsStore.settings.value.defaultLibraryLayout)
        assertEquals(LibraryLayoutMode.LIST, createTestLunarAppState(scope = this, runtime = runtime).layoutMode)
    }

    @Test
    fun plainCtrlAndShiftClicksDriveFocusAndSelection() = runBlocking {
        val appState = createTestLunarAppState(scope = this)
        val ordered = listOf("a", "b", "c", "d", "e")

        appState.focusScore("b")
        assertEquals("b", appState.focusedScoreId)
        assertEquals(emptySet(), appState.selectedScoreIds)

        appState.selectScoreRange(ordered, "d")
        assertEquals(setOf("b", "c", "d"), appState.selectedScoreIds)
        assertEquals("d", appState.focusedScoreId)

        appState.toggleScoreSelectionAndFocus("c")
        assertEquals(setOf("b", "d"), appState.selectedScoreIds)
        assertEquals("c", appState.focusedScoreId)

        appState.selectScoreRange(ordered, "a")
        assertEquals(setOf("a", "b", "c", "d"), appState.selectedScoreIds)

        appState.updateBrowseMode(LibraryBrowseMode.HIDDEN)
        assertNull(appState.focusedScoreId)
        assertEquals(emptySet(), appState.selectedScoreIds)

        appState.selectScoreRange(ordered, "e")
        assertEquals(setOf("e"), appState.selectedScoreIds)
    }

    @Test
    fun focusClearsWhenFocusedScoreIsHidden() = runBlocking {
        val runtime = createTestPlatformRuntime(initialItems = listOf(testSheetMusicItem(id = "moon")))
        val appState = createTestLunarAppState(scope = this, runtime = runtime)
        appState.focusScore("moon")

        appState.hideScore("moon")
        yield()

        assertNull(appState.focusedScoreId)
    }

    @Test
    fun focusClearsWhenFocusedScoreIsDeleted() = runBlocking {
        val runtime = createTestPlatformRuntime(initialItems = listOf(testSheetMusicItem(id = "moon")))
        val appState = createTestLunarAppState(scope = this, runtime = runtime)
        appState.focusScore("moon")

        appState.requestDelete("moon")
        appState.confirmDelete()
        yield()

        assertNull(appState.focusedScoreId)
    }

    @Test
    fun galleryGroupPositionsAccountForHeaders() {
        val groups = listOf(
            LibraryGroup("A", List(2) { testSheetMusicItem(id = "a$it") }),
            LibraryGroup("B", List(3) { testSheetMusicItem(id = "b$it") }),
            LibraryGroup("C", List(1) { testSheetMusicItem(id = "c$it") }),
        )

        assertEquals(listOf(0, 3, 7), galleryGroupPositions(groups))
        assertEquals(listOf(0), galleryGroupPositions(listOf(LibraryGroup("", groups.first().items))))
    }

    @Test
    fun openLabelAndSwatchSlotAreStable() {
        val item = testSheetMusicItem(id = "moon")

        assertEquals("Open", galleryOpenLabel(item))
        assertEquals("Open at p. 4", galleryOpenLabel(item.copy(lastViewedPage = 3)))
        assertEquals(collectionSwatchSlot("Real Book", 5), collectionSwatchSlot(" real book ", 5))
    }
}
