package com.ryanjames.lunar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.ryanjames.lunar.library.model.LibrarySortOption
import com.ryanjames.lunar.library.model.SortDirection
import com.ryanjames.lunar.platform.PdfDocumentInfo
import com.ryanjames.lunar.platform.PdfPageRenderer
import com.ryanjames.lunar.platform.RenderedPdfPage
import com.ryanjames.lunar.settings.AppSkin
import com.ryanjames.lunar.settings.AppSettings
import com.ryanjames.lunar.settings.LibraryLayoutPreference
import com.ryanjames.lunar.ui.LibraryLayoutMode
import com.ryanjames.lunar.ui.LibraryScreen
import com.ryanjames.lunar.ui.LunarAppState
import com.ryanjames.lunar.ui.LunarTheme
import com.ryanjames.lunar.ui.ScoreThumbnailPlaceholderTag
import com.ryanjames.lunar.ui.ViewerTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibraryGalleryUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val items = listOf(
        testSheetMusicItem(id = "air", title = "Air on the G String", composer = "Bach", collection = "Baroque"),
        testSheetMusicItem(id = "gavotte", title = "Gavotte", composer = "Bach", collection = "Baroque"),
        testSheetMusicItem(id = "nocturne", title = "Nocturne", composer = "Chopin", collection = "Recital")
            .copy(lastViewedPage = 2),
    )

    @Test
    fun switchingToGalleryShowsCardsGroupedLikeTheSort() {
        runBlocking {
            val (runtime, appState) = setUpLibrary()
            rule.runOnIdle {
                appState.updateSortOption(LibrarySortOption.COMPOSER)
                appState.updateSortDirection(SortDirection.ASCENDING)
            }

            rule.onNodeWithContentDescription("Show as gallery").performClick()

            rule.runOnIdle {
                assertEquals(LibraryLayoutMode.GRID, appState.layoutMode)
            }
            rule.onNodeWithText("Bach").assertIsDisplayed()
            rule.onNodeWithText("Gavotte").assertIsDisplayed()
            assertEquals(2, rule.onAllNodesWithText("Baroque").fetchSemanticsNodes().size)
            rule.onNodeWithContentDescription("Jump to C").performClick()
            rule.onNodeWithText("Chopin").assertIsDisplayed()
            rule.onNodeWithText("Nocturne").assertIsDisplayed()
            rule.onNodeWithText("Recital").assertIsDisplayed()
            rule.runOnIdle {
                assertEquals(
                    LibraryLayoutPreference.GRID,
                    runtime.settingsStore.settings.value.defaultLibraryLayout,
                )
            }
        }
    }

    @Test
    fun focusingACardShowsTheOpenAction() {
        runBlocking {
            val (_, appState) = setUpLibrary(layout = LibraryLayoutPreference.GRID)
            assertTrue(rule.onAllNodesWithText("Open at p. 3").fetchSemanticsNodes().isEmpty())

            rule.onNodeWithContentDescription("Nocturne").performClick()
            rule.waitUntil(timeoutMillis = 5_000) {
                rule.onAllNodesWithText("Open at p. 3").fetchSemanticsNodes().isNotEmpty()
            }
            rule.runOnIdle { assertEquals("nocturne", appState.focusedScoreId) }

            rule.onNodeWithText("Open at p. 3").performClick()
            rule.runOnIdle {
                assertEquals(ViewerTarget.Score("nocturne"), appState.fullscreenTarget)
            }
        }
    }

    @Test
    fun tappingSelectionCheckboxesAddsScoresToANewSetlist() {
        runBlocking {
            val (runtime, appState) = setUpLibrary(layout = LibraryLayoutPreference.GRID)

            rule.onNodeWithContentDescription("Select Gavotte").assertIsOff()
            rule.onNodeWithContentDescription("Select Gavotte").performClick()
            rule.onNodeWithContentDescription("Select Nocturne").performClick()
            rule.onNodeWithContentDescription("Select Gavotte").assertIsOn()
            rule.runOnIdle {
                assertEquals(setOf("gavotte", "nocturne"), appState.selectedScoreIds)
            }

            rule.onNodeWithText("Add to setlist").performClick()
            rule.onNodeWithText("ADD 2 SCORES TO A SETLIST").assertIsDisplayed()
            rule.onNodeWithContentDescription("New setlist...").performTextInput("Recital run")
            rule.onNodeWithText("Create").performClick()

            rule.waitUntil(timeoutMillis = 5_000) {
                runtime.repository.library.value.setlists.any { it.name == "Recital run" }
            }
            val setlist = runtime.repository.library.value.setlists.single { it.name == "Recital run" }
            assertEquals(setOf("gavotte", "nocturne"), setlist.itemIds.toSet())
        }
    }

    @Test
    fun longPressingACardTogglesItsSelection() {
        runBlocking {
            val (_, appState) = setUpLibrary(layout = LibraryLayoutPreference.GRID)

            rule.onNodeWithContentDescription("Nocturne").performTouchInput { longClick() }
            rule.runOnIdle {
                assertEquals(setOf("nocturne"), appState.selectedScoreIds)
                assertEquals("nocturne", appState.focusedScoreId)
            }
            rule.onNodeWithContentDescription("Select Nocturne").assertIsOn()

            rule.onNodeWithContentDescription("Nocturne").performTouchInput { longClick() }
            rule.runOnIdle { assertTrue(appState.selectedScoreIds.isEmpty()) }
        }
    }

    @Test
    fun thumbnailFallsBackToPlaceholderWhenRenderingFails() {
        runBlocking {
            val renderer = FailingPdfPageRenderer()
            setUpLibrary(layout = LibraryLayoutPreference.GRID, renderer = renderer)

            rule.waitUntil(timeoutMillis = 5_000) { renderer.renderAttempts.get() >= items.size }
            rule.waitForIdle()
            assertEquals(
                items.size,
                rule.onAllNodesWithTag(ScoreThumbnailPlaceholderTag, useUnmergedTree = true).fetchSemanticsNodes().size,
            )
        }
    }

    private suspend fun setUpLibrary(
        layout: LibraryLayoutPreference = LibraryLayoutPreference.LIST,
        renderer: PdfPageRenderer? = null,
    ): Pair<com.ryanjames.lunar.platform.PlatformRuntime, LunarAppState> {
        val settings = AppSettings(defaultLibraryLayout = layout)
        val runtime = if (renderer == null) {
            createTestPlatformRuntime(initialItems = items, initialSettings = settings)
        } else {
            createTestPlatformRuntime(initialItems = items, initialSettings = settings, renderer = renderer)
        }
        val appState = createTestLunarAppState(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            runtime = runtime,
        )
        rule.setContent {
            LunarTheme(skin = AppSkin.PAPER) {
                LibraryScreen(
                    runtime = runtime,
                    snapshot = runtime.repository.library.value,
                    appState = appState,
                )
            }
        }
        return runtime to appState
    }
}

private class FailingPdfPageRenderer : PdfPageRenderer {
    val renderAttempts = AtomicInteger(0)

    override suspend fun inspect(documentPath: String): PdfDocumentInfo? = null

    override suspend fun renderPage(
        documentPath: String,
        pageIndex: Int,
        targetWidth: Int,
    ): RenderedPdfPage? {
        renderAttempts.incrementAndGet()
        error("Cannot decode $documentPath")
    }
}
