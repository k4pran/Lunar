package com.ryanjames.lunar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ryanjames.lunar.settings.AppSettings
import com.ryanjames.lunar.settings.AppSkin
import com.ryanjames.lunar.settings.AutoRefreshSchedule
import com.ryanjames.lunar.settings.CacheLimitPreset
import com.ryanjames.lunar.settings.ViewerChromeAutoHideOptions
import com.ryanjames.lunar.settings.ViewerPageModePreference
import com.ryanjames.lunar.settings.ViewerShortcutAction
import com.ryanjames.lunar.ui.design.LunarButton
import com.ryanjames.lunar.ui.design.LunarButtonStyle
import com.ryanjames.lunar.ui.design.LunarIcon
import com.ryanjames.lunar.ui.design.LunarIconKind
import com.ryanjames.lunar.ui.design.LunarTestTags
import com.ryanjames.lunar.ui.design.PageHeading
import com.ryanjames.lunar.ui.design.SmallCapsLabel
import com.ryanjames.lunar.ui.design.TextToggle
import com.ryanjames.lunar.ui.design.TextToggleOption
import com.ryanjames.lunar.ui.design.lunarColors
import com.ryanjames.lunar.ui.design.lunarTokens

private enum class SettingsPage(val label: String) {
    GENERAL("General"),
    KEYBINDINGS("Keybindings"),
}

/** Below this width a settings row stacks its control under the label. */
private val SettingsRowStackWidth = 560.dp

