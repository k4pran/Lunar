package com.ryanjames.lunar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryanjames.lunar.settings.AppSettings
import com.ryanjames.lunar.settings.AppSkin
import com.ryanjames.lunar.settings.AutoRefreshSchedule
import com.ryanjames.lunar.settings.CacheLimitPreset
import com.ryanjames.lunar.settings.ViewerChromeAutoHideOptions
import com.ryanjames.lunar.settings.ViewerPageModePreference
import com.ryanjames.lunar.settings.ViewerShortcutAction
import com.ryanjames.lunar.ui.design.LunarIcon
import com.ryanjames.lunar.ui.design.LunarIconKind
import com.ryanjames.lunar.ui.design.LunarTestTags
import com.ryanjames.lunar.ui.design.lunarColors
import com.ryanjames.lunar.ui.design.lunarTokens

private enum class SettingsPage {
    GENERAL,
    KEYBINDINGS,
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    appState: LunarAppState,
    modifier: Modifier = Modifier,
) {
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
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary,
                            )
                        )
                    )
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Global settings",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    text = "Choose how Lunar looks, refreshes, and behaves across desktop and Android.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
                )
            }
        }

        SettingsPageSwitcher(
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
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = {
                    if (selectedPage == SettingsPage.KEYBINDINGS) {
                        pendingBindingAction = null
                        bindingFeedback = null
                        appState.resetViewerKeybindings()
                    } else {
                        appState.resetGlobalSettings()
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(if (selectedPage == SettingsPage.KEYBINDINGS) "Reset keybindings" else "Reset defaults")
            }
            Button(
                onClick = { appState.selectSection(AppSection.LIBRARY) },
                modifier = Modifier.weight(1f),
            ) {
                Text("Back to library")
            }
        }
    }
}

@Composable
private fun SettingsPageSwitcher(
    selectedPage: SettingsPage,
    onSelect: (SettingsPage) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedPage == SettingsPage.GENERAL,
                onClick = { onSelect(SettingsPage.GENERAL) },
                label = { Text("General") },
            )
            FilterChip(
                selected = selectedPage == SettingsPage.KEYBINDINGS,
                onClick = { onSelect(SettingsPage.KEYBINDINGS) },
                label = { Text("Keybindings") },
            )
        }
    }
}

