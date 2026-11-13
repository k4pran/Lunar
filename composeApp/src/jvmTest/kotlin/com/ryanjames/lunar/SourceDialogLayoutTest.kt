package com.ryanjames.lunar

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.ryanjames.lunar.library.data.SyncProviderIds
import com.ryanjames.lunar.settings.AppSkin
import com.ryanjames.lunar.ui.AddCloudSourceDialog
import com.ryanjames.lunar.ui.LunarTheme
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SourceDialogLayoutTest {

    @Test
    fun cloudDialogKeepsActionsVisibleOnShortWindows() = runDesktopComposeUiTest(width = 800, height = 360) {
        setContent {
            LunarTheme(skin = AppSkin.MOONLIGHT) {
                AddCloudSourceDialog(
                    googleDriveOAuthSupported = false,
                    initialProviderId = SyncProviderIds.GOOGLE_DRIVE,
                    onDismiss = {},
                    onConfirm = {},
                )
            }
        }

        listOf("Cancel", "Connect").forEach { label ->
            val button = onNodeWithText(label).assertIsDisplayed()
            val bounds = button.getBoundsInRoot()
            assertTrue(bounds.bottom - bounds.top > 0.dp, "$label should be measured with a real height")
        }
    }
}
