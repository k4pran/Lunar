package com.ryanjames.lunar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ryanjames.lunar.platform.rememberPlatformRuntime
import com.ryanjames.lunar.ui.AppSection
import com.ryanjames.lunar.ui.FullscreenViewerScreen
import com.ryanjames.lunar.ui.ImportScreen
import com.ryanjames.lunar.ui.LibraryScreen
import com.ryanjames.lunar.ui.LunarTheme
import com.ryanjames.lunar.ui.SettingsScreen
import com.ryanjames.lunar.ui.ViewerScreen
import com.ryanjames.lunar.ui.lunarThemePalette
import com.ryanjames.lunar.ui.rememberLunarAppState
import com.ryanjames.lunar.settings.ViewerPageModePreference
import com.ryanjames.lunar.settings.intervalMillis
import kotlinx.coroutines.delay

@Composable
fun App() {
    val runtime = rememberPlatformRuntime()
    val appState = rememberLunarAppState(runtime)
    val snapshot by runtime.repository.library.collectAsState()
    val importerState by runtime.importer.state.collectAsState()
    val syncState by runtime.syncManager.state.collectAsState()
    val appSettings by runtime.settingsStore.settings.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val previewItem = snapshot.items.firstOrNull { it.id == appState.previewItemId }
    val fullscreenItem = snapshot.items.firstOrNull { it.id == appState.fullscreenItemId }
    val defaultTwoPageMode = appSettings.defaultViewerPageMode == ViewerPageModePreference.TWO_PAGE

    LaunchedEffect(runtime.repository, runtime.syncManager, runtime.sourceRegistry, runtime.settingsStore) {
        runtime.repository.initialize()
        runtime.sourceRegistry.initialize()
        runtime.settingsStore.initialize()
        runtime.syncManager.initialize()
        if (runtime.settingsStore.settings.value.refreshOnLaunch) {
            runtime.syncManager.refreshAllSources(
                sources = runtime.sourceRegistry.sources.value,
                force = false,
            )
        }
    }

    LaunchedEffect(appSettings.defaultLibraryLayout) {
        appState.applySettings(appSettings)
    }

    LaunchedEffect(appSettings.autoRefreshSchedule, runtime.syncManager, runtime.sourceRegistry) {
        val intervalMillis = appSettings.autoRefreshSchedule.intervalMillis ?: return@LaunchedEffect
        while (true) {
            delay(intervalMillis)
            runtime.syncManager.refreshAllSources(
                sources = runtime.sourceRegistry.sources.value,
                force = false,
            )
        }
    }

    LaunchedEffect(appState.bannerMessage) {
        val message = appState.bannerMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        appState.clearBanner()
    }

    LunarTheme(theme = appSettings.theme) {
        val themePalette = lunarThemePalette()

        if (fullscreenItem != null) {
            FullscreenViewerScreen(
                runtime = runtime,
                item = fullscreenItem,
                onBack = appState::closeFullscreen,
                onToggleFavorite = { appState.toggleFavorite(fullscreenItem.id) },
                onPageChanged = { pageIndex ->
                    appState.updateViewerProgress(fullscreenItem.id, pageIndex)
                },
                onPageCountResolved = { pageCount ->
                    appState.updateViewerPageCount(fullscreenItem.id, pageCount)
                },
                defaultTwoPageMode = defaultTwoPageMode,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background,
                                themePalette.appBackgroundGradientBottom,
                            )
                        )
                    ),
            ) {
                Scaffold(
                    containerColor = Color.Transparent,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        BottomNavigationPanel(
                            selectedSection = appState.selectedSection,
                            onSelectSection = appState::selectSection,
                        )
                    },
                ) { innerPadding ->
                    when (appState.selectedSection) {
                        AppSection.IMPORT -> ImportScreen(
                            runtime = runtime,
                            importerState = importerState,
                            syncState = syncState,
                            settings = appSettings,
                            libraryCount = snapshot.items.size,
                            appState = appState,
                            modifier = Modifier.padding(innerPadding),
                        )

                        AppSection.LIBRARY -> LibraryScreen(
                            runtime = runtime,
                            snapshot = snapshot,
                            appState = appState,
                            modifier = Modifier.padding(innerPadding),
                        )

                        AppSection.SETTINGS -> SettingsScreen(
                            settings = appSettings,
                            appState = appState,
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }

                if (previewItem != null) {
                    Dialog(
                        onDismissRequest = appState::closePreview,
                        properties = DialogProperties(
                            usePlatformDefaultWidth = false,
                        ),
                    ) {
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            val horizontalMargin = if (maxWidth < 720.dp) 12.dp else 28.dp
                            val verticalMargin = if (maxHeight < 720.dp) 12.dp else 24.dp
                            val dialogWidth = ((maxHeight - verticalMargin * 2) * 1.32f)
                                .coerceAtMost(maxWidth - horizontalMargin * 2)
                                .coerceAtLeast(320.dp)
                            val dialogHeight = (maxHeight - verticalMargin * 2).coerceAtLeast(360.dp)

                            Surface(
                                modifier = Modifier
                                    .width(dialogWidth)
                                    .height(dialogHeight),
                                shape = MaterialTheme.shapes.extraLarge,
                                tonalElevation = 10.dp,
                                color = MaterialTheme.colorScheme.surface,
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        ViewerScreen(
                                            runtime = runtime,
                                            item = previewItem,
                                            onBack = appState::closePreview,
                                            onToggleFavorite = { appState.toggleFavorite(previewItem.id) },
                                            onPageChanged = { pageIndex ->
                                                appState.updateViewerProgress(previewItem.id, pageIndex)
                                            },
                                            onPageCountResolved = { pageCount ->
                                                appState.updateViewerPageCount(previewItem.id, pageCount)
                                            },
                                            defaultTwoPageMode = defaultTwoPageMode,
                                            backButtonLabel = "Close",
                                        )
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                        shape = MaterialTheme.shapes.extraLarge,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            androidx.compose.material3.OutlinedButton(
                                                onClick = appState::closePreview,
                                            ) {
                                                Text("Close")
                                            }
                                            androidx.compose.material3.Button(
                                                onClick = { appState.openFullscreen(previewItem.id) },
                                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                ),
                                            ) {
                                                Text("View Fullscreen", color = MaterialTheme.colorScheme.onPrimary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNavigationPanel(
    selectedSection: AppSection,
    onSelectSection: (AppSection) -> Unit,
) {
    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BottomNavItem(
                title = "Sources",
                selected = selectedSection == AppSection.IMPORT,
                onClick = { onSelectSection(AppSection.IMPORT) },
            )
            BottomNavItem(
                title = "Library",
                selected = selectedSection == AppSection.LIBRARY,
                onClick = { onSelectSection(AppSection.LIBRARY) },
            )
            BottomNavItem(
                title = "Settings",
                selected = selectedSection == AppSection.SETTINGS,
                onClick = { onSelectSection(AppSection.SETTINGS) },
            )
        }
    }
}

@Composable
private fun RowScope.BottomNavItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }

    Surface(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick),
        color = containerColor,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