/**
 * Settings as inline rows. Every change goes straight to the settings store through
 * [LunarAppState], so there is no Save button and no confirmation dialog.
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    appState: LunarAppState,
    modifier: Modifier = Modifier,
) {
    val tokens = lunarTokens()
    var selectedPage by rememberSaveable { mutableStateOf(SettingsPage.GENERAL) }
    var pendingBindingAction by remember { mutableStateOf<ViewerShortcutAction?>(null) }
    var bindingFeedback by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(selectedPage, pendingBindingAction) {
        if (selectedPage == SettingsPage.KEYBINDINGS) {
            focusRequester.requestFocus()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.colors.bg)
            .testTag(LunarTestTags.SETTINGS_PANEL)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val action = pendingBindingAction ?: return@onPreviewKeyEvent false
                if (selectedPage != SettingsPage.KEYBINDINGS || event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }

                val keyId = viewerShortcutKeyId(event.key)
                if (keyId == null) {
                    bindingFeedback = "That key is not supported yet. Try letters, numbers, arrows, function keys, Home/End, Page Up/Down, minus, equals, Space, or Enter."
                    return@onPreviewKeyEvent true
                }

                val conflictingAction = settings.viewerKeybindings.actionForKeyId(keyId)
                if (conflictingAction != null && conflictingAction != action) {
                    bindingFeedback = "${viewerShortcutKeyLabel(keyId)} is already bound to ${conflictingAction.displayLabel().lowercase()}."
                    return@onPreviewKeyEvent true
                }

                appState.updateViewerKeybinding(action = action, keyId = keyId)
                bindingFeedback = "${action.displayLabel()} set to ${viewerShortcutKeyLabel(keyId)}."
                pendingBindingAction = null
                true
            }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = tokens.spacing.xxl, vertical = tokens.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.xl),
    ) {
        PageHeading(
            title = "Settings",
            subtitle = "Changes are saved as soon as you make them.",
        )

        SettingsTabs(
            selectedPage = selectedPage,
            onSelect = { page ->
                selectedPage = page
                pendingBindingAction = null
                bindingFeedback = null
            },
        )

        when (selectedPage) {
            SettingsPage.GENERAL -> GeneralSettingsPage(
                settings = settings,
                appState = appState,
            )

            SettingsPage.KEYBINDINGS -> KeybindingsSettingsPage(
                settings = settings,
                appState = appState,
                pendingBindingAction = pendingBindingAction,
                bindingFeedback = bindingFeedback,
                onStartBinding = { action ->
                    pendingBindingAction = action
                    bindingFeedback = "Press a supported key for ${action.displayLabel().lowercase()}."
                },
                onCancelBinding = {
                    pendingBindingAction = null
                    bindingFeedback = "Key capture cancelled."
                },
                onReset = {
                    pendingBindingAction = null
                    bindingFeedback = null
                    appState.resetViewerKeybindings()
                },
            )
        }
    }
}

@Composable
private fun SettingsTabs(
    selectedPage: SettingsPage,
    onSelect: (SettingsPage) -> Unit,
) {
    val tokens = lunarTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xl),
    ) {
        SettingsPage.entries.forEach { page ->
            val selected = page == selectedPage
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .testTag(LunarTestTags.settingsTab(page.name))
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { onSelect(page) },
                    ),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
            ) {
                Text(
                    text = page.label,
                    style = tokens.typography.button,
                    color = if (selected) tokens.colors.ink else tokens.colors.muted,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(if (selected) tokens.colors.accent else tokens.colors.bg),
                )
            }
        }
    }
}

@Composable
private fun GeneralSettingsPage(
    settings: AppSettings,
    appState: LunarAppState,
) {
    val tokens = lunarTokens()
    SettingsSection(title = "Appearance") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(LunarTestTags.SETTINGS_APPEARANCE_PANEL)
                .padding(vertical = tokens.spacing.md),
        ) {
            SkinPicker(
                selected = settings.resolvedSkin,
                onSelect = appState::updateSkin,
            )
        }
    }

    SettingsSection(title = "Layout") {
        SettingsChoiceRow(
            key = "start_sidebar_collapsed",
            label = "Start with the sidebar collapsed",
            supportingText = "Compose collapses it while you write and restores it when you leave.",
            selected = settings.sidebarCollapsed,
            options = listOf(
                TextToggleOption("On", true),
                TextToggleOption("Off", false),
            ),
            onSelect = appState::updateStartSidebarCollapsed,
        )
        SettingsChoiceRow(
            key = "library_layout",
            label = "Library layout",
            selected = appState.layoutMode,
            options = listOf(
                TextToggleOption("List", LibraryLayoutMode.LIST),
                TextToggleOption("Gallery", LibraryLayoutMode.GRID),
            ),
            onSelect = appState::updateLayoutMode,
        )
    }

    SettingsSection(title = "Viewer") {
        SettingsChoiceRow(
            key = "default_page_view",
            label = "Default page view",
            selected = settings.defaultViewerPageMode,
            options = listOf(
                TextToggleOption("1 page", ViewerPageModePreference.SINGLE_PAGE),
                TextToggleOption("2 pages", ViewerPageModePreference.TWO_PAGE),
            ),
            onSelect = appState::updateDefaultViewerPageMode,
        )
        SettingsChoiceRow(
            key = "viewer_toolbar_auto_hide",
            label = "Hide the viewer toolbar after",
            supportingText = "Moving the pointer, tapping or pressing a key brings it back.",
            selected = settings.viewerChromeAutoHideSeconds,
            options = ViewerChromeAutoHideOptions.map { seconds ->
                TextToggleOption(if (seconds == null) "Never" else "${seconds}s", seconds)
            },
            onSelect = appState::updateViewerChromeAutoHideSeconds,
        )
    }

    SettingsSection(title = "Sync") {
        SettingsChoiceRow(
            key = "refresh_on_launch",
            label = "Refresh on launch",
            supportingText = "Turn off for a faster offline start.",
            selected = settings.refreshOnLaunch,
            options = listOf(
                TextToggleOption("On", true),
                TextToggleOption("Off", false),
            ),
            onSelect = appState::updateRefreshOnLaunch,
        )
        SettingsChoiceRow(
            key = "auto_refresh",
            label = "Auto refresh",
            selected = settings.autoRefreshSchedule,
            options = listOf(
                TextToggleOption("Off", AutoRefreshSchedule.DISABLED),
                TextToggleOption("5 min", AutoRefreshSchedule.MINUTES_5),
                TextToggleOption("15 min", AutoRefreshSchedule.MINUTES_15),
                TextToggleOption("30 min", AutoRefreshSchedule.MINUTES_30),
                TextToggleOption("Hourly", AutoRefreshSchedule.HOURLY),
                TextToggleOption("Daily", AutoRefreshSchedule.DAILY),
            ),
            onSelect = appState::updateAutoRefreshSchedule,
        )
        SettingsChoiceRow(
            key = "connect_timeout",
            label = "Connect timeout",
            selected = settings.cloudConnectTimeoutSeconds,
            options = listOf(10, 15, 30, 60).map { seconds -> TextToggleOption("${seconds}s", seconds) },
            onSelect = appState::updateCloudConnectTimeout,
        )
        SettingsChoiceRow(
            key = "read_timeout",
            label = "Read timeout",
            selected = settings.cloudReadTimeoutSeconds,
            options = listOf(30, 60, 120, 300).map { seconds -> TextToggleOption("${seconds}s", seconds) },
            onSelect = appState::updateCloudReadTimeout,
        )
    }

    SettingsSection(title = "Storage") {
        SettingsChoiceRow(
            key = "cache_budget",
            label = "Cache budget",
            supportingText = "A soft cap: Lunar keeps scores on disk for offline viewing and warns when you go over.",
            selected = settings.cacheLimit,
            options = CacheLimitPreset.entries.map { preset -> TextToggleOption(preset.label(), preset) },
            onSelect = appState::updateCacheLimit,
        )
    }

    LunarButton(
        text = "Reset to defaults",
        onClick = appState::resetGlobalSettings,
        style = LunarButtonStyle.TEXT,
    )
}

@Composable
private fun KeybindingsSettingsPage(
    settings: AppSettings,
    appState: LunarAppState,
    pendingBindingAction: ViewerShortcutAction?,
    bindingFeedback: String?,
    onStartBinding: (ViewerShortcutAction) -> Unit,
    onCancelBinding: () -> Unit,
    onReset: () -> Unit,
) {
    val tokens = lunarTokens()
    SettingsSection(title = "Viewer keybindings") {
        Text(
            text = "Shortcuts used while a score viewer has focus, plus the random-score shortcut in the library. " +
                "Supported keys include letters, numbers, arrows, function keys, Home/End, Page Up/Down, minus, equals, Space, and Enter.",
            style = tokens.typography.bodySmall,
            color = tokens.colors.muted,
            modifier = Modifier.padding(vertical = tokens.spacing.sm),
        )
        bindingFeedback?.let { message ->
            Text(
                text = message,
                style = tokens.typography.bodySmall,
                color = tokens.colors.accent,
                modifier = Modifier.padding(bottom = tokens.spacing.sm),
            )
        }

        ViewerShortcutAction.entries.forEach { action ->
            ViewerKeybindingRow(
                action = action,
                keyLabel = viewerShortcutKeyLabel(settings.viewerKeybindings.bindingFor(action)),
                isCapturing = pendingBindingAction == action,
                onBind = { onStartBinding(action) },
                onClear = { appState.clearViewerKeybinding(action) },
                onCancel = onCancelBinding,
            )
        }
    }

    LunarButton(
        text = "Reset keybindings",
        onClick = onReset,
        style = LunarButtonStyle.TEXT,
    )
}

@Composable
private fun ViewerKeybindingRow(
    action: ViewerShortcutAction,
    keyLabel: String,
    isCapturing: Boolean,
    onBind: () -> Unit,
    onClear: () -> Unit,
    onCancel: () -> Unit,
) {
    val tokens = lunarTokens()
    SettingsRow(
        key = "keybinding_${action.name.lowercase()}",
        label = action.displayLabel(),
        supportingText = action.supportingText(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(min = 72.dp)
                    .border(
                        width = 1.dp,
                        color = if (isCapturing) tokens.colors.accent else tokens.colors.rule,
                        shape = tokens.shapes.small,
                    )
                    .background(tokens.colors.field, tokens.shapes.small)
                    .padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isCapturing) "Press a key..." else keyLabel,
                    style = tokens.typography.bodySmall,
                    color = if (isCapturing) tokens.colors.accent else tokens.colors.ink,
                )
            }
            LunarButton(
                text = if (isCapturing) "Cancel" else "Bind",
                onClick = if (isCapturing) onCancel else onBind,
                style = LunarButtonStyle.TEXT,
            )
            LunarButton(
                text = "Clear",
                onClick = onClear,
                style = LunarButtonStyle.TEXT,
                enabled = keyLabel != "Unbound",
            )
        }
    }
}

/** A titled group of ruled rows. */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = lunarTokens()
    Column(modifier = Modifier.fillMaxWidth()) {
        SmallCapsLabel(text = title, modifier = Modifier.padding(bottom = tokens.spacing.xs))
        HorizontalDivider(color = tokens.colors.strong)
        content()
    }
}

