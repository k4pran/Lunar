package com.ryanjames.lunar.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryanjames.lunar.platform.LilyPondLiveRenderResult
import com.ryanjames.lunar.platform.LilyPondSourceSnapshot
import com.ryanjames.lunar.platform.PlatformRuntime
import com.ryanjames.lunar.platform.RenderedPdfPage
import kotlinx.coroutines.delay

private const val ComposeRenderDebounceMillis = 650L
private const val ComposePreviewPageGap = 18

private val DefaultLilyPondComposition = """
    \version "2.24.0"

    \header {
      title = "Untitled"
      composer = "Lunar"
    }

    \score {
      \relative c' {
        c4 d e f
        g2 g
      }
      \layout { }
    }
""".trimIndent()

private enum class ComposeNotationMode(
    val label: String,
    val sourceFileName: String,
    val defaultSource: String,
) {
    LILYPOND(
        label = "LilyPond",
        sourceFileName = "composition.ly",
        defaultSource = DefaultLilyPondComposition,
    ),
}

@Composable
fun ComposeScreen(
    runtime: PlatformRuntime,
    modifier: Modifier = Modifier,
) {
    var notationMode by rememberSaveable { mutableStateOf(ComposeNotationMode.LILYPOND) }
    var sourceText by rememberSaveable(notationMode) { mutableStateOf(notationMode.defaultSource) }
    var autoRefresh by rememberSaveable(notationMode) { mutableStateOf(true) }
    var manualPreviewText by rememberSaveable(notationMode) { mutableStateOf<String?>(null) }
    var manualRefreshRequest by rememberSaveable(notationMode) { mutableIntStateOf(0) }
    var renderResult by remember(notationMode) { mutableStateOf<LilyPondLiveRenderResult?>(null) }
    var renderError by remember(notationMode) { mutableStateOf<String?>(null) }
    var isRendering by remember(notationMode) { mutableStateOf(false) }
    var currentPage by remember(notationMode) { mutableIntStateOf(0) }
    var renderedPages by remember(notationMode, currentPage) { mutableStateOf<List<RenderedPdfPage>>(emptyList()) }
    var pageCount by remember(notationMode) { mutableStateOf<Int?>(null) }
    var pageError by remember(notationMode) { mutableStateOf<String?>(null) }
    var isLoadingPage by remember(notationMode, currentPage) { mutableStateOf(false) }

    val themePalette = lunarThemePalette()
    val previewText = when {
        autoRefresh -> sourceText
        else -> manualPreviewText
    }
    val refreshPending = !autoRefresh && sourceText != manualPreviewText
    val renderRequestKey = previewText?.let { text ->
        "${notationMode.name}:${text.hashCode()}:$manualRefreshRequest"
    }
    val liveRenderSupported = notationMode == ComposeNotationMode.LILYPOND &&
        runtime.capabilities.lilyPondLiveViewingSupported

    LaunchedEffect(runtime, notationMode, liveRenderSupported, renderRequestKey) {
        if (!liveRenderSupported) {
            renderResult = null
            renderError = "Live LilyPond rendering is unavailable on this device."
            isRendering = false
            return@LaunchedEffect
        }
        val text = previewText ?: return@LaunchedEffect
        if (text.isBlank()) {
            renderResult = null
            renderError = "The LilyPond source is empty."
            isRendering = false
            return@LaunchedEffect
        }

        isRendering = true
        renderError = null
        if (autoRefresh) {
            delay(ComposeRenderDebounceMillis)
        }
        runCatching {
            runtime.lilyPondLiveRenderer.renderSource(
                LilyPondSourceSnapshot(
                    sourceText = text,
                    revision = "${notationMode.name.lowercase()}:compose:${text.hashCode()}:$manualRefreshRequest",
                    displayName = notationMode.sourceFileName,
                    sourcePath = null,
                )
            )
        }.onSuccess { result ->
            renderResult = result
            renderError = null
            pageCount = result.pageCount
            if (result.pageCount != null && currentPage > result.pageCount - 1) {
                currentPage = (result.pageCount - 1).coerceAtLeast(0)
            }
        }.onFailure { error ->
            renderResult = null
            renderError = error.message
                ?.takeIf(String::isNotBlank)
                ?: "LilyPond could not render this composition."
        }
        isRendering = false
    }

    LaunchedEffect(runtime, renderResult?.documentPath, currentPage) {
        val documentPath = renderResult?.documentPath
        if (documentPath == null) {
            renderedPages = emptyList()
            pageError = null
            isLoadingPage = false
            return@LaunchedEffect
        }

        isLoadingPage = true
        pageError = null
        val info = runtime.renderer.inspect(documentPath)
        if (info != null) {
            pageCount = info.pageCount
            if (currentPage > info.pageCount - 1) {
                currentPage = (info.pageCount - 1).coerceAtLeast(0)
                isLoadingPage = false
                return@LaunchedEffect
            }
        }

        renderedPages = runtime.renderer.renderPage(
            documentPath = documentPath,
            pageIndex = currentPage,
        )?.let(::listOf).orEmpty()
        renderedPages.firstOrNull()?.let { page -> pageCount = page.pageCount }
        if (renderedPages.isEmpty()) {
            pageError = "The rendered PDF preview is not available yet."
        }
        isLoadingPage = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ComposeScreenHeader(
            selectedMode = notationMode,
            onModeSelected = { mode ->
                notationMode = mode
                sourceText = mode.defaultSource
                manualPreviewText = null
                manualRefreshRequest = 0
                currentPage = 0
            },
        )

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(themePalette.viewerBackdrop),
        ) {
            if (maxWidth < 880.dp) {
                Column(modifier = Modifier.fillMaxSize()) {
                    FreeformSourcePane(
                        mode = notationMode,
                        sourceText = sourceText,
                        autoRefresh = autoRefresh,
                        refreshPending = refreshPending,
                        onSourceTextChange = { sourceText = it },
                        onAutoRefreshChange = { enabled ->
                            autoRefresh = enabled
                            manualPreviewText = if (!enabled) sourceText else null
                            manualRefreshRequest = 0
                        },
                        onRefreshPreview = {
                            manualPreviewText = sourceText
                            manualRefreshRequest += 1
                        },
                        onReset = {
                            sourceText = notationMode.defaultSource
                            manualPreviewText = null
                            manualRefreshRequest = 0
                        },
                        modifier = Modifier
                            .weight(0.48f)
                            .fillMaxWidth(),
                    )
                    CompositionPreviewPane(
                        mode = notationMode,
                        renderedPages = renderedPages,
                        pageCount = pageCount,
                        currentPage = currentPage,
                        canGoPrevious = currentPage > 0,
                        canGoNext = pageCount?.let { currentPage + 1 < it } ?: false,
                        isBusy = isRendering || isLoadingPage,
                        renderError = renderError,
                        pageError = pageError,
                        refreshPending = refreshPending,
                        onPreviousPage = { currentPage = (currentPage - 1).coerceAtLeast(0) },
                        onNextPage = {
                            currentPage = pageCount
                                ?.let { (currentPage + 1).coerceAtMost((it - 1).coerceAtLeast(0)) }
                                ?: currentPage
                        },
                        modifier = Modifier
                            .weight(0.52f)
                            .fillMaxWidth(),
                    )
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    FreeformSourcePane(
                        mode = notationMode,
                        sourceText = sourceText,
                        autoRefresh = autoRefresh,
                        refreshPending = refreshPending,
                        onSourceTextChange = { sourceText = it },
                        onAutoRefreshChange = { enabled ->
                            autoRefresh = enabled
                            manualPreviewText = if (!enabled) sourceText else null
                            manualRefreshRequest = 0
                        },
                        onRefreshPreview = {
                            manualPreviewText = sourceText
                            manualRefreshRequest += 1
                        },
                        onReset = {
                            sourceText = notationMode.defaultSource
                            manualPreviewText = null
                            manualRefreshRequest = 0
                        },
                        modifier = Modifier
                            .weight(0.44f)
                            .fillMaxSize(),
                    )
                    CompositionPreviewPane(
                        mode = notationMode,
                        renderedPages = renderedPages,
                        pageCount = pageCount,
                        currentPage = currentPage,
                        canGoPrevious = currentPage > 0,
                        canGoNext = pageCount?.let { currentPage + 1 < it } ?: false,
                        isBusy = isRendering || isLoadingPage,
                        renderError = renderError,
                        pageError = pageError,
                        refreshPending = refreshPending,
                        onPreviousPage = { currentPage = (currentPage - 1).coerceAtLeast(0) },
                        onNextPage = {
                            currentPage = pageCount
                                ?.let { (currentPage + 1).coerceAtMost((it - 1).coerceAtLeast(0)) }
                                ?: currentPage
                        },
                        modifier = Modifier
                            .weight(0.56f)
                            .fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ComposeScreenHeader(
    selectedMode: ComposeNotationMode,
    onModeSelected: (ComposeNotationMode) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Compose",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            ComposeNotationMode.entries.forEach { mode ->
                FilterChip(
                    selected = selectedMode == mode,
                    onClick = { onModeSelected(mode) },
                    label = { Text(mode.label) },
                )
            }
        }
    }
}

@Composable
private fun FreeformSourcePane(
    mode: ComposeNotationMode,
    sourceText: String,
    autoRefresh: Boolean,
    refreshPending: Boolean,
    onSourceTextChange: (String) -> Unit,
    onAutoRefreshChange: (Boolean) -> Unit,
    onRefreshPreview: () -> Unit,
    onReset: () -> Unit,
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
                    text = "${mode.label} source",
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
                        checked = autoRefresh,
                        onCheckedChange = onAutoRefreshChange,
                        modifier = Modifier.semantics {
                            contentDescription = "Toggle compose auto refresh"
                        },
                    )
                    TextButton(
                        onClick = onRefreshPreview,
                        enabled = refreshPending,
                        modifier = Modifier.semantics {
                            contentDescription = "Refresh composition preview"
                        },
                    ) {
                        Text("Refresh")
                    }
                    TextButton(onClick = onReset) {
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
                        contentDescription = "Freeform LilyPond editor"
                    },
                singleLine = false,
            )
        }
    }
}

