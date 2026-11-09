package com.ryanjames.lunar

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ryanjames.lunar.library.data.LibrarySnapshot
import com.ryanjames.lunar.library.model.LibrarySongbook
import com.ryanjames.lunar.platform.externalScoreDropTarget
import com.ryanjames.lunar.platform.rememberPlatformRuntime
import com.ryanjames.lunar.ui.AppSection
import com.ryanjames.lunar.ui.ComposeScreen
import com.ryanjames.lunar.ui.ImportScreen
import com.ryanjames.lunar.ui.LibraryScreen
import com.ryanjames.lunar.ui.LunarTheme
import com.ryanjames.lunar.ui.SettingsScreen
import com.ryanjames.lunar.ui.ViewerDocumentState
import com.ryanjames.lunar.ui.ViewerTarget
import com.ryanjames.lunar.ui.design.LunarTestTags
import com.ryanjames.lunar.ui.design.lunarTokens
import com.ryanjames.lunar.ui.currentLibraryScopeItems
import com.ryanjames.lunar.ui.rememberLunarAppState
import com.ryanjames.lunar.ui.shell.LunarAppShell
import com.ryanjames.lunar.ui.toViewerDocumentState
import com.ryanjames.lunar.settings.ViewerPageModePreference
import com.ryanjames.lunar.settings.intervalMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * @param sidebarToggleRequests window-level shortcut requests (desktop Ctrl+B) to toggle the
 * sidebar even when nothing inside the app holds keyboard focus.
 */
@Composable
fun App(sidebarToggleRequests: Flow<Unit> = emptyFlow()) {
    val runtime = rememberPlatformRuntime()
    LunarApp(runtime, sidebarToggleRequests)
}

