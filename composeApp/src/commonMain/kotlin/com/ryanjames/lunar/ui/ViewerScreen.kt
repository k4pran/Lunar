package com.ryanjames.lunar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ryanjames.lunar.library.model.SheetMusicItem
import com.ryanjames.lunar.platform.PlatformRuntime
import com.ryanjames.lunar.platform.RenderedPdfPage

private const val MinZoom = 0.2f
private const val MaxZoom = 4.0f
private const val ZoomStep = 0.2f
private const val FitZoom = 1f
private const val PageGap = 20

@Composable
fun ViewerScreen(
    runtime: PlatformRuntime,
    item: SheetMusicItem,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onPageCountResolved: (Int) -> Unit,
    onEnterFullscreen: (() -> Unit)? = null,
    backButtonLabel: String = "Back to library",
    modifier: Modifier = Modifier,
) {
    var currentPage by remember(item.id) { mutableIntStateOf(item.lastViewedPage.coerceAtLeast(0)) }
    var zoom by remember(item.id) { mutableFloatStateOf(1f) }
    var twoPageMode by remember(item.id) { mutableStateOf(false) }
    var renderedPages by remember(item.id, currentPage, twoPageMode) { mutableStateOf<List<RenderedPdfPage>>(emptyList()) }
    var errorMessage by remember(item.id, currentPage) { mutableStateOf<String?>(null) }
    var isLoading by remember(item.id, currentPage) { mutableStateOf(true) }

    LaunchedEffect(item.id, currentPage, twoPageMode) {
        isLoading = true
        errorMessage = null
        onPageChanged(currentPage)

        val info = runtime.renderer.inspect(item.document.storedPath)
        if (info != null) {
            onPageCountResolved(info.pageCount)
            if (currentPage > info.pageCount - 1) {
                currentPage = (info.pageCount - 1).coerceAtLeast(0)
                return@LaunchedEffect
            }
        }

        renderedPages = buildList {
            runtime.renderer.renderPage(
                documentPath = item.document.storedPath,
                pageIndex = currentPage,
            )?.let(::add)

            if (twoPageMode) {
                runtime.renderer.renderPage(
                    documentPath = item.document.storedPath,
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

    val resolvedPageCount = renderedPages.firstOrNull()?.pageCount ?: item.pageCount
    val pageStep = if (twoPageMode) 2 else 1
    val canGoPrevious = currentPage > 0
    val canGoNext = resolvedPageCount?.let { currentPage + pageStep < it } ?: true

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(Color(0xFF1A3E4F), Color(0xFF176A8A)))
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
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    item.composer?.let { composer ->
                        Text(
                            text = composer,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                CompactNavButton("<", canGoPrevious) {
                    currentPage = (currentPage - pageStep).coerceAtLeast(0)
                }
                Text(
                    text = buildPageLabel(currentPage, resolvedPageCount, renderedPages.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.9f),
                )
                CompactNavButton(">", canGoNext) {
                    currentPage = nextPageIndex(currentPage, resolvedPageCount, pageStep)
                }
                CompactNavButton("-", zoom > MinZoom) { zoom = (zoom - ZoomStep).coerceAtLeast(MinZoom) }
                CompactNavButton("+", zoom < MaxZoom) { zoom = (zoom + ZoomStep).coerceAtMost(MaxZoom) }
                CompactNavButton("Fit", zoom != FitZoom) { zoom = FitZoom }
                CompactNavButton(if (twoPageMode) "1-Up" else "2-Up", true) {
                    twoPageMode = !twoPageMode
                    zoom = FitZoom
                }
                Text(
                    text = if (item.isFavorite) "*" else "o",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (item.isFavorite) Color(0xFFA7C6ED) else Color.White.copy(alpha = 0.55f),
                    modifier = Modifier
                        .clickable(onClick = onToggleFavorite)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
                if (onEnterFullscreen != null) {
                    CompactNavButton("Full", true, onClick = onEnterFullscreen)
                }
                CompactNavButton("Close", true, onClick = onBack)
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF1A1A2E)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isLoading -> CircularProgressIndicator(color = Color(0xFFA7C6ED))
                renderedPages.isNotEmpty() -> PdfPageCanvas(pages = renderedPages, zoom = zoom)
                else -> ViewerMessage(
                    title = "Viewer unavailable",
                    body = errorMessage ?: "The viewer could not load this PDF page.",
                )
            }
        }
    }
}

@Composable
fun FullscreenViewerScreen(
    runtime: PlatformRuntime,
    item: SheetMusicItem,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onPageCountResolved: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentPage by remember(item.id) { mutableIntStateOf(item.lastViewedPage.coerceAtLeast(0)) }
    var zoom by remember(item.id) { mutableFloatStateOf(1f) }
    var twoPageMode by remember(item.id) { mutableStateOf(false) }
    var renderedPages by remember(item.id, currentPage, twoPageMode) { mutableStateOf<List<RenderedPdfPage>>(emptyList()) }
    var errorMessage by remember(item.id, currentPage) { mutableStateOf<String?>(null) }
    var isLoading by remember(item.id, currentPage) { mutableStateOf(true) }
    var overlayVisible by remember { mutableStateOf(false) }

    LaunchedEffect(item.id, currentPage, twoPageMode) {
        isLoading = true
        errorMessage = null
        onPageChanged(currentPage)

        val info = runtime.renderer.inspect(item.document.storedPath)
        if (info != null) {
            onPageCountResolved(info.pageCount)
            if (currentPage > info.pageCount - 1) {
                currentPage = (info.pageCount - 1).coerceAtLeast(0)
                return@LaunchedEffect
            }
        }

        renderedPages = buildList {
            runtime.renderer.renderPage(
                documentPath = item.document.storedPath,
                pageIndex = currentPage,
            )?.let(::add)

            if (twoPageMode) {
                runtime.renderer.renderPage(
                    documentPath = item.document.storedPath,
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

    val resolvedPageCount = renderedPages.firstOrNull()?.pageCount ?: item.pageCount
    val pageStep = if (twoPageMode) 2 else 1
    val canGoPrevious = currentPage > 0
    val canGoNext = resolvedPageCount?.let { currentPage + pageStep < it } ?: true
    val zoomLabel = "${(zoom * 100).toInt()}%"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { overlayVisible = !overlayVisible }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoading -> CircularProgressIndicator(color = Color(0xFFA7C6ED))
            renderedPages.isNotEmpty() -> PdfPageCanvas(
                pages = renderedPages,
                zoom = zoom,
            )
            else -> ViewerMessage(
                title = "Viewer unavailable",
                body = errorMessage ?: "The viewer could not load this PDF page.",
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
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF113243).copy(alpha = 0.96f),
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
                                    text = item.title,
                                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                item.composer?.let { composer ->
                                    Text(
                                        text = composer,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.78f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Text(
                                text = buildPageLabel(currentPage, resolvedPageCount, renderedPages.size),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White.copy(alpha = 0.92f),
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
                            Surface(
                                color = Color.White.copy(alpha = 0.12f),
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Text(
                                    text = "Zoom $zoomLabel",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FilledTonalButton(
                                onClick = { zoom = (zoom - ZoomStep).coerceAtLeast(MinZoom) },
                                enabled = zoom > MinZoom,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Zoom -")
                            }
                            FilledTonalButton(
                                onClick = { zoom = FitZoom },
                                enabled = zoom != FitZoom,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Best Fit")
                            }
                            FilledTonalButton(
                                onClick = { zoom = (zoom + ZoomStep).coerceAtMost(MaxZoom) },
                                enabled = zoom < MaxZoom,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Zoom +")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    twoPageMode = !twoPageMode
                                    zoom = FitZoom
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(if (twoPageMode) "Single Page" else "Two Pages")
                            }
                            Surface(
                                color = Color.White.copy(alpha = 0.12f),
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier.weight(1f),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = if (twoPageMode) "Spread view" else "Single view",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White,
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                onClick = onToggleFavorite,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(if (item.isFavorite) "Unfavorite" else "Favorite")
                            }
                            Button(
                                onClick = onBack,
                                modifier = Modifier.weight(1f),
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

@Composable
private fun CompactNavButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (enabled) Color.White else Color.White.copy(alpha = 0.3f),
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .background(
                if (enabled) Color.White.copy(alpha = 0.15f) else Color.Transparent,
                MaterialTheme.shapes.small,
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
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
            color = Color.White,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.7f),
        )
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
