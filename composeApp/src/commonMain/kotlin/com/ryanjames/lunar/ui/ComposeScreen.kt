package com.ryanjames.lunar.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ryanjames.lunar.composition.CompositionDraft
import com.ryanjames.lunar.composition.CompositionNotation
import com.ryanjames.lunar.composition.createCompositionDraft
import com.ryanjames.lunar.composition.withUpdatedContents
import com.ryanjames.lunar.platform.CompositionPdfImportRequest
import com.ryanjames.lunar.platform.LilyPondLiveRenderResult
import com.ryanjames.lunar.platform.LilyPondSourceSnapshot
import com.ryanjames.lunar.platform.PlatformRuntime
import com.ryanjames.lunar.platform.RenderedPdfPage
import com.ryanjames.lunar.ui.design.LunarButton
import com.ryanjames.lunar.ui.design.LunarButtonStyle
import com.ryanjames.lunar.ui.design.LunarIcon
import com.ryanjames.lunar.ui.design.LunarIconKind
import com.ryanjames.lunar.ui.design.LunarTestTags
import com.ryanjames.lunar.ui.design.SmallCapsLabel
import com.ryanjames.lunar.ui.design.lunarTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock

private const val ComposeRenderDebounceMillis = 650L
private const val ComposeAutosaveDebounceMillis = 500L
private const val ComposePreviewPageGap = 18
private const val DefaultCompositionTitle = "Untitled"
private const val DefaultCompositionComposer = "Lunar"

private val DefaultLilyPondComposition = """
    \version "2.24.0"

    \header {
      title = "$DefaultCompositionTitle"
      composer = "$DefaultCompositionComposer"
    }

    \score {
      \relative c' {
        c4 d e f
        g2 g
      }
      \layout { }
    }
""".trimIndent()

