package com.ryanjames.lunar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ryanjames.lunar.library.model.PdfDocumentReference
import com.ryanjames.lunar.library.model.SheetMusicItem
import com.ryanjames.lunar.platform.LilyPondLiveRenderResult
import com.ryanjames.lunar.platform.LilyPondSourceSnapshot
import com.ryanjames.lunar.platform.PlatformRuntime
import com.ryanjames.lunar.platform.RenderedPdfPage
import com.ryanjames.lunar.settings.ViewerKeybindings
import kotlinx.coroutines.delay

private const val MinZoom = 0.2f
private const val MaxZoom = 4.0f
private const val ZoomStep = 0.2f
private const val FitZoom = 1f
private const val PageGap = 20
private const val LilyPondLiveRefreshIntervalMillis = 1_000L
private val LilyPondSourceExtensions = setOf("ly", "ily", "lyi")

data class ViewerDocumentState(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val document: PdfDocumentReference,
    val lastViewedPage: Int = 0,
    val pageCount: Int? = null,
    val isFavorite: Boolean? = null,
    val isHidden: Boolean = false,
)

fun SheetMusicItem.toViewerDocumentState(): ViewerDocumentState = ViewerDocumentState(
    id = id,
    title = title,
    subtitle = composer,
    document = document,
    lastViewedPage = lastViewedPage,
    pageCount = pageCount,
    isFavorite = isFavorite,
    isHidden = isHidden,
)

private data class ActiveViewerDocument(
    val documentPath: String?,
    val statusLabel: String?,
    val liveErrorMessage: String?,
    val isPreparing: Boolean,
)

@Composable
private fun rememberActiveViewerDocument(
    runtime: PlatformRuntime,
    documentState: ViewerDocumentState,
): ActiveViewerDocument {
    val liveLilyPondEnabled = runtime.capabilities.lilyPondLiveViewingSupported &&
        documentState.isLilyPondSourceDocument()
    var sourceSnapshot by remember(documentState.id) { mutableStateOf<LilyPondSourceSnapshot?>(null) }
    var sourceUnavailableMessage by remember(documentState.id) { mutableStateOf<String?>(null) }
    var renderResult by remember(documentState.id) { mutableStateOf<LilyPondLiveRenderResult?>(null) }
    var renderErrorMessage by remember(documentState.id) { mutableStateOf<String?>(null) }
    var isRendering by remember(documentState.id) { mutableStateOf(false) }

    LaunchedEffect(documentState.id, liveLilyPondEnabled) {
        if (!liveLilyPondEnabled) {
            sourceSnapshot = null
            sourceUnavailableMessage = null
            renderResult = null
            renderErrorMessage = null
            isRendering = false
            return@LaunchedEffect
        }

        var lastRevision: String? = null
        var sourceWasAvailable = false
        while (true) {
            runCatching {
                runtime.lilyPondLiveRenderer.loadSource(documentState.document)
            }.onSuccess { nextSource ->
                if (nextSource == null) {
                    sourceWasAvailable = false
                    sourceSnapshot = null
                    renderResult = null
                    renderErrorMessage = null
                    sourceUnavailableMessage = "The LilyPond source file is unavailable; showing the imported PDF."
                } else {
                    sourceUnavailableMessage = null
                    if (!sourceWasAvailable || nextSource.revision != lastRevision) {
                        sourceWasAvailable = true
                        lastRevision = nextSource.revision
                        sourceSnapshot = nextSource
                    }
                }
            }.onFailure { error ->
                sourceWasAvailable = false
                sourceSnapshot = null
                renderResult = null
                renderErrorMessage = null
                sourceUnavailableMessage = error.message
                    ?.takeIf(String::isNotBlank)
                    ?: "The LilyPond source file is unavailable; showing the imported PDF."
            }
            delay(LilyPondLiveRefreshIntervalMillis)
        }
    }

    LaunchedEffect(documentState.id, liveLilyPondEnabled, sourceSnapshot?.revision) {
        if (!liveLilyPondEnabled) {
            return@LaunchedEffect
        }
        val source = sourceSnapshot ?: return@LaunchedEffect

        isRendering = true
        renderErrorMessage = null
        runCatching {
            runtime.lilyPondLiveRenderer.renderSource(source)
        }.onSuccess { result ->
            renderResult = result
            renderErrorMessage = null
        }.onFailure { error ->
            renderResult = null
            renderErrorMessage = error.message
                ?.takeIf(String::isNotBlank)
                ?: "LilyPond could not render this source."
        }
        isRendering = false
    }

    val documentPath = when {
        !liveLilyPondEnabled -> documentState.document.storedPath
        renderResult != null -> renderResult?.documentPath
        sourceUnavailableMessage != null -> documentState.document.storedPath
        else -> null
    }
    val statusLabel = when {
        !liveLilyPondEnabled -> null
        renderErrorMessage != null -> "LilyPond error"
        isRendering -> "Rendering LilyPond"
        renderResult != null -> "Live LilyPond"
        sourceUnavailableMessage != null -> "Imported PDF"
        else -> "Loading LilyPond"
    }
    val isPreparing = liveLilyPondEnabled &&
        renderErrorMessage == null &&
        sourceUnavailableMessage == null &&
        (renderResult == null || isRendering)

    return ActiveViewerDocument(
        documentPath = documentPath,
        statusLabel = statusLabel,
        liveErrorMessage = renderErrorMessage,
        isPreparing = isPreparing,
    )
}

