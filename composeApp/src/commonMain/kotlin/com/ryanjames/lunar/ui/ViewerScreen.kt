package com.ryanjames.lunar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
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
import com.ryanjames.lunar.settings.AppSettings
import com.ryanjames.lunar.settings.ViewerKeybindings
import com.ryanjames.lunar.settings.ViewerShortcutAction
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
import org.jetbrains.compose.resources.painterResource

private const val MinZoom = 0.2f
private const val MaxZoom = 4.0f
private const val ZoomStep = 0.2f
private const val FitZoom = 1f
private const val PageGap = 20
private const val LilyPondLiveRefreshIntervalMillis = 1_000L
private const val LilyPondEditorRenderDebounceMillis = 650L
private const val PageThumbnailTargetWidthPx = 128
private const val PopoverGapPx = 8

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

/** Runs [onToggle] each time [request] changes after first composition, ignoring its initial value. */
@Composable
private fun OnPageModeToggleRequest(request: Int, onToggle: () -> Unit) {
    var handledRequest by remember { mutableIntStateOf(request) }
    LaunchedEffect(request) {
        if (request != handledRequest) {
            handledRequest = request
            onToggle()
        }
    }
}

private enum class ViewerPopover {
    METRONOME,
    ADD,
    MORE,
}

private data class ViewerMoreAction(
    val label: String,
    val onClick: () -> Unit,
)

/**
 * Tracks whether the viewer chrome (top bar, footer and edge buttons) is showing. Input calls
 * [registerActivity]; [runAutoHide] fades the chrome after a quiet period. Only [visible] is
 * snapshot state, so pointer movement does not recompose the viewer unless visibility flips.
 */
@Stable
internal class ViewerChromeState {
    var visible by mutableStateOf(true)
        private set

    private val activity = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    fun registerActivity() {
        visible = true
        activity.tryEmit(Unit)
    }

