package com.ryanjames.lunar

import androidx.compose.ui.input.key.Key
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import com.ryanjames.lunar.library.model.PdfDocumentReference
import com.ryanjames.lunar.platform.LilyPondLiveRenderResult
import com.ryanjames.lunar.platform.LilyPondLiveRenderer
import com.ryanjames.lunar.platform.LilyPondSourceSnapshot
import com.ryanjames.lunar.platform.PdfDocumentInfo
import com.ryanjames.lunar.platform.PdfPageRenderer
import com.ryanjames.lunar.platform.RenderedPdfPage
import com.ryanjames.lunar.settings.AppSkin
import com.ryanjames.lunar.settings.ViewerKeybindings
import com.ryanjames.lunar.ui.LunarTheme
import com.ryanjames.lunar.ui.ViewerDocumentState
import com.ryanjames.lunar.ui.ViewerScreen
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ViewerShortcutTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun viewerScreenAppliesCustomKeybindingsForNavigationFavoriteAndFullscreen() {
        val runtime = createShortcutTestRuntime()
        val documentState = ViewerDocumentState(
            id = "moon_river",
            title = "Moon River",
            document = testSheetMusicItem(id = "moon_river", title = "Moon River").document,
            pageCount = 6,
            isFavorite = false,
        )
        val keybindings = ViewerKeybindings(
            toggleFullscreen = "Q",
            nextPage = "L",
            previousPage = "H",
            toggleFavorite = "F",
            zoomIn = "I",
            zoomOut = "O",
            togglePageViewMode = "T",
            openRandomScore = "R",
        )
        var toggledFavoriteCount = 0
        var enteredFullscreenCount = 0
        var openedRandomCount = 0

        rule.setContent {
            LunarTheme(skin = AppSkin.PAPER) {
                ViewerScreen(
                    runtime = runtime,
                    documentState = documentState,
                    onBack = {},
                    onToggleFavorite = { toggledFavoriteCount += 1 },
                    onOpenRandomScore = { openedRandomCount += 1 },
                    onPageChanged = { _ -> },
                    onPageCountResolved = { _ -> },
                    chromeAutoHideMillis = null,
                    viewerKeybindings = keybindings,
                    onEnterFullscreen = { enteredFullscreenCount += 1 },
                )
            }
        }

        rule.onNodeWithText("Page 1 of 6").assertIsDisplayed()
        rule.onNodeWithContentDescription("Single-page layout").assertIsDisplayed()
        rule.onRoot().performKeyInput { pressKey(Key.T) }
        rule.onNodeWithContentDescription("Two-page layout").assertIsDisplayed()
        rule.onNodeWithText("Pages 1 and 2 of 6").assertIsDisplayed()
        rule.onRoot().performKeyInput { pressKey(Key.L) }
        rule.onNodeWithText("Pages 3 and 4 of 6").assertIsDisplayed()
        rule.onRoot().performKeyInput { pressKey(Key.H) }
        rule.onNodeWithText("Pages 1 and 2 of 6").assertIsDisplayed()
        rule.onRoot().performKeyInput { pressKey(Key.F) }
        rule.onRoot().performKeyInput { pressKey(Key.Q) }
        rule.onRoot().performKeyInput { pressKey(Key.R) }

        rule.runOnIdle {
            assertEquals(1, toggledFavoriteCount)
            assertEquals(1, enteredFullscreenCount)
            assertEquals(1, openedRandomCount)
        }
    }

    @Test
    fun viewerScreenOpensMetronomePopoverFromToolbarAndKeepsTempo() {
        val runtime = createShortcutTestRuntime()
        val documentState = ViewerDocumentState(
            id = "moon_river",
            title = "Moon River",
            document = testSheetMusicItem(id = "moon_river", title = "Moon River").document,
            pageCount = 6,
        )

        rule.setContent {
            LunarTheme(skin = AppSkin.PAPER) {
                ViewerScreen(
                    runtime = runtime,
                    documentState = documentState,
                    onBack = {},
                    onPageChanged = { _ -> },
                    onPageCountResolved = { _ -> },
                    chromeAutoHideMillis = null,
                )
            }
        }

        assertTrue(rule.onAllNodesWithText("Tap Tempo").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithContentDescription("Metronome").performClick()
        rule.onNodeWithText("Tap Tempo").assertIsDisplayed()
        rule.onNodeWithText("Time: 4/4").assertIsDisplayed()
        rule.onNodeWithText("Start").assertIsDisplayed()
        rule.onNodeWithText("120 BPM").assertIsDisplayed()
        rule.onNodeWithText("+5").performClick()
        rule.onNodeWithText("125 BPM").assertIsDisplayed()

        rule.onNodeWithText("Close").performClick()
        assertTrue(rule.onAllNodesWithText("Tap Tempo").fetchSemanticsNodes().isEmpty())

        rule.onNodeWithContentDescription("Metronome").performClick()
        rule.onNodeWithText("125 BPM").assertIsDisplayed()
    }

    @Test
    fun viewerChromeHidesAfterDelayAndReturnsOnInput() {
        val runtime = createShortcutTestRuntime()
        val documentState = ViewerDocumentState(
            id = "moon_river",
            title = "Moon River",
            document = testSheetMusicItem(id = "moon_river", title = "Moon River").document,
            pageCount = 6,
        )
        rule.mainClock.autoAdvance = false

        rule.setContent {
            LunarTheme(theme = AppColorTheme.OCEAN) {
                ViewerScreen(
                    runtime = runtime,
                    documentState = documentState,
                    onBack = {},
                    onPageChanged = { _ -> },
                    onPageCountResolved = { _ -> },
                    chromeAutoHideMillis = 3_000L,
                )
            }
        }

        rule.mainClock.advanceTimeBy(2_000L)
        rule.onNodeWithContentDescription("Zoom in").assertIsDisplayed()
        rule.onNodeWithText("Page 1 of 6").assertIsDisplayed()

        rule.mainClock.advanceTimeBy(ChromeHideSettleMillis)
        assertChromeHidden()

        rule.onRoot().performKeyInput { pressKey(Key.A) }
        rule.mainClock.advanceTimeBy(ChromeShowSettleMillis)
        rule.onNodeWithContentDescription("Zoom in").assertIsDisplayed()

        rule.mainClock.advanceTimeBy(ChromeHideSettleMillis + 2_000L)
        assertChromeHidden()

        rule.onRoot().performMouseInput { moveTo(center) }
        rule.mainClock.advanceTimeBy(ChromeShowSettleMillis)
        rule.onNodeWithContentDescription("Zoom in").assertIsDisplayed()

        rule.mainClock.advanceTimeBy(ChromeHideSettleMillis + 2_000L)
        assertChromeHidden()

        rule.onRoot().performTouchInput { click(center) }
        rule.mainClock.advanceTimeBy(ChromeShowSettleMillis)
        rule.onNodeWithContentDescription("Zoom in").assertIsDisplayed()
    }

    @Test
    fun viewerChromeStaysVisibleWhenAutoHideIsOffOrAPopoverIsOpen() {
        val runtime = createShortcutTestRuntime()
        val documentState = ViewerDocumentState(
            id = "moon_river",
            title = "Moon River",
            document = testSheetMusicItem(id = "moon_river", title = "Moon River").document,
            pageCount = 6,
        )
        var autoHideMillis by mutableStateOf<Long?>(null)
        rule.mainClock.autoAdvance = false

        rule.setContent {
            LunarTheme(theme = AppColorTheme.OCEAN) {
                ViewerScreen(
                    runtime = runtime,
                    documentState = documentState,
                    onBack = {},
                    onPageChanged = { _ -> },
                    onPageCountResolved = { _ -> },
                    chromeAutoHideMillis = autoHideMillis,
                )
            }
        }

        rule.mainClock.advanceTimeBy(30_000L)
        rule.onNodeWithContentDescription("Zoom in").assertIsDisplayed()

        rule.onNodeWithContentDescription("Metronome").performClick()
        autoHideMillis = 3_000L
        rule.mainClock.advanceTimeBy(30_000L)
        rule.onNodeWithText("Tap Tempo").assertIsDisplayed()
        rule.onNodeWithContentDescription("Zoom in").assertIsDisplayed()
    }

    @Test
    fun viewerFooterStripAndEdgeButtonsChangePages() {
        val runtime = createShortcutTestRuntime()
        val documentState = ViewerDocumentState(
            id = "moon_river",
            title = "Moon River",
            document = testSheetMusicItem(id = "moon_river", title = "Moon River").document,
            pageCount = 6,
        )
        var randomOpens = 0
        var lastPage = -1

        rule.setContent {
            LunarTheme(theme = AppColorTheme.OCEAN) {
                ViewerScreen(
                    runtime = runtime,
                    documentState = documentState,
                    onBack = {},
                    onOpenRandomScore = { randomOpens += 1 },
                    onPageChanged = { page -> lastPage = page },
                    onPageCountResolved = { _ -> },
                    chromeAutoHideMillis = null,
                    nextInSetlist = { Text("Next: Autumn Leaves") },
                )
            }
        }

        rule.onNodeWithText("Next: Autumn Leaves").assertIsDisplayed()
        rule.onNodeWithContentDescription("Previous page").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Go to page 1").assertIsSelected()
        rule.onNodeWithContentDescription("Go to page 4").performClick()
        rule.onNodeWithText("Page 4 of 6").assertIsDisplayed()
        rule.onNodeWithContentDescription("Go to page 4").assertIsSelected()

        rule.onNodeWithContentDescription("Next page").performClick()
        rule.onNodeWithText("Page 5 of 6").assertIsDisplayed()
        rule.onNodeWithContentDescription("Previous page").performClick()
        rule.onNodeWithContentDescription("Previous page").performClick()
        rule.onNodeWithText("Page 3 of 6").assertIsDisplayed()

        rule.onNodeWithText("Random score").performClick()
        rule.runOnIdle {
            assertEquals(1, randomOpens)
            assertEquals(2, lastPage)
        }
    }

    @Test
    fun viewerToolbarBackButtonUsesLabel() {
        val runtime = createShortcutTestRuntime()
        val documentState = ViewerDocumentState(
            id = "moon_river",
            title = "Moon River",
            document = testSheetMusicItem(id = "moon_river", title = "Moon River").document,
            pageCount = 6,
        )
        var backs = 0

        rule.setContent {
            LunarTheme(theme = AppColorTheme.OCEAN) {
                ViewerScreen(
                    runtime = runtime,
                    documentState = documentState,
                    onBack = { backs += 1 },
                    onPageChanged = { _ -> },
                    onPageCountResolved = { _ -> },
                    chromeAutoHideMillis = null,
                    backLabel = "Friday gig",
                )
            }
        }

        rule.onNodeWithContentDescription("Back to Friday gig").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back to Friday gig").performClick()
        rule.runOnIdle { assertEquals(1, backs) }
    }

    private fun assertChromeHidden() {
        rule.onNodeWithContentDescription("Zoom in").assertDoesNotExist()
        rule.onNodeWithContentDescription("Next page").assertDoesNotExist()
        rule.onNodeWithText("Page 1 of 6").assertDoesNotExist()
    }

    @Test
    fun viewerScreenShowsPdfBadgeAndPageCountForPdfDocuments() {
        val runtime = createShortcutTestRuntime()
        val documentState = ViewerDocumentState(
            id = "moon_river",
            title = "Moon River",
            document = testSheetMusicItem(id = "moon_river", title = "Moon River").document,
            pageCount = 6,
        )

        rule.setContent {
            LunarTheme(skin = AppSkin.PAPER) {
                ViewerScreen(
                    runtime = runtime,
                    documentState = documentState,
                    onBack = {},
                    onPageChanged = { _ -> },
                    onPageCountResolved = { _ -> },
                    chromeAutoHideMillis = null,
                )
            }
        }

        rule.onNodeWithContentDescription("PDF viewer").assertIsDisplayed()
        rule.onNodeWithText("Page 1 of 6").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText("PDF Viewer").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun viewerScreenGroupsCollectionActionsBehindAddMenuAndUsesHideIcon() {
        val runtime = createShortcutTestRuntime()
        val documentState = ViewerDocumentState(
            id = "moon_river",
            title = "Moon River",
            document = testSheetMusicItem(id = "moon_river", title = "Moon River").document,
            pageCount = 6,
        )
        var setlistAdds = 0
        var songbookAdds = 0
        var hiddenScores = 0

        rule.setContent {
            LunarTheme(skin = AppSkin.PAPER) {
                ViewerScreen(
                    runtime = runtime,
                    documentState = documentState,
                    onBack = {},
                    onHideScore = { hiddenScores += 1 },
                    onAddToSetlist = { setlistAdds += 1 },
                    onAddToSongbook = { songbookAdds += 1 },
                    onPageChanged = { _ -> },
                    onPageCountResolved = { _ -> },
                    chromeAutoHideMillis = null,
                )
            }
        }

        rule.onNodeWithContentDescription("Add to setlist or songbook").assertIsDisplayed()
        rule.onNodeWithContentDescription("Hide score").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText("Add to setlist").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithText("Add to songbook").fetchSemanticsNodes().isEmpty())

        rule.onNodeWithContentDescription("Add to setlist or songbook").performClick()
        rule.onNodeWithText("Add to setlist").assertIsDisplayed()
        rule.onNodeWithText("Add to songbook").assertIsDisplayed()
        rule.onNodeWithText("Add to setlist").performClick()

        rule.onNodeWithContentDescription("Add to setlist or songbook").performClick()
        rule.onNodeWithText("Add to songbook").performClick()
        rule.onNodeWithContentDescription("Hide score").performClick()

        rule.runOnIdle {
            assertEquals(1, setlistAdds)
            assertEquals(1, songbookAdds)
            assertEquals(1, hiddenScores)
        }
    }

    @Test
    fun viewerScreenShowsStarFavoriteMarkerLikeLibraryView() {
        val runtime = createShortcutTestRuntime()
        val documentState = ViewerDocumentState(
            id = "moon_river",
            title = "Moon River",
            document = testSheetMusicItem(id = "moon_river", title = "Moon River").document,
            pageCount = 6,
            isFavorite = true,
        )

        rule.setContent {
            LunarTheme(skin = AppSkin.PAPER) {
                ViewerScreen(
                    runtime = runtime,
                    documentState = documentState,
                    onBack = {},
                    onToggleFavorite = {},
                    onPageChanged = { _ -> },
                    onPageCountResolved = { _ -> },
                    chromeAutoHideMillis = null,
                )
            }
        }

        rule.onNodeWithText("★").assertIsDisplayed()
        rule.onNodeWithContentDescription("Remove Moon River from favorites").assertIsDisplayed()
    }

    @Test
    fun viewerScreenUsesLiveLilyPondPreviewForLilyPondDocuments() {
        val pdfRenderer = RecordingShortcutPdfPageRenderer()
        val lilyPondRenderer = ShortcutTestLilyPondLiveRenderer()
        val runtime = runBlocking {
            createTestPlatformRuntime(
                renderer = pdfRenderer,
                lilyPondLiveRenderer = lilyPondRenderer,
                lilyPondLiveViewingSupported = true,
            )
        }
        val documentState = ViewerDocumentState(
            id = "moon_river",
            title = "Moon River",
            document = testSheetMusicItem(id = "moon_river", title = "Moon River").document.copy(
                originalFileName = "moon_river.ly",
                sourceUri = "file:///scores/moon_river.ly",
            ),
            pageCount = 6,
        )

        rule.setContent {
            LunarTheme(skin = AppSkin.PAPER) {
                ViewerScreen(
                    runtime = runtime,
                    documentState = documentState,
                    onBack = {},
                    onPageChanged = { _ -> },
                    onPageCountResolved = { _ -> },
                    chromeAutoHideMillis = null,
                )
            }
        }

        rule.waitUntil(timeoutMillis = 5_000) {
            pdfRenderer.inspectedPaths.contains("/live/moon_river.pdf")
        }
        rule.onNodeWithContentDescription("LilyPond viewer").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText("LilyPond Viewer").fetchSemanticsNodes().isEmpty())
        rule.runOnIdle {
            assertTrue(lilyPondRenderer.renderedRevisions.contains("moon-river-v1"))
            assertTrue(pdfRenderer.inspectedPaths.contains("/live/moon_river.pdf"))
        }
    }

    @Test
    fun viewerScreenEditsLilyPondSourceAndRendersEditedPreview() {
        val pdfRenderer = RecordingShortcutPdfPageRenderer()
        val lilyPondRenderer = ShortcutTestLilyPondLiveRenderer()
        val runtime = runBlocking {
            createTestPlatformRuntime(
                renderer = pdfRenderer,
                lilyPondLiveRenderer = lilyPondRenderer,
                lilyPondLiveViewingSupported = true,
            )
        }
        val documentState = ViewerDocumentState(
            id = "moon_river",
            title = "Moon River",
            document = testSheetMusicItem(id = "moon_river", title = "Moon River").document.copy(
                originalFileName = "moon_river.ly",
                sourceUri = "file:///scores/moon_river.ly",
            ),
            pageCount = 6,
        )
        val editedSource = "\\version \"2.24.0\"\n{ g'4 a' b' c'' }"

        rule.setContent {
            LunarTheme(skin = AppSkin.PAPER) {
                ViewerScreen(
                    runtime = runtime,
                    documentState = documentState,
                    onBack = {},
                    onPageChanged = { _ -> },
                    onPageCountResolved = { _ -> },
                    chromeAutoHideMillis = null,
                )
            }
        }

        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithContentDescription("More actions").fetchSemanticsNodes().isNotEmpty()
        }
        rule.runOnIdle {
            assertEquals(1, lilyPondRenderer.renderedSources.size)
        }
        rule.onNodeWithContentDescription("More actions").performClick()
        rule.onNodeWithText("Edit source").performClick()
        rule.onNodeWithText("LilyPond source").assertIsDisplayed()
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(1, lilyPondRenderer.renderedSources.size)
        }
        rule.onNodeWithContentDescription("LilyPond source editor")
            .performTextReplacement(editedSource)

        rule.waitUntil(timeoutMillis = 5_000) {
            lilyPondRenderer.renderedSources.any { source -> source.sourceText == editedSource }
        }
        rule.onNodeWithText("LilyPond Editor").assertIsDisplayed()
    }

    @Test
    fun viewerScreenCanManuallyRefreshLilyPondEditorPreview() {
        val pdfRenderer = RecordingShortcutPdfPageRenderer()
        val lilyPondRenderer = ShortcutTestLilyPondLiveRenderer()
        val runtime = runBlocking {
            createTestPlatformRuntime(
                renderer = pdfRenderer,
                lilyPondLiveRenderer = lilyPondRenderer,
                lilyPondLiveViewingSupported = true,
            )
        }
        val documentState = ViewerDocumentState(
            id = "moon_river",
            title = "Moon River",
            document = testSheetMusicItem(id = "moon_river", title = "Moon River").document.copy(
                originalFileName = "moon_river.ly",
                sourceUri = "file:///scores/moon_river.ly",
            ),
            pageCount = 6,
        )
        val editedSource = "\\version \"2.24.0\"\n{ b'4 a' g' f' }"

        rule.setContent {
            LunarTheme(skin = AppSkin.PAPER) {
                ViewerScreen(
                    runtime = runtime,
                    documentState = documentState,
                    onBack = {},
                    onPageChanged = { _ -> },
                    onPageCountResolved = { _ -> },
                    chromeAutoHideMillis = null,
                )
            }
        }

        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithContentDescription("More actions").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription("More actions").performClick()
        rule.onNodeWithText("Edit source").performClick()
        rule.onNodeWithContentDescription("Toggle LilyPond auto refresh").performClick()
        rule.onNodeWithContentDescription("LilyPond source editor")
            .performTextReplacement(editedSource)
        rule.waitForIdle()
        rule.runOnIdle {
            assertTrue(lilyPondRenderer.renderedSources.none { source -> source.sourceText == editedSource })
        }

        rule.onNodeWithText("LilyPond edits pending").assertIsDisplayed()
        rule.onNodeWithContentDescription("Refresh LilyPond preview").performClick()

        rule.waitUntil(timeoutMillis = 5_000) {
            lilyPondRenderer.renderedSources.any { source -> source.sourceText == editedSource }
        }
        rule.onNodeWithText("LilyPond Editor").assertIsDisplayed()
    }
}

