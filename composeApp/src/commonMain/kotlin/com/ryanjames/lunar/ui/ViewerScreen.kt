package com.ryanjames.lunar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.ryanjames.lunar.composeapp.generated.resources.Res
import com.ryanjames.lunar.composeapp.generated.resources.lilypond_logo
import com.ryanjames.lunar.composeapp.generated.resources.pdf_logo
import com.ryanjames.lunar.library.model.PdfDocumentReference
import com.ryanjames.lunar.library.model.ScoreViewerSupport
import com.ryanjames.lunar.library.model.SheetMusicItem
import com.ryanjames.lunar.library.model.viewerSupport
import com.ryanjames.lunar.platform.LilyPondLiveRenderResult
import com.ryanjames.lunar.platform.LilyPondSourceSnapshot
import com.ryanjames.lunar.platform.PlatformRuntime
import com.ryanjames.lunar.platform.RenderedPdfPage
import com.ryanjames.lunar.settings.ViewerKeybindings
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

private const val MinZoom = 0.2f
private const val MaxZoom = 4.0f
private const val ZoomStep = 0.2f
private const val FitZoom = 1f
private const val PageGap = 20
private const val LilyPondLiveRefreshIntervalMillis = 1_000L
private const val LilyPondEditorRenderDebounceMillis = 650L

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
    val sourceSnapshot: LilyPondSourceSnapshot? = null,
)

@Composable
private fun rememberActiveViewerDocument(
    runtime: PlatformRuntime,
    documentState: ViewerDocumentState,
    editedSourceText: String? = null,
    editedSourceRenderNonce: Int = 0,
    editorAutoRefreshEnabled: Boolean = true,
    editorRefreshPending: Boolean = false,
): ActiveViewerDocument {
    val liveLilyPondEnabled = runtime.capabilities.lilyPondLiveViewingSupported &&
        documentState.document.viewerSupport == ScoreViewerSupport.LILYPOND
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

    val baseSourceSnapshot = sourceSnapshot
    var isUsingEditedSource = false
    val renderSource = if (
        baseSourceSnapshot != null &&
        editedSourceText != null &&
        editedSourceText != baseSourceSnapshot.sourceText
    ) {
        isUsingEditedSource = true
        baseSourceSnapshot.copy(
            sourceText = editedSourceText,
            revision = "${baseSourceSnapshot.revision}:editor:${editedSourceText.hashCode()}",
        )
    } else {
        baseSourceSnapshot
    }
    val renderRequestKey = when {
        renderSource == null -> null
        isUsingEditedSource -> "${renderSource.revision}:request:$editedSourceRenderNonce"
        else -> renderSource.revision
    }

    LaunchedEffect(documentState.id, liveLilyPondEnabled, renderRequestKey) {
        if (!liveLilyPondEnabled) {
            return@LaunchedEffect
        }
        val source = renderSource ?: return@LaunchedEffect

        isRendering = true
        renderErrorMessage = null
        if (isUsingEditedSource && editorAutoRefreshEnabled) {
            delay(LilyPondEditorRenderDebounceMillis)
        }
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
        !liveLilyPondEnabled -> "PDF Viewer"
        editorRefreshPending -> "LilyPond edits pending"
        renderErrorMessage != null && isUsingEditedSource -> "LilyPond editor error"
        renderErrorMessage != null -> "LilyPond Viewer error"
        isRendering && isUsingEditedSource -> "Rendering LilyPond editor"
        isRendering -> "Rendering LilyPond Viewer"
        renderResult != null && isUsingEditedSource -> "LilyPond Editor"
        renderResult != null -> "LilyPond Viewer"
        sourceUnavailableMessage != null -> "PDF Viewer"
        else -> "Loading LilyPond Viewer"
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
        sourceSnapshot = sourceSnapshot,
    )
}