@Composable
private fun GeneralSettingsPage(
    settings: AppSettings,
    appState: LunarAppState,
) {
    SettingsSection(
        title = "Viewer",
        description = "Set the default page layout and how long the viewer controls stay on screen.",
    ) {
        SettingsChoiceRow(
            label = "Default page view",
            selected = settings.defaultViewerPageMode,
            options = listOf(
                ChoiceOption("1 page", ViewerPageModePreference.SINGLE_PAGE),
                ChoiceOption("2 pages", ViewerPageModePreference.TWO_PAGE),
            ),
            onSelect = appState::updateDefaultViewerPageMode,
        )
        SettingsChoiceRow(
            label = "Hide viewer controls",
            selected = settings.viewerChromeAutoHideSeconds,
            options = ViewerChromeAutoHideOptions.map { seconds ->
                ChoiceOption(if (seconds == null) "Never" else "After ${seconds}s", seconds)
            },
            onSelect = appState::updateViewerChromeAutoHideSeconds,
            supportingText = "Moving the pointer, tapping or pressing a key brings the controls back.",
        )
    }

    SettingsSection(
        title = "Library",
        description = "Choose the layout Lunar should use when you browse your scores.",
    ) {
        SettingsChoiceRow(
            label = "Default layout",
            selected = appState.layoutMode,
            options = listOf(
                ChoiceOption("List", LibraryLayoutMode.LIST),
                ChoiceOption("Grid", LibraryLayoutMode.GRID),
            ),
            onSelect = appState::updateLayoutMode,
        )
    }

    SettingsSection(
        title = "Appearance",
        description = "Choose a skin. Your library, layout and settings stay the same.",
        modifier = Modifier.testTag(LunarTestTags.SETTINGS_APPEARANCE_PANEL),
    ) {
        SkinPicker(
            selected = settings.resolvedSkin,
            onSelect = appState::updateSkin,
        )
    }

    SettingsSection(
        title = "Sync",
        description = "Control how aggressively Lunar checks cloud sources for updates.",
    ) {
        SettingsChoiceRow(
            label = "Refresh on launch",
            supportingText = "Useful to disable when you want a faster offline startup.",
            selected = settings.refreshOnLaunch,
            options = listOf(
                ChoiceOption("On", true),
                ChoiceOption("Off", false),
            ),
            onSelect = appState::updateRefreshOnLaunch,
        )
        SettingsChoiceRow(
            label = "Auto refresh",
            selected = settings.autoRefreshSchedule,
            options = listOf(
                ChoiceOption("Off", AutoRefreshSchedule.DISABLED),
                ChoiceOption("5 min", AutoRefreshSchedule.MINUTES_5),
                ChoiceOption("15 min", AutoRefreshSchedule.MINUTES_15),
                ChoiceOption("30 min", AutoRefreshSchedule.MINUTES_30),
                ChoiceOption("Hourly", AutoRefreshSchedule.HOURLY),
                ChoiceOption("Daily", AutoRefreshSchedule.DAILY),
            ),
            onSelect = appState::updateAutoRefreshSchedule,
        )
    }

    SettingsSection(
        title = "Network",
        description = "Tune how long Lunar waits for cloud APIs before timing out.",
    ) {
        SettingsChoiceRow(
            label = "Connect timeout",
            selected = settings.cloudConnectTimeoutSeconds,
            options = listOf(
                ChoiceOption("10s", 10),
                ChoiceOption("15s", 15),
                ChoiceOption("30s", 30),
                ChoiceOption("60s", 60),
            ),
            onSelect = appState::updateCloudConnectTimeout,
        )
        SettingsChoiceRow(
            label = "Read timeout",
            selected = settings.cloudReadTimeoutSeconds,
            options = listOf(
                ChoiceOption("30s", 30),
                ChoiceOption("60s", 60),
                ChoiceOption("120s", 120),
                ChoiceOption("300s", 300),
            ),
            onSelect = appState::updateCloudReadTimeout,
        )
    }

    SettingsSection(
        title = "Storage",
        description = "Set a preferred cache budget for the local PDF store.",
    ) {
        SettingsChoiceRow(
            label = "Cache budget",
            supportingText = "This is a soft cap for now. Lunar keeps managed scores on disk for offline viewing and warns when you go over budget.",
            selected = settings.cacheLimit,
            options = listOf(
                ChoiceOption("512 MB", CacheLimitPreset.MB_512),
                ChoiceOption("1 GB", CacheLimitPreset.GB_1),
                ChoiceOption("2 GB", CacheLimitPreset.GB_2),
                ChoiceOption("4 GB", CacheLimitPreset.GB_4),
                ChoiceOption("8 GB", CacheLimitPreset.GB_8),
                ChoiceOption("Unlimited", CacheLimitPreset.UNLIMITED),
            ),
            onSelect = appState::updateCacheLimit,
        )
    }
}

@Composable
private fun KeybindingsSettingsPage(
    settings: AppSettings,
    appState: LunarAppState,
    pendingBindingAction: ViewerShortcutAction?,
    bindingFeedback: String?,
    onStartBinding: (ViewerShortcutAction) -> Unit,
    onCancelBinding: () -> Unit,
) {
    SettingsSection(
        title = "Viewer keybindings",
        description = "Choose the keyboard shortcuts Lunar should use while a score viewer has focus, plus the random-score shortcut available from the library.",
    ) {
        bindingFeedback?.let { message ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }

        Text(
            text = "Supported keys include letters, numbers, arrows, function keys, Home/End, Page Up/Down, minus, equals, Space, and Enter.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                shape = MaterialTheme.shapes.large,
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = action.displayLabel(),
            style = MaterialTheme.typography.labelLarge,
        )
        action.supportingText()?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                color = if (isCapturing) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .weight(1f)
                    .sizeIn(minHeight = 44.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = if (isCapturing) "Press any supported key..." else keyLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCapturing) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            OutlinedButton(
                onClick = if (isCapturing) onCancel else onBind,
            ) {
                Text(if (isCapturing) "Cancel" else "Bind")
            }
            TextButton(
                onClick = onClear,
                enabled = keyLabel != "Unbound",
            ) {
                Text("Clear")
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                content()
            },
        )
    }
}

@Composable
private fun <T> SettingsChoiceRow(
    label: String,
    selected: T,
    options: List<ChoiceOption<T>>,
    onSelect: (T) -> Unit,
    supportingText: String? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
        )
        supportingText?.let { hint ->
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option.value == selected,
                    onClick = { onSelect(option.value) },
                    label = { Text(option.label) },
                )
            }
        }
    }
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

private data class ChoiceOption<T>(
    val label: String,
    val value: T,
)