// Past the 3s auto-hide delay plus the fade-out, and long enough for the fade-in.
private const val ChromeHideSettleMillis = 1_500L
private const val ChromeShowSettleMillis = 500L

private fun createShortcutTestRuntime() = runBlocking {
    createTestPlatformRuntime(
        renderer = ShortcutTestPdfPageRenderer(),
    )
}

private class ShortcutTestPdfPageRenderer : PdfPageRenderer {
    override suspend fun inspect(documentPath: String): PdfDocumentInfo = PdfDocumentInfo(pageCount = 6)

    override suspend fun renderPage(
        documentPath: String,
        pageIndex: Int,
        targetWidth: Int,
    ): RenderedPdfPage? = null
}

private class RecordingShortcutPdfPageRenderer : PdfPageRenderer {
    val inspectedPaths = mutableListOf<String>()

    override suspend fun inspect(documentPath: String): PdfDocumentInfo {
        inspectedPaths += documentPath
        return PdfDocumentInfo(pageCount = 6)
    }

    override suspend fun renderPage(
        documentPath: String,
        pageIndex: Int,
        targetWidth: Int,
    ): RenderedPdfPage? = null
}

private class ShortcutTestLilyPondLiveRenderer : LilyPondLiveRenderer {
    val renderedRevisions = mutableListOf<String>()
    val renderedSources = mutableListOf<LilyPondSourceSnapshot>()

    override suspend fun loadSource(document: PdfDocumentReference): LilyPondSourceSnapshot =
        LilyPondSourceSnapshot(
            sourceText = "\\version \"2.24.0\"\n{ c'4 d' e' f' }",
            revision = "moon-river-v1",
            displayName = "moon_river.ly",
            sourcePath = "/scores/moon_river.ly",
        )

    override suspend fun renderSource(source: LilyPondSourceSnapshot): LilyPondLiveRenderResult {
        renderedRevisions += source.revision
        renderedSources += source
        return LilyPondLiveRenderResult(
            documentPath = "/live/moon_river.pdf",
            pageCount = 6,
        )
    }
}
