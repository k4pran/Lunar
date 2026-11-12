package com.ryanjames.lunar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.ryanjames.lunar.settings.AppSkin
import com.ryanjames.lunar.ui.LunarTheme
import com.ryanjames.lunar.ui.design.LeaderRow
import com.ryanjames.lunar.ui.design.LunarButton
import com.ryanjames.lunar.ui.design.LunarButtonStyle
import com.ryanjames.lunar.ui.design.LunarDialog
import com.ryanjames.lunar.ui.design.LunarIcon
import com.ryanjames.lunar.ui.design.LunarIconKind
import com.ryanjames.lunar.ui.design.PageHeading
import com.ryanjames.lunar.ui.design.SmallCapsLabel
import com.ryanjames.lunar.ui.design.TextToggle
import com.ryanjames.lunar.ui.design.TextToggleOption
import com.ryanjames.lunar.ui.design.TruncatedText
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val hasLongClick = SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick)

class DesignComponentsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun everyIconLoadsInBothSkins() {
        var skin by mutableStateOf(AppSkin.PAPER)
        rule.setContent {
            LunarTheme(skin = skin) {
                Column {
                    LunarIconKind.entries.forEach { kind ->
                        LunarIcon(kind = kind, contentDescription = "icon ${kind.name}")
                    }
                }
            }
        }

        AppSkin.entries.forEach { next ->
            skin = next
            rule.waitForIdle()
            LunarIconKind.entries.forEach { kind ->
                rule.onNodeWithContentDescription("icon ${kind.name}").assertExists()
            }
        }
    }

    @Test
    fun headingAndLabelRender() {
        rule.setContent {
            LunarTheme(skin = AppSkin.PAPER) {
                Column {
                    PageHeading(title = "Library", subtitle = "Every score, ready offline")
                    SmallCapsLabel(text = "Composer")
                }
            }
        }

        rule.onNodeWithText("Library").assertIsDisplayed()
        rule.onNodeWithText("Every score, ready offline").assertIsDisplayed()
        rule.onNodeWithText("COMPOSER").assertIsDisplayed()
    }

    @Test
    fun truncatedTextShowsFullTitleInTooltipOnlyWhenCutShort() {
        val longTitle = "Variations on a Theme by Haydn, Op. 56a, for two pianos and orchestra"
        var text by mutableStateOf("Bach")
        rule.setContent {
            LunarTheme(skin = AppSkin.PAPER) {
                TruncatedText(
                    text = text,
                    modifier = Modifier.width(120.dp),
                )
            }
        }

        rule.waitForIdle()
        assertTrue(rule.onAllNodes(hasLongClick).fetchSemanticsNodes().isEmpty(), "Short text should not offer a tooltip")

        text = longTitle
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodes(hasLongClick).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(hasLongClick).performSemanticsAction(SemanticsActions.OnLongClick)
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText(longTitle).fetchSemanticsNodes().size > 1
        }
    }

    @Test
    fun leaderRowKeepsTrailingContentVisibleNextToLongTitle() {
        rule.setContent {
            LunarTheme(skin = AppSkin.MOONLIGHT) {
                LeaderRow(
                    modifier = Modifier
                        .width(240.dp)
                        .testTag("row"),
                    title = {
                        TruncatedText("An exceptionally long setlist title that cannot possibly fit on one line")
                    },
                    trailing = { Text("12 pp", modifier = Modifier.testTag("trailing")) },
                )
            }
        }

        val row = rule.onNodeWithTag("row").getBoundsInRoot()
        val trailing = rule.onNodeWithTag("trailing").getBoundsInRoot()
        rule.onNodeWithTag("trailing").assertIsDisplayed()
        assertTrue(trailing.right <= row.right, "Trailing content overflowed the row")
        assertTrue(trailing.right > trailing.left, "Trailing content was squeezed to nothing")
    }

    @Test
    fun textToggleSelectsOption() {
        var selected by mutableStateOf("list")
        rule.setContent {
            LunarTheme(skin = AppSkin.PAPER) {
                TextToggle(
                    options = listOf(TextToggleOption("List", "list"), TextToggleOption("Gallery", "gallery")),
                    selected = selected,
                    onSelect = { selected = it },
                )
            }
        }

        rule.onNodeWithText("List").assertIsSelected()
        rule.onNodeWithText("Gallery").assertIsNotSelected().performClick()
        rule.runOnIdle { assertEquals("gallery", selected) }
        rule.onNodeWithText("Gallery").assertIsSelected()
    }

    @Test
    fun dialogShowsTitleBodyAndActions() {
        var dismissed = false
        rule.setContent {
            LunarTheme(skin = AppSkin.MOONLIGHT) {
                var open by remember { mutableStateOf(true) }
                if (open) {
                    LunarDialog(
                        title = "Delete setlist",
                        onDismissRequest = { open = false },
                        actions = {
                            LunarButton(text = "Cancel", onClick = { open = false }, style = LunarButtonStyle.TEXT)
                            LunarButton(
                                text = "Delete",
                                onClick = {
                                    dismissed = true
                                    open = false
                                },
                            )
                        },
                    ) {
                        Text("This cannot be undone.")
                    }
                }
            }
        }

        rule.onNodeWithText("Delete setlist").assertIsDisplayed()
        rule.onNodeWithText("This cannot be undone.").assertIsDisplayed()
        rule.onNodeWithText("Delete").performClick()
        rule.runOnIdle { assertTrue(dismissed) }
        assertTrue(rule.onAllNodesWithText("Delete setlist").fetchSemanticsNodes().isEmpty())
    }
}