    /** Hides the chrome [autoHideMillis] after the latest activity; null keeps it visible. */
    suspend fun runAutoHide(autoHideMillis: Long?) {
        visible = true
        if (autoHideMillis == null) {
            awaitCancellation()
        }
        activity
            .onStart { emit(Unit) }
            .collectLatest {
                delay(autoHideMillis)
                visible = false
            }
    }
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
    defaultTwoPageMode: Boolean = false,
    pageModeToggleRequest: Int = 0,
    viewerKeybindings: ViewerKeybindings = ViewerKeybindings(),
    chromeAutoHideMillis: Long? = AppSettings().viewerChromeAutoHideMillis,
    onEnterFullscreen: (() -> Unit)? = null,
    backLabel: String = "Library",
    nextInSetlist: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var currentPage by remember(documentState.id) {
        mutableIntStateOf(documentState.lastViewedPage.coerceAtLeast(0))
    }
    var zoom by remember(documentState.id) { mutableFloatStateOf(1f) }
    var twoPageMode by remember(documentState.id, defaultTwoPageMode) { mutableStateOf(defaultTwoPageMode) }
    OnPageModeToggleRequest(pageModeToggleRequest) {
        twoPageMode = !twoPageMode
        zoom = FitZoom
    }
    var renderedPages by remember(documentState.id, currentPage, twoPageMode) {
        mutableStateOf<List<RenderedPdfPage>>(emptyList())
    }
    var errorMessage by remember(documentState.id, currentPage) { mutableStateOf<String?>(null) }
    var isLoading by remember(documentState.id, currentPage) { mutableStateOf(true) }
    var openPopover by remember { mutableStateOf<ViewerPopover?>(null) }
    var sourceEditorVisible by remember(documentState.id) { mutableStateOf(false) }
    var sourceEditorText by remember(documentState.id) { mutableStateOf("") }
    var sourceEditorTextReady by remember(documentState.id) { mutableStateOf(false) }
    var sourceEditorDirty by remember(documentState.id) { mutableStateOf(false) }
    var sourceEditorAutoRefresh by remember(documentState.id) { mutableStateOf(true) }
    var sourceEditorManualRefreshText by remember(documentState.id) { mutableStateOf<String?>(null) }
    var sourceEditorManualRefreshRequest by remember(documentState.id) { mutableIntStateOf(0) }
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    var footerHeightPx by remember { mutableIntStateOf(0) }
    val themePalette = lunarThemePalette()
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }
    val chromeState = remember { ViewerChromeState() }
    val metronomeState = rememberMetronomeState()
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
    val pageThumbnails = remember(activeDocument.documentPath) { mutableStateMapOf<Int, ImageBitmap>() }
    // The chrome stays put while source is being edited and when auto-hide is off; the page
    // then lays out around it instead of under it. Open popovers hold it without re-layout.
    val chromePinned = chromeAutoHideMillis == null || sourceEditorVisible
    val chromeHeld = chromePinned || openPopover != null

    MetronomePlaybackEffect(metronomeState)

    LaunchedEffect(chromeState, chromeAutoHideMillis, chromeHeld) {
        chromeState.runAutoHide(if (chromeHeld) null else chromeAutoHideMillis)
    }

    LaunchedEffect(documentState.id, openPopover, sourceEditorVisible) {
        if (openPopover == null && !sourceEditorVisible) {
            focusRequester.requestFocus()
        }
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
    val goToPreviousPage = {
        if (canGoPrevious) {
            currentPage = (currentPage - pageStep).coerceAtLeast(0)
        }
    }
    val goToNextPage = {
        if (canGoNext) {
            currentPage = nextPageIndex(currentPage, resolvedPageCount, pageStep)
        }
    }
    val zoomIn = { zoom = (zoom + ZoomStep).coerceAtMost(MaxZoom) }
    val zoomOut = { zoom = (zoom - ZoomStep).coerceAtLeast(MinZoom) }
    val togglePageViewMode = {
        twoPageMode = !twoPageMode
        zoom = FitZoom
    }
    val moreActions = buildList {
        activeDocument.sourceSnapshot?.let { source ->
            add(
                ViewerMoreAction(if (sourceEditorVisible) "Hide source" else "Edit source") {
                    if (!sourceEditorVisible && !sourceEditorTextReady) {
                        sourceEditorText = source.sourceText
                        sourceEditorTextReady = true
                        sourceEditorDirty = false
                        sourceEditorManualRefreshText = null
                        sourceEditorManualRefreshRequest = 0
                    }
                    sourceEditorVisible = !sourceEditorVisible
                }
            )
        }
        onEnterFullscreen?.let { add(ViewerMoreAction("Open fullscreen", it)) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .viewerShortcutHandler(
                enabled = !sourceEditorVisible,
                keybindings = viewerKeybindings,
                onActivity = chromeState::registerActivity,
                onToggleFullscreen = onEnterFullscreen ?: onBack,
                onNextPage = goToNextPage,
                onPreviousPage = goToPreviousPage,
                onToggleFavorite = onToggleFavorite,
                onOpenRandomScore = onOpenRandomScore,
                onZoomIn = zoomIn,
                onZoomOut = zoomOut,
                onTogglePageViewMode = togglePageViewMode,
            )
            .background(themePalette.viewerBackdrop)
            .observeViewerActivity(chromeState::registerActivity),
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
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = if (chromePinned) with(density) { topBarHeightPx.toDp() } else 0.dp,
                    bottom = if (chromePinned) with(density) { footerHeightPx.toDp() } else 0.dp,
                ),
        )

        ViewerChromeVisibility(
            visible = chromeState.visible,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            ViewerEdgePageButton(
                contentDescription = "Previous page",
                tooltip = viewerKeybindings.tooltipFor(ViewerShortcutAction.PREVIOUS_PAGE),
                pointsLeft = true,
                enabled = canGoPrevious,
                onClick = goToPreviousPage,
            )
        }
        ViewerChromeVisibility(
            visible = chromeState.visible,
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            ViewerEdgePageButton(
                contentDescription = "Next page",
                tooltip = viewerKeybindings.tooltipFor(ViewerShortcutAction.NEXT_PAGE),
                pointsLeft = false,
                enabled = canGoNext,
                onClick = goToNextPage,
            )
        }

        ViewerChromeVisibility(
            visible = chromeState.visible,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { topBarHeightPx = it.height },
        ) {
            ViewerTopBar(
                backLabel = backLabel,
                onBack = onBack,
                title = documentState.title,
                subtitle = documentState.subtitle,
                viewerSupport = documentState.document.viewerSupport,
                statusLabel = visibleViewerStatusLabel(
                    statusLabel = activeDocument.statusLabel,
                    viewerSupport = documentState.document.viewerSupport,
                ),
            ) {
                ViewerToolbar(
                    keybindings = viewerKeybindings,
                    zoom = zoom,
                    onZoomOut = zoomOut,
                    onFit = { zoom = FitZoom },
                    onZoomIn = zoomIn,
                    twoPageMode = twoPageMode,
                    onTogglePageViewMode = togglePageViewMode,
                    metronomeState = metronomeState,
                    title = documentState.title,
                    isFavorite = documentState.isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    onAddToSetlist = onAddToSetlist,
                    onAddToSongbook = onAddToSongbook,
                    onHideScore = onHideScore.takeUnless { documentState.isHidden },
                    moreActions = moreActions,
                    openPopover = openPopover,
                    onOpenPopoverChange = { openPopover = it },
                )
            }
        }

        ViewerChromeVisibility(
            visible = chromeState.visible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { footerHeightPx = it.height },
        ) {
            ViewerFooter(
                runtime = runtime,
                documentPath = activeDocument.documentPath,
                currentPage = currentPage,
                pageCount = resolvedPageCount,
                twoPageMode = twoPageMode,
                thumbnails = pageThumbnails,
                onSelectPage = { pageIndex -> currentPage = pageIndex },
                onOpenRandomScore = onOpenRandomScore,
                nextInSetlist = nextInSetlist,
            )
        }
    }
}