@Composable
fun ViewerScreen(
    runtime: PlatformRuntime,
    documentState: ViewerDocumentState,
    onBack: () -> Unit,
    onToggleFavorite: (() -> Unit)? = null,
    onHideScore: (() -> Unit)? = null,
    onOpenRandomScore: (() -> Unit)? = null,
    onPageChanged: (Int) -> Unit,
    onPageCountResolved: (Int) -> Unit,
    defaultTwoPageMode: Boolean = false,
    viewerKeybindings: ViewerKeybindings = ViewerKeybindings(),
    onEnterFullscreen: (() -> Unit)? = null,
    backButtonLabel: String = "Back to library",
    modifier: Modifier = Modifier,
) {
    var currentPage by remember(documentState.id) {
        mutableIntStateOf(documentState.lastViewedPage.coerceAtLeast(0))
    }
    var zoom by remember(documentState.id) { mutableFloatStateOf(1f) }
    var twoPageMode by remember(documentState.id, defaultTwoPageMode) { mutableStateOf(defaultTwoPageMode) }
    var renderedPages by remember(documentState.id, currentPage, twoPageMode) {
        mutableStateOf<List<RenderedPdfPage>>(emptyList())
    }
    var errorMessage by remember(documentState.id, currentPage) { mutableStateOf<String?>(null) }
    var isLoading by remember(documentState.id, currentPage) { mutableStateOf(true) }
    var showMetronomeDialog by remember { mutableStateOf(false) }
    val themePalette = lunarThemePalette()
    val focusRequester = remember { FocusRequester() }
    val activeDocument = rememberActiveViewerDocument(
        runtime = runtime,
        documentState = documentState,
    )

    LaunchedEffect(documentState.id) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(documentState.id, activeDocument.documentPath, currentPage, twoPageMode) {
        isLoading = true
        errorMessage = null
        onPageChanged(currentPage)
        val documentPath = activeDocument.documentPath
        if (documentPath == null) {
            renderedPages = emptyList()
            isLoading = false
            return@LaunchedEffect
        }

        val info = runtime.renderer.inspect(documentPath)
        if (info != null) {
            onPageCountResolved(info.pageCount)
            if (currentPage > info.pageCount - 1) {
                currentPage = (info.pageCount - 1).coerceAtLeast(0)
                return@LaunchedEffect
            }
        }

        renderedPages = buildList {
            runtime.renderer.renderPage(
                documentPath = documentPath,
                pageIndex = currentPage,
            )?.let(::add)

            if (twoPageMode) {
                runtime.renderer.renderPage(
                    documentPath = documentPath,
                    pageIndex = currentPage + 1,
                )?.let(::add)
            }
        }
        renderedPages.firstOrNull()?.let { onPageCountResolved(it.pageCount) }

        if (renderedPages.isEmpty()) {
            errorMessage = if (runtime.capabilities.inAppViewingSupported) {
                "The PDF page could not be rendered yet."
            } else {
                runtime.capabilities.statusLine
            }
        }
        isLoading = false
    }

    val resolvedPageCount = renderedPages.firstOrNull()?.pageCount ?: documentState.pageCount
    val pageStep = if (twoPageMode) 2 else 1
    val canGoPrevious = currentPage > 0
    val canGoNext = resolvedPageCount?.let { currentPage + pageStep < it } ?: true

    Column(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .viewerShortcutHandler(
                keybindings = viewerKeybindings,
                onToggleFullscreen = onEnterFullscreen,
                onNextPage = {
                    if (canGoNext) {
                        currentPage = nextPageIndex(currentPage, resolvedPageCount, pageStep)
                    }
                },
                onPreviousPage = {
                    if (canGoPrevious) {
                        currentPage = (currentPage - pageStep).coerceAtLeast(0)
                    }
                },
                onToggleFavorite = onToggleFavorite,
                onOpenRandomScore = onOpenRandomScore,
                onZoomIn = {
                    zoom = (zoom + ZoomStep).coerceAtMost(MaxZoom)
                },
                onZoomOut = {
                    zoom = (zoom - ZoomStep).coerceAtLeast(MinZoom)
                },
                onTogglePageViewMode = {
                    twoPageMode = !twoPageMode
                    zoom = FitZoom
                },
            ),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            themePalette.headerGradientStart,
                            themePalette.headerGradientEnd,
                        )
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = documentState.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
                        color = themePalette.headerForeground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    documentState.subtitle?.let { subtitle ->
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = themePalette.headerForeground.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                CompactNavButton("<", canGoPrevious, tooltip = "Previous page") {
                    currentPage = (currentPage - pageStep).coerceAtLeast(0)
                }
                Text(
                    text = buildPageLabel(currentPage, resolvedPageCount, renderedPages.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = themePalette.headerForeground.copy(alpha = 0.9f),
                )
                activeDocument.statusLabel?.let { status ->
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        color = themePalette.headerForeground.copy(alpha = 0.78f),
                        modifier = Modifier
                            .background(
                                themePalette.headerForeground.copy(alpha = 0.12f),
                                MaterialTheme.shapes.small,
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                CompactNavButton(">", canGoNext, tooltip = "Next page") {
                    currentPage = nextPageIndex(currentPage, resolvedPageCount, pageStep)
                }
                CompactNavButton("-", zoom > MinZoom, tooltip = "Zoom out") { zoom = (zoom - ZoomStep).coerceAtLeast(MinZoom) }
                CompactNavButton("+", zoom < MaxZoom, tooltip = "Zoom in") { zoom = (zoom + ZoomStep).coerceAtMost(MaxZoom) }
                CompactNavButton("Fit", zoom != FitZoom, tooltip = "Fit page to viewer") { zoom = FitZoom }
                CompactNavButton(
                    label = if (twoPageMode) "1-Up" else "2-Up",
                    enabled = true,
                    tooltip = if (twoPageMode) "Switch to single-page view" else "Switch to two-page view",
                ) {
                    twoPageMode = !twoPageMode
                    zoom = FitZoom
                }
                if (documentState.isFavorite != null && onToggleFavorite != null) {
                    ViewerFavoriteButton(
                        title = documentState.title,
                        isFavorite = documentState.isFavorite,
                        onToggleFavorite = onToggleFavorite,
                    )
                }
                if (!documentState.isHidden && onHideScore != null) {
                    CompactNavButton("Hide", true, tooltip = "Hide this score", onClick = onHideScore)
                }
                if (onEnterFullscreen != null) {
                    CompactNavButton("Full", true, tooltip = "Open fullscreen viewer", onClick = onEnterFullscreen)
                }
                CompactNavButton("\u2669", true, tooltip = "Open metronome", onClick = { showMetronomeDialog = true })
                CompactNavButton(backButtonLabel, true, tooltip = backButtonLabel, onClick = onBack)
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(themePalette.viewerBackdrop),
            contentAlignment = Alignment.Center,
        ) {
            when {
                activeDocument.isPreparing || isLoading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                renderedPages.isNotEmpty() -> PdfPageCanvas(pages = renderedPages, zoom = zoom)
                else -> ViewerMessage(
                    title = if (activeDocument.liveErrorMessage != null) {
                        "LilyPond render failed"
                    } else {
                        "Viewer unavailable"
                    },
                    body = activeDocument.liveErrorMessage
                        ?: errorMessage
                        ?: "The viewer could not load this PDF page.",
                )
            }
        }
    }

    if (showMetronomeDialog) {
        MetronomeDialog(onDismiss = { showMetronomeDialog = false })
    }
}

@Composable
fun FullscreenViewerScreen(
    runtime: PlatformRuntime,
    documentState: ViewerDocumentState,
    onBack: () -> Unit,
    onToggleFavorite: (() -> Unit)? = null,
    onHideScore: (() -> Unit)? = null,
    onOpenRandomScore: (() -> Unit)? = null,
    onPageChanged: (Int) -> Unit,
    onPageCountResolved: (Int) -> Unit,
    defaultTwoPageMode: Boolean = false,
    viewerKeybindings: ViewerKeybindings = ViewerKeybindings(),
    modifier: Modifier = Modifier,
) {
    var currentPage by remember(documentState.id) {
        mutableIntStateOf(documentState.lastViewedPage.coerceAtLeast(0))
    }
    var zoom by remember(documentState.id) { mutableFloatStateOf(1f) }
    var twoPageMode by remember(documentState.id, defaultTwoPageMode) { mutableStateOf(defaultTwoPageMode) }
    var renderedPages by remember(documentState.id, currentPage, twoPageMode) {
        mutableStateOf<List<RenderedPdfPage>>(emptyList())
    }
    var errorMessage by remember(documentState.id, currentPage) { mutableStateOf<String?>(null) }
    var isLoading by remember(documentState.id, currentPage) { mutableStateOf(true) }
    var overlayVisible by remember { mutableStateOf(false) }
    var showMetronomeDialog by remember { mutableStateOf(false) }
    val themePalette = lunarThemePalette()
    val focusRequester = remember { FocusRequester() }
    val activeDocument = rememberActiveViewerDocument(
        runtime = runtime,
        documentState = documentState,
    )

    LaunchedEffect(documentState.id) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(documentState.id, activeDocument.documentPath, currentPage, twoPageMode) {
        isLoading = true
        errorMessage = null
        onPageChanged(currentPage)
        val documentPath = activeDocument.documentPath
        if (documentPath == null) {
            renderedPages = emptyList()
            isLoading = false
            return@LaunchedEffect
        }

        val info = runtime.renderer.inspect(documentPath)
        if (info != null) {
            onPageCountResolved(info.pageCount)
            if (currentPage > info.pageCount - 1) {
                currentPage = (info.pageCount - 1).coerceAtLeast(0)
                return@LaunchedEffect
            }
        }

        renderedPages = buildList {
            runtime.renderer.renderPage(
                documentPath = documentPath,
                pageIndex = currentPage,
            )?.let(::add)

            if (twoPageMode) {
                runtime.renderer.renderPage(
                    documentPath = documentPath,
                    pageIndex = currentPage + 1,
                )?.let(::add)
            }
        }
        renderedPages.firstOrNull()?.let { onPageCountResolved(it.pageCount) }

        if (renderedPages.isEmpty()) {
            errorMessage = if (runtime.capabilities.inAppViewingSupported) {
                "The PDF page could not be rendered yet."
            } else {
                runtime.capabilities.statusLine
            }
        }
        isLoading = false
    }

    val resolvedPageCount = renderedPages.firstOrNull()?.pageCount ?: documentState.pageCount
    val pageStep = if (twoPageMode) 2 else 1
    val canGoPrevious = currentPage > 0
    val canGoNext = resolvedPageCount?.let { currentPage + pageStep < it } ?: true
    val zoomLabel = "${(zoom * 100).toInt()}%"

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .viewerShortcutHandler(
                keybindings = viewerKeybindings,
                onToggleFullscreen = onBack,
                onNextPage = {
                    if (canGoNext) {
                        currentPage = nextPageIndex(currentPage, resolvedPageCount, pageStep)
                    }
                },
                onPreviousPage = {
                    if (canGoPrevious) {
                        currentPage = (currentPage - pageStep).coerceAtLeast(0)
                    }
                },
                onToggleFavorite = onToggleFavorite,
                onOpenRandomScore = onOpenRandomScore,
                onZoomIn = {
                    zoom = (zoom + ZoomStep).coerceAtMost(MaxZoom)
                },
                onZoomOut = {
                    zoom = (zoom - ZoomStep).coerceAtLeast(MinZoom)
                },
                onTogglePageViewMode = {
                    twoPageMode = !twoPageMode
                    zoom = FitZoom
                },
            )
            .background(themePalette.viewerBackdrop)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { overlayVisible = !overlayVisible }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            activeDocument.isPreparing || isLoading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            renderedPages.isNotEmpty() -> PdfPageCanvas(
                pages = renderedPages,
                zoom = zoom,
            )
            else -> ViewerMessage(
                title = if (activeDocument.liveErrorMessage != null) {
                    "LilyPond render failed"
                } else {
                    "Viewer unavailable"
                },
                body = activeDocument.liveErrorMessage
                    ?: errorMessage
                    ?: "The viewer could not load this PDF page.",
            )
        }

        AnimatedVisibility(
            visible = overlayVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, themePalette.viewerScrim)
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = themePalette.viewerOverlay,
                    shape = MaterialTheme.shapes.extraLarge,
                    tonalElevation = 10.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = documentState.title,
                                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif),
                                    color = themePalette.viewerForeground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                documentState.subtitle?.let { subtitle ->
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = themePalette.viewerForeground.copy(alpha = 0.78f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                activeDocument.statusLabel?.let { status ->
                                    Text(
                                        text = status,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = themePalette.viewerForeground.copy(alpha = 0.72f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Text(
                                text = buildPageLabel(currentPage, resolvedPageCount, renderedPages.size),
                                style = MaterialTheme.typography.labelLarge,
                                color = themePalette.viewerForeground.copy(alpha = 0.92f),
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FullscreenToolbarButton(
                                label = "Prev",
                                tooltip = "Previous page",
                                enabled = canGoPrevious,
                                onClick = { currentPage = (currentPage - pageStep).coerceAtLeast(0) },
                            )
                            FullscreenToolbarButton(
                                label = "Next",
                                tooltip = "Next page",
                                enabled = canGoNext,
                                onClick = {
                                    currentPage = nextPageIndex(currentPage, resolvedPageCount, pageStep)
                                },
                            )
                            Surface(
                                color = themePalette.viewerForeground.copy(alpha = 0.12f),
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Text(
                                    text = "Zoom $zoomLabel",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = themePalette.viewerForeground,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                LunarTooltip("Zoom out") {
                                    FilledTonalButton(
                                        onClick = { zoom = (zoom - ZoomStep).coerceAtLeast(MinZoom) },
                                        enabled = zoom > MinZoom,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("Zoom -")
                                    }
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                LunarTooltip("Fit page to viewer") {
                                    FilledTonalButton(
                                        onClick = { zoom = FitZoom },
                                        enabled = zoom != FitZoom,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("Best Fit")
                                    }
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                LunarTooltip("Zoom in") {
                                    FilledTonalButton(
                                        onClick = { zoom = (zoom + ZoomStep).coerceAtMost(MaxZoom) },
                                        enabled = zoom < MaxZoom,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("Zoom +")
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                LunarTooltip(
                                    text = if (twoPageMode) "Switch to single-page view" else "Switch to two-page view",
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            twoPageMode = !twoPageMode
                                            zoom = FitZoom
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(if (twoPageMode) "Single Page" else "Two Pages")
                                    }
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                LunarTooltip("Open metronome") {
                                    FilledTonalButton(
                                        onClick = { showMetronomeDialog = true },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("\u2669 Metronome")
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (documentState.isFavorite != null && onToggleFavorite != null) {
                                Box(modifier = Modifier.weight(1f)) {
                                    LunarTooltip(
                                        text = if (documentState.isFavorite) {
                                            "Remove ${documentState.title} from favorites"
                                        } else {
                                            "Mark ${documentState.title} as favorite"
                                        },
                                    ) {
                                        OutlinedButton(
                                            onClick = onToggleFavorite,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text(if (documentState.isFavorite) "Unfavorite" else "Favorite")
                                        }
                                    }
                                }
                            }
                            if (!documentState.isHidden && onHideScore != null) {
                                Box(modifier = Modifier.weight(1f)) {
                                    LunarTooltip("Hide this score") {
                                        OutlinedButton(
                                            onClick = onHideScore,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text("Hide score")
                                        }
                                    }
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                LunarTooltip("Exit fullscreen viewer") {
                                    Button(
                                        onClick = onBack,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("Exit Fullscreen")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMetronomeDialog) {
        MetronomeDialog(onDismiss = { showMetronomeDialog = false })
    }
}

@Composable
private fun CompactNavButton(
    label: String,
    enabled: Boolean,
    tooltip: String,
    onClick: () -> Unit,
) {
    val themePalette = lunarThemePalette()

    LunarTooltip(tooltip) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) {
                themePalette.headerForeground
            } else {
                themePalette.headerForeground.copy(alpha = 0.3f)
            },
            modifier = Modifier
                .clickable(enabled = enabled, onClick = onClick)
                .background(
                    if (enabled) {
                        themePalette.headerForeground.copy(alpha = 0.15f)
                    } else {
                        Color.Transparent
                    },
                    MaterialTheme.shapes.small,
                )
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun ViewerFavoriteButton(
    title: String,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
) {
    val themePalette = lunarThemePalette()
    val backgroundColor = if (isFavorite) {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.82f)
    } else {
        themePalette.headerForeground.copy(alpha = 0.12f)
    }
    val iconColor = if (isFavorite) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        themePalette.headerForeground.copy(alpha = 0.82f)
    }
    val tooltip = if (isFavorite) {
        "Remove $title from favorites"
    } else {
        "Mark $title as favorite"
    }

    LunarTooltip(tooltip) {
        Text(
        text = if (isFavorite) "★" else "☆",
        style = MaterialTheme.typography.titleMedium,
        color = iconColor,
        modifier = Modifier
            .semantics {
                contentDescription = tooltip
            }
            .clickable(onClick = onToggleFavorite)
            .background(backgroundColor, MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun FullscreenToolbarButton(
    label: String,
    tooltip: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    LunarTooltip(tooltip) {
        Button(
            enabled = enabled,
            onClick = onClick,
        ) {
            Text(label)
        }
    }
}

@Composable
private fun PdfPageCanvas(
    pages: List<RenderedPdfPage>,
    zoom: Float,
) {
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.large),
    ) {
        val horizontalPadding = if (maxWidth < 720.dp) 12.dp else 20.dp
        val verticalPadding = if (maxHeight < 720.dp) 12.dp else 20.dp
        val pageSpacing = if (pages.size > 1) PageGap.dp else 0.dp
        val availableWidth = (maxWidth - (horizontalPadding * 2) - (pageSpacing * (pages.size - 1)))
            .coerceAtLeast(120.dp)
        val availableHeight = (maxHeight - (verticalPadding * 2)).coerceAtLeast(160.dp)
        val totalAspectRatio = pages
            .sumOf { page -> page.aspectRatio.coerceAtLeast(0.1f).toDouble() }
            .toFloat()
            .coerceAtLeast(0.1f)
        val fitPageHeight = availableHeight.coerceAtMost(availableWidth / totalAspectRatio)
        val scaledPageHeight = fitPageHeight * zoom

        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(horizontalScroll)
                .verticalScroll(verticalScroll),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
                horizontalArrangement = Arrangement.spacedBy(pageSpacing),
                verticalAlignment = Alignment.Top,
            ) {
                pages.forEach { page ->
                    val aspectRatio = page.aspectRatio.coerceAtLeast(0.1f)
                    Image(
                        bitmap = page.image,
                        contentDescription = "Sheet music page ${page.pageIndex + 1}",
                        modifier = Modifier
                            .background(Color.White)
                            .width(scaledPageHeight * aspectRatio)
                            .height(scaledPageHeight),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewerMessage(
    title: String,
    body: String,
) {
    val themePalette = lunarThemePalette()

    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
            ),
            color = themePalette.viewerForeground,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = themePalette.viewerForeground.copy(alpha = 0.7f),
        )
    }
}

private fun Modifier.viewerShortcutHandler(
    keybindings: ViewerKeybindings,
    onToggleFullscreen: (() -> Unit)?,
    onNextPage: (() -> Unit)?,
    onPreviousPage: (() -> Unit)?,
    onToggleFavorite: (() -> Unit)?,
    onOpenRandomScore: (() -> Unit)?,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onTogglePageViewMode: () -> Unit,
): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) {
        return@onPreviewKeyEvent false
    }

    when (keybindings.actionForKeyId(viewerShortcutKeyId(event.key))) {
        com.ryanjames.lunar.settings.ViewerShortcutAction.TOGGLE_FULLSCREEN ->
            onToggleFullscreen?.let {
                it()
                true
            } ?: false

        com.ryanjames.lunar.settings.ViewerShortcutAction.NEXT_PAGE ->
            onNextPage?.let {
                it()
                true
            } ?: false

        com.ryanjames.lunar.settings.ViewerShortcutAction.PREVIOUS_PAGE ->
            onPreviousPage?.let {
                it()
                true
            } ?: false

        com.ryanjames.lunar.settings.ViewerShortcutAction.TOGGLE_FAVORITE ->
            onToggleFavorite?.let {
                it()
                true
            } ?: false

        com.ryanjames.lunar.settings.ViewerShortcutAction.OPEN_RANDOM_SCORE ->
            onOpenRandomScore?.let {
                it()
                true
            } ?: false

        com.ryanjames.lunar.settings.ViewerShortcutAction.ZOOM_IN -> {
            onZoomIn()
            true
        }

        com.ryanjames.lunar.settings.ViewerShortcutAction.ZOOM_OUT -> {
            onZoomOut()
            true
        }

        com.ryanjames.lunar.settings.ViewerShortcutAction.TOGGLE_PAGE_VIEW_MODE -> {
            onTogglePageViewMode()
            true
        }

        null -> false
    }
}

private fun buildPageLabel(
    currentPage: Int,
    pageCount: Int?,
    visiblePages: Int,
): String = if (pageCount == null) {
    "p.${currentPage + 1}"
} else if (visiblePages > 1) {
    "${currentPage + 1}-${(currentPage + visiblePages).coerceAtMost(pageCount)} / $pageCount"
} else {
    "${currentPage + 1} / $pageCount"
}

private fun nextPageIndex(
    currentPage: Int,
    pageCount: Int?,
    pageStep: Int,
): Int = if (pageCount == null) {
    currentPage + pageStep
} else {
    (currentPage + pageStep).coerceAtMost((pageCount - 1).coerceAtLeast(0))
}

private fun ViewerDocumentState.isLilyPondSourceDocument(): Boolean =
    document.originalFileName.substringAfterLast('.', "").lowercase() in LilyPondSourceExtensions