@Composable
internal fun LunarApp(
    runtime: com.ryanjames.lunar.platform.PlatformRuntime,
    sidebarToggleRequests: Flow<Unit> = emptyFlow(),
) {
    val appState = rememberLunarAppState(runtime)
    val snapshot by runtime.repository.library.collectAsState()
    val importerState by runtime.importer.state.collectAsState()
    val syncState by runtime.syncManager.state.collectAsState()
    val appSettings by runtime.settingsStore.settings.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var appReady by remember(runtime) { mutableStateOf(false) }
    var startupError by remember(runtime) { mutableStateOf<String?>(null) }
    val fullscreenDocument = snapshot.resolveViewerDocument(appState.fullscreenTarget)
        ?.let { document ->
            appState.viewerStartPage?.let { document.copy(lastViewedPage = it) } ?: document
        }
    val defaultTwoPageMode = appSettings.defaultViewerPageMode == ViewerPageModePreference.TWO_PAGE
    val libraryScopeItems = currentLibraryScopeItems(snapshot = snapshot, appState = appState)

    LaunchedEffect(
        runtime.repository,
        runtime.syncManager,
        runtime.sourceRegistry,
        runtime.compositionStore,
        runtime.settingsStore,
    ) {
        appReady = false
        startupError = null
        runCatching {
            runtime.settingsStore.initialize()
            runtime.repository.initialize()
            runtime.sourceRegistry.initialize()
            runtime.compositionStore.initialize()
            runtime.syncManager.initialize()
        }.onSuccess {
            appReady = true
            if (runtime.settingsStore.settings.value.refreshOnLaunch) {
                runtime.syncManager.refreshAllSources(
                    sources = runtime.sourceRegistry.sources.value,
                    force = false,
                )
            }
        }.onFailure { error ->
            startupError = error.message
                ?.takeIf(String::isNotBlank)
                ?: "Lunar could not load its local library."
        }
    }

    if (!appReady) {
        LunarTheme(skin = appSettings.resolvedSkin) {
            StartupLoadingScreen(errorMessage = startupError)
        }
        return
    }

    LaunchedEffect(appReady, appSettings.defaultLibraryLayout) {
        if (!appReady) return@LaunchedEffect
        appState.applySettings(appSettings)
    }

    LaunchedEffect(appReady, appSettings.autoRefreshSchedule, runtime.syncManager, runtime.sourceRegistry) {
        if (!appReady) return@LaunchedEffect
        val intervalMillis = appSettings.autoRefreshSchedule.intervalMillis ?: return@LaunchedEffect
        while (true) {
            delay(intervalMillis)
            runtime.syncManager.refreshAllSources(
                sources = runtime.sourceRegistry.sources.value,
                force = false,
            )
        }
    }

    LaunchedEffect(appState, sidebarToggleRequests) {
        sidebarToggleRequests.collect { appState.toggleSidebarCollapsed() }
    }

    LaunchedEffect(appState.bannerMessage) {
        val message = appState.bannerMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        appState.clearBanner()
    }

    LunarTheme(skin = appSettings.resolvedSkin) {
        // The viewer replaces the main screens, so keep their saveable state (e.g. lazy scroll positions)
        // while it is open and restore it on return.
        val mainScreensStateHolder = rememberSaveableStateHolder()

        if (fullscreenDocument != null) {
            ViewerScreen(
                runtime = runtime,
                documentState = fullscreenDocument,
                onBack = appState::closeFullscreen,
                onToggleFavorite = appState.fullscreenTarget.favoriteToggle(appState),
                onHideScore = appState.fullscreenTarget.hideScore(appState),
                onAddToSetlist = appState.fullscreenTarget.addToSetlist(appState),
                onAddToSongbook = appState.fullscreenTarget.addToSongbook(appState),
                onOpenRandomScore = { appState.openRandomSheetInCurrentViewer(libraryScopeItems) },
                onPageChanged = appState.fullscreenTarget.pageChanged(appState),
                onPageCountResolved = appState.fullscreenTarget.pageCountResolved(appState),
                defaultTwoPageMode = defaultTwoPageMode,
                viewerKeybindings = appSettings.viewerKeybindings,
                chromeAutoHideMillis = appSettings.viewerChromeAutoHideMillis,
            )
        } else {
            mainScreensStateHolder.SaveableStateProvider(MainScreensStateKey) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .externalScoreDropTarget(
                            enabled = runtime.capabilities.fileImportSupported,
                            onDroppedPaths = appState::importDroppedPaths,
                        ),
                ) {
                    LunarAppShell(
                        appState = appState,
                        snapshot = snapshot,
                        syncState = syncState,
                        settings = appSettings,
                    ) { sidebarContextVisible ->
                        Scaffold(
                            containerColor = Color.Transparent,
                            snackbarHost = { SnackbarHost(snackbarHostState) },
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

                                AppSection.LIBRARY,
                                AppSection.SETLISTS,
                                AppSection.SONGBOOKS -> LibraryScreen(
                                    runtime = runtime,
                                    snapshot = snapshot,
                                    appState = appState,
                                    viewerKeybindings = appSettings.viewerKeybindings,
                                    showArrangementSwitcher = !sidebarContextVisible,
                                    modifier = Modifier.padding(innerPadding),
                                )

                                AppSection.COMPOSE -> ComposeScreen(
                                    runtime = runtime,
                                    modifier = Modifier.padding(innerPadding),
                                )

                                AppSection.SETTINGS -> SettingsScreen(
                                    settings = appSettings,
                                    appState = appState,
                                    modifier = Modifier.padding(innerPadding),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val MainScreensStateKey = "main-screens"

@Composable
private fun StartupLoadingScreen(
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "startup-loading")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
        ),
        label = "startup-spinner",
    )
    val description = if (errorMessage == null) {
        "Lunar is starting"
    } else {
        "Lunar startup failed"
    }
    val tokens = lunarTokens()

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(LunarTestTags.STARTUP_SCREEN)
            .background(tokens.colors.bg)
            .semantics {
                contentDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
        ) {
            StartupSpinner(
                rotation = rotation,
                color = if (errorMessage == null) tokens.colors.accent else tokens.colors.danger,
            )
            Text(
                text = errorMessage ?: "Loading Lunar",
                style = tokens.typography.subtitle,
                color = if (errorMessage == null) tokens.colors.ink2 else tokens.colors.danger,
            )
        }
    }
}

@Composable
private fun StartupSpinner(
    rotation: Float,
    color: Color,
) {
    Canvas(modifier = Modifier.size(44.dp)) {
        val strokeWidth = 4.dp.toPx()
        val inset = strokeWidth / 2f
        val arcSize = Size(
            width = size.width - strokeWidth,
            height = size.height - strokeWidth,
        )
        val topLeft = Offset(inset, inset)
        drawArc(
            color = color.copy(alpha = 0.24f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
        drawArc(
            color = color,
            startAngle = rotation,
            sweepAngle = 88f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
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

private fun ViewerTarget?.addToSetlist(appState: com.ryanjames.lunar.ui.LunarAppState): (() -> Unit)? =
    when (this) {
        is ViewerTarget.Score -> { { appState.showSetlistPickerForScore(itemId) } }
        is ViewerTarget.Songbook, null -> null
    }

private fun ViewerTarget?.addToSongbook(appState: com.ryanjames.lunar.ui.LunarAppState): (() -> Unit)? =
    when (this) {
        is ViewerTarget.Score -> {
            if (appState.canCreateSongbooks) {
                { appState.showSongbookPickerForScore(itemId) }
            } else {
                null
            }
        }
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