@Composable
private fun ViewerChromeVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        content()
    }
}

private fun Modifier.observeViewerActivity(onActivity: () -> Unit): Modifier =
    pointerInput(onActivity) {
        awaitPointerEventScope {
            while (true) {
                // The initial pass sees moves, taps and scrolls without consuming them.
                awaitPointerEvent(PointerEventPass.Initial)
                onActivity()
            }
        }
    }

@Composable
private fun ViewerTopBar(
    backLabel: String,
    onBack: () -> Unit,
    title: String,
    subtitle: String?,
    viewerSupport: ScoreViewerSupport,
    statusLabel: String?,
    toolbar: @Composable () -> Unit,
) {
    val themePalette = lunarThemePalette()

    BoxWithConstraints(
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
        val heading = @Composable { headingModifier: Modifier ->
            Row(
                modifier = headingModifier,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ViewerBackButton(label = backLabel, onClick = onBack)
                ViewerHeading(
                    title = title,
                    subtitle = subtitle,
                    viewerSupport = viewerSupport,
                    statusLabel = statusLabel,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (maxWidth < 720.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                heading(Modifier.fillMaxWidth())
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    toolbar()
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                heading(Modifier.weight(1f))
                toolbar()
            }
        }
    }
}

@Composable
private fun ViewerBackButton(
    label: String,
    onClick: () -> Unit,
) {
    val themePalette = lunarThemePalette()

    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "Back to $label"
            }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChevronIcon(
            pointsLeft = true,
            color = themePalette.headerForeground,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = themePalette.headerForeground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp),
        )
    }
}

