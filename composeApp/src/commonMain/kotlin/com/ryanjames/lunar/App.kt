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
import com.ryanjames.lunar.library.data.LibrarySnapshot
import com.ryanjames.lunar.library.model.LibrarySongbook
import com.ryanjames.lunar.platform.rememberPlatformRuntime
import com.ryanjames.lunar.ui.AppSection
import com.ryanjames.lunar.ui.ComposeScreen
import com.ryanjames.lunar.ui.FullscreenViewerScreen
import com.ryanjames.lunar.ui.ImportScreen
import com.ryanjames.lunar.ui.LibraryScreen
import com.ryanjames.lunar.ui.LunarTheme
import com.ryanjames.lunar.ui.SettingsScreen
import com.ryanjames.lunar.ui.ViewerDocumentState
import com.ryanjames.lunar.ui.ViewerScreen
import com.ryanjames.lunar.ui.ViewerTarget
import com.ryanjames.lunar.ui.currentLibraryScopeItems
import com.ryanjames.lunar.ui.lunarThemePalette
import com.ryanjames.lunar.ui.rememberLunarAppState
import com.ryanjames.lunar.ui.toViewerDocumentState
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
    val previewDocument = snapshot.resolveViewerDocument(appState.previewTarget)
    val fullscreenDocument = snapshot.resolveViewerDocument(appState.fullscreenTarget)
    val defaultTwoPageMode = appSettings.defaultViewerPageMode == ViewerPageModePreference.TWO_PAGE
    val libraryScopeItems = currentLibraryScopeItems(snapshot = snapshot, appState = appState)

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

        if (fullscreenDocument != null) {
            FullscreenViewerScreen(
                runtime = runtime,
                documentState = fullscreenDocument,
                onBack = appState::closeFullscreen,
                onToggleFavorite = appState.fullscreenTarget.favoriteToggle(appState),
                onHideScore = appState.fullscreenTarget.hideScore(appState),
                onOpenRandomScore = { appState.openRandomSheetInCurrentViewer(libraryScopeItems) },
                onPageChanged = appState.fullscreenTarget.pageChanged(appState),
                onPageCountResolved = appState.fullscreenTarget.pageCountResolved(appState),
                defaultTwoPageMode = defaultTwoPageMode,
                viewerKeybindings = appSettings.viewerKeybindings,
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
                            viewerKeybindings = appSettings.viewerKeybindings,
                            modifier = Modifier.padding(innerPadding),
                        )

                        AppSection.COMPOSE -> ComposeScreen(
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

                if (previewDocument != null) {
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
                                            documentState = previewDocument,
                                            onBack = appState::closePreview,
                                            onToggleFavorite = appState.previewTarget.favoriteToggle(appState),
                                            onHideScore = appState.previewTarget.hideScore(appState),
                                            onOpenRandomScore = { appState.openRandomSheetInCurrentViewer(libraryScopeItems) },
                                            onPageChanged = appState.previewTarget.pageChanged(appState),
                                            onPageCountResolved = appState.previewTarget.pageCountResolved(appState),
                                            defaultTwoPageMode = defaultTwoPageMode,
                                            viewerKeybindings = appSettings.viewerKeybindings,
                                            onEnterFullscreen = {
                                                when (val target = appState.previewTarget) {
                                                    is ViewerTarget.Score ->
                                                        appState.openFullscreen(target.itemId)

                                                    is ViewerTarget.Songbook ->
                                                        appState.openSongbookFullscreen(target.songbookId)

                                                    null -> Unit
                                                }
                                            },
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
                                                onClick = {
                                                    when (val target = appState.previewTarget) {
                                                        is ViewerTarget.Score ->
                                                            appState.openFullscreen(target.itemId)

                                                        is ViewerTarget.Songbook ->
                                                            appState.openSongbookFullscreen(target.songbookId)

                                                        null -> Unit
                                                    }
                                                },
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

private fun LibrarySnapshot.resolveViewerDocument(target: ViewerTarget?): ViewerDocumentState? = when (target) {
    is ViewerTarget.Score -> items
        .firstOrNull { it.id == target.itemId }
        ?.toViewerDocumentState()

    is ViewerTarget.Songbook -> songbooks
        .firstOrNull { it.id == target.songbookId }
        ?.toViewerDocumentState()

    null -> null
}

private fun LibrarySongbook.toViewerDocumentState(): ViewerDocumentState = ViewerDocumentState(
    id = id,
    title = name,
    subtitle = buildString {
        append("${itemIds.size} score")
        if (itemIds.size != 1) append("s")
        append(" combined")
        pageCount?.let {
            append("  |  ")
            append("$it pages")
        }
    },
    document = document,
    pageCount = pageCount,
)

private fun ViewerTarget?.favoriteToggle(appState: com.ryanjames.lunar.ui.LunarAppState): (() -> Unit)? =
    when (this) {
        is ViewerTarget.Score -> { { appState.toggleFavorite(itemId) } }
        is ViewerTarget.Songbook, null -> null
    }

private fun ViewerTarget?.hideScore(appState: com.ryanjames.lunar.ui.LunarAppState): (() -> Unit)? =
    when (this) {
        is ViewerTarget.Score -> { { appState.hideScore(itemId) } }
        is ViewerTarget.Songbook, null -> null
    }

private fun ViewerTarget?.pageChanged(appState: com.ryanjames.lunar.ui.LunarAppState): (Int) -> Unit =
    when (this) {
        is ViewerTarget.Score -> { pageIndex -> appState.updateViewerProgress(itemId, pageIndex) }
        is ViewerTarget.Songbook, null -> { _ -> }
    }

private fun ViewerTarget?.pageCountResolved(appState: com.ryanjames.lunar.ui.LunarAppState): (Int) -> Unit =
    when (this) {
        is ViewerTarget.Score -> { pageCount -> appState.updateViewerPageCount(itemId, pageCount) }
        is ViewerTarget.Songbook, null -> { _ -> }
    }

@Composable
internal fun BottomNavigationPanel(
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
                title = "Compose",
                selected = selectedSection == AppSection.COMPOSE,
                onClick = { onSelectSection(AppSection.COMPOSE) },
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
