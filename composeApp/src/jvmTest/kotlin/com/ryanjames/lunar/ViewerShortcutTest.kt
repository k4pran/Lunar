package com.ryanjames.lunar

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import com.ryanjames.lunar.library.model.PdfDocumentReference
import com.ryanjames.lunar.platform.LilyPondLiveRenderResult
import com.ryanjames.lunar.platform.LilyPondLiveRenderer
import com.ryanjames.lunar.platform.LilyPondSourceSnapshot
import com.ryanjames.lunar.platform.PdfDocumentInfo
import com.ryanjames.lunar.platform.PdfPageRenderer
import com.ryanjames.lunar.platform.RenderedPdfPage
import com.ryanjames.lunar.settings.AppColorTheme
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
            LunarTheme(theme = AppColorTheme.OCEAN) {
                ViewerScreen(
                    runtime = runtime,
                    documentState = documentState,
                    onBack = {},
                    onToggleFavorite = { toggledFavoriteCount += 1 },
                    onOpenRandomScore = { openedRandomCount += 1 },
                    onPageChanged = { _ -> },
                    onPageCountResolved = { _ -> },
                    viewerKeybindings = keybindings,
                    onEnterFullscreen = { enteredFullscreenCount += 1 },
                )
            }
        }

        rule.onNodeWithText("1 / 6").assertIsDisplayed()
        rule.onRoot().performKeyInput { pressKey(Key.T) }
        rule.onNodeWithText("1-Up").assertIsDisplayed()
        rule.onRoot().performKeyInput { pressKey(Key.L) }
        rule.onNodeWithText("3 / 6").assertIsDisplayed()
        rule.onRoot().performKeyInput { pressKey(Key.H) }
        rule.onNodeWithText("1 / 6").assertIsDisplayed()
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
    fun viewerScreenOpensMetronomeDialogFromToolbar() {
        val runtime = createShortcutTestRuntime()
        val documentState = ViewerDocumentState(
            id = "moon_river",
            title = "Moon River",
            document = testSheetMusicItem(id = "moon_river", title = "Moon River").document,
            pageCount = 6,
        )

        rule.setContent {
            LunarTheme(theme = AppColorTheme.OCEAN) {
                ViewerScreen(
                    runtime = runtime,
                    documentState = documentState,
                    onBack = {},
                    onPageChanged = { _ -> },
                    onPageCountResolved = { _ -> },
                )
            }
        }

        rule.onNodeWithText("\u2669").performClick()
        rule.onNodeWithText("Tap Tempo").assertIsDisplayed()
        rule.onNodeWithText("Time: 4/4").assertIsDisplayed()
        rule.onNodeWithText("Start").assertIsDisplayed()
    }

    @Test
    fun viewerScreenLabelsPdfViewerForPdfDocuments() {
        val runtime = createShortcutTestRuntime()
        val documentState = ViewerDocumentState(
            id = "moon_river",
            title = "Moon River",
            document = testSheetMusicItem(id = "moon_river", title = "Moon River").document,
            pageCount = 6,
        )

        rule.setContent {
            LunarTheme(theme = AppColorTheme.OCEAN) {
                ViewerScreen(
                    runtime = runtime,
                    documentState = documentState,
                    onBack = {},
                    onPageChanged = { _ -> },
                    onPageCountResolved = { _ -> },
                )
            }
        }

        rule.onNodeWithText("PDF Viewer").assertIsDisplayed()
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
            LunarTheme(theme = AppColorTheme.OCEAN) {
                ViewerScreen(
                    runtime = runtime,
                    documentState = documentState,
                    onBack = {},
                    onToggleFavorite = {},
                    onPageChanged = { _ -> },
                    onPageCountResolved = { _ -> },
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
            LunarTheme(theme = AppColorTheme.OCEAN) {
                ViewerScreen(
                    runtime = runtime,
                    documentState = documentState,
                    onBack = {},
                    onPageChanged = { _ -> },
                    onPageCountResolved = { _ -> },
                )
            }
        }

        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText("LilyPond Viewer").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("LilyPond Viewer").assertIsDisplayed()
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
            LunarTheme(theme = AppColorTheme.OCEAN) {
                ViewerScreen(
                    runtime = runtime,
                    documentState = documentState,
                    onBack = {},
                    onPageChanged = { _ -> },
                    onPageCountResolved = { _ -> },
                )
            }
        }

        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText("Edit").fetchSemanticsNodes().isNotEmpty()
        }
        rule.runOnIdle {
            assertEquals(1, lilyPondRenderer.renderedSources.size)
        }
        rule.onNodeWithText("Edit").performClick()
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
            LunarTheme(theme = AppColorTheme.OCEAN) {
                ViewerScreen(
                    runtime = runtime,
                    documentState = documentState,
                    onBack = {},
                    onPageChanged = { _ -> },
                    onPageCountResolved = { _ -> },
                )
            }
        }

        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText("Edit").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Edit").performClick()
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