private val EmptyLilyPondComposition = """
    \version "2.24.0"

    \score {
      \new Staff { s1 }
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
    val drafts by runtime.compositionStore.drafts.collectAsState()
    val sortedDrafts = remember(drafts) {
        drafts.sortedByDescending(CompositionDraft::updatedAtEpochMillis)
    }
    val scope = rememberCoroutineScope()
    var notationMode by rememberSaveable { mutableStateOf(ComposeNotationMode.LILYPOND) }
    var sourceText by rememberSaveable(notationMode) { mutableStateOf(notationMode.defaultSource) }
    var draftTitle by rememberSaveable { mutableStateOf(DefaultCompositionTitle) }
    var draftComposer by rememberSaveable { mutableStateOf(DefaultCompositionComposer) }
    var selectedDraftId by rememberSaveable { mutableStateOf<String?>(null) }
    var loadedDraftId by remember { mutableStateOf<String?>(null) }
    var sidebarExpanded by rememberSaveable { mutableStateOf(true) }
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
    var isSaving by remember { mutableStateOf(false) }
    var compositionStatus by remember { mutableStateOf<String?>(null) }
    var compositionError by remember { mutableStateOf<String?>(null) }

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

    fun loadDraft(draft: CompositionDraft) {
        selectedDraftId = draft.id
        loadedDraftId = draft.id
        notationMode = draft.notation.toComposeNotationMode()
        draftTitle = draft.title
        draftComposer = draft.composer
        sourceText = draft.sourceText
        manualPreviewText = if (autoRefresh) null else draft.sourceText
        manualRefreshRequest = 0
        currentPage = 0
        compositionError = null
        compositionStatus = null
    }

    suspend fun persistCurrentDraft(
        normalizedTitle: String = draftTitle,
        normalizedComposer: String = draftComposer,
        importedItemId: String? = selectedDraftId
            ?.let(runtime.compositionStore::getDraft)
            ?.importedItemId,
    ): CompositionDraft {
        val existingDraft = selectedDraftId?.let(runtime.compositionStore::getDraft)
        val draft = existingDraft
            ?.withUpdatedContents(
                title = normalizedTitle,
                composer = normalizedComposer,
                sourceText = sourceText,
                notation = notationMode.toCompositionNotation(),
                importedItemId = importedItemId,
            )
            ?: createCompositionDraft(
                title = normalizedTitle,
                composer = normalizedComposer,
                sourceText = sourceText,
                notation = notationMode.toCompositionNotation(),
            ).copy(importedItemId = importedItemId)
        runtime.compositionStore.upsertDraft(draft)
        selectedDraftId = draft.id
        loadedDraftId = draft.id
        return draft
    }

    fun validateCurrentComposition(requireSource: Boolean = false): String? = when {
        draftTitle.isBlank() -> "Add a title before saving this composition."
        draftComposer.isBlank() -> "Add a composer before saving this composition."
        requireSource && sourceText.isBlank() -> "Add LilyPond source before saving to the library."
        else -> null
    }

    fun clearCompositionSource() {
        sourceText = EmptyLilyPondComposition
        currentPage = 0
        compositionError = null
        compositionStatus = null
    }

    fun loadTemplateSource() {
        sourceText = notationMode.defaultSource
        currentPage = 0
        compositionError = null
        compositionStatus = null
    }

    fun createNewComposition() {
        validateCurrentComposition()?.let { message ->
            compositionError = message
            compositionStatus = null
            return
        }

        scope.launch {
            compositionError = null
            compositionStatus = null
            val savedTitle = draftTitle.trim()
            val savedComposer = draftComposer.trim()
            persistCurrentDraft(
                normalizedTitle = savedTitle,
                normalizedComposer = savedComposer,
            )
            val newDraft = createCompositionDraft(
                title = "",
                composer = "",
                sourceText = EmptyLilyPondComposition,
                notation = notationMode.toCompositionNotation(),
            )
            runtime.compositionStore.upsertDraft(newDraft)
            loadDraft(newDraft)
            compositionStatus = "Saved \"$savedTitle\" as a draft."
        }
    }

    fun saveCompositionToLibrary() {
        validateCurrentComposition(requireSource = true)?.let { message ->
            compositionError = message
            compositionStatus = null
            return
        }
        if (!liveRenderSupported) {
            compositionError = "Live LilyPond rendering is unavailable on this device."
            compositionStatus = null
            return
        }

        scope.launch {
            isSaving = true
            compositionError = null
            compositionStatus = null
            val normalizedTitle = draftTitle.trim()
            val normalizedComposer = draftComposer.trim()
            runCatching {
                val draft = persistCurrentDraft(
                    normalizedTitle = normalizedTitle,
                    normalizedComposer = normalizedComposer,
                )
                val rendered = renderCompositionForLibrary(
                    runtime = runtime,
                    sourceText = sourceText,
                    draftId = draft.id,
                    mode = notationMode,
                )
                val descriptor = runtime.compositionPdfImporter.importRenderedComposition(
                    CompositionPdfImportRequest(
                        documentPath = rendered.documentPath,
                        draftId = draft.id,
                        title = normalizedTitle,
                        composer = normalizedComposer,
                        pageCount = rendered.pageCount ?: pageCount,
                    )
                )
                val importResult = runtime.repository.importDocuments(listOf(descriptor))
                val importedItem = importResult.importedItems.firstOrNull()
                if (importedItem != null) {
                    val updatedDraft = draft.withUpdatedContents(
                        title = normalizedTitle,
                        composer = normalizedComposer,
                        sourceText = sourceText,
                        notation = notationMode.toCompositionNotation(),
                        importedItemId = importedItem.id,
                    )
                    runtime.compositionStore.upsertDraft(updatedDraft)
                    compositionStatus = "Saved \"$normalizedTitle\" to the library."
                } else {
                    compositionStatus = importResult.skippedDocuments.firstOrNull()?.let { skipped ->
                        "Skipped \"$normalizedTitle\": ${skipped.reason}"
                    } ?: "No library item was created."
                }
            }.onFailure { error ->
                compositionError = error.message
                    ?.takeIf(String::isNotBlank)
                    ?: "Could not save this composition to the library."
            }
            isSaving = false
        }
    }

    LaunchedEffect(runtime.compositionStore) {
        runtime.compositionStore.initialize()
        if (runtime.compositionStore.drafts.value.isEmpty()) {
            runtime.compositionStore.upsertDraft(
                createCompositionDraft(
                    title = DefaultCompositionTitle,
                    composer = DefaultCompositionComposer,
                    sourceText = DefaultLilyPondComposition,
                    notation = ComposeNotationMode.LILYPOND.toCompositionNotation(),
                )
            )
        }
    }

    LaunchedEffect(sortedDrafts, selectedDraftId, loadedDraftId) {
        if (sortedDrafts.isEmpty()) return@LaunchedEffect
        val draftToLoad = selectedDraftId
            ?.let { id -> sortedDrafts.firstOrNull { draft -> draft.id == id } }
            ?: sortedDrafts.first()
        if (draftToLoad.id != loadedDraftId) {
            loadDraft(draftToLoad)
        }
    }

    LaunchedEffect(selectedDraftId, draftTitle, draftComposer, sourceText, notationMode) {
        val draftId = selectedDraftId ?: return@LaunchedEffect
        val existingDraft = runtime.compositionStore.getDraft(draftId) ?: return@LaunchedEffect
        delay(ComposeAutosaveDebounceMillis)
        runtime.compositionStore.upsertDraft(
            existingDraft.withUpdatedContents(
                title = draftTitle,
                composer = draftComposer,
                sourceText = sourceText,
                notation = notationMode.toCompositionNotation(),
            )
        )
    }

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
            .background(lunarTokens().colors.bg),
    ) {
        ComposeScreenHeader(
            selectedMode = notationMode,
            isSaving = isSaving,
            statusMessage = compositionError ?: compositionStatus,
            statusIsError = compositionError != null,
            onClear = ::clearCompositionSource,
            onLoadTemplate = ::loadTemplateSource,
            onSave = ::saveCompositionToLibrary,
            onNew = ::createNewComposition,
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
                .background(lunarTokens().colors.bg),
        ) {
            val useStackedLayout = maxWidth < 960.dp
            val expandedSidebarWidth = if (maxWidth < 720.dp) 184.dp else 252.dp
            Row(modifier = Modifier.fillMaxSize()) {
                CompositionDraftSidebar(
                    expanded = sidebarExpanded,
                    expandedWidth = expandedSidebarWidth,
                    drafts = sortedDrafts,
                    selectedDraftId = selectedDraftId,
                    onToggleExpanded = { sidebarExpanded = !sidebarExpanded },
                    onSelectDraft = ::loadDraft,
                    modifier = Modifier.fillMaxHeight(),
                )
                CompositionWorkspace(
                    mode = notationMode,
                    title = draftTitle,
                    composer = draftComposer,
                    sourceText = sourceText,
                    autoRefresh = autoRefresh,
                    refreshPending = refreshPending,
                    renderedPages = renderedPages,
                    pageCount = pageCount,
                    currentPage = currentPage,
                    isBusy = isRendering || isLoadingPage,
                    renderError = renderError,
                    pageError = pageError,
                    onTitleChange = { draftTitle = it },
                    onComposerChange = { draftComposer = it },
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
                    onPreviousPage = { currentPage = (currentPage - 1).coerceAtLeast(0) },
                    onNextPage = {
                        currentPage = pageCount
                            ?.let { (currentPage + 1).coerceAtMost((it - 1).coerceAtLeast(0)) }
                            ?: currentPage
                    },
                    modifier = Modifier.fillMaxSize(),
                    stacked = useStackedLayout,
                )
            }
        }
    }
}

@Composable
private fun ComposeScreenHeader(
    selectedMode: ComposeNotationMode,
    isSaving: Boolean,
    statusMessage: String?,
    statusIsError: Boolean,
    onClear: () -> Unit,
    onLoadTemplate: () -> Unit,
    onSave: () -> Unit,
    onNew: () -> Unit,
    onModeSelected: (ComposeNotationMode) -> Unit,
) {
    val tokens = lunarTokens()
    Column(modifier = Modifier.background(tokens.colors.panel)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = tokens.spacing.xl, vertical = tokens.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Compose",
                style = tokens.typography.heading,
                color = tokens.colors.ink,
            )
            ComposeNotationMode.entries.forEach { mode ->
                Text(
                    text = mode.label,
                    style = tokens.typography.bodySmall,
                    color = if (selectedMode == mode) tokens.colors.ink else tokens.colors.muted,
                    modifier = Modifier
                        .clickable { onModeSelected(mode) }
                        .padding(horizontal = tokens.spacing.xs, vertical = tokens.spacing.xxs),
                )
            }
            statusMessage?.let { message ->
                Text(
                    text = message,
                    style = tokens.typography.bodySmall,
                    color = if (statusIsError) tokens.colors.danger else tokens.colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(modifier = Modifier.width(tokens.spacing.lg))
            LunarButton(
                text = "Clear",
                onClick = onClear,
                style = LunarButtonStyle.TEXT,
                modifier = Modifier.semantics {
                    contentDescription = "Clear LilyPond source"
                },
            )
            LunarButton(
                text = "Load template",
                onClick = onLoadTemplate,
                style = LunarButtonStyle.TEXT,
                modifier = Modifier.semantics {
                    contentDescription = "Load LilyPond template"
                },
            )
            LunarButton(
                text = "New draft",
                onClick = onNew,
                style = LunarButtonStyle.SECONDARY,
                enabled = !isSaving,
                modifier = Modifier.semantics {
                    contentDescription = "New composition"
                },
            )
            LunarButton(
                text = if (isSaving) "Saving..." else "Save to library",
                onClick = onSave,
                enabled = !isSaving,
                modifier = Modifier
                    .testTag(LunarTestTags.COMPOSE_SAVE_TO_LIBRARY)
                    .semantics {
                        contentDescription = "Save composition"
                    },
            )
        }
        HorizontalDivider(color = tokens.colors.rule)
    }
}

@Composable
private fun CompositionDraftSidebar(
    expanded: Boolean,
    expandedWidth: Dp,
    drafts: List<CompositionDraft>,
    selectedDraftId: String?,
    onToggleExpanded: () -> Unit,
    onSelectDraft: (CompositionDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = lunarTokens()
    val width = if (expanded) expandedWidth else 56.dp
    Row(modifier = modifier.width(width)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(tokens.colors.panel),
        ) {
            if (!expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = tokens.spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
                ) {
                    TextButton(
                        onClick = onToggleExpanded,
                        modifier = Modifier
                            .size(44.dp)
                            .semantics {
                                contentDescription = "Expand composition list"
                            },
                    ) {
                        LunarIcon(kind = LunarIconKind.CHEVRON_RIGHT, contentDescription = null, tint = tokens.colors.ink2)
                    }
                    Text(
                        text = drafts.size.toString(),
                        style = tokens.typography.bodySmall,
                        color = tokens.colors.muted,
                    )
                }
                return@Box
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(tokens.spacing.md),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SmallCapsLabel(text = "Drafts")
                    TextButton(
                        onClick = onToggleExpanded,
                        modifier = Modifier.semantics {
                            contentDescription = "Collapse composition list"
                        },
                    ) {
                        LunarIcon(kind = LunarIconKind.CHEVRON_LEFT, contentDescription = null, tint = tokens.colors.ink2)
                    }
                }
                HorizontalDivider(color = tokens.colors.rule)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    drafts.forEach { draft ->
                        CompositionDraftRow(
                            draft = draft,
                            selected = draft.id == selectedDraftId,
                            onClick = { onSelectDraft(draft) },
                        )
                        HorizontalDivider(color = tokens.colors.rule)
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(tokens.colors.rule),
        )
    }
}

@Composable
private fun CompositionDraftRow(
    draft: CompositionDraft,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = lunarTokens()
    val title = draft.displayTitle()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) tokens.colors.selection else tokens.colors.panel)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "Open composition $title"
            }
            .padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.xxs),
    ) {
        Text(
            text = title,
            style = tokens.typography.body,
            color = tokens.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = draft.composer.ifBlank { "Composer needed" },
            style = tokens.typography.subtitle,
            color = tokens.colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CompositionWorkspace(
    mode: ComposeNotationMode,
    title: String,
    composer: String,
    sourceText: String,
    autoRefresh: Boolean,
    refreshPending: Boolean,
    renderedPages: List<RenderedPdfPage>,
    pageCount: Int?,
    currentPage: Int,
    isBusy: Boolean,
    renderError: String?,
    pageError: String?,
    onTitleChange: (String) -> Unit,
    onComposerChange: (String) -> Unit,
    onSourceTextChange: (String) -> Unit,
    onAutoRefreshChange: (Boolean) -> Unit,
    onRefreshPreview: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    stacked: Boolean,
    modifier: Modifier = Modifier,
) {
    if (stacked) {
        Column(modifier = modifier) {
            FreeformSourcePane(
                mode = mode,
                title = title,
                composer = composer,
                sourceText = sourceText,
                autoRefresh = autoRefresh,
                refreshPending = refreshPending,
                onTitleChange = onTitleChange,
                onComposerChange = onComposerChange,
                onSourceTextChange = onSourceTextChange,
                onAutoRefreshChange = onAutoRefreshChange,
                onRefreshPreview = onRefreshPreview,
                modifier = Modifier
                    .weight(0.48f)
                    .fillMaxWidth(),
            )
            CompositionPreviewPane(
                mode = mode,
                renderedPages = renderedPages,
                pageCount = pageCount,
                currentPage = currentPage,
                canGoPrevious = currentPage > 0,
                canGoNext = pageCount?.let { currentPage + 1 < it } ?: false,
                isBusy = isBusy,
                renderError = renderError,
                pageError = pageError,
                refreshPending = refreshPending,
                onPreviousPage = onPreviousPage,
                onNextPage = onNextPage,
                modifier = Modifier
                    .weight(0.52f)
                    .fillMaxWidth(),
            )
        }
    } else {
        Row(modifier = modifier) {
            FreeformSourcePane(
                mode = mode,
                title = title,
                composer = composer,
                sourceText = sourceText,
                autoRefresh = autoRefresh,
                refreshPending = refreshPending,
                onTitleChange = onTitleChange,
                onComposerChange = onComposerChange,
                onSourceTextChange = onSourceTextChange,
                onAutoRefreshChange = onAutoRefreshChange,
                onRefreshPreview = onRefreshPreview,
                modifier = Modifier
                    .weight(0.44f)
                    .fillMaxSize(),
            )
            CompositionPreviewPane(
                mode = mode,
                renderedPages = renderedPages,
                pageCount = pageCount,
                currentPage = currentPage,
                canGoPrevious = currentPage > 0,
                canGoNext = pageCount?.let { currentPage + 1 < it } ?: false,
                isBusy = isBusy,
                renderError = renderError,
                pageError = pageError,
                refreshPending = refreshPending,
                onPreviousPage = onPreviousPage,
                onNextPage = onNextPage,
                modifier = Modifier
                    .weight(0.56f)
                    .fillMaxSize(),
            )
        }
    }
}

@Composable
private fun FreeformSourcePane(
    mode: ComposeNotationMode,
    title: String,
    composer: String,
    sourceText: String,
    autoRefresh: Boolean,
    refreshPending: Boolean,
    onTitleChange: (String) -> Unit,
    onComposerChange: (String) -> Unit,
    onSourceTextChange: (String) -> Unit,
    onAutoRefreshChange: (Boolean) -> Unit,
    onRefreshPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = lunarTokens()
    Surface(
        modifier = modifier.padding(tokens.spacing.md),
        color = tokens.colors.panel,
        shape = tokens.shapes.large,
        border = BorderStroke(1.dp, tokens.colors.rule),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(tokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmallCapsLabel(text = "${mode.label} source")
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Auto refresh",
                        style = tokens.typography.bodySmall,
                        color = tokens.colors.muted,
                    )
                    Switch(
                        checked = autoRefresh,
                        onCheckedChange = onAutoRefreshChange,
                        modifier = Modifier.semantics {
                            contentDescription = "Toggle compose auto refresh"
                        },
                    )
                    LunarButton(
                        text = "Refresh",
                        onClick = onRefreshPreview,
                        style = LunarButtonStyle.TEXT,
                        enabled = refreshPending,
                        modifier = Modifier.semantics {
                            contentDescription = "Refresh composition preview"
                        },
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Composition title"
                        },
                )
                OutlinedTextField(
                    value = composer,
                    onValueChange = onComposerChange,
                    label = { Text("Composer") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Composition composer"
                        },
                )
            }
            OutlinedTextField(
                value = sourceText,
                onValueChange = onSourceTextChange,
                textStyle = tokens.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
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
    val tokens = lunarTokens()
    Surface(
        modifier = modifier.padding(tokens.spacing.md),
        color = tokens.colors.panel,
        shape = tokens.shapes.large,
        border = BorderStroke(1.dp, tokens.colors.rule),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmallCapsLabel(
                    text = "${mode.label} preview",
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = when {
                        refreshPending -> "Edits pending"
                        isBusy -> "Rendering"
                        renderError == null && renderedPages.isNotEmpty() -> "Ready"
                        else -> "Preview"
                    },
                    style = tokens.typography.bodySmall,
                    color = tokens.colors.muted,
                )
                LunarButton(
                    text = "Prev",
                    onClick = onPreviousPage,
                    style = LunarButtonStyle.TEXT,
                    enabled = canGoPrevious,
                )
                Text(
                    text = buildComposePageLabel(currentPage, pageCount),
                    style = tokens.typography.bodySmall,
                    color = tokens.colors.muted,
                )
                LunarButton(
                    text = "Next",
                    onClick = onNextPage,
                    style = LunarButtonStyle.TEXT,
                    enabled = canGoNext,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(lunarThemePalette().viewerBackdrop),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isBusy -> CircularProgressIndicator(color = tokens.colors.accent)
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
            style = lunarTokens().typography.heading,
            color = lunarThemePalette().viewerForeground,
        )
        Text(
            text = body,
            style = lunarTokens().typography.body,
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

private suspend fun renderCompositionForLibrary(
    runtime: PlatformRuntime,
    sourceText: String,
    draftId: String,
    mode: ComposeNotationMode,
): LilyPondLiveRenderResult {
    if (sourceText.isBlank()) {
        throw IllegalStateException("Add LilyPond source before saving to the library.")
    }

    return runtime.lilyPondLiveRenderer.renderSource(
        LilyPondSourceSnapshot(
            sourceText = sourceText,
            revision = "${mode.name.lowercase()}:compose-save:$draftId:${sourceText.hashCode()}:${Clock.System.now().toEpochMilliseconds()}",
            displayName = mode.sourceFileName,
            sourcePath = null,
        )
    )
}

private fun CompositionNotation.toComposeNotationMode(): ComposeNotationMode = when (this) {
    CompositionNotation.LILYPOND -> ComposeNotationMode.LILYPOND
}

private fun ComposeNotationMode.toCompositionNotation(): CompositionNotation = when (this) {
    ComposeNotationMode.LILYPOND -> CompositionNotation.LILYPOND
}

private fun CompositionDraft.displayTitle(): String =
    title.ifBlank { "Untitled draft" }