@Composable
private fun ViewerHeading(
    title: String,
    subtitle: String?,
    viewerSupport: ScoreViewerSupport,
    statusLabel: String?,
    modifier: Modifier = Modifier,
) {
    val themePalette = lunarThemePalette()

    Column(modifier = modifier) {
        LunarTooltip(title) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
                color = themePalette.headerForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ViewerDocumentMetaRow(
                viewerSupport = viewerSupport,
                statusLabel = statusLabel,
                foregroundColor = themePalette.headerForeground,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = themePalette.headerForeground.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ViewerToolbar(
    keybindings: ViewerKeybindings,
    zoom: Float,
    onZoomOut: () -> Unit,
    onFit: () -> Unit,
    onZoomIn: () -> Unit,
    twoPageMode: Boolean,
    onTogglePageViewMode: () -> Unit,
    metronomeState: MetronomeState,
    title: String,
    isFavorite: Boolean?,
    onToggleFavorite: (() -> Unit)?,
    onAddToSetlist: (() -> Unit)?,
    onAddToSongbook: (() -> Unit)?,
    onHideScore: (() -> Unit)?,
    moreActions: List<ViewerMoreAction>,
    openPopover: ViewerPopover?,
    onOpenPopoverChange: (ViewerPopover?) -> Unit,
) {
    val canFavorite = isFavorite != null && onToggleFavorite != null
    val canAdd = onAddToSetlist != null || onAddToSongbook != null
    val groups = buildList<@Composable () -> Unit> {
        add {
            ViewerToolbarButton(
                contentDescription = "Zoom out",
                tooltip = keybindings.tooltipFor(ViewerShortcutAction.ZOOM_OUT),
                enabled = zoom > MinZoom,
                onClick = onZoomOut,
            ) { color -> ZoomIcon(zoomIn = false, color = color) }
            ViewerToolbarTextButton(
                label = "Fit",
                contentDescription = "Fit to page",
                enabled = zoom != FitZoom,
                onClick = onFit,
            )
            ViewerToolbarButton(
                contentDescription = "Zoom in",
                tooltip = keybindings.tooltipFor(ViewerShortcutAction.ZOOM_IN),
                enabled = zoom < MaxZoom,
                onClick = onZoomIn,
            ) { color -> ZoomIcon(zoomIn = true, color = color) }
        }
        add {
            ViewerToolbarButton(
                contentDescription = if (twoPageMode) "Two-page layout" else "Single-page layout",
                tooltip = keybindings.tooltipFor(ViewerShortcutAction.TOGGLE_PAGE_VIEW_MODE),
                selected = twoPageMode,
                onClick = onTogglePageViewMode,
            ) { color -> PageLayoutIcon(twoPage = true, color = color) }
        }
        add {
            Box {
                ViewerToolbarButton(
                    contentDescription = "Metronome",
                    tooltip = if (metronomeState.isPlaying) {
                        "Metronome (${metronomeState.bpm} BPM)"
                    } else {
                        "Metronome"
                    },
                    selected = openPopover == ViewerPopover.METRONOME || metronomeState.isPlaying,
                    onClick = {
                        onOpenPopoverChange(
                            if (openPopover == ViewerPopover.METRONOME) null else ViewerPopover.METRONOME
                        )
                    },
                ) { color -> MetronomeIcon(color = color) }
                if (openPopover == ViewerPopover.METRONOME) {
                    MetronomePopover(
                        state = metronomeState,
                        onDismiss = { onOpenPopoverChange(null) },
                    )
                }
            }
        }
        if (canFavorite || canAdd) {
            add {
                if (isFavorite != null && onToggleFavorite != null) {
                    ViewerFavoriteButton(
                        title = title,
                        isFavorite = isFavorite,
                        tooltip = keybindings.tooltipFor(
                            action = ViewerShortcutAction.TOGGLE_FAVORITE,
                            label = if (isFavorite) "Remove from favorites" else "Mark as favorite",
                        ),
                        onToggleFavorite = onToggleFavorite,
                    )
                }
                ViewerAddDropdownButton(
                    expanded = openPopover == ViewerPopover.ADD,
                    onExpandedChange = { expanded ->
                        onOpenPopoverChange(if (expanded) ViewerPopover.ADD else null)
                    },
                    onAddToSetlist = onAddToSetlist,
                    onAddToSongbook = onAddToSongbook,
                )
            }
        }
        if (onHideScore != null || moreActions.isNotEmpty()) {
            add {
                onHideScore?.let { onHide ->
                    ViewerToolbarButton(
                        contentDescription = "Hide score",
                        tooltip = "Hide score",
                        onClick = onHide,
                    ) { color -> HideScoreIcon(color = color) }
                }
                if (moreActions.isNotEmpty()) {
                    ViewerMoreButton(
                        actions = moreActions,
                        expanded = openPopover == ViewerPopover.MORE,
                        onExpandedChange = { expanded ->
                            onOpenPopoverChange(if (expanded) ViewerPopover.MORE else null)
                        },
                    )
                }
            }
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        groups.forEach { group ->
            ViewerToolbarDivider()
            group()
        }
    }
}

@Composable
private fun ViewerToolbarDivider() {
    val themePalette = lunarThemePalette()

    Box(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .width(1.dp)
            .height(22.dp)
            .background(themePalette.headerForeground.copy(alpha = 0.28f)),
    )
}

@Composable
private fun MetronomePopover(
    state: MetronomeState,
    onDismiss: () -> Unit,
) {
    val positionProvider = remember { AnchoredBelowPopupPositionProvider() }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            modifier = Modifier
                .width(360.dp)
                .heightIn(max = 560.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 10.dp,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Metronome",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
                MetronomePanel(state = state)
            }
        }
    }
}

/** Places a popup under its anchor, centred on it and kept inside the window. */
private class AnchoredBelowPopupPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        val x = anchorBounds.center.x - popupContentSize.width / 2
        val y = anchorBounds.bottom + PopoverGapPx
        return IntOffset(x.coerceIn(0, maxX), y.coerceIn(0, maxY))
    }
}

