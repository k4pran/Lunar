package com.ryanjames.lunar

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import com.ryanjames.lunar.ui.MetronomeDialog
import com.ryanjames.lunar.ui.QuickFindEntry
import com.ryanjames.lunar.ui.QuickFindPalette
import com.ryanjames.lunar.ui.SettingsScreen
import com.ryanjames.lunar.ui.NextInSetlistButton
import com.ryanjames.lunar.ui.ViewerDocumentState
import com.ryanjames.lunar.ui.ViewerScreen
import com.ryanjames.lunar.ui.ViewerTarget
import com.ryanjames.lunar.ui.availableQuickFindActions
import com.ryanjames.lunar.ui.buildQuickFindEntries
import com.ryanjames.lunar.ui.isQuickFindShortcut
import com.ryanjames.lunar.ui.design.LunarTestTags
import com.ryanjames.lunar.ui.design.lunarTokens
import com.ryanjames.lunar.ui.TemporarySetlistSession
import com.ryanjames.lunar.ui.currentLibraryScopeItems
import com.ryanjames.lunar.ui.rememberLunarAppState
import com.ryanjames.lunar.ui.resolveSetlistItems
import com.ryanjames.lunar.ui.shell.LunarAppShell
import com.ryanjames.lunar.ui.toViewerDocumentState
import com.ryanjames.lunar.settings.ViewerPageModePreference
import com.ryanjames.lunar.settings.intervalMillis
import androidx.compose.runtime.withFrameNanos
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
    val fullscreenDocument = snapshot.resolveViewerDocument(appState.fullscreenTarget, appState.temporarySetlist)
        ?.let { document ->
            appState.viewerStartPage?.let { document.copy(lastViewedPage = it) } ?: document
        }
    val fullscreenItemId = snapshot.viewerItemId(appState.fullscreenTarget, appState.temporarySetlist)
    val fullscreenInSetlist = appState.fullscreenTarget is ViewerTarget.Setlist
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

    val rootFocusRequester = remember { FocusRequester() }
    var rootHasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(
        appState.quickFindVisible,
        appState.metronomeVisible,
        appState.selectedSection,
        appState.fullscreenTarget,
    ) {
        // Keep a focus target in the tree so Ctrl+K works even when no control has focus.
        if (appState.quickFindVisible || appState.metronomeVisible) return@LaunchedEffect
        withFrameNanos { }
        if (!rootHasFocus) {
            runCatching { rootFocusRequester.requestFocus() }
        }
    }

    LunarTheme(skin = appSettings.resolvedSkin) {
        // The viewer replaces the main screens, so keep their saveable state (e.g. lazy scroll positions)
        // while it is open and restore it on return.
        val mainScreensStateHolder = rememberSaveableStateHolder()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .quickFindShortcut(appState::openQuickFind)
                .onFocusChanged { rootHasFocus = it.hasFocus }
                .focusRequester(rootFocusRequester)
                .focusable(),
        ) {
            if (fullscreenDocument != null) {
                ViewerScreen(
                    runtime = runtime,
                    documentState = fullscreenDocument,
                    onBack = appState::closeFullscreen,
                    backLabel = if (fullscreenInSetlist) "Back to programme" else "Library",
                    onToggleFavorite = favoriteToggle(fullscreenItemId, appState),
                    onHideScore = hideScore(fullscreenItemId.takeUnless { fullscreenInSetlist }, appState),
                    onAddToSetlist = addToSetlist(fullscreenItemId, appState),
                    onAddToSongbook = addToSongbook(fullscreenItemId, appState),
                    onOpenRandomScore = { appState.openRandomSheetInCurrentViewer(libraryScopeItems) }
                        .takeUnless { fullscreenInSetlist },
                    onPageChanged = pageChanged(fullscreenItemId, appState),
                    onPageCountResolved = pageCountResolved(fullscreenItemId, appState),
                    nextInSetlist = if (fullscreenInSetlist) {
                        {
                            NextInSetlistButton(
                                enabled = appState.hasNextInSetlist(appState.fullscreenTarget),
                                onClick = appState::nextInSetlist,
                            )
                        }
                    } else {
                        null
                    },
                    defaultTwoPageMode = defaultTwoPageMode,
                    pageModeToggleRequest = appState.viewerPageModeToggleRequest,
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

        if (appState.quickFindVisible) {
            val hasFocusedScore = appState.quickFindTargetScoreId != null
            val entriesFor = remember(snapshot, appSettings.viewerKeybindings, hasFocusedScore) {
                { query: String ->
                    buildQuickFindEntries(
                        snapshot = snapshot,
                        query = query,
                        actions = availableQuickFindActions(hasFocusedScore),
                        keybindings = appSettings.viewerKeybindings,
                    )
                }
            }
            QuickFindPalette(
                entriesFor = entriesFor,
                onSelect = { entry ->
                    when (entry) {
                        is QuickFindEntry.Score -> appState.openScoreFromQuickFind(entry.item)
                        is QuickFindEntry.Setlist -> appState.openSetlistFromQuickFind(entry.setlist.id)
                        is QuickFindEntry.Songbook -> appState.openSongbookFromQuickFind(entry.songbook.id)
                        is QuickFindEntry.Action -> appState.runQuickFindAction(entry.action, libraryScopeItems)
                    }
                },
                onDismiss = appState::dismissQuickFind,
            )
        }

        if (appState.metronomeVisible) {
            MetronomeDialog(onDismiss = appState::dismissMetronome)
        }
    }
}

private fun Modifier.quickFindShortcut(onOpen: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    if (event.isQuickFindShortcut()) {
        onOpen()
        true
    } else {
        false
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

private fun LibrarySnapshot.resolveViewerDocument(
    target: ViewerTarget?,
    temporarySetlist: TemporarySetlistSession?,
): ViewerDocumentState? = when (target) {
    is ViewerTarget.Score -> items
        .firstOrNull { it.id == target.itemId }
        ?.toViewerDocumentState()

    is ViewerTarget.Songbook -> songbooks
        .firstOrNull { it.id == target.songbookId }
        ?.toViewerDocumentState()

    is ViewerTarget.Setlist -> {
        val pieces = resolveSetlistItems(this, target.setlistId, temporarySetlist)
        pieces.getOrNull(target.index)?.let { item ->
            item.toViewerDocumentState().copy(
                subtitle = listOfNotNull(
                    "Piece ${target.index + 1} of ${pieces.size}",
                    item.composer?.takeIf(String::isNotBlank),
                ).joinToString("  |  "),
            )
        }
    }

    null -> null
}

private fun LibrarySnapshot.viewerItemId(
    target: ViewerTarget?,
    temporarySetlist: TemporarySetlistSession?,
): String? = when (target) {
    is ViewerTarget.Score -> target.itemId
    is ViewerTarget.Setlist -> resolveSetlistItems(this, target.setlistId, temporarySetlist)
        .getOrNull(target.index)
        ?.id

    is ViewerTarget.Songbook, null -> null
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

private fun favoriteToggle(itemId: String?, appState: com.ryanjames.lunar.ui.LunarAppState): (() -> Unit)? =
    itemId?.let { id -> { appState.toggleFavorite(id) } }

private fun hideScore(itemId: String?, appState: com.ryanjames.lunar.ui.LunarAppState): (() -> Unit)? =
    itemId?.let { id -> { appState.hideScore(id) } }

private fun addToSetlist(itemId: String?, appState: com.ryanjames.lunar.ui.LunarAppState): (() -> Unit)? =
    itemId?.let { id -> { appState.showSetlistPickerForScore(id) } }

private fun addToSongbook(itemId: String?, appState: com.ryanjames.lunar.ui.LunarAppState): (() -> Unit)? =
    itemId
        ?.takeIf { appState.canCreateSongbooks }
        ?.let { id -> { appState.showSongbookPickerForScore(id) } }

private fun pageChanged(itemId: String?, appState: com.ryanjames.lunar.ui.LunarAppState): (Int) -> Unit =
    itemId?.let { id -> { pageIndex: Int -> appState.updateViewerProgress(id, pageIndex) } } ?: { _ -> }

private fun pageCountResolved(itemId: String?, appState: com.ryanjames.lunar.ui.LunarAppState): (Int) -> Unit =
    itemId?.let { id -> { pageCount: Int -> appState.updateViewerPageCount(id, pageCount) } } ?: { _ -> }
