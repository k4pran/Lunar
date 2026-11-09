package com.ryanjames.lunar

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ryanjames.lunar.library.data.LibrarySnapshot
import com.ryanjames.lunar.library.model.LibrarySongbook
import com.ryanjames.lunar.platform.externalScoreDropTarget
import com.ryanjames.lunar.platform.rememberPlatformRuntime
import com.ryanjames.lunar.ui.AppSection
import com.ryanjames.lunar.ui.ComposeScreen
import com.ryanjames.lunar.ui.FullscreenViewerScreen
import com.ryanjames.lunar.ui.ImportScreen
import com.ryanjames.lunar.ui.LibraryScreen
import com.ryanjames.lunar.ui.LunarTooltip
import com.ryanjames.lunar.ui.LunarTheme
import com.ryanjames.lunar.ui.SettingsScreen
import com.ryanjames.lunar.ui.ViewerDocumentState
import com.ryanjames.lunar.ui.ViewerScreen
import com.ryanjames.lunar.ui.ViewerTarget
import com.ryanjames.lunar.ui.TemporarySetlistSession
import com.ryanjames.lunar.ui.resolveSetlistItems
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
    LunarApp(runtime)
}

@Composable
internal fun LunarApp(runtime: com.ryanjames.lunar.platform.PlatformRuntime) {
    val appState = rememberLunarAppState(runtime)
    val snapshot by runtime.repository.library.collectAsState()
    val importerState by runtime.importer.state.collectAsState()
    val syncState by runtime.syncManager.state.collectAsState()
    val appSettings by runtime.settingsStore.settings.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var appReady by remember(runtime) { mutableStateOf(false) }
    var startupError by remember(runtime) { mutableStateOf<String?>(null) }
    val previewDocument = snapshot.resolveViewerDocument(appState.previewTarget, appState.temporarySetlist)
    val fullscreenDocument = snapshot.resolveViewerDocument(appState.fullscreenTarget, appState.temporarySetlist)
    val previewItemId = snapshot.viewerItemId(appState.previewTarget, appState.temporarySetlist)
    val fullscreenItemId = snapshot.viewerItemId(appState.fullscreenTarget, appState.temporarySetlist)
    val previewInSetlist = appState.previewTarget is ViewerTarget.Setlist
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
        StartupLoadingScreen(errorMessage = startupError)
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
                onToggleFavorite = favoriteToggle(fullscreenItemId, appState),
                onHideScore = hideScore(fullscreenItemId.takeUnless { fullscreenInSetlist }, appState),
                onAddToSetlist = addToSetlist(fullscreenItemId, appState),
                onAddToSongbook = addToSongbook(fullscreenItemId, appState),
                onOpenRandomScore = { appState.openRandomSheetInCurrentViewer(libraryScopeItems) }
                    .takeUnless { fullscreenInSetlist },
                onPageChanged = pageChanged(fullscreenItemId, appState),
                onPageCountResolved = pageCountResolved(fullscreenItemId, appState),
                onNextInSetlist = appState::nextInSetlist.takeIf { fullscreenInSetlist },
                nextInSetlistAvailable = appState.hasNextInSetlist(appState.fullscreenTarget),
                defaultTwoPageMode = defaultTwoPageMode,
                viewerKeybindings = appSettings.viewerKeybindings,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .externalScoreDropTarget(
                        enabled = runtime.capabilities.fileImportSupported,
                        onDroppedPaths = appState::importDroppedPaths,
                    )
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
                                            onToggleFavorite = favoriteToggle(previewItemId, appState),
                                            onHideScore = hideScore(previewItemId.takeUnless { previewInSetlist }, appState),
                                            onAddToSetlist = addToSetlist(previewItemId, appState),
                                            onAddToSongbook = addToSongbook(previewItemId, appState),
                                            onOpenRandomScore = { appState.openRandomSheetInCurrentViewer(libraryScopeItems) }
                                                .takeUnless { previewInSetlist },
                                            onPageChanged = pageChanged(previewItemId, appState),
                                            onPageCountResolved = pageCountResolved(previewItemId, appState),
                                            onNextInSetlist = appState::nextInSetlist.takeIf { previewInSetlist },
                                            nextInSetlistAvailable = appState.hasNextInSetlist(appState.previewTarget),
                                            defaultTwoPageMode = defaultTwoPageMode,
                                            viewerKeybindings = appSettings.viewerKeybindings,
                                            onEnterFullscreen = {
                                                when (val target = appState.previewTarget) {
                                                    is ViewerTarget.Score ->
                                                        appState.openFullscreen(target.itemId)

                                                    is ViewerTarget.Songbook ->
                                                        appState.openSongbookFullscreen(target.songbookId)

                                                    is ViewerTarget.Setlist ->
                                                        appState.openFullscreen(target)

                                                    null -> Unit
                                                }
                                            },
                                            backButtonLabel = if (previewInSetlist) "Back to programme" else "Close",
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
                                                Text(if (previewInSetlist) "Back to programme" else "Close")
                                            }
                                            androidx.compose.material3.Button(
                                                onClick = {
                                                    when (val target = appState.previewTarget) {
                                                        is ViewerTarget.Score ->
                                                            appState.openFullscreen(target.itemId)

                                                        is ViewerTarget.Songbook ->
                                                            appState.openSongbookFullscreen(target.songbookId)

                                                        is ViewerTarget.Setlist ->
                                                            appState.openFullscreen(target)

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF111820))
            .semantics {
                contentDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StartupSpinner(rotation = rotation)
            Text(
                text = errorMessage ?: "Loading Lunar",
                color = Color(0xFFE8F2F8),
            )
        }
    }
}

@Composable
private fun StartupSpinner(rotation: Float) {
    Canvas(modifier = Modifier.size(44.dp)) {
        val strokeWidth = 4.dp.toPx()
        val inset = strokeWidth / 2f
        val arcSize = Size(
            width = size.width - strokeWidth,
            height = size.height - strokeWidth,
        )
        val topLeft = Offset(inset, inset)
        drawArc(
            color = Color(0xFF8DD7FF).copy(alpha = 0.24f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
        drawArc(
            color = Color(0xFF8DD7FF),
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

    Box(modifier = Modifier.weight(1f)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
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
}