/** An inline choice that saves as soon as a different option is picked. */
@Composable
private fun <T> SettingsChoiceRow(
    key: String,
    label: String,
    selected: T,
    options: List<TextToggleOption<T>>,
    onSelect: (T) -> Unit,
    supportingText: String? = null,
) {
    SettingsRow(
        key = key,
        label = label,
        supportingText = supportingText,
    ) {
        TextToggle(
            options = options,
            selected = selected,
            onSelect = { value -> if (value != selected) onSelect(value) },
        )
    }
}

/**
 * A ruled row with a label (and optional supporting text) beside its control, stacking the
 * control underneath when the window is narrow.
 */
@Composable
private fun SettingsRow(
    key: String,
    label: String,
    supportingText: String?,
    control: @Composable () -> Unit,
) {
    val tokens = lunarTokens()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(LunarTestTags.settingsRow(key))
            .padding(vertical = tokens.spacing.md),
    ) {
        val labelBlock: @Composable (Modifier) -> Unit = { labelModifier ->
            Column(
                modifier = labelModifier,
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.xxs),
            ) {
                Text(text = label, style = tokens.typography.body, color = tokens.colors.ink)
                supportingText?.let { hint ->
                    Text(text = hint, style = tokens.typography.bodySmall, color = tokens.colors.muted)
                }
            }
        }
        if (maxWidth < SettingsRowStackWidth) {
            Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
                labelBlock(Modifier.fillMaxWidth())
                control()
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                labelBlock(Modifier.weight(1f))
                control()
            }
        }
    }
    HorizontalDivider(color = tokens.colors.rule)
}

