package com.ryanjames.lunar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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

// ─── Preview mode (inside dialog) ───────────────────────────────────────────

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
    var renderedPage by remember(item.id, currentPage) { mutableStateOf<RenderedPdfPage?>(null) }
    var errorMessage by remember(item.id, currentPage) { mutableStateOf<String?>(null) }
    var isLoading by remember(item.id, currentPage) { mutableStateOf(true) }

    LaunchedEffect(item.id, currentPage) {
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

        renderedPage = runtime.renderer.renderPage(
            documentPath = item.document.storedPath,
            pageIndex = currentPage,
        )
        renderedPage?.let { onPageCountResolved(it.pageCount) }

        if (renderedPage == null) {
            errorMessage = if (runtime.capabilities.inAppViewingSupported) {
                "The PDF page could not be rendered yet."
            } else {
                runtime.capabilities.statusLine
            }
        }
        isLoading = false
    }

    val resolvedPageCount = renderedPage?.pageCount ?: item.pageCount
    val canGoPrevious = currentPage > 0
    val canGoNext = resolvedPageCount?.let { currentPage < it - 1 } ?: true

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // ── Compact top bar ──────────────────────────────────────────────────
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
                // Title + composer
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
                // Prev / page label / Next
                CompactNavButton("◀", canGoPrevious) {
                    currentPage = (currentPage - 1).coerceAtLeast(0)
                }
                Text(
                    text = buildPageLabel(currentPage, resolvedPageCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.9f),
                )
                CompactNavButton("▶", canGoNext) { currentPage += 1 }
                // Zoom
                CompactNavButton("−", zoom > 0.6f) { zoom = (zoom - 0.2f).coerceAtLeast(0.6f) }
                CompactNavButton("+", zoom < 2.2f) { zoom = (zoom + 0.2f).coerceAtMost(2.2f) }
                // Favourite
                Text(
                    text = if (item.isFavorite) "★" else "☆",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (item.isFavorite) Color(0xFFA7C6ED) else Color.White.copy(alpha = 0.55f),
                    modifier = Modifier
                        .clickable(onClick = onToggleFavorite)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
                // Close
                CompactNavButton("✕", true, onClick = onBack)
            }
        }

        // ── PDF canvas fills everything below the bar ────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF1A1A2E)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isLoading -> CircularProgressIndicator(color = Color(0xFFA7C6ED))
                renderedPage != null -> PdfPageCanvas(page = renderedPage!!, zoom = zoom)
                else -> ViewerMessage(
                    title = "Viewer unavailable",
                    body = errorMessage ?: "The viewer could not load this PDF page.",
                )
            }
        }
    }
}

// ─── Fullscreen mode ─────────────────────────────────────────────────────────

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
    var renderedPage by remember(item.id, currentPage) { mutableStateOf<RenderedPdfPage?>(null) }
    var errorMessage by remember(item.id, currentPage) { mutableStateOf<String?>(null) }
    var isLoading by remember(item.id, currentPage) { mutableStateOf(true) }
    var overlayVisible by remember { mutableStateOf(false) }

    LaunchedEffect(item.id, currentPage) {
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

        renderedPage = runtime.renderer.renderPage(
            documentPath = item.document.storedPath,
            pageIndex = currentPage,
        )
        renderedPage?.let { onPageCountResolved(it.pageCount) }

        if (renderedPage == null) {
            errorMessage = if (runtime.capabilities.inAppViewingSupported) {
                "The PDF page could not be rendered yet."
            } else {
                runtime.capabilities.statusLine
            }
        }
        isLoading = false
    }

    val resolvedPageCount = renderedPage?.pageCount ?: item.pageCount
    val canGoPrevious = currentPage > 0
    val canGoNext = resolvedPageCount?.let { currentPage < it - 1 } ?: true

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            // Double-tap anywhere on the PDF area toggles the overlay
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { overlayVisible = !overlayVisible }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // ── PDF fills the entire screen ──────────────────────────────────────
        when {
            isLoading -> CircularProgressIndicator(color = Color(0xFFA7C6ED))
            renderedPage != null -> PdfPageCanvas(page = renderedPage!!, zoom = zoom)
            else -> ViewerMessage(
                title = "Viewer unavailable",
                body = errorMessage ?: "The viewer could not load this PDF page.",
            )
        }

        // ── Overlay: appears on double-tap, hidden by default ────────────────
        AnimatedVisibility(
            visible = overlayVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
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
                            )
                        }
                    }
                    CompactNavButton("◀", canGoPrevious) {
                        currentPage = (currentPage - 1).coerceAtLeast(0)
                    }
                    Text(
                        text = buildPageLabel(currentPage, resolvedPageCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                    CompactNavButton("▶", canGoNext) { currentPage += 1 }
                    CompactNavButton("−", zoom > 0.6f) { zoom = (zoom - 0.2f).coerceAtLeast(0.6f) }
                    CompactNavButton("+", zoom < 2.2f) { zoom = (zoom + 0.2f).coerceAtMost(2.2f) }
                    Text(
                        text = if (item.isFavorite) "★" else "☆",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (item.isFavorite) Color(0xFFA7C6ED) else Color.White.copy(alpha = 0.55f),
                        modifier = Modifier
                            .clickable(onClick = onToggleFavorite)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                    CompactNavButton("✕  Exit", true, onClick = onBack)
                }
            }
        }
    }
}

// ─── Shared helpers ───────────────────────────────────────────────────────────

@Composable
private fun CompactNavButton(label: String, enabled: Boolean, onClick: () -> Unit) {
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
private fun PdfPageCanvas(
    page: RenderedPdfPage,
    zoom: Float,
) {
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.large)
            .horizontalScroll(horizontalScroll)
            .verticalScroll(verticalScroll),
    ) {
        Column(
            modifier = Modifier.padding(0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                bitmap = page.image,
                contentDescription = "Sheet music page ${page.pageIndex + 1}",
                modifier = Modifier
                    .background(Color.White)
                    .width((680 * zoom).dp)
                    .height((900 * zoom).dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun ViewerMessage(title: String, body: String) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("📄", style = MaterialTheme.typography.displaySmall)
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

private fun buildPageLabel(currentPage: Int, pageCount: Int?): String =
    if (pageCount == null) "p.${currentPage + 1}" else "${currentPage + 1} / $pageCount"