@Composable
private fun ViewerMoreButton(
    actions: List<ViewerMoreAction>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    Box {
        ViewerToolbarButton(
            contentDescription = "More actions",
            tooltip = "More",
            selected = expanded,
            onClick = { onExpandedChange(!expanded) },
        ) { color -> MoreIcon(color = color) }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    onClick = {
                        onExpandedChange(false)
                        action.onClick()
                    },
                )
            }
        }
    }
}

@Composable
private fun ViewerFooter(
    runtime: PlatformRuntime,
    documentPath: String?,
    currentPage: Int,
    pageCount: Int?,
    twoPageMode: Boolean,
    thumbnails: MutableMap<Int, ImageBitmap>,
    onSelectPage: (Int) -> Unit,
    onOpenRandomScore: (() -> Unit)?,
    nextInSetlist: (@Composable () -> Unit)?,
) {
    val themePalette = lunarThemePalette()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(themePalette.viewerScrim.copy(alpha = 0f), themePalette.viewerScrim)
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = viewerPageLabel(currentPage, pageCount, twoPageMode),
            style = MaterialTheme.typography.labelLarge,
            color = themePalette.viewerForeground,
            maxLines = 1,
        )
        Box(modifier = Modifier.weight(1f)) {
            if (documentPath != null && pageCount != null && pageCount > 1) {
                ViewerPageStrip(
                    runtime = runtime,
                    documentPath = documentPath,
                    currentPage = currentPage,
                    pageCount = pageCount,
                    twoPageMode = twoPageMode,
                    thumbnails = thumbnails,
                    onSelectPage = onSelectPage,
                )
            }
        }
        onOpenRandomScore?.let { onRandom ->
            TextButton(onClick = onRandom) {
                Text("Random score", color = themePalette.viewerForeground)
            }
        }
        nextInSetlist?.invoke()
    }
}

/** Footer action for stepping through a setlist programme; hosted via [ViewerScreen]'s `nextInSetlist` slot. */
@Composable
fun NextInSetlistButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val themePalette = lunarThemePalette()
    TextButton(onClick = onClick, enabled = enabled) {
        Text(
            text = "Next in setlist",
            color = themePalette.viewerForeground.copy(alpha = if (enabled) 1f else 0.4f),
        )
    }
}

@Composable
private fun ViewerPageStrip(
    runtime: PlatformRuntime,
    documentPath: String,
    currentPage: Int,
    pageCount: Int,
    twoPageMode: Boolean,
    thumbnails: MutableMap<Int, ImageBitmap>,
    onSelectPage: (Int) -> Unit,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (currentPage - 2).coerceIn(0, pageCount - 1),
    )
    val visiblePages = viewerVisiblePages(currentPage, pageCount, twoPageMode)

    LaunchedEffect(currentPage) {
        listState.animateScrollToItem((currentPage - 2).coerceIn(0, pageCount - 1))
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(count = pageCount, key = { it }) { pageIndex ->
            ViewerPageThumbnail(
                runtime = runtime,
                documentPath = documentPath,
                pageIndex = pageIndex,
                current = pageIndex in visiblePages,
                thumbnails = thumbnails,
                onClick = { onSelectPage(pageIndex) },
            )
        }
    }
}