private data class SkinDescription(
    val skin: AppSkin,
    val name: String,
    val blurb: String,
)

private val SkinDescriptions = listOf(
    SkinDescription(AppSkin.PAPER, "Paper", "Warm ivory pages with oxblood accents, for daylight practice."),
    SkinDescription(AppSkin.MOONLIGHT, "Moonlight", "Deep slate with silvered gold, easy on the eyes on a dim stage."),
)

@Composable
private fun SkinPicker(
    selected: AppSkin,
    onSelect: (AppSkin) -> Unit,
) {
    val tokens = lunarTokens()
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
    ) {
        SkinDescriptions.forEach { description ->
            SkinPreviewCard(
                description = description,
                selected = description.skin == selected,
                onClick = { onSelect(description.skin) },
            )
        }
    }
}

/**
 * A miniature of the skin drawn from its own colour roles, so it previews correctly whichever skin
 * is active.
 */
@Composable
private fun SkinPreviewCard(
    description: SkinDescription,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = lunarTokens()
    val preview = lunarColors(description.skin)
    Column(
        modifier = Modifier
            .widthIn(min = 200.dp, max = 260.dp)
            .testTag(LunarTestTags.skinOption(description.skin))
            .clip(tokens.shapes.large)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) tokens.colors.accent else tokens.colors.rule,
                shape = tokens.shapes.large,
            )
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(tokens.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(tokens.shapes.medium)
                .background(preview.bg)
                .border(1.dp, preview.rule, tokens.shapes.medium)
                .padding(tokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
        ) {
            Text(
                text = "Nocturnes",
                style = tokens.typography.heading,
                color = preview.ink,
            )
            Text(
                text = "Op. 9, No. 2",
                style = tokens.typography.subtitle,
                color = preview.muted,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = tokens.spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(preview.rule),
                )
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 14.dp)
                        .background(preview.accent, tokens.shapes.extraSmall),
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = description.name,
                style = tokens.typography.button,
                color = tokens.colors.ink,
            )
            if (selected) {
                LunarIcon(
                    kind = LunarIconKind.CHECK,
                    contentDescription = "Selected",
                    size = 16.dp,
                    tint = tokens.colors.accent,
                )
            }
        }
        Text(
            text = description.blurb,
            style = tokens.typography.bodySmall,
            color = tokens.colors.muted,
        )
    }
}