@Composable
fun ViewerScreen(
    runtime: PlatformRuntime,
    documentState: ViewerDocumentState,
    onBack: () -> Unit,
    onToggleFavorite: (() -> Unit)? = null,
    onHideScore: (() -> Unit)? = null,
    onAddToSetlist: (() -> Unit)? = null,
    onAddToSongbook: (() -> Unit)? = null,
    onOpenRandomScore: (() -> Unit)? = null,
    onPageChanged: (Int) -> Unit,
    onPageCountResolved: (Int) -> Unit,
    onNextInSetlist: (() -> Unit)? = null,
    nextInSetlistAvailable: Boolean = onNextInSetlist != null,
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
    var sourceEditorVisible by remember(documentState.id) { mutableStateOf(false) }
    var sourceEditorText by remember(documentState.id) { mutableStateOf("") }
    var sourceEditorTextReady by remember(documentState.id) { mutableStateOf(false) }
    var sourceEditorDirty by remember(documentState.id) { mutableStateOf(false) }
    var sourceEditorAutoRefresh by remember(documentState.id) { mutableStateOf(true) }
    var sourceEditorManualRefreshText by remember(documentState.id) { mutableStateOf<String?>(null) }
    var sourceEditorManualRefreshRequest by remember(documentState.id) { mutableIntStateOf(0) }
    val themePalette = lunarThemePalette()
    val focusRequester = remember { FocusRequester() }
    val sourceEditorPreviewText = when {
        !sourceEditorVisible || !sourceEditorTextReady -> null
        sourceEditorAutoRefresh -> sourceEditorText
        else -> sourceEditorManualRefreshText
    }
    val sourceEditorRefreshPending = sourceEditorVisible &&
        sourceEditorTextReady &&
        sourceEditorDirty &&
        !sourceEditorAutoRefresh &&
        sourceEditorText != sourceEditorManualRefreshText
    val activeDocument = rememberActiveViewerDocument(
        runtime = runtime,
        documentState = documentState,
        editedSourceText = sourceEditorPreviewText,
        editedSourceRenderNonce = sourceEditorManualRefreshRequest,
        editorAutoRefreshEnabled = sourceEditorAutoRefresh,
        editorRefreshPending = sourceEditorRefreshPending,
    )

    LaunchedEffect(documentState.id) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(activeDocument.sourceSnapshot?.revision) {
        val source = activeDocument.sourceSnapshot ?: return@LaunchedEffect
        if (!sourceEditorDirty || !sourceEditorTextReady) {
            sourceEditorText = source.sourceText
            sourceEditorTextReady = true
            sourceEditorDirty = false
            sourceEditorManualRefreshText = null
            sourceEditorManualRefreshRequest = 0
        }
    }

    LaunchedEffect(activeDocument.sourceSnapshot) {
        if (activeDocument.sourceSnapshot == null) {
            sourceEditorVisible = false
            sourceEditorText = ""
            sourceEditorTextReady = false
            sourceEditorDirty = false
            sourceEditorManualRefreshText = null
            sourceEditorManualRefreshRequest = 0
        }
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
                enabled = !sourceEditorVisible,
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
                    ViewerDocumentMetaRow(
                        viewerSupport = documentState.document.viewerSupport,
                        pageCount = resolvedPageCount,
                        statusLabel = visibleViewerStatusLabel(
                            statusLabel = activeDocument.statusLabel,
                            viewerSupport = documentState.document.viewerSupport,
                        ),
                        foregroundColor = themePalette.headerForeground,
                    )
                }
                CompactNavButton("<", canGoPrevious) {
                    currentPage = (currentPage - pageStep).coerceAtLeast(0)
                }
                Text(
                    text = buildPageLabel(currentPage, resolvedPageCount, renderedPages.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = themePalette.headerForeground.copy(alpha = 0.9f),
                )
                CompactNavButton(">", canGoNext) {
                    currentPage = nextPageIndex(currentPage, resolvedPageCount, pageStep)
                }
                CompactNavButton("-", zoom > MinZoom) { zoom = (zoom - ZoomStep).coerceAtLeast(MinZoom) }
                CompactNavButton("+", zoom < MaxZoom) { zoom = (zoom + ZoomStep).coerceAtMost(MaxZoom) }
                CompactNavButton("Fit", zoom != FitZoom) { zoom = FitZoom }
                LayoutModeDropdownButton(
                    twoPageMode = twoPageMode,
                    onSelectSinglePage = {
                        twoPageMode = false
                        zoom = FitZoom
                    },
                    onSelectTwoPage = {
                        twoPageMode = true
                        zoom = FitZoom
                    },
                )
                if (activeDocument.sourceSnapshot != null) {
                    CompactNavButton(
                        label = if (sourceEditorVisible) "View" else "Edit",
                        enabled = true,
                    ) {
                        if (!sourceEditorVisible && !sourceEditorTextReady) {
                            activeDocument.sourceSnapshot.let { source ->
                                sourceEditorText = source.sourceText
                                sourceEditorTextReady = true
                                sourceEditorDirty = false
                                sourceEditorManualRefreshText = null
                                sourceEditorManualRefreshRequest = 0
                            }
                        }
                        sourceEditorVisible = !sourceEditorVisible
                    }
                }
                if (documentState.isFavorite != null && onToggleFavorite != null) {
                    ViewerFavoriteButton(
                        title = documentState.title,
                        isFavorite = documentState.isFavorite,
                        onToggleFavorite = onToggleFavorite,
                    )
                }
                ViewerAddDropdownButton(
                    onAddToSetlist = onAddToSetlist,
                    onAddToSongbook = onAddToSongbook,
                )
                if (!documentState.isHidden && onHideScore != null) {
                    CompactToolbarIconButton(
                        contentDescription = "Hide score",
                        onClick = onHideScore,
                    ) { color ->
                        HideScoreIcon(color = color)
                    }
                }
                if (onEnterFullscreen != null) {
                    CompactToolbarIconButton(
                        contentDescription = "Enter fullscreen",
                        onClick = onEnterFullscreen,
                    ) { color ->
                        FullscreenIcon(color = color)
                    }
                }
                if (onNextInSetlist != null) {
                    CompactNavButton("Next in setlist", nextInSetlistAvailable, onClick = onNextInSetlist)
                }
                CompactNavButton("\u2669", true, onClick = { showMetronomeDialog = true })
                CompactToolbarIconButton(
                    contentDescription = backButtonLabel,
                    onClick = onBack,
                ) { color ->
                    CloseIcon(color = color)
                }
            }
        }

        ViewerContentArea(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(themePalette.viewerBackdrop),
            activeDocument = activeDocument,
            renderedPages = renderedPages,
            zoom = zoom,
            isLoading = isLoading,
            errorMessage = errorMessage,
            sourceEditorVisible = sourceEditorVisible,
            sourceEditorText = sourceEditorText.takeIf { sourceEditorTextReady },
            sourceEditorDirty = sourceEditorDirty,
            sourceEditorAutoRefresh = sourceEditorAutoRefresh,
            sourceEditorRefreshPending = sourceEditorRefreshPending,
            onSourceEditorTextChange = { text ->
                sourceEditorText = text
                sourceEditorTextReady = true
                sourceEditorDirty = text != activeDocument.sourceSnapshot?.sourceText
            },
            onSourceEditorAutoRefreshChange = { enabled ->
                sourceEditorAutoRefresh = enabled
                sourceEditorManualRefreshText = if (!enabled && sourceEditorTextReady) {
                    sourceEditorText
                } else {
                    null
                }
                sourceEditorManualRefreshRequest = 0
            },
            onRefreshSourcePreview = {
                sourceEditorManualRefreshText = sourceEditorText
                sourceEditorManualRefreshRequest += 1
            },
            onResetSourceEditorText = {
                activeDocument.sourceSnapshot?.let { source ->
                    sourceEditorText = source.sourceText
                    sourceEditorTextReady = true
                    sourceEditorDirty = false
                    sourceEditorManualRefreshText = null
                    sourceEditorManualRefreshRequest = 0
                }
            },
        )
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
    onAddToSetlist: (() -> Unit)? = null,
    onAddToSongbook: (() -> Unit)? = null,
    onOpenRandomScore: (() -> Unit)? = null,
    onPageChanged: (Int) -> Unit,
    onPageCountResolved: (Int) -> Unit,
    onNextInSetlist: (() -> Unit)? = null,
    nextInSetlistAvailable: Boolean = onNextInSetlist != null,
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
    var sourceEditorVisible by remember(documentState.id) { mutableStateOf(false) }
    var sourceEditorText by remember(documentState.id) { mutableStateOf("") }
    var sourceEditorTextReady by remember(documentState.id) { mutableStateOf(false) }
    var sourceEditorDirty by remember(documentState.id) { mutableStateOf(false) }
    var sourceEditorAutoRefresh by remember(documentState.id) { mutableStateOf(true) }
    var sourceEditorManualRefreshText by remember(documentState.id) { mutableStateOf<String?>(null) }
    var sourceEditorManualRefreshRequest by remember(documentState.id) { mutableIntStateOf(0) }
    val themePalette = lunarThemePalette()
    val focusRequester = remember { FocusRequester() }
    val sourceEditorPreviewText = when {
        !sourceEditorVisible || !sourceEditorTextReady -> null
        sourceEditorAutoRefresh -> sourceEditorText
        else -> sourceEditorManualRefreshText
    }
    val sourceEditorRefreshPending = sourceEditorVisible &&
        sourceEditorTextReady &&
        sourceEditorDirty &&
        !sourceEditorAutoRefresh &&
        sourceEditorText != sourceEditorManualRefreshText
    val activeDocument = rememberActiveViewerDocument(
        runtime = runtime,
        documentState = documentState,
        editedSourceText = sourceEditorPreviewText,
        editedSourceRenderNonce = sourceEditorManualRefreshRequest,
        editorAutoRefreshEnabled = sourceEditorAutoRefresh,
        editorRefreshPending = sourceEditorRefreshPending,
    )

    LaunchedEffect(documentState.id) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(activeDocument.sourceSnapshot?.revision) {
        val source = activeDocument.sourceSnapshot ?: return@LaunchedEffect
        if (!sourceEditorDirty || !sourceEditorTextReady) {
            sourceEditorText = source.sourceText
            sourceEditorTextReady = true
            sourceEditorDirty = false
            sourceEditorManualRefreshText = null
            sourceEditorManualRefreshRequest = 0
        }
    }

    LaunchedEffect(activeDocument.sourceSnapshot) {
        if (activeDocument.sourceSnapshot == null) {
            sourceEditorVisible = false
            sourceEditorText = ""
            sourceEditorTextReady = false
            sourceEditorDirty = false
            sourceEditorManualRefreshText = null
            sourceEditorManualRefreshRequest = 0
        }
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
                enabled = !sourceEditorVisible,
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
        ViewerContentArea(
            activeDocument = activeDocument,
            renderedPages = renderedPages,
            zoom = zoom,
            isLoading = isLoading,
            errorMessage = errorMessage,
            sourceEditorVisible = sourceEditorVisible,
            sourceEditorText = sourceEditorText.takeIf { sourceEditorTextReady },
            sourceEditorDirty = sourceEditorDirty,
            sourceEditorAutoRefresh = sourceEditorAutoRefresh,
            sourceEditorRefreshPending = sourceEditorRefreshPending,
            onSourceEditorTextChange = { text ->
                sourceEditorText = text
                sourceEditorTextReady = true
                sourceEditorDirty = text != activeDocument.sourceSnapshot?.sourceText
            },
            onSourceEditorAutoRefreshChange = { enabled ->
                sourceEditorAutoRefresh = enabled
                sourceEditorManualRefreshText = if (!enabled && sourceEditorTextReady) {
                    sourceEditorText
                } else {
                    null
                }
                sourceEditorManualRefreshRequest = 0
            },
            onRefreshSourcePreview = {
                sourceEditorManualRefreshText = sourceEditorText
                sourceEditorManualRefreshRequest += 1
            },
            onResetSourceEditorText = {
                activeDocument.sourceSnapshot?.let { source ->
                    sourceEditorText = source.sourceText
                    sourceEditorTextReady = true
                    sourceEditorDirty = false
                    sourceEditorManualRefreshText = null
                    sourceEditorManualRefreshRequest = 0
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

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
                                ViewerDocumentMetaRow(
                                    viewerSupport = documentState.document.viewerSupport,
                                    pageCount = resolvedPageCount,
                                    statusLabel = visibleViewerStatusLabel(
                                        statusLabel = activeDocument.statusLabel,
                                        viewerSupport = documentState.document.viewerSupport,
                                    ),
                                    foregroundColor = themePalette.viewerForeground,
                                )
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
                                enabled = canGoPrevious,
                                onClick = { currentPage = (currentPage - pageStep).coerceAtLeast(0) },
                            )
                            FullscreenToolbarButton(
                                label = "Next",
                                enabled = canGoNext,
                                onClick = {
                                    currentPage = nextPageIndex(currentPage, resolvedPageCount, pageStep)
                                },
                            )
                            if (onNextInSetlist != null) {
                                FullscreenToolbarButton(
                                    label = "Next in setlist",
                                    enabled = nextInSetlistAvailable,
                                    onClick = onNextInSetlist,
                                )
                            }
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
                                FilledTonalButton(
                                    onClick = { zoom = (zoom - ZoomStep).coerceAtLeast(MinZoom) },
                                    enabled = zoom > MinZoom,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Zoom -")
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                FilledTonalButton(
                                    onClick = { zoom = FitZoom },
                                    enabled = zoom != FitZoom,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Best Fit")
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                FilledTonalButton(
                                    onClick = { zoom = (zoom + ZoomStep).coerceAtMost(MaxZoom) },
                                    enabled = zoom < MaxZoom,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Zoom +")
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
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
                            if (activeDocument.sourceSnapshot != null) {
                                Box(modifier = Modifier.weight(1f)) {
                                    FilledTonalButton(
                                        onClick = {
                                            if (!sourceEditorVisible && !sourceEditorTextReady) {
                                                activeDocument.sourceSnapshot.let { source ->
                                                    sourceEditorText = source.sourceText
                                                    sourceEditorTextReady = true
                                                    sourceEditorDirty = false
                                                    sourceEditorManualRefreshText = null
                                                    sourceEditorManualRefreshRequest = 0
                                                }
                                            }
                                            sourceEditorVisible = !sourceEditorVisible
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(if (sourceEditorVisible) "Hide Source" else "Edit Source")
                                    }
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                FilledTonalButton(
                                    onClick = { showMetronomeDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("\u2669 Metronome")
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FullscreenAddDropdownButton(
                                onAddToSetlist = onAddToSetlist,
                                onAddToSongbook = onAddToSongbook,
                                modifier = Modifier.weight(1f),
                            )
                            if (documentState.isFavorite != null && onToggleFavorite != null) {
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedButton(
                                        onClick = onToggleFavorite,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(if (documentState.isFavorite) "Unfavorite" else "Favorite")
                                    }
                                }
                            }
                            if (!documentState.isHidden && onHideScore != null) {
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedButton(
                                        onClick = onHideScore,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .semantics {
                                                contentDescription = "Hide score"
                                            },
                                    ) {
                                        HideScoreIcon(
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
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

    if (showMetronomeDialog) {
        MetronomeDialog(onDismiss = { showMetronomeDialog = false })
    }
}

@Composable
private fun ViewerDocumentMetaRow(
    viewerSupport: ScoreViewerSupport,
    pageCount: Int?,
    statusLabel: String?,
    foregroundColor: Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ViewerSupportIcon(
            viewerSupport = viewerSupport,
            foregroundColor = foregroundColor,
        )
        pageCount?.let { count ->
            Text(
                text = pageCountLabel(count),
                style = MaterialTheme.typography.labelSmall,
                color = foregroundColor.copy(alpha = 0.74f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        statusLabel?.let { status ->
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                color = foregroundColor.copy(alpha = 0.74f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ViewerSupportIcon(
    viewerSupport: ScoreViewerSupport,
    foregroundColor: Color,
) {
    val description = when (viewerSupport) {
        ScoreViewerSupport.PDF -> "PDF viewer"
        ScoreViewerSupport.LILYPOND -> "LilyPond viewer"
    }
    val logo = when (viewerSupport) {
        ScoreViewerSupport.PDF -> Res.drawable.pdf_logo
        ScoreViewerSupport.LILYPOND -> Res.drawable.lilypond_logo
    }

    LunarTooltip(description) {
        Surface(
            color = foregroundColor.copy(alpha = 0.13f),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .size(24.dp)
                .semantics {
                    contentDescription = description
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(logo),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

@Composable
private fun LayoutModeDropdownButton(
    twoPageMode: Boolean,
    onSelectSinglePage: () -> Unit,
    onSelectTwoPage: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        CompactToolbarIconButton(
            contentDescription = if (twoPageMode) "Two-page layout" else "Single-page layout",
            onClick = { expanded = true },
        ) { color ->
            PageLayoutIcon(twoPage = twoPageMode, color = color)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LayoutModeMenuOption(
                    twoPage = false,
                    selected = !twoPageMode,
                    contentDescription = "Single-page layout",
                    onClick = {
                        expanded = false
                        onSelectSinglePage()
                    },
                )
                LayoutModeMenuOption(
                    twoPage = true,
                    selected = twoPageMode,
                    contentDescription = "Two-page layout",
                    onClick = {
                        expanded = false
                        onSelectTwoPage()
                    },
                )
            }
        }
    }
}

@Composable
private fun ViewerAddDropdownButton(
    onAddToSetlist: (() -> Unit)?,
    onAddToSongbook: (() -> Unit)?,
) {
    if (onAddToSetlist == null && onAddToSongbook == null) return

    var expanded by remember { mutableStateOf(false) }
    val description = addActionsDescription(
        onAddToSetlist = onAddToSetlist,
        onAddToSongbook = onAddToSongbook,
    )

    Box {
        CompactToolbarIconButton(
            contentDescription = description,
            onClick = { expanded = true },
        ) { color ->
            AddIcon(color = color)
        }
        AddActionsDropdownMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            onAddToSetlist = onAddToSetlist?.let { onClick ->
                {
                    expanded = false
                    onClick()
                }
            },
            onAddToSongbook = onAddToSongbook?.let { onClick ->
                {
                    expanded = false
                    onClick()
                }
            },
        )
    }
}

@Composable
private fun FullscreenAddDropdownButton(
    onAddToSetlist: (() -> Unit)?,
    onAddToSongbook: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (onAddToSetlist == null && onAddToSongbook == null) return

    var expanded by remember { mutableStateOf(false) }
    val description = addActionsDescription(
        onAddToSetlist = onAddToSetlist,
        onAddToSongbook = onAddToSongbook,
    )

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = description
                },
        ) {
            AddIcon(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add")
        }
        AddActionsDropdownMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            onAddToSetlist = onAddToSetlist?.let { onClick ->
                {
                    expanded = false
                    onClick()
                }
            },
            onAddToSongbook = onAddToSongbook?.let { onClick ->
                {
                    expanded = false
                    onClick()
                }
            },
        )
    }
}

@Composable
private fun AddActionsDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAddToSetlist: (() -> Unit)?,
    onAddToSongbook: (() -> Unit)?,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        onAddToSetlist?.let { onClick ->
            DropdownMenuItem(
                text = { Text("Add to setlist") },
                onClick = onClick,
            )
        }
        onAddToSongbook?.let { onClick ->
            DropdownMenuItem(
                text = { Text("Add to songbook") },
                onClick = onClick,
            )
        }
    }
}

private fun addActionsDescription(
    onAddToSetlist: (() -> Unit)?,
    onAddToSongbook: (() -> Unit)?,
): String = when {
    onAddToSetlist != null && onAddToSongbook != null -> "Add to setlist or songbook"
    onAddToSetlist != null -> "Add to setlist"
    else -> "Add to songbook"
}

@Composable
private fun LayoutModeMenuOption(
    twoPage: Boolean,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val color = MaterialTheme.colorScheme.onSurface
    val background = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }

    LunarTooltip(contentDescription) {
        Surface(
            color = background,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .size(44.dp)
                .semantics {
                    this.contentDescription = contentDescription
                }
                .clickable(onClick = onClick),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                PageLayoutIcon(
                    twoPage = twoPage,
                    color = color,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun CompactToolbarIconButton(
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable (Color) -> Unit,
) {
    val themePalette = lunarThemePalette()
    val iconColor = if (enabled) {
        themePalette.headerForeground
    } else {
        themePalette.headerForeground.copy(alpha = 0.32f)
    }
    val background = if (enabled) {
        themePalette.headerForeground.copy(alpha = 0.15f)
    } else {
        Color.Transparent
    }

    LunarTooltip(contentDescription) {
        Surface(
            color = background,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .size(32.dp)
                .semantics {
                    this.contentDescription = contentDescription
                }
                .clickable(enabled = enabled, onClick = onClick),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(7.dp),
                contentAlignment = Alignment.Center,
            ) {
                content(iconColor)
            }
        }
    }
}

@Composable
private fun PageLayoutIcon(
    twoPage: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val strokeWidth = size.minDimension * 0.11f
        val pageGap = size.minDimension * 0.14f
        val pageWidth = if (twoPage) {
            (size.width - pageGap) / 2f
        } else {
            size.width * 0.68f
        }
        val pageHeight = size.height * 0.78f
        val startY = (size.height - pageHeight) / 2f
        val startX = if (twoPage) 0f else (size.width - pageWidth) / 2f
        val style = Stroke(width = strokeWidth)

        drawRect(
            color = color,
            topLeft = Offset(startX, startY),
            size = androidx.compose.ui.geometry.Size(pageWidth, pageHeight),
            style = style,
        )
        if (twoPage) {
            drawRect(
                color = color,
                topLeft = Offset(startX + pageWidth + pageGap, startY),
                size = androidx.compose.ui.geometry.Size(pageWidth, pageHeight),
                style = style,
            )
        }
    }
}

@Composable
private fun FullscreenIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val strokeWidth = size.minDimension * 0.13f
        val armLength = size.minDimension * 0.34f
        val inset = strokeWidth / 2f
        val maxX = size.width - inset
        val maxY = size.height - inset
        val style = Stroke(width = strokeWidth, cap = StrokeCap.Square)

        drawLine(color, Offset(inset, armLength), Offset(inset, inset), strokeWidth, StrokeCap.Square)
        drawLine(color, Offset(inset, inset), Offset(armLength, inset), strokeWidth, StrokeCap.Square)
        drawLine(color, Offset(maxX - armLength + inset, inset), Offset(maxX, inset), strokeWidth, StrokeCap.Square)
        drawLine(color, Offset(maxX, inset), Offset(maxX, armLength), strokeWidth, StrokeCap.Square)
        drawLine(color, Offset(maxX, maxY - armLength + inset), Offset(maxX, maxY), strokeWidth, StrokeCap.Square)
        drawLine(color, Offset(maxX, maxY), Offset(maxX - armLength + inset, maxY), strokeWidth, StrokeCap.Square)
        drawLine(color, Offset(armLength, maxY), Offset(inset, maxY), strokeWidth, StrokeCap.Square)
        drawLine(color, Offset(inset, maxY), Offset(inset, maxY - armLength + inset), strokeWidth, StrokeCap.Square)
    }
}

@Composable
private fun AddIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val strokeWidth = size.minDimension * 0.14f
        val center = Offset(size.width / 2f, size.height / 2f)
        val inset = size.minDimension * 0.18f
        drawLine(
            color = color,
            start = Offset(inset, center.y),
            end = Offset(size.width - inset, center.y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(center.x, inset),
            end = Offset(center.x, size.height - inset),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun HideScoreIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val strokeWidth = size.minDimension * 0.11f
        val eyeWidth = size.width * 0.82f
        val eyeHeight = size.height * 0.46f
        val eyeStart = Offset((size.width - eyeWidth) / 2f, (size.height - eyeHeight) / 2f)
        drawOval(
            color = color,
            topLeft = eyeStart,
            size = androidx.compose.ui.geometry.Size(eyeWidth, eyeHeight),
            style = Stroke(width = strokeWidth),
        )
        drawCircle(
            color = color,
            radius = size.minDimension * 0.11f,
            center = Offset(size.width / 2f, size.height / 2f),
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.16f, size.height * 0.84f),
            end = Offset(size.width * 0.84f, size.height * 0.16f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun CloseIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val strokeWidth = size.minDimension * 0.14f
        val inset = strokeWidth
        drawLine(
            color = color,
            start = Offset(inset, inset),
            end = Offset(size.width - inset, size.height - inset),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width - inset, inset),
            end = Offset(inset, size.height - inset),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

private fun pageCountLabel(pageCount: Int): String =
    "$pageCount page${if (pageCount == 1) "" else "s"}"

private fun visibleViewerStatusLabel(
    statusLabel: String?,
    viewerSupport: ScoreViewerSupport,
): String? {
    val defaultStatusLabel = when (viewerSupport) {
        ScoreViewerSupport.PDF -> "PDF Viewer"
        ScoreViewerSupport.LILYPOND -> "LilyPond Viewer"
    }
    return statusLabel
        ?.takeUnless { it == defaultStatusLabel }
        ?.takeUnless { it == "Loading LilyPond Viewer" }
}

@Composable
private fun CompactNavButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val themePalette = lunarThemePalette()

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
    val contentDescription = if (isFavorite) {
        "Remove $title from favorites"
    } else {
        "Mark $title as favorite"
    }
    val tooltip = if (isFavorite) "Remove from favorites" else "Mark as favorite"

    LunarTooltip(tooltip) {
        Text(
        text = if (isFavorite) "★" else "☆",
            style = MaterialTheme.typography.titleMedium,
            color = iconColor,
            modifier = Modifier
                .semantics {
                    this.contentDescription = contentDescription
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
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        enabled = enabled,
        onClick = onClick,
    ) {
        Text(label)
    }
}

@Composable
private fun ViewerContentArea(
    activeDocument: ActiveViewerDocument,
    renderedPages: List<RenderedPdfPage>,
    zoom: Float,
    isLoading: Boolean,
    errorMessage: String?,
    sourceEditorVisible: Boolean,
    sourceEditorText: String?,
    sourceEditorDirty: Boolean,
    sourceEditorAutoRefresh: Boolean,
    sourceEditorRefreshPending: Boolean,
    onSourceEditorTextChange: (String) -> Unit,
    onSourceEditorAutoRefreshChange: (Boolean) -> Unit,
    onRefreshSourcePreview: () -> Unit,
    onResetSourceEditorText: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val showEditor = sourceEditorVisible &&
            sourceEditorText != null &&
            activeDocument.sourceSnapshot != null

        if (showEditor) {
            if (maxWidth < 860.dp) {
                Column(modifier = Modifier.fillMaxSize()) {
                    LilyPondSourceEditorPane(
                        sourceText = sourceEditorText,
                        isDirty = sourceEditorDirty,
                        autoRefreshEnabled = sourceEditorAutoRefresh,
                        refreshPending = sourceEditorRefreshPending,
                        onSourceTextChange = onSourceEditorTextChange,
                        onAutoRefreshChange = onSourceEditorAutoRefreshChange,
                        onRefreshPreview = onRefreshSourcePreview,
                        onResetSourceText = onResetSourceEditorText,
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxWidth(),
                    )
                    ViewerPagePane(
                        activeDocument = activeDocument,
                        renderedPages = renderedPages,
                        zoom = zoom,
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxWidth(),
                    )
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    LilyPondSourceEditorPane(
                        sourceText = sourceEditorText,
                        isDirty = sourceEditorDirty,
                        autoRefreshEnabled = sourceEditorAutoRefresh,
                        refreshPending = sourceEditorRefreshPending,
                        onSourceTextChange = onSourceEditorTextChange,
                        onAutoRefreshChange = onSourceEditorAutoRefreshChange,
                        onRefreshPreview = onRefreshSourcePreview,
                        onResetSourceText = onResetSourceEditorText,
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxSize(),
                    )
                    ViewerPagePane(
                        activeDocument = activeDocument,
                        renderedPages = renderedPages,
                        zoom = zoom,
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        modifier = Modifier
                            .weight(0.58f)
                            .fillMaxSize(),
                    )
                }
            }
        } else {
            ViewerPagePane(
                activeDocument = activeDocument,
                renderedPages = renderedPages,
                zoom = zoom,
                isLoading = isLoading,
                errorMessage = errorMessage,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ViewerPagePane(
    activeDocument: ActiveViewerDocument,
    renderedPages: List<RenderedPdfPage>,
    zoom: Float,
    isLoading: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        when {
            activeDocument.isPreparing || isLoading ->
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)

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

@Composable
private fun LilyPondSourceEditorPane(
    sourceText: String,
    isDirty: Boolean,
    autoRefreshEnabled: Boolean,
    refreshPending: Boolean,
    onSourceTextChange: (String) -> Unit,
    onAutoRefreshChange: (Boolean) -> Unit,
    onRefreshPreview: () -> Unit,
    onResetSourceText: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "LilyPond source",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Auto refresh",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Switch(
                        checked = autoRefreshEnabled,
                        onCheckedChange = onAutoRefreshChange,
                        modifier = Modifier.semantics {
                            contentDescription = "Toggle LilyPond auto refresh"
                        },
                    )
                    TextButton(
                        onClick = onRefreshPreview,
                        enabled = refreshPending,
                        modifier = Modifier.semantics {
                            contentDescription = "Refresh LilyPond preview"
                        },
                    ) {
                        Text("Refresh")
                    }
                    TextButton(
                        onClick = onResetSourceText,
                        enabled = isDirty,
                    ) {
                        Text("Reset")
                    }
                }
            }
            OutlinedTextField(
                value = sourceText,
                onValueChange = onSourceTextChange,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxSize()
                    .semantics {
                        contentDescription = "LilyPond source editor"
                    },
                singleLine = false,
            )
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
    enabled: Boolean = true,
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
    if (!enabled) {
        return@onPreviewKeyEvent false
    }
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