@Composable
private fun ViewerPageThumbnail(
    runtime: PlatformRuntime,
    documentPath: String,
    pageIndex: Int,
    current: Boolean,
    thumbnails: MutableMap<Int, ImageBitmap>,
    onClick: () -> Unit,
) {
    val themePalette = lunarThemePalette()
    val bitmap = thumbnails[pageIndex]

    if (bitmap == null) {
        LaunchedEffect(documentPath, pageIndex) {
            runCatching {
                runtime.renderer.renderPage(
                    documentPath = documentPath,
                    pageIndex = pageIndex,
                    targetWidth = PageThumbnailTargetWidthPx,
                )
            }.getOrNull()?.let { page -> thumbnails[pageIndex] = page.image }
        }
    }

    Box(
        modifier = Modifier
            .size(width = 34.dp, height = 46.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(Color.White)
            .border(
                width = if (current) 2.dp else 1.dp,
                color = if (current) {
                    MaterialTheme.colorScheme.primary
                } else {
                    themePalette.viewerForeground.copy(alpha = 0.25f)
                },
                shape = MaterialTheme.shapes.extraSmall,
            )
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "Go to page ${pageIndex + 1}"
                selected = current
            },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                text = "${pageIndex + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Black.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun ViewerEdgePageButton(
    contentDescription: String,
    tooltip: String,
    pointsLeft: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val themePalette = lunarThemePalette()

    LunarTooltip(tooltip) {
        Surface(
            color = themePalette.viewerOverlay.copy(alpha = if (enabled) 0.72f else 0.32f),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .size(width = 52.dp, height = 112.dp)
                .semantics { this.contentDescription = contentDescription }
                .clickable(enabled = enabled, onClick = onClick),
        ) {
            Box(contentAlignment = Alignment.Center) {
                ChevronIcon(
                    pointsLeft = pointsLeft,
                    color = themePalette.viewerForeground.copy(alpha = if (enabled) 1f else 0.4f),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun ViewerDocumentMetaRow(
    viewerSupport: ScoreViewerSupport,
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
                .size(20.dp)
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
private fun ViewerAddDropdownButton(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAddToSetlist: (() -> Unit)?,
    onAddToSongbook: (() -> Unit)?,
) {
    if (onAddToSetlist == null && onAddToSongbook == null) return

    val description = addActionsDescription(
        onAddToSetlist = onAddToSetlist,
        onAddToSongbook = onAddToSongbook,
    )

    Box {
        ViewerToolbarButton(
            contentDescription = description,
            tooltip = description,
            selected = expanded,
            onClick = { onExpandedChange(!expanded) },
        ) { color ->
            AddIcon(color = color)
        }
        AddActionsDropdownMenu(
            expanded = expanded,
            onDismiss = { onExpandedChange(false) },
            onAddToSetlist = onAddToSetlist?.let { onClick ->
                {
                    onExpandedChange(false)
                    onClick()
                }
            },
            onAddToSongbook = onAddToSongbook?.let { onClick ->
                {
                    onExpandedChange(false)
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
private fun viewerToolbarBackground(
    enabled: Boolean,
    selected: Boolean,
): Color {
    val themePalette = lunarThemePalette()
    return when {
        !enabled -> Color.Transparent
        selected -> themePalette.headerForeground.copy(alpha = 0.3f)
        else -> themePalette.headerForeground.copy(alpha = 0.12f)
    }
}

@Composable
private fun ViewerToolbarButton(
    contentDescription: String,
    tooltip: String,
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit,
    content: @Composable (Color) -> Unit,
) {
    val themePalette = lunarThemePalette()
    val iconColor = if (enabled) {
        themePalette.headerForeground
    } else {
        themePalette.headerForeground.copy(alpha = 0.32f)
    }

    LunarTooltip(tooltip) {
        Surface(
            color = viewerToolbarBackground(enabled = enabled, selected = selected),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .size(34.dp)
                .semantics {
                    this.contentDescription = contentDescription
                    this.selected = selected
                }
                .clickable(enabled = enabled, onClick = onClick),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                content(iconColor)
            }
        }
    }
}

@Composable
private fun ViewerToolbarTextButton(
    label: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val themePalette = lunarThemePalette()

    LunarTooltip(contentDescription) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) {
                themePalette.headerForeground
            } else {
                themePalette.headerForeground.copy(alpha = 0.32f)
            },
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(enabled = enabled, onClick = onClick)
                .background(viewerToolbarBackground(enabled = enabled, selected = false))
                .semantics { this.contentDescription = contentDescription }
                .padding(horizontal = 10.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun ViewerFavoriteButton(
    title: String,
    isFavorite: Boolean,
    tooltip: String,
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

    LunarTooltip(tooltip) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(MaterialTheme.shapes.small)
                .background(backgroundColor)
                .clickable(onClick = onToggleFavorite)
                .semantics {
                    this.contentDescription = contentDescription
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isFavorite) "★" else "☆",
                style = MaterialTheme.typography.titleMedium,
                color = iconColor,
            )
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
private fun ZoomIcon(
    zoomIn: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val strokeWidth = size.minDimension * 0.11f
        val radius = size.minDimension * 0.32f
        val center = Offset(size.width * 0.42f, size.height * 0.42f)
        drawCircle(
            color = color,
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth),
        )
        val handleStart = Offset(center.x + radius * 0.72f, center.y + radius * 0.72f)
        drawLine(color, handleStart, Offset(size.width * 0.94f, size.height * 0.94f), strokeWidth, StrokeCap.Round)
        val arm = radius * 0.5f
        drawLine(color, Offset(center.x - arm, center.y), Offset(center.x + arm, center.y), strokeWidth, StrokeCap.Round)
        if (zoomIn) {
            drawLine(color, Offset(center.x, center.y - arm), Offset(center.x, center.y + arm), strokeWidth, StrokeCap.Round)
        }
    }
}

@Composable
private fun MetronomeIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val strokeWidth = size.minDimension * 0.1f
        val body = Path().apply {
            moveTo(size.width * 0.36f, size.height * 0.08f)
            lineTo(size.width * 0.64f, size.height * 0.08f)
            lineTo(size.width * 0.9f, size.height * 0.92f)
            lineTo(size.width * 0.1f, size.height * 0.92f)
            close()
        }
        drawPath(body, color = color, style = Stroke(width = strokeWidth))
        drawLine(
            color = color,
            start = Offset(size.width * 0.5f, size.height * 0.74f),
            end = Offset(size.width * 0.78f, size.height * 0.2f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun MoreIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val radius = size.minDimension * 0.09f
        listOf(0.2f, 0.5f, 0.8f).forEach { fraction ->
            drawCircle(color = color, radius = radius, center = Offset(size.width * fraction, size.height / 2f))
        }
    }
}

@Composable
private fun ChevronIcon(
    pointsLeft: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val strokeWidth = size.minDimension * 0.14f
        val tipX = if (pointsLeft) size.width * 0.3f else size.width * 0.7f
        val tailX = if (pointsLeft) size.width * 0.7f else size.width * 0.3f
        drawLine(color, Offset(tailX, size.height * 0.12f), Offset(tipX, size.height / 2f), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(tipX, size.height / 2f), Offset(tailX, size.height * 0.88f), strokeWidth, StrokeCap.Round)
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
    onActivity: () -> Unit,
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
    onActivity()

    when (keybindings.actionForKeyId(viewerShortcutKeyId(event.key))) {
        ViewerShortcutAction.TOGGLE_FULLSCREEN ->
            onToggleFullscreen?.let {
                it()
                true
            } ?: false

        ViewerShortcutAction.NEXT_PAGE ->
            onNextPage?.let {
                it()
                true
            } ?: false

        ViewerShortcutAction.PREVIOUS_PAGE ->
            onPreviousPage?.let {
                it()
                true
            } ?: false

        ViewerShortcutAction.TOGGLE_FAVORITE ->
            onToggleFavorite?.let {
                it()
                true
            } ?: false

        ViewerShortcutAction.OPEN_RANDOM_SCORE ->
            onOpenRandomScore?.let {
                it()
                true
            } ?: false

        ViewerShortcutAction.ZOOM_IN -> {
            onZoomIn()
            true
        }

        ViewerShortcutAction.ZOOM_OUT -> {
            onZoomOut()
            true
        }

        ViewerShortcutAction.TOGGLE_PAGE_VIEW_MODE -> {
            onTogglePageViewMode()
            true
        }

        null -> false
    }
}

/** Footer label such as "Page 3 of 6" or, for a two-page spread, "Pages 3 and 4 of 6". */
internal fun viewerPageLabel(
    currentPage: Int,
    pageCount: Int?,
    twoPageMode: Boolean,
): String {
    val first = currentPage + 1
    if (pageCount == null) {
        return if (twoPageMode) "Pages $first and ${first + 1}" else "Page $first"
    }
    val pages = viewerVisiblePages(currentPage, pageCount, twoPageMode)
    return if (pages.count() > 1) {
        "Pages ${pages.first + 1} and ${pages.last + 1} of $pageCount"
    } else {
        "Page ${pages.first + 1} of $pageCount"
    }
}

/** Zero-based page indices on screen: one page, or a spread clipped to the document. */
internal fun viewerVisiblePages(
    currentPage: Int,
    pageCount: Int,
    twoPageMode: Boolean,
): IntRange {
    val lastIndex = (pageCount - 1).coerceAtLeast(0)
    val first = currentPage.coerceIn(0, lastIndex)
    val last = if (twoPageMode) (first + 1).coerceAtMost(lastIndex) else first
    return first..last
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
