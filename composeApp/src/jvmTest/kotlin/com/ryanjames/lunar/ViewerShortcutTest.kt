package com.ryanjames.lunar

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
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
        )
        var toggledFavoriteCount = 0
        var enteredFullscreenCount = 0

        rule.setContent {
            LunarTheme(theme = AppColorTheme.OCEAN) {
                ViewerScreen(
                    runtime = runtime,
                    documentState = documentState,
                    onBack = {},
                    onToggleFavorite = { toggledFavoriteCount += 1 },
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

        rule.runOnIdle {
            assertEquals(1, toggledFavoriteCount)
            assertEquals(1, enteredFullscreenCount)
        }
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
