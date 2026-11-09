package com.ryanjames.lunar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performMouseInput
import com.ryanjames.lunar.platform.PlatformRuntime
import com.ryanjames.lunar.settings.AppColorTheme
import com.ryanjames.lunar.settings.AppSettings
import com.ryanjames.lunar.ui.LibraryBrowseMode
import com.ryanjames.lunar.ui.LibraryScreen
import com.ryanjames.lunar.ui.LunarAppState
import com.ryanjames.lunar.ui.LunarTheme
import com.ryanjames.lunar.ui.ViewerTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetlistsAndSongbooksScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun setlistProgrammeReordersAndPlaysThroughTheViewer() {
        runBlocking {
            val items = listOf(
                testSheetMusicItem(id = "moon_river", title = "Moon River", pageCount = 2),
                testSheetMusicItem(id = "clair_de_lune", title = "Clair de Lune", pageCount = 4),
                testSheetMusicItem(id = "autumn_leaves", title = "Autumn Leaves"),
            )
            val setlist = testLibrarySetlist(
                id = "set_morning",
                name = "Morning rehearsal",
                itemIds = items.map { it.id },
            )
            val runtime = createTestPlatformRuntime(
                initialItems = items,
                initialSetlists = listOf(setlist),
                initialSettings = AppSettings(refreshOnLaunch = false),
            )

            rule.setContent {
                LunarApp(runtime)
            }
            rule.waitUntil(timeoutMillis = 5_000) {
                rule.onAllNodesWithText("Setlists").fetchSemanticsNodes().isNotEmpty()
            }

            rule.onNodeWithText("Setlists").performClick()
            rule.onNodeWithText("Morning rehearsal").performClick()
            rule.onNodeWithText("Start the set").assertIsDisplayed()
            assertTrue(
                rule.onAllNodesWithText("3 pieces \u00B7 6+ pages").fetchSemanticsNodes().isNotEmpty()
            )

            // Drag Moon River down by its handle (the row merges semantics, so look in the unmerged tree).
            rule.onNodeWithContentDescription("Reorder Moon River", useUnmergedTree = true).performMouseInput {
                moveTo(center)
                press()
                repeat(20) { moveBy(Offset(0f, 12f)) }
                release()
            }
            rule.waitUntil(timeoutMillis = 5_000) {
                runtime.repository.getSetlist(setlist.id)?.itemIds?.first() != "moon_river"
            }
            val reordered = runtime.repository.getSetlist(setlist.id)!!.itemIds
            assertEquals(items.map { it.id }.toSet(), reordered.toSet())
            assertEquals(3, reordered.size)
            val firstTitle = items.first { it.id == reordered[0] }.title
            val secondTitle = items.first { it.id == reordered[1] }.title

            rule.onNodeWithText("Start the set").performClick()
            rule.waitUntil(timeoutMillis = 5_000) {
                rule.onAllNodesWithText("Piece 1 of 3", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(rule.onAllNodesWithText(firstTitle).fetchSemanticsNodes().isNotEmpty())

            rule.onNodeWithText("Next in setlist").performClick()
            rule.waitUntil(timeoutMillis = 5_000) {
                rule.onAllNodesWithText("Piece 2 of 3", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(rule.onAllNodesWithText(secondTitle).fetchSemanticsNodes().isNotEmpty())

            rule.onNodeWithText("Back to programme").performClick()
            rule.waitUntil(timeoutMillis = 5_000) {
                rule.onAllNodesWithText("Next in setlist").fetchSemanticsNodes().isEmpty()
            }
            rule.onNodeWithText("Start the set").assertIsDisplayed()
        }
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun consecutiveProgrammeDragsPersistAgainstTheCurrentOrder() {
        runBlocking {
            val items = listOf(
                testSheetMusicItem(id = "moon_river", title = "Moon River"),
                testSheetMusicItem(id = "clair_de_lune", title = "Clair de Lune"),
                testSheetMusicItem(id = "autumn_leaves", title = "Autumn Leaves"),
            )
            val originalOrder = items.map { it.id }
            val setlist = testLibrarySetlist(id = "set_drag", name = "Drag rehearsal", itemIds = originalOrder)
            val runtime = createTestPlatformRuntime(initialItems = items, initialSetlists = listOf(setlist))
            val appState = createTestLunarAppState(scope = backgroundScope(), runtime = runtime)
            appState.updateBrowseMode(LibraryBrowseMode.SETLISTS)
            appState.openSetlist(setlist.id)
            rule.setLibraryContent(runtime, appState)

            fun dragMoonRiver(stepPx: Float) {
                rule.onNodeWithContentDescription("Reorder Moon River", useUnmergedTree = true).performMouseInput {
                    moveTo(center)
                    press()
                    repeat(20) { moveBy(Offset(0f, stepPx)) }
                    release()
                }
            }

            dragMoonRiver(12f)
            rule.waitUntil(timeoutMillis = 5_000) {
                runtime.repository.getSetlist(setlist.id)?.itemIds?.first() != "moon_river"
            }
            rule.waitForIdle()

            // Dragging back to the original order must still persist: the handler has to see
            // the reordered programme, not the order it was first composed with.
            dragMoonRiver(-12f)
            rule.waitUntil(timeoutMillis = 5_000) {
                runtime.repository.getSetlist(setlist.id)?.itemIds == originalOrder
            }
        }
    }

    @Test
    fun programmeRowsShowPageCountsResolvedByTheViewer() {
        runBlocking {
            val items = listOf(
                testSheetMusicItem(id = "moon_river", title = "Moon River", pageCount = 2),
                testSheetMusicItem(id = "autumn_leaves", title = "Autumn Leaves"),
            )
            val setlist = testLibrarySetlist(id = "set_pages", name = "Page rehearsal", itemIds = items.map { it.id })
            val runtime = createTestPlatformRuntime(initialItems = items, initialSetlists = listOf(setlist))
            val appState = createTestLunarAppState(scope = backgroundScope(), runtime = runtime)
            appState.updateBrowseMode(LibraryBrowseMode.SETLISTS)
            appState.openSetlist(setlist.id)
            rule.setLibraryContent(runtime, appState)
            rule.onNodeWithText("Start the set").assertIsDisplayed()
            assertEquals(0, rule.onAllNodesWithText("7 pp.").fetchSemanticsNodes().size)

            // Play Autumn Leaves, let the viewer report its page count, then return to the programme.
            rule.runOnIdle { appState.startSetlist(setlist.id, index = 1) }
            rule.runOnIdle { appState.updateViewerPageCount("autumn_leaves", 7) }
            rule.waitUntil(timeoutMillis = 5_000) {
                runtime.repository.getItem("autumn_leaves")?.pageCount == 7
            }
            rule.runOnIdle { appState.closePreview() }

            rule.waitUntil(timeoutMillis = 5_000) {
                rule.onAllNodesWithText("7 pp.", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(rule.onAllNodesWithText("2 pieces · 9 pages").fetchSemanticsNodes().isNotEmpty())
        }
    }

    @Test
    fun programmeActionsWrapOnPhoneWidth() {
        runBlocking {
            val items = listOf(testSheetMusicItem(id = "moon_river", title = "Moon River"))
            val setlist = testLibrarySetlist(
                id = "set_narrow",
                name = "Narrow rehearsal",
                itemIds = items.map { it.id },
            )
            val runtime = createTestPlatformRuntime(initialItems = items, initialSetlists = listOf(setlist))
            val appState = createTestLunarAppState(scope = backgroundScope(), runtime = runtime)
            appState.updateBrowseMode(LibraryBrowseMode.SETLISTS)
            appState.openSetlist(setlist.id)
            val paneWidth = 320.dp
            rule.setLibraryContent(runtime, appState, Modifier.width(paneWidth))

            val paneRight = rule.onRoot().getUnclippedBoundsInRoot().right
            listOf("Start the set", "Add scores", "Rename", "Delete").forEach { label ->
                val bounds = rule.onNodeWithText(label).assertIsDisplayed().getUnclippedBoundsInRoot()
                assertTrue(bounds.right <= paneRight, "$label overflows the pane")
                assertTrue(bounds.width >= 40.dp, "$label is squeezed to ${bounds.width}")
            }

            rule.onNodeWithText("Delete").performClick()
            rule.onNodeWithText("Delete setlist?").assertIsDisplayed()
        }
    }

    @Test
    fun randomMixCanBeSavedOrDiscardedAndNewSetlistsComeFromAPopover() {
        runBlocking {
            val items = listOf(
                testSheetMusicItem(id = "moon_river", title = "Moon River"),
                testSheetMusicItem(id = "clair_de_lune", title = "Clair de Lune"),
            )
            val runtime = createTestPlatformRuntime(initialItems = items)
            val appState = createTestLunarAppState(scope = backgroundScope(), runtime = runtime)
            appState.updateBrowseMode(LibraryBrowseMode.SETLISTS)
            rule.setLibraryContent(runtime, appState)

            rule.onNodeWithText("Random setlist").performClick()
            rule.onNodeWithText("Shuffle").performClick()
            rule.waitUntil(timeoutMillis = 5_000) { appState.temporarySetlist != null }
            rule.onAllNodesWithText("Random Mix").onFirst().assertIsDisplayed()

            rule.onAllNodesWithText("Save").onFirst().performClick()
            rule.onNodeWithText("Save temporary setlist").assertIsDisplayed()
            rule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextReplacement("Kept mix")
            rule.onNode(hasText("Save") and hasAnyAncestor(isDialog())).performClick()
            rule.waitUntil(timeoutMillis = 5_000) {
                runtime.repository.library.value.setlists.any { it.name == "Kept mix" }
            }
            rule.runOnIdle {
                assertNull(appState.temporarySetlist)
                assertEquals(
                    runtime.repository.library.value.setlists.single().id,
                    appState.selectedSetlistId,
                )
            }

            rule.onNodeWithText("Random setlist").performClick()
            rule.onNodeWithText("Shuffle").performClick()
            rule.waitUntil(timeoutMillis = 5_000) { appState.temporarySetlist != null }
            rule.onAllNodesWithText("Discard").onFirst().performClick()
            rule.waitUntil(timeoutMillis = 5_000) { appState.temporarySetlist == null }
            rule.onNodeWithText("Shuffle a few scores into a one-off set. Save it if it works.").assertIsDisplayed()

            rule.onNodeWithText("New setlist").performClick()
            rule.onNode(hasSetTextAction() and hasAnyAncestor(isPopup())).performTextReplacement("  Encore  ")
            rule.onNodeWithText("Create").performClick()
            rule.waitUntil(timeoutMillis = 5_000) {
                runtime.repository.library.value.setlists.any { it.name == "Encore" }
            }
            rule.onAllNodesWithText("This programme is empty. Use Add scores to build it.").onFirst()
                .assertIsDisplayed()

            rule.onNodeWithText("Add scores").performClick()
            rule.onNodeWithContentDescription("Choose Clair de Lune").performClick()
            rule.onNodeWithText("Add 1 score").performClick()
            rule.waitUntil(timeoutMillis = 5_000) {
                runtime.repository.library.value.setlists.first { it.name == "Encore" }.itemIds == listOf("clair_de_lune")
            }

            rule.onNodeWithText("Rename").performClick()
            rule.onNode(hasSetTextAction()).performTextReplacement("Finale")
            rule.onNodeWithText("Save name").performClick()
            rule.waitUntil(timeoutMillis = 5_000) {
                runtime.repository.library.value.setlists.any { it.name == "Finale" }
            }

            rule.onNodeWithText("Delete").performClick()
            rule.onNodeWithText("Delete setlist?").assertIsDisplayed()
        }
    }

    @Test
    fun songbookDetailShowsContentsWithStartPages() {
        runBlocking {
            val items = listOf(
                testSheetMusicItem(id = "autumn_leaves", title = "Autumn Leaves", pageCount = 3),
                testSheetMusicItem(id = "all_the_things", title = "All The Things You Are"),
                testSheetMusicItem(id = "blue_bossa", title = "Blue Bossa", pageCount = 2),
            )
            val songbook = testLibrarySongbook(
                id = "songbook_standards",
                name = "Standards Book",
                itemIds = items.map { it.id },
                pageCount = 8,
            )
            val runtime = createTestPlatformRuntime(
                initialItems = items,
                initialSongbooks = listOf(songbook),
                pdfExporter = TestPdfDocumentExporter(),
                scoreDownloadSupported = true,
            )
            val appState = createTestLunarAppState(scope = backgroundScope(), runtime = runtime)
            appState.updateBrowseMode(LibraryBrowseMode.SONGBOOKS)
            rule.setLibraryContent(runtime, appState)

            rule.onNodeWithText("Choose a songbook to see its contents.").assertIsDisplayed()
            rule.onNodeWithText("Standards Book").performClick()
            rule.onNodeWithContentDescription("Cover of Standards Book").assertIsDisplayed()
            rule.onNodeWithText("Autumn Leaves").assertIsDisplayed()
            rule.onNodeWithContentDescription("Starts on page 1").assertIsDisplayed()
            rule.onNodeWithContentDescription("Starts on page 4").assertIsDisplayed()
            assertEquals(
                1,
                rule.onAllNodesWithContentDescriptionCount("Start page unknown"),
            )

            rule.onNodeWithText("Export PDF").performClick()
            rule.waitUntil(timeoutMillis = 5_000) {
                appState.bannerMessage == "Saved \"Standards Book\" to Downloads/Standards Book.pdf."
            }

            rule.onNodeWithText("Open songbook").performClick()
            rule.runOnIdle {
                assertEquals(ViewerTarget.Songbook(songbook.id), appState.previewTarget)
            }
            assertNotEquals(null, appState.selectedSongbookId)
        }
    }
}

private fun ComposeContentTestRule.setLibraryContent(
    runtime: PlatformRuntime,
    appState: LunarAppState,
    modifier: Modifier = Modifier,
) {
    setContent {
        LunarTheme(theme = AppColorTheme.OCEAN) {
            Box(modifier = modifier) {
                val snapshot by runtime.repository.library.collectAsState()
                LibraryScreen(
                    runtime = runtime,
                    snapshot = snapshot,
                    appState = appState,
                )
            }
        }
    }
}

private fun ComposeContentTestRule.onAllNodesWithContentDescriptionCount(description: String): Int =
    onAllNodes(androidx.compose.ui.test.hasContentDescription(description)).fetchSemanticsNodes().size

private fun backgroundScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
