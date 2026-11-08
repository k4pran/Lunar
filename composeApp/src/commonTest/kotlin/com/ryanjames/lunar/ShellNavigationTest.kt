package com.ryanjames.lunar

import com.ryanjames.lunar.settings.AppSettings
import com.ryanjames.lunar.settings.JsonAppSettingsStore
import com.ryanjames.lunar.sync.CloudSyncState
import com.ryanjames.lunar.ui.AppSection
import com.ryanjames.lunar.ui.LibraryArrangement
import com.ryanjames.lunar.ui.LibraryBrowseMode
import com.ryanjames.lunar.ui.libraryArrangementFor
import com.ryanjames.lunar.ui.shell.SidebarForm
import com.ryanjames.lunar.ui.shell.WindowClass
import com.ryanjames.lunar.ui.shell.collectionSwatchSlot
import com.ryanjames.lunar.ui.shell.sidebarFormFor
import com.ryanjames.lunar.ui.shell.sidebarSyncLine
import com.ryanjames.lunar.ui.shell.windowClassFor
import com.ryanjames.lunar.ui.sidebarShowsLibraryContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShellNavigationTest {

    @Test
    fun windowClassForHonoursBreakpointBoundaries() {
        assertEquals(WindowClass.COMPACT, windowClassFor(0f))
        assertEquals(WindowClass.COMPACT, windowClassFor(599.9f))
        assertEquals(WindowClass.MEDIUM, windowClassFor(600f))
        assertEquals(WindowClass.MEDIUM, windowClassFor(1023.9f))
        assertEquals(WindowClass.EXPANDED, windowClassFor(1024f))
        assertEquals(WindowClass.EXPANDED, windowClassFor(1920f))
    }

    @Test
    fun sidebarFormFollowsWindowClassAndCollapsePreference() {
        assertEquals(SidebarForm.DRAWER, sidebarFormFor(WindowClass.COMPACT, collapsed = false))
        assertEquals(SidebarForm.DRAWER, sidebarFormFor(WindowClass.COMPACT, collapsed = true))
        assertEquals(SidebarForm.RAIL, sidebarFormFor(WindowClass.MEDIUM, collapsed = false))
        assertEquals(SidebarForm.EXPANDED, sidebarFormFor(WindowClass.EXPANDED, collapsed = false))
        assertEquals(SidebarForm.RAIL, sidebarFormFor(WindowClass.EXPANDED, collapsed = true))
    }

    @Test
    fun libraryContextShowsOnLibraryOnly() {
        AppSection.entries.forEach { section ->
            assertEquals(section == AppSection.LIBRARY, sidebarShowsLibraryContext(section), section.name)
        }
    }

    @Test
    fun switchingSectionsClearsSelectionAndTransientUi() = runBlocking {
        val appState = createTestLunarAppState(this)

        appState.toggleScoreSelection("score-1")
        appState.showSetlistPicker()
        appState.showRandomSetlistBuilder()

        appState.selectSection(AppSection.SONGBOOKS)

        assertEquals(AppSection.SONGBOOKS, appState.selectedSection)
        assertTrue(appState.selectedScoreIds.isEmpty())
        assertFalse(appState.setlistPickerVisible)
        assertFalse(appState.randomSetlistBuilderVisible)
    }

    @Test
    fun reselectingCurrentSectionKeepsSelection() = runBlocking {
        val appState = createTestLunarAppState(this)

        appState.toggleScoreSelection("score-1")
        appState.selectSection(AppSection.LIBRARY)

        assertEquals(setOf("score-1"), appState.selectedScoreIds)
    }

    @Test
    fun openingSetlistFromElsewhereLandsInSetlists() = runBlocking {
        val appState = createTestLunarAppState(this)

        appState.selectSection(AppSection.COMPOSE)
        appState.openSetlist("saved-setlist")

        assertEquals(AppSection.SETLISTS, appState.selectedSection)
        assertEquals("saved-setlist", appState.selectedSetlistId)
    }

    @Test
    fun randomSetlistFromLibraryLandsInSetlists() = runBlocking {
        val appState = createTestLunarAppState(this)

        appState.createTemporaryRandomSetlist(
            items = listOf(testSheetMusicItem("one"), testSheetMusicItem("two")),
            requestedCount = 2,
        )

        assertEquals(AppSection.SETLISTS, appState.selectedSection)
        assertTrue(appState.temporarySetlistOpen)
    }

    @Test
    fun scorePickerStaysInSetlistsButLeavesNonLibrarySections() = runBlocking {
        val item = testSheetMusicItem("one")
        val runtime = createTestPlatformRuntime(initialItems = listOf(item))
        val appState = createTestLunarAppState(scope = this, runtime = runtime)

        appState.selectSection(AppSection.SETLISTS)
        appState.showSetlistPickerForScore(item.id)
        assertEquals(AppSection.SETLISTS, appState.selectedSection)
        assertTrue(appState.setlistPickerVisible)

        appState.selectSection(AppSection.SETTINGS)
        appState.showSetlistPickerForScore(item.id)
        assertEquals(AppSection.LIBRARY, appState.selectedSection)
        assertTrue(appState.setlistPickerVisible)
    }

    @Test
    fun arrangementsMapOntoBrowseModesAndFavourites() = runBlocking {
        val appState = createTestLunarAppState(this)

        appState.selectArrangement(LibraryArrangement.FAVOURITES)
        assertEquals(LibraryBrowseMode.ALL, appState.browseMode)
        assertTrue(appState.query.favoritesOnly)
        assertEquals(LibraryArrangement.FAVOURITES, appState.libraryArrangement)

        appState.selectArrangement(LibraryArrangement.COMPOSER)
        assertEquals(LibraryBrowseMode.BY_COMPOSER, appState.browseMode)
        assertFalse(appState.query.favoritesOnly)

        appState.selectArrangement(LibraryArrangement.HIDDEN)
        assertEquals(LibraryBrowseMode.HIDDEN, appState.browseMode)

        appState.selectArrangement(LibraryArrangement.ALL)
        assertEquals(LibraryArrangement.ALL, appState.libraryArrangement)

        assertEquals(LibraryArrangement.COLLECTION, libraryArrangementFor(LibraryBrowseMode.BY_COLLECTION, true))
    }

    @Test
    fun sidebarCollapsedRoundTripsThroughJsonSettingsStore() = runBlocking {
        val fileSystem = FakeFileSystem()
        val path = "/lunar/app_settings.json".toPath()
        val store = JsonAppSettingsStore(fileSystem, path)
        store.initialize()
        assertFalse(store.settings.value.sidebarCollapsed)

        store.updateSettings { it.copy(sidebarCollapsed = true) }

        val reloaded = JsonAppSettingsStore(fileSystem, path)
        reloaded.initialize()
        assertTrue(reloaded.settings.value.sidebarCollapsed)
    }

    @Test
    fun togglingSidebarPersistsOutsideCompose() = runBlocking {
        val fileSystem = FakeFileSystem()
        val path = "/lunar/app_settings.json".toPath()
        val store = JsonAppSettingsStore(fileSystem, path).also { it.initialize() }
        val runtime = createTestPlatformRuntime().copy(settingsStore = store)
        val appState = createTestLunarAppState(scope = this, runtime = runtime)

        appState.toggleSidebarCollapsed()
        awaitCondition { store.settings.value.sidebarCollapsed }

        val reloaded = JsonAppSettingsStore(fileSystem, path).also { it.initialize() }
        assertTrue(reloaded.settings.value.sidebarCollapsed)
        assertTrue(appState.isSidebarCollapsed(store.settings.value))
    }

    @Test
    fun composeAutoCollapseDoesNotPersist() = runBlocking {
        val fileSystem = FakeFileSystem()
        val path = "/lunar/app_settings.json".toPath()
        val store = JsonAppSettingsStore(fileSystem, path).also { it.initialize() }
        val runtime = createTestPlatformRuntime().copy(settingsStore = store)
        val appState = createTestLunarAppState(scope = this, runtime = runtime)
        val expanded = AppSettings(sidebarCollapsed = false)

        appState.selectSection(AppSection.COMPOSE)
        assertTrue(appState.isSidebarCollapsed(expanded))

        // Expanding inside Compose is also temporary.
        appState.toggleSidebarCollapsed()
        assertFalse(appState.isSidebarCollapsed(AppSettings(sidebarCollapsed = true)))

        appState.selectSection(AppSection.LIBRARY)
        assertNull(appState.temporarySidebarCollapsed)
        assertFalse(appState.isSidebarCollapsed(expanded))
        assertFalse(store.settings.value.sidebarCollapsed)
        assertNull(fileSystem.metadataOrNull(path), "Compose collapse must not write settings.")
    }

    @Test
    fun collectionSwatchSlotIsStableAndSpread() {
        val slot = collectionSwatchSlot("Standards", 6)
        assertEquals(slot, collectionSwatchSlot("standards ", 6))
        val names = listOf("Standards", "Bach", "Chopin", "Jazz", "Etudes", "Hymns", "Pop", "Film")
        val slots = names.map { collectionSwatchSlot(it, 6) }
        assertTrue(slots.all { it in 0 until 6 })
        assertTrue(slots.distinct().size >= 3, "Expected names to spread across slots: $slots")
    }

    @Test
    fun syncLineDescribesSyncState() {
        val now = 10L * 24 * 60 * 60 * 1000
        assertEquals("Not synced yet", sidebarSyncLine(CloudSyncState(), now))
        assertEquals("Syncing...", sidebarSyncLine(CloudSyncState(isRefreshing = true), now))
        assertEquals("Sync issue (1)", sidebarSyncLine(CloudSyncState(syncErrors = listOf("x")), now))
        assertEquals("Synced just now", sidebarSyncLine(CloudSyncState(lastRefreshEpochMillis = now - 10_000), now))
        assertEquals("Synced 5 min ago", sidebarSyncLine(CloudSyncState(lastRefreshEpochMillis = now - 5 * 60_000), now))
        assertEquals("Synced 2 h ago", sidebarSyncLine(CloudSyncState(lastRefreshEpochMillis = now - 2 * 3_600_000), now))
        assertEquals("Synced 3 d ago", sidebarSyncLine(CloudSyncState(lastRefreshEpochMillis = now - 3 * 86_400_000L), now))
    }
}

private suspend fun awaitCondition(condition: () -> Boolean) {
    repeat(200) {
        if (condition()) return
        delay(10)
    }
    error("Condition not met in time.")
}