@Composable
private fun CompositionPreviewPane(
    mode: ComposeNotationMode,
    renderedPages: List<RenderedPdfPage>,
    pageCount: Int?,
    currentPage: Int,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    isBusy: Boolean,
    renderError: String?,
    pageError: String?,
    refreshPending: Boolean,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 6.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${mode.label} preview",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = when {
                        refreshPending -> "Edits pending"
                        isBusy -> "Rendering"
                        renderError == null && renderedPages.isNotEmpty() -> "Ready"
                        else -> "Preview"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = onPreviousPage,
                    enabled = canGoPrevious,
                ) {
                    Text("Prev")
                }
                Text(
                    text = buildComposePageLabel(currentPage, pageCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = onNextPage,
                    enabled = canGoNext,
                ) {
                    Text("Next")
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(lunarThemePalette().viewerBackdrop),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isBusy -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    renderedPages.isNotEmpty() -> CompositionPdfCanvas(renderedPages)
                    else -> CompositionMessage(
                        title = if (renderError != null) "Render failed" else "Preview unavailable",
                        body = renderError ?: pageError ?: "There is no rendered preview yet.",
                    )
                }
            }
        }
    }
}

@Composable
private fun CompositionPdfCanvas(
    pages: List<RenderedPdfPage>,
) {
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        val horizontalPadding = if (maxWidth < 720.dp) 12.dp else 20.dp
        val verticalPadding = if (maxHeight < 720.dp) 12.dp else 20.dp
        val pageSpacing = if (pages.size > 1) ComposePreviewPageGap.dp else 0.dp
        val availableWidth = (maxWidth - (horizontalPadding * 2) - (pageSpacing * (pages.size - 1)))
            .coerceAtLeast(120.dp)
        val availableHeight = (maxHeight - (verticalPadding * 2)).coerceAtLeast(160.dp)
        val totalAspectRatio = pages
            .sumOf { page -> page.aspectRatio.coerceAtLeast(0.1f).toDouble() }
            .toFloat()
            .coerceAtLeast(0.1f)
        val pageHeight = availableHeight.coerceAtMost(availableWidth / totalAspectRatio)

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
                        contentDescription = "Composition page ${page.pageIndex + 1}",
                        modifier = Modifier
                            .background(Color.White)
                            .width(pageHeight * aspectRatio)
                            .height(pageHeight),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompositionMessage(
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
            color = lunarThemePalette().viewerForeground,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = lunarThemePalette().viewerForeground.copy(alpha = 0.7f),
        )
    }
}

private fun buildComposePageLabel(
    currentPage: Int,
    pageCount: Int?,
): String = pageCount?.let { count ->
    "${(currentPage + 1).coerceAtMost(count.coerceAtLeast(1))} / $count"
} ?: "-"
