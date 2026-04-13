package com.ryanjames.lunar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryanjames.lunar.settings.AppColorTheme
import com.ryanjames.lunar.settings.AppSettings
import com.ryanjames.lunar.settings.AutoRefreshSchedule
import com.ryanjames.lunar.settings.CacheLimitPreset
import com.ryanjames.lunar.settings.ViewerPageModePreference

@Composable
fun SettingsScreen(
    settings: AppSettings,
    appState: LunarAppState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
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

        SettingsSection(
            title = "Viewer",
            description = "Set the default page layout when you open a score.",
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
            description = "Switch the app palette without changing your data or layout.",
        ) {
            SettingsChoiceRow(
                label = "Color theme",
                selected = settings.theme,
                options = listOf(
                    ChoiceOption("Ocean", AppColorTheme.OCEAN),
                    ChoiceOption("Forest", AppColorTheme.FOREST),
                    ChoiceOption("Sunset", AppColorTheme.SUNSET),
                    ChoiceOption("Aurora", AppColorTheme.AURORA),
                    ChoiceOption("Darcula", AppColorTheme.DARCULA),
                    ChoiceOption("Midnight", AppColorTheme.MIDNIGHT),
                    ChoiceOption("Ember", AppColorTheme.EMBER),
                ),
                onSelect = appState::updateTheme,
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = appState::resetGlobalSettings,
                modifier = Modifier.weight(1f),
            ) {
                Text("Reset defaults")
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
private fun SettingsSection(
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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

private data class ChoiceOption<T>(
    val label: String,
    val value: T,
)
