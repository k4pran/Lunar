package com.ryanjames.lunar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.platform.testTag
import com.ryanjames.lunar.library.model.jumpBackInItems
import kotlin.time.Clock
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryanjames.lunar.composeapp.generated.resources.Res
import com.ryanjames.lunar.composeapp.generated.resources.lilypond_logo
import com.ryanjames.lunar.composeapp.generated.resources.pdf_logo
import com.ryanjames.lunar.ui.design.LunarTestTags
import com.ryanjames.lunar.library.data.LibrarySnapshot
import com.ryanjames.lunar.library.model.HiddenScoreFilter
import com.ryanjames.lunar.library.model.LibrarySongbook
import com.ryanjames.lunar.library.model.LibrarySetlist
import com.ryanjames.lunar.library.model.LibrarySortOption
import com.ryanjames.lunar.library.model.ScoreViewerSupport
import com.ryanjames.lunar.library.model.SheetMusicItem
import com.ryanjames.lunar.library.model.ViewerSupportFilter
import com.ryanjames.lunar.library.model.applyLibraryQuery
import com.ryanjames.lunar.library.model.availableCollections
import com.ryanjames.lunar.library.model.availableTags
import com.ryanjames.lunar.library.model.viewerSupport
import com.ryanjames.lunar.platform.PlatformRuntime
import com.ryanjames.lunar.platform.SelectedCoverImage
import com.ryanjames.lunar.settings.ViewerKeybindings
import com.ryanjames.lunar.settings.ViewerShortcutAction
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Instant

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LibraryScreen(
    runtime: PlatformRuntime,
    snapshot: LibrarySnapshot,
    appState: LunarAppState,
    viewerKeybindings: ViewerKeybindings = ViewerKeybindings(),
    showArrangementSwitcher: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val screenModel = buildLibraryScreenModel(snapshot = snapshot, appState = appState)
    val hostedSection = appState.hostedSection
    val focusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
    var isTextInputFocused by remember { mutableStateOf(false) }
    var searchFocusPending by remember { mutableStateOf(false) }
    var alphabetIndexVisible by rememberSaveable { mutableStateOf(true) }
    val nowEpochMillis = remember(snapshot) { Clock.System.now().toEpochMilliseconds() }
    val catalogueMode = hostedSection == AppSection.LIBRARY
    val focusedItem = screenModel.focusedItem
    val detailsDraft = rememberScoreDetailsDraft(focusedItem, appState.editingItemId)
    val showSelectionBar = screenModel.selectedItems.isNotEmpty()
    val showToolbar = catalogueMode && !showSelectionBar

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    LaunchedEffect(screenModel.focusableItemIds) {
        appState.reconcileFocus(screenModel.focusableItemIds)
    }
    // The search field only exists while the toolbar is shown (Ctrl/Cmd+K clears any selection first),
    // so wait for it to be composed and laid out before focusing it.
    LaunchedEffect(searchFocusPending, showToolbar) {
        if (searchFocusPending && showToolbar) {
            withFrameNanos { }
            searchFocusRequester.requestFocus()
            searchFocusPending = false
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag(LunarTestTags.LIBRARY_PANEL)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (isTextInputFocused || event.type != KeyEventType.KeyDown) {
                    return@onKeyEvent false
                }
                when {
                    event.key == Key.K && (event.isCtrlPressed || event.isMetaPressed) && catalogueMode -> {
                        appState.clearScoreSelection()
                        searchFocusPending = true
                        true
                    }

                    event.key == Key.Enter && focusedItem != null -> {
                        appState.openScore(focusedItem)
                        true
                    }

                    (event.key == Key.DirectionDown || event.key == Key.DirectionUp) && catalogueMode -> {
                        appState.moveFocus(
                            orderedItemIds = screenModel.navigationOrderIds(appState.layoutMode),
                            delta = if (event.key == Key.DirectionDown) 1 else -1,
                        )
                        true
                    }

                    event.key == Key.Escape && (appState.selectedScoreIds.isNotEmpty() || focusedItem != null) -> {
                        if (appState.selectedScoreIds.isNotEmpty()) {
                            appState.clearScoreSelection()
                        } else {
                            appState.clearFocus()
                        }
                        true
                    }

                    viewerKeybindings.actionForKeyId(viewerShortcutKeyId(event.key)) ==
                        ViewerShortcutAction.OPEN_RANDOM_SCORE -> {
                        appState.openRandomSheet(screenModel.currentScopeItems)
                        true
                    }

                    else -> false
                }
            }
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        val wide = maxWidth >= DetailPanelSideBreakpoint
        val showDetailPanel = focusedItem != null && detailsDraft != null && screenModel.selectedItems.isEmpty()

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LibraryHeading(
                    visibleScoreCount = screenModel.visibleScoreCount,
                    hiddenScoreCount = screenModel.hiddenScoreCount,
                    shownCount = screenModel.visibleItems.size.takeIf { catalogueMode },
                    totalInView = screenModel.refinementTotalCount,
                )
                if (showArrangementSwitcher && hostedSection == AppSection.LIBRARY) {
                    ArrangementSwitcher(appState = appState)
                }
                if (showSelectionBar) {
                    LibrarySelectionBar(
                        selectedItems = screenModel.selectedItems,
                        setlists = snapshot.setlists,
                        songbooks = snapshot.songbooks,
                        appState = appState,
                    )
                } else if (showToolbar) {
                    LibraryToolbar(
                        appState = appState,
                        searchFocusRequester = searchFocusRequester,
                        onTextInputFocusChanged = { isTextInputFocused = it },
                        alphabetIndexAvailable = appState.layoutMode == LibraryLayoutMode.LIST &&
                            screenModel.catalogueIndexAvailable,
                        alphabetIndexVisible = alphabetIndexVisible,
                        onToggleAlphabetIndex = { alphabetIndexVisible = !alphabetIndexVisible },
                        filterCollections = screenModel.filterCollections,
                        filterTags = screenModel.filterTags,
                        randomScoreAvailable = screenModel.currentScopeItems.isNotEmpty(),
                        onOpenRandomScore = { appState.openRandomSheet(screenModel.currentScopeItems) },
                    )
                }
                if (catalogueMode) {
                    ActiveFilterChips(appState = appState)
                }

                Box(modifier = Modifier.weight(1f)) {
                    LibraryContent(
                        snapshot = snapshot,
                        screenModel = screenModel,
                        appState = appState,
                        showAlphabetIndex = alphabetIndexVisible,
                        nowEpochMillis = nowEpochMillis,
                    )
                }

                if (showDetailPanel && !wide) {
                    ScoreDetailPanel(
                        item = focusedItem,
                        snapshot = snapshot,
                        appState = appState,
                        layout = DetailPanelLayout.BOTTOM,
                        nowEpochMillis = nowEpochMillis,
                        onTextInputFocusChanged = { isTextInputFocused = it },
                        draft = detailsDraft,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = this@BoxWithConstraints.maxHeight * 0.45f),
                    )
                }
            }

            if (showDetailPanel && wide) {
                ScoreDetailPanel(
                    item = focusedItem,
                    snapshot = snapshot,
                    appState = appState,
                    layout = DetailPanelLayout.SIDE,
                    nowEpochMillis = nowEpochMillis,
                    onTextInputFocusChanged = { isTextInputFocused = it },
                    draft = detailsDraft,
                    modifier = Modifier
                        .width(DetailPanelSideWidth)
                        .fillMaxHeight(),
                )
            }
        }
    }

    if (screenModel.deleteCandidate != null) {
        DeleteScoreDialog(
            item = screenModel.deleteCandidate,
            onDismiss = appState::dismissDeleteRequest,
            onConfirm = appState::confirmDelete,
        )
    }

    val songbookTargets = screenModel.pickerTargetItems
    if (appState.newSongbookDialogVisible && songbookTargets.isNotEmpty()) {
        NewSongbookDialog(
            runtime = runtime,
            selectedCount = songbookTargets.size,
            coverImageSupported = appState.canPickSongbookCover,
            onDismiss = appState::dismissNewSongbookDialog,
            onCreateSongbook = { name, coverImage ->
                appState.createSongbook(
                    name = name,
                    items = songbookTargets,
                    coverImage = coverImage,
                )
            },
        )
    }

    if (appState.randomSetlistBuilderVisible) {
        RandomSetlistDialog(
            availableCount = screenModel.currentScopeItems.size,
            scopeLabel = screenModel.randomScopeLabel,
            onDismiss = appState::dismissRandomSetlistBuilder,
            onCreate = { count ->
                appState.createTemporaryRandomSetlist(
                    items = screenModel.currentScopeItems,
                    requestedCount = count,
                )
            },
        )
    }

    if (appState.saveTemporarySetlistDialogVisible && screenModel.temporarySetlist != null) {
        SaveTemporarySetlistDialog(
            session = screenModel.temporarySetlist,
            onDismiss = appState::dismissSaveTemporarySetlistDialog,
            onSave = appState::saveTemporarySetlist,
        )
    }

    if (screenModel.deleteSetlistCandidate != null) {
        DeleteSetlistDialog(
            setlist = screenModel.deleteSetlistCandidate,
            onDismiss = appState::dismissDeleteSetlistRequest,
            onConfirm = appState::confirmDeleteSetlist,
        )
    }

    if (screenModel.deleteSongbookCandidate != null) {
        DeleteSongbookDialog(
            songbook = screenModel.deleteSongbookCandidate,
            onDismiss = appState::dismissDeleteSongbookRequest,
            onConfirm = appState::confirmDeleteSongbook,
        )
    }
}

private val DetailPanelSideWidth = 360.dp

@Composable
private fun LibraryContent(
    snapshot: LibrarySnapshot,
    screenModel: LibraryScreenModel,
    appState: LunarAppState,
    showAlphabetIndex: Boolean,
    nowEpochMillis: Long,
) {
    when (appState.hostedSection) {
        AppSection.SETLISTS -> {
            val activeTemporarySetlist = screenModel.activeTemporarySetlist
            val activeSetlist = screenModel.activeSetlist
            if (activeTemporarySetlist != null) {
                TemporarySetlistDetailView(
                    session = activeTemporarySetlist,
                    items = screenModel.activeTemporarySetlistItems,
                    appState = appState,
                    onBack = appState::closeSetlist,
                    onSave = appState::showSaveTemporarySetlistDialog,
                    onDiscard = appState::discardTemporarySetlist,
                )
            } else if (activeSetlist == null) {
                SetlistsOverview(
                    temporarySetlist = screenModel.temporarySetlist,
                    setlists = snapshot.setlists,
                    itemsById = screenModel.itemsById,
                    randomSetlistAvailable = screenModel.currentScopeItems.isNotEmpty(),
                    onCreateRandomSetlist = appState::showRandomSetlistBuilder,
                    onOpenTemporarySetlist = appState::openTemporarySetlist,
                    onSaveTemporarySetlist = appState::showSaveTemporarySetlistDialog,
                    onDiscardTemporarySetlist = appState::discardTemporarySetlist,
                    onOpenSetlist = appState::openSetlist,
                    onDeleteSetlist = appState::requestDeleteSetlist,
                    onOpenLibrary = { appState.selectSection(AppSection.LIBRARY) },
                )
            } else {
                SetlistDetailView(
                    setlist = activeSetlist,
                    items = screenModel.activeSetlistItems,
                    appState = appState,
                    onBack = appState::closeSetlist,
                    onDeleteSetlist = {
                        appState.requestDeleteSetlist(activeSetlist.id)
                    },
                )
            }
        }

        AppSection.SONGBOOKS -> {
            SongbooksOverview(
                songbooks = snapshot.songbooks,
                itemsById = screenModel.itemsById,
                onOpenSongbook = appState::openSongbook,
                onDeleteSongbook = appState::requestDeleteSongbook,
                onOpenLibrary = { appState.selectSection(AppSection.LIBRARY) },
            )
        }

        else -> {
            if (screenModel.visibleItems.isEmpty()) {
                if (appState.showingHiddenScores) {
                    HiddenScoresEmptyState(
                        hiddenCount = screenModel.hiddenScoreCount,
                        onClearFilters = appState::clearFilters,
                    )
                } else {
                    EmptyLibraryState(
                        totalItemCount = screenModel.visibleScoreCount,
                        onOpenImport = { appState.selectSection(AppSection.IMPORT) },
                        onClearFilters = appState::clearFilters,
                    )
                }
            } else {
                ScoreCatalogue(
                    screenModel = screenModel,
                    appState = appState,
                    showAlphabetIndex = showAlphabetIndex,
                    nowEpochMillis = nowEpochMillis,
                )
            }
        }

    }
}

@Composable
private fun ScoreCatalogue(
    screenModel: LibraryScreenModel,
    appState: LunarAppState,
    showAlphabetIndex: Boolean,
    nowEpochMillis: Long,
) {
    val mode = if (appState.showingHiddenScores) ScoreListMode.HIDDEN else ScoreListMode.NORMAL
    val orderedIds = screenModel.navigationOrderIds(appState.layoutMode)
    when (appState.layoutMode) {
        LibraryLayoutMode.GRID -> LibraryGrid(
            items = screenModel.visibleItems,
            appState = appState,
            mode = mode,
            onCardClick = { item -> appState.focusScore(item.id) },
        )

        LibraryLayoutMode.LIST -> LibraryCatalogue(
            groups = screenModel.catalogueGroups,
            sortOption = appState.query.sortOption,
            jumpBackInItems = screenModel.jumpBackInItems,
            focusedItemId = appState.focusedScoreId,
            selectedItemIds = appState.selectedScoreIds,
            showAlphabetIndex = showAlphabetIndex,
            nowEpochMillis = nowEpochMillis,
            selectionEnabled = mode == ScoreListMode.NORMAL,
            onRowClick = { item, kind ->
                when (kind) {
                    CatalogueClickKind.FOCUS -> appState.focusScore(item.id)
                    CatalogueClickKind.TOGGLE -> appState.toggleScoreSelection(item.id)
                    CatalogueClickKind.RANGE -> appState.extendScoreSelection(item.id, orderedIds)
                }
            },
            onRowOpen = { item -> appState.openScore(item) },
            onToggleSelection = { item -> appState.toggleScoreSelection(item.id) },
        )
    }
}

@Composable
private fun LibraryHeading(
    visibleScoreCount: Int,
    hiddenScoreCount: Int,
    shownCount: Int?,
    totalInView: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = buildList {
                add(visibleScoreCount.scoreLabel())
                if (hiddenScoreCount > 0) add("$hiddenScoreCount hidden")
                if (shownCount != null && shownCount != totalInView) add("$shownCount shown")
            }.joinToString(", "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 3.dp),
        )
    }
}

// --- Arrangement switcher (shown when the sidebar context is not visible) ---

@Composable
private fun ArrangementSwitcher(appState: LunarAppState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LibraryArrangement.entries.forEach { arrangement ->
            BrowseModeTab(arrangement, appState.libraryArrangement == arrangement) {
                appState.selectArrangement(arrangement)
            }
        }
    }
}

@Composable
private fun BrowseModeTab(arrangement: LibraryArrangement, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
    }
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = bg,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .testTag(LunarTestTags.libraryArrangementTab(arrangement))
            .clickable(onClick = onClick),
    ) {
        Text(
            text = arrangement.label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = fg,
        )
    }
}

// ─── Flat score list (setlist detail) ──────────────────────────────────────────────

private enum class ScoreListMode {
    NORMAL,
    HIDDEN,
}

@Composable
private fun FlatScoreList(
    items: List<SheetMusicItem>,
    appState: LunarAppState,
    mode: ScoreListMode = ScoreListMode.NORMAL,
) {
    when (appState.layoutMode) {
        LibraryLayoutMode.GRID -> LibraryGrid(items = items, appState = appState, mode = mode)
        LibraryLayoutMode.LIST -> LibraryList(items = items, appState = appState, mode = mode)
    }
}

@Composable
private fun SummaryPillDark(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun SetlistsOverview(
    temporarySetlist: TemporarySetlistSession?,
    setlists: List<LibrarySetlist>,
    itemsById: Map<String, SheetMusicItem>,
    randomSetlistAvailable: Boolean,
    onCreateRandomSetlist: () -> Unit,
    onOpenTemporarySetlist: () -> Unit,
    onSaveTemporarySetlist: () -> Unit,
    onDiscardTemporarySetlist: () -> Unit,
    onOpenSetlist: (String) -> Unit,
    onDeleteSetlist: (String) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val orderedSetlists = setlists.sortedByDescending { it.updatedAtEpochMillis }

    if (temporarySetlist == null && orderedSetlists.isEmpty()) {
        EmptySetlistsState(
            randomSetlistAvailable = randomSetlistAvailable,
            onCreateRandomSetlist = onCreateRandomSetlist,
            onOpenLibrary = onOpenLibrary,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "random_setlist") {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(enabled = randomSetlistAvailable, onClick = onCreateRandomSetlist) {
                    Text("Random setlist")
                }
            }
        }
        if (temporarySetlist != null) {
            item(key = "temporary_setlist") {
                TemporarySetlistCard(
                    session = temporarySetlist,
                    itemsById = itemsById,
                    onOpen = onOpenTemporarySetlist,
                    onSave = onSaveTemporarySetlist,
                    onDiscard = onDiscardTemporarySetlist,
                )
            }
        }
        items(
            count = orderedSetlists.size,
            key = { index -> orderedSetlists[index].id },
        ) { index ->
            SetlistCard(
                setlist = orderedSetlists[index],
                itemsById = itemsById,
                onOpen = { onOpenSetlist(orderedSetlists[index].id) },
                onDelete = { onDeleteSetlist(orderedSetlists[index].id) },
            )
        }
    }
}

@Composable
private fun SongbooksOverview(
    songbooks: List<LibrarySongbook>,
    itemsById: Map<String, SheetMusicItem>,
    onOpenSongbook: (String) -> Unit,
    onDeleteSongbook: (String) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val orderedSongbooks = songbooks.sortedByDescending { it.updatedAtEpochMillis }

    if (orderedSongbooks.isEmpty()) {
        EmptySongbooksState(onOpenLibrary = onOpenLibrary)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            count = orderedSongbooks.size,
            key = { index -> orderedSongbooks[index].id },
        ) { index ->
            SongbookCard(
                songbook = orderedSongbooks[index],
                itemsById = itemsById,
                onOpen = { onOpenSongbook(orderedSongbooks[index].id) },
                onDelete = { onDeleteSongbook(orderedSongbooks[index].id) },
            )
        }
    }
}

@Composable
private fun EmptySetlistsState(
    randomSetlistAvailable: Boolean,
    onCreateRandomSetlist: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.36f),
                            Color.Transparent,
                        )
                    )
                )
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Setlists are empty",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Select one or more scores from the library, use Add to setlist, or build a temporary random mix to explore something new.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .widthIn(max = 560.dp),
            )
            Row(
                modifier = Modifier.padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(enabled = randomSetlistAvailable, onClick = onCreateRandomSetlist) {
                    Text("Random setlist")
                }
                Button(onClick = onOpenLibrary) {
                    Text("Browse scores")
                }
            }
        }
    }
}

@Composable
private fun EmptySongbooksState(onOpenLibrary: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f),
                            Color.Transparent,
                        )
                    )
                )
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Songbooks are empty",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Select scores from the library, then use Add to songbook to merge them into one PDF. You can optionally add a front cover image while creating it.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .widthIn(max = 560.dp),
            )
            Button(
                onClick = onOpenLibrary,
                modifier = Modifier.padding(top = 18.dp),
            ) {
                Text("Browse scores")
            }
        }
    }
}

@Composable
private fun TemporarySetlistCard(
    session: TemporarySetlistSession,
    itemsById: Map<String, SheetMusicItem>,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    val themePalette = lunarThemePalette()
    SetlistCardFrame(
        accentBrush = Brush.verticalGradient(
            listOf(
                themePalette.headerGradientStart,
                themePalette.headerGradientEnd,
            )
        ),
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        onClick = onOpen,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "${session.itemIds.size.scoreLabel()}  |  Temporary session",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    text = "Temporary",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Text(
            text = buildSetlistPreviewTitles(
                itemIds = session.itemIds,
                itemsById = itemsById,
                emptyText = "No scores are left in this temporary mix.",
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.84f),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Keep it temporary, or save it as a regular setlist if it clicks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDiscard) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
                OutlinedButton(onClick = onSave) {
                    Text("Save")
                }
                Button(onClick = onOpen) {
                    Text("Open")
                }
            }
        }
    }
}

@Composable
private fun SetlistCard(
    setlist: LibrarySetlist,
    itemsById: Map<String, SheetMusicItem>,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val themePalette = lunarThemePalette()
    SetlistCardFrame(
        accentBrush = Brush.verticalGradient(
            listOf(
                themePalette.accentGradientStart,
                themePalette.accentGradientEnd,
            )
        ),
        containerColor = MaterialTheme.colorScheme.surface,
        onClick = onOpen,
        modifier = Modifier.testTag(LunarTestTags.setlistRow(setlist.id)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = setlist.name,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${setlist.itemIds.size.scoreLabel()}  |  Updated ${formatEpochMillis(setlist.updatedAtEpochMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SummaryPillDark("${setlist.itemIds.size.scoreLabel()}")
        }
        Text(
            text = buildSetlistPreviewTitles(
                itemIds = setlist.itemIds,
                itemsById = itemsById,
                emptyText = "No scores saved yet.",
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Open to browse the scores in order.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = onOpen) {
                    Text("Open")
                }
            }
        }
    }
}

@Composable
private fun SongbookCard(
    songbook: LibrarySongbook,
    itemsById: Map<String, SheetMusicItem>,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val themePalette = lunarThemePalette()
    SetlistCardFrame(
        accentBrush = Brush.verticalGradient(
            listOf(
                themePalette.headerGradientStart,
                themePalette.accentGradientEnd,
            )
        ),
        containerColor = MaterialTheme.colorScheme.surface,
        onClick = onOpen,
        modifier = Modifier.testTag(LunarTestTags.songbookRow(songbook.id)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = songbook.name,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = buildSongbookMetaLine(songbook),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SummaryPillDark(songbook.pageCount?.let { "$it pages" } ?: "PDF")
        }
        Text(
            text = buildSetlistPreviewTitles(
                itemIds = songbook.itemIds,
                itemsById = itemsById,
                emptyText = "This songbook no longer has linked scores, but the merged PDF is still available.",
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Open to read the merged PDF as one continuous songbook.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = onOpen) {
                    Text("Open")
                }
            }
        }
    }
}

@Composable
private fun TemporarySetlistDetailView(
    session: TemporarySetlistSession,
    items: List<SheetMusicItem>,
    appState: LunarAppState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    SetlistDetailScaffold(
        title = session.title,
        subtitle = "Temporary random mix. Save it if you want to keep this run.",
        itemCount = items.size,
        emptyMessage = "This temporary setlist no longer has any available scores.",
        items = items,
        appState = appState,
        onBack = onBack,
    ) {
        OutlinedButton(onClick = onSave) {
            Text("Save")
        }
        TextButton(onClick = onDiscard) {
            Text("Discard", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SetlistDetailView(
    setlist: LibrarySetlist,
    items: List<SheetMusicItem>,
    appState: LunarAppState,
    onBack: () -> Unit,
    onDeleteSetlist: () -> Unit,
) {
    SetlistDetailScaffold(
        title = setlist.name,
        subtitle = "Saved automatically. Delete it whenever you no longer need it.",
        itemCount = items.size,
        emptyMessage = "This setlist is empty right now.",
        items = items,
        appState = appState,
        onBack = onBack,
    ) {
        TextButton(onClick = onDeleteSetlist) {
            Text("Delete", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun LibraryActionIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tooltipText: String = contentDescription,
    content: @Composable (Color) -> Unit,
) {
    val shape = MaterialTheme.shapes.small

    LunarTooltip(tooltipText, modifier = modifier) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
            shape = shape,
            modifier = Modifier
                .size(36.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f),
                    shape = shape,
                )
                .semantics {
                    this.contentDescription = contentDescription
                }
                .clickable(onClick = onClick),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                content(MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LibraryItemQuickActions(
    item: SheetMusicItem,
    appState: LunarAppState,
    includeDownload: Boolean,
    mode: ScoreListMode,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (includeDownload) {
            LibraryActionIconButton(
                onClick = { appState.downloadScore(item) },
                contentDescription = "Download ${item.title}",
                tooltipText = "Download score",
            ) { contentColor ->
                DownloadGlyph(color = contentColor)
            }
        }
        LibraryActionIconButton(
            onClick = { appState.toggleFavorite(item.id) },
            contentDescription = if (item.isFavorite) {
                "Remove ${item.title} from favorites"
            } else {
                "Mark ${item.title} as favorite"
            },
            tooltipText = if (item.isFavorite) {
                "Remove from favorites"
            } else {
                "Mark as favorite"
            },
        ) {
            Text(
                text = if (item.isFavorite) "\u2605" else "\u2606",
                color = if (item.isFavorite) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        LibraryActionIconButton(
            onClick = { appState.showScoreInfo(item.id) },
            contentDescription = "Show info for ${item.title}",
            tooltipText = "Show info",
        ) {
            Text(
                text = "i",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (mode == ScoreListMode.HIDDEN) {
            LibraryActionIconButton(
                onClick = { appState.restoreScore(item.id) },
                contentDescription = "Restore ${item.title}",
                tooltipText = "Restore score",
            ) {
                Text(
                    text = "+",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            LibraryActionIconButton(
                onClick = { appState.hideScore(item.id) },
                contentDescription = "Hide ${item.title}",
                tooltipText = "Hide score",
            ) {
                Text(
                    text = "\u2298",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        LibraryActionIconButton(
            onClick = { appState.startEditing(item.id) },
            contentDescription = "Edit ${item.title}",
            tooltipText = "Edit score",
        ) {
            Text(
                text = "\u270E",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        LibraryActionIconButton(
            onClick = { appState.requestDelete(item.id) },
            contentDescription = "Delete ${item.title}",
            tooltipText = "Delete score",
        ) {
            Text(
                text = "\uD83D\uDDD1",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun DownloadGlyph(color: Color) {
    Canvas(
        modifier = Modifier.size(16.dp),
    ) {
        val strokeWidth = size.minDimension * 0.16f
        val centerX = size.width / 2f
        val arrowTop = size.height * 0.14f
        val arrowBottom = size.height * 0.68f
        val trayTop = size.height * 0.78f

        drawLine(
            color = color,
            start = Offset(centerX, arrowTop),
            end = Offset(centerX, arrowBottom),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(centerX, arrowBottom),
            end = Offset(size.width * 0.28f, size.height * 0.46f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(centerX, arrowBottom),
            end = Offset(size.width * 0.72f, size.height * 0.46f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.22f, trayTop),
            end = Offset(size.width * 0.78f, trayTop),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.22f, trayTop),
            end = Offset(size.width * 0.22f, size.height * 0.6f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.78f, trayTop),
            end = Offset(size.width * 0.78f, size.height * 0.6f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun LibraryGrid(
    items: List<SheetMusicItem>,
    appState: LunarAppState,
    mode: ScoreListMode,
    onCardClick: (SheetMusicItem) -> Unit = appState::openScore,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 260.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            count = items.size,
            key = { index -> items[index].id },
        ) { index ->
            LibraryCard(
                item = items[index],
                appState = appState,
                mode = mode,
                onClick = { onCardClick(items[index]) },
            )
        }
    }
}

@Composable
private fun LibraryList(
    items: List<SheetMusicItem>,
    appState: LunarAppState,
    mode: ScoreListMode,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            count = items.size,
            key = { index -> items[index].id },
        ) { index ->
            LibraryCard(
                item = items[index],
                appState = appState,
                mode = mode,
                onClick = { appState.openScore(items[index]) },
            )
        }
    }
}

@Composable
private fun LibraryCard(
    item: SheetMusicItem,
    appState: LunarAppState,
    mode: ScoreListMode,
    onClick: () -> Unit,
) {
    val themePalette = lunarThemePalette()
    val isChecked = item.id in appState.selectedScoreIds
    // Focus (which score the detail panel shows) highlights the card but never ticks its checkbox.
    val isHighlighted = isChecked || item.id == appState.focusedScoreId
    val containerColor = if (isHighlighted) {
        MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.96f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(LibraryTestTags.row(item.id))
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = containerColor,
        ),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(
            defaultElevation = if (isHighlighted) 4.dp else 2.dp,
        ),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left accent bar
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                themePalette.accentGradientStart,
                                themePalette.accentGradientEnd,
                            )
                        )
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (appState.layoutMode == LibraryLayoutMode.GRID) {
                    ScoreThumbnail(
                        item = item,
                        cache = appState.thumbnailCache,
                        modifier = Modifier
                            .width(96.dp)
                            .height(128.dp)
                            .clip(MaterialTheme.shapes.medium),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = item.composer ?: "Composer not set",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { appState.toggleScoreSelection(item.id) },
                            modifier = Modifier.semantics {
                                contentDescription = "Select ${item.title}"
                            },
                        )
                        ViewerSupportBadge(item = item)
                        FavoriteMarker(isFavorite = item.isFavorite)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val collection = item.collection
                    if (collection != null) {
                        CollectionPill(collection)
                    }
                    PagesPill("${item.pageCount ?: "?"} pages")
                }
                if (item.tags.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        item.tags.forEach { tag ->
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Text(
                                    text = tag,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }
                if (appState.layoutMode == LibraryLayoutMode.GRID) {
                    GridLibraryCardFooter(
                        item = item,
                        appState = appState,
                        mode = mode,
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = buildDetailsLine(item),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            modifier = Modifier.weight(1f),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LibraryItemQuickActions(
                                item = item,
                                appState = appState,
                                includeDownload = appState.canDownloadScores,
                                mode = mode,
                            )
                            androidx.compose.material3.Button(onClick = { appState.openScore(item) }) {
                                Text("Open")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GridLibraryCardFooter(
    item: SheetMusicItem,
    appState: LunarAppState,
    mode: ScoreListMode,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = buildDetailsLine(item),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LibraryItemQuickActions(
                item = item,
                appState = appState,
                includeDownload = false,
                mode = mode,
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
            )
            androidx.compose.material3.Button(onClick = { appState.openScore(item) }) {
                Text("Open")
            }
        }
    }
}

@Composable
private fun DeleteScoreDialog(
    item: SheetMusicItem,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete score?") },
        text = {
            Text(
                "Remove \"${item.title}\" from the library? Lunar will also delete its imported PDF copy for this score.",
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("Delete")
            }
        },
    )
}

@Composable
private fun RandomSetlistDialog(
    availableCount: Int,
    scopeLabel: String,
    onDismiss: () -> Unit,
    onCreate: (Int) -> Unit,
) {
    var requestedCountText by remember(availableCount, scopeLabel) {
        mutableStateOf(minOf(5, availableCount).coerceAtLeast(1).toString())
    }
    val requestedCount = requestedCountText.toIntOrNull()
    val canConfirm = availableCount > 0 && requestedCount != null && requestedCount > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create a random setlist") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (availableCount > 0) {
                        "Build a temporary random setlist from $scopeLabel. Lunar will clamp the size to the ${availableCount.scoreLabel()} currently available."
                    } else {
                        "There are no scores available in $scopeLabel right now."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = requestedCountText,
                    onValueChange = { value ->
                        requestedCountText = value.filter(Char::isDigit)
                    },
                    label = { Text("How many scores?") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = availableCount > 0,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(
                enabled = canConfirm,
                onClick = {
                    requestedCount?.let(onCreate)
                },
            ) {
                Text("Shuffle")
            }
        },
    )
}

@Composable
private fun SaveTemporarySetlistDialog(
    session: TemporarySetlistSession,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var setlistName by remember(session.createdAtEpochMillis) {
        mutableStateOf(session.title)
    }
    val trimmedName = setlistName.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save temporary setlist") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Save this ${session.itemIds.size.scoreLabel()} as a regular setlist so it stays in your library.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = setlistName,
                    onValueChange = { setlistName = it },
                    label = { Text("Setlist name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(
                enabled = trimmedName.isNotEmpty(),
                onClick = {
                    onSave(trimmedName)
                },
            ) {
                Text("Save")
            }
        },
    )
}

@Composable
private fun NewSongbookDialog(
    runtime: PlatformRuntime,
    selectedCount: Int,
    coverImageSupported: Boolean,
    onDismiss: () -> Unit,
    onCreateSongbook: (String, SelectedCoverImage?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var newSongbookName by remember(selectedCount) { mutableStateOf("") }
    val selectedSongbookId: String? = null
    var selectedCoverImage by remember(selectedCount) { mutableStateOf<SelectedCoverImage?>(null) }
    var coverPickerError by remember(selectedCount) { mutableStateOf<String?>(null) }
    var coverPickerBusy by remember(selectedCount) { mutableStateOf(false) }
    val trimmedName = newSongbookName.trim()
    val canConfirm = trimmedName.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bind a new songbook") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Name this songbook and Lunar will merge ${selectedCount.scoreLabel()} into one combined document.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = newSongbookName,
                    onValueChange = { newSongbookName = it },
                    label = { Text("Songbook name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (coverImageSupported) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Front cover",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = if (selectedSongbookId == null) {
                                "Optional. Add an image cover when creating a new songbook."
                            } else {
                                "Existing songbooks keep their current cover when you append more scores."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                enabled = selectedSongbookId == null && !coverPickerBusy,
                                onClick = {
                                    scope.launch {
                                        coverPickerBusy = true
                                        coverPickerError = null
                                        try {
                                            selectedCoverImage = runtime.coverImagePicker.pickCoverImage()
                                        } catch (error: Throwable) {
                                            coverPickerError = error.message ?: "Could not load that cover image."
                                        } finally {
                                            coverPickerBusy = false
                                        }
                                    }
                                },
                            ) {
                                Text(
                                    when {
                                        coverPickerBusy -> "Loading..."
                                        selectedCoverImage != null -> "Change cover"
                                        else -> "Choose cover"
                                    }
                                )
                            }
                            selectedCoverImage?.let { cover ->
                                Text(
                                    text = cover.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    enabled = selectedSongbookId == null,
                                    onClick = { selectedCoverImage = null },
                                ) {
                                    Text("Remove")
                                }
                            }
                        }
                        coverPickerError?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(
                enabled = canConfirm,
                onClick = { onCreateSongbook(trimmedName, selectedCoverImage) },
            ) {
                Text("Bind")
            }
        },
    )
}

@Composable
private fun DeleteSetlistDialog(
    setlist: LibrarySetlist,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete setlist?") },
        text = {
            Text(
                "Delete \"${setlist.name}\"? The saved score list will be removed, but the PDFs will stay in your library.",
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("Delete")
            }
        },
    )
}

@Composable
private fun DeleteSongbookDialog(
    songbook: LibrarySongbook,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete songbook?") },
        text = {
            Text(
                "Delete \"${songbook.name}\"? Lunar will remove the merged songbook PDF, but the original scores will stay in your library.",
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("Delete")
            }
        },
    )
}

@Composable
internal fun ViewerSupportBadge(item: SheetMusicItem) {
    val description = when (item.viewerSupport) {
        ScoreViewerSupport.PDF -> "PDF viewer"
        ScoreViewerSupport.LILYPOND -> "LilyPond viewer"
    }
    val containerColor = when (item.viewerSupport) {
        ScoreViewerSupport.PDF -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.76f)
        ScoreViewerSupport.LILYPOND -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.76f)
    }
    val logo = when (item.viewerSupport) {
        ScoreViewerSupport.PDF -> Res.drawable.pdf_logo
        ScoreViewerSupport.LILYPOND -> Res.drawable.lilypond_logo
    }

    LunarTooltip(description) {
        Surface(
            color = containerColor,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .size(32.dp)
                .semantics {
                    contentDescription = description
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
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
private fun FavoriteMarker(isFavorite: Boolean) {
    val bgColor = if (isFavorite) {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f)
    } else {
        Color.Transparent
    }
    val textColor = if (isFavorite) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }
    Surface(
        color = bgColor,
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = if (isFavorite) "★" else "☆",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
        )
    }
}

@Composable
private fun CollectionPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = "📁 $text",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun PagesPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = "📄 $text",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun EmptyLibraryState(
    totalItemCount: Int,
    onOpenImport: () -> Unit,
    onClearFilters: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                            Color.Transparent,
                        )
                    )
                )
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "🎵",
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = if (totalItemCount == 0) {
                    "Your score library is ready"
                } else {
                    "No scores match the current view"
                },
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = if (totalItemCount == 0) {
                    "Import a few PDFs to start building a searchable, sync-ready sheet music catalog."
                } else {
                    "Your library has scores, but the current search or filters are hiding them. Clear the filters to show everything again."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .widthIn(max = 520.dp),
            )
            if (totalItemCount == 0) {
                Button(
                    onClick = onOpenImport,
                    modifier = Modifier.padding(top = 18.dp),
                ) {
                    Text("Open import panel")
                }
            } else {
                Button(
                    onClick = onClearFilters,
                    modifier = Modifier.padding(top = 18.dp),
                ) {
                    Text("Clear filters")
                }
            }
        }
    }
}

@Composable
private fun HiddenScoresEmptyState(
    hiddenCount: Int,
    onClearFilters: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (hiddenCount == 0) {
                    "No hidden scores"
                } else {
                    "No hidden scores match the current view"
                },
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (hiddenCount == 0) {
                    "Scores you hide from the viewer or library will appear here for review and restoration."
                } else {
                    "Clear the current search or filters to review every hidden score."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .widthIn(max = 520.dp),
            )
            if (hiddenCount > 0) {
                Button(
                    onClick = onClearFilters,
                    modifier = Modifier.padding(top = 18.dp),
                ) {
                    Text("Clear filters")
                }
            }
        }
    }
}

private data class LibraryScreenModel(
    val visibleItems: List<SheetMusicItem>,
    val catalogueGroups: List<CatalogueGroup>,
    val catalogueIndexAvailable: Boolean,
    val jumpBackInItems: List<SheetMusicItem>,
    val visibleScoreCount: Int,
    val hiddenScoreCount: Int,
    val refinementTotalCount: Int,
    val filterCollections: List<String>,
    val filterTags: List<String>,
    val itemsById: Map<String, SheetMusicItem>,
    val temporarySetlist: TemporarySetlistSession?,
    val activeTemporarySetlist: TemporarySetlistSession?,
    val activeTemporarySetlistItems: List<SheetMusicItem>,
    val activeSetlist: LibrarySetlist?,
    val activeSetlistItems: List<SheetMusicItem>,
    val currentScopeItems: List<SheetMusicItem>,
    val randomScopeLabel: String,
    val selectedItems: List<SheetMusicItem>,
    val focusedItem: SheetMusicItem?,
    val focusableItemIds: Set<String>,
    val pickerTargetItems: List<SheetMusicItem>,
    val deleteCandidate: SheetMusicItem?,
    val deleteSetlistCandidate: LibrarySetlist?,
    val deleteSongbookCandidate: LibrarySongbook?,
) {
    /** Row order as displayed: the list shows grouped rows, the gallery keeps the sorted order. */
    fun navigationOrderIds(layoutMode: LibraryLayoutMode): List<String> = when (layoutMode) {
        LibraryLayoutMode.LIST -> catalogueGroups.flatMap { group -> group.items.map { it.id } }
        LibraryLayoutMode.GRID -> visibleItems.map { it.id }
    }
}

internal fun currentLibraryScopeItems(
    snapshot: LibrarySnapshot,
    appState: LunarAppState,
): List<SheetMusicItem> {
    val visibleItems = snapshot.items.applyLibraryQuery(
        appState.query.withHiddenFilterFor(appState.showingHiddenScores)
    )
    val visibleLibraryItems = snapshot.items.filterNot { it.isHidden }
    val itemsById = visibleLibraryItems.associateBy { it.id }
    val temporarySetlist = appState.temporarySetlist
    val activeTemporarySetlist = temporarySetlist.takeIf { appState.temporarySetlistOpen }
    val activeTemporarySetlistItems = activeTemporarySetlist
        ?.itemIds
        ?.mapNotNull(itemsById::get)
        .orEmpty()
    val activeSetlist = snapshot.setlists.firstOrNull { it.id == appState.selectedSetlistId }
    val activeSetlistItems = activeSetlist
        ?.itemIds
        ?.mapNotNull(itemsById::get)
        .orEmpty()
    return currentScopeItems(
        snapshotItems = visibleLibraryItems,
        visibleItems = visibleItems,
        section = appState.hostedSection,
        browseMode = appState.browseMode,
        selectedGroup = appState.selectedGroup,
        activeTemporarySetlistItems = activeTemporarySetlistItems,
        activeSetlistItems = activeSetlistItems,
        hasActiveTemporarySetlist = activeTemporarySetlist != null,
        hasActiveSetlist = activeSetlist != null,
    )
}

private fun buildLibraryScreenModel(
    snapshot: LibrarySnapshot,
    appState: LunarAppState,
): LibraryScreenModel {
    val visibleLibraryItems = snapshot.items.filterNot { it.isHidden }
    val hiddenLibraryItems = snapshot.items.filter { it.isHidden }
    val query = appState.query.withHiddenFilterFor(appState.showingHiddenScores)
    val visibleItems = snapshot.items.applyLibraryQuery(query)
    val catalogueGroups = buildCatalogueGroups(
        items = visibleItems,
        grouping = catalogueGroupingFor(appState.browseMode, query.sortOption),
        sortOption = query.sortOption,
        sortDirection = query.sortDirection,
    )
    val filtersActive = query.searchText.isNotBlank() ||
        query.selectedTags.isNotEmpty() ||
        query.selectedCollection != null ||
        query.viewerSupport != ViewerSupportFilter.ALL ||
        query.favoritesOnly
    val filterBaseItems = if (appState.showingHiddenScores) {
        hiddenLibraryItems
    } else {
        visibleLibraryItems
    }
    val itemsById = visibleLibraryItems.associateBy { it.id }
    val temporarySetlist = appState.temporarySetlist
    val activeTemporarySetlist = temporarySetlist.takeIf { appState.temporarySetlistOpen }
    val activeTemporarySetlistItems = activeTemporarySetlist
        ?.itemIds
        ?.mapNotNull(itemsById::get)
        .orEmpty()
    val activeSetlist = snapshot.setlists.firstOrNull { it.id == appState.selectedSetlistId }
    val activeSetlistItems = activeSetlist
        ?.itemIds
        ?.mapNotNull(itemsById::get)
        .orEmpty()
    val currentScopeItems = currentLibraryScopeItems(
        snapshot = snapshot,
        appState = appState,
    )
    val focusableItemIds = when (appState.hostedSection) {
        AppSection.SETLISTS -> itemsById.keys
        AppSection.SONGBOOKS -> emptySet()
        else -> visibleItems.mapTo(mutableSetOf()) { it.id }
    }
    val focusedItem = appState.focusedScoreId
        ?.takeIf { it in focusableItemIds }
        ?.let { focusedId -> snapshot.items.firstOrNull { it.id == focusedId } }
    val pickerTargetIds = appState.pickerTargetIds
    return LibraryScreenModel(
        visibleItems = visibleItems,
        catalogueGroups = catalogueGroups,
        catalogueIndexAvailable = catalogueIndexAnchors(catalogueGroups, query.sortOption).isNotEmpty(),
        jumpBackInItems = if (appState.browseMode == LibraryBrowseMode.ALL && !filtersActive) {
            snapshot.items.jumpBackInItems()
        } else {
            emptyList()
        },
        visibleScoreCount = visibleLibraryItems.size,
        hiddenScoreCount = hiddenLibraryItems.size,
        refinementTotalCount = filterBaseItems.size,
        filterCollections = filterBaseItems.availableCollections().toList(),
        filterTags = filterBaseItems.availableTags().toList(),
        itemsById = itemsById,
        temporarySetlist = temporarySetlist,
        activeTemporarySetlist = activeTemporarySetlist,
        activeTemporarySetlistItems = activeTemporarySetlistItems,
        activeSetlist = activeSetlist,
        activeSetlistItems = activeSetlistItems,
        currentScopeItems = currentScopeItems,
        randomScopeLabel = randomScopeLabel(
            section = appState.hostedSection,
            browseMode = appState.browseMode,
            selectedGroup = appState.selectedGroup,
            activeTemporarySetlist = activeTemporarySetlist,
            activeSetlist = activeSetlist,
        ),
        selectedItems = visibleLibraryItems.filter { it.id in appState.selectedScoreIds },
        focusedItem = focusedItem,
        focusableItemIds = focusableItemIds,
        pickerTargetItems = visibleLibraryItems.filter { it.id in pickerTargetIds },
        deleteCandidate = snapshot.items.firstOrNull { it.id == appState.deleteCandidateItemId },
        deleteSetlistCandidate = snapshot.setlists.firstOrNull { it.id == appState.deleteCandidateSetlistId },
        deleteSongbookCandidate = snapshot.songbooks.firstOrNull { it.id == appState.deleteCandidateSongbookId },
    )
}

private fun currentScopeItems(
    snapshotItems: List<SheetMusicItem>,
    visibleItems: List<SheetMusicItem>,
    section: AppSection,
    browseMode: LibraryBrowseMode,
    selectedGroup: String?,
    activeTemporarySetlistItems: List<SheetMusicItem>,
    activeSetlistItems: List<SheetMusicItem>,
    hasActiveTemporarySetlist: Boolean,
    hasActiveSetlist: Boolean,
): List<SheetMusicItem> = when {
    section == AppSection.SETLISTS -> when {
        hasActiveTemporarySetlist -> activeTemporarySetlistItems
        hasActiveSetlist -> activeSetlistItems
        else -> snapshotItems
    }
    section == AppSection.SONGBOOKS -> emptyList()
    else -> libraryScopeItems(
        visibleItems = visibleItems,
        browseMode = browseMode,
        selectedGroup = selectedGroup,
    )
}

private fun libraryScopeItems(
    visibleItems: List<SheetMusicItem>,
    browseMode: LibraryBrowseMode,
    selectedGroup: String?,
): List<SheetMusicItem> = when (browseMode) {
    LibraryBrowseMode.ALL -> visibleItems
    LibraryBrowseMode.BY_COLLECTION -> selectedGroup?.let {
        visibleItems.itemsInBrowseGroup(browseMode, it)
    } ?: visibleItems
    LibraryBrowseMode.BY_COMPOSER -> selectedGroup?.let {
        visibleItems.itemsInBrowseGroup(browseMode, it)
    } ?: visibleItems
    LibraryBrowseMode.HIDDEN -> emptyList()
}

/** The library-hosted section `LibraryScreen` renders; other sections fall back to Library. */
internal val LunarAppState.hostedSection: AppSection
    get() = if (selectedSection.isLibraryHosted) selectedSection else AppSection.LIBRARY

private val LunarAppState.showingHiddenScores: Boolean
    get() = hostedSection == AppSection.LIBRARY && browseMode == LibraryBrowseMode.HIDDEN

private fun com.ryanjames.lunar.library.model.LibraryQuery.withHiddenFilterFor(
    showingHiddenScores: Boolean,
): com.ryanjames.lunar.library.model.LibraryQuery = copy(
    hiddenFilter = if (showingHiddenScores) {
        HiddenScoreFilter.HIDDEN
    } else {
        HiddenScoreFilter.VISIBLE
    },
)

private fun randomScopeLabel(
    section: AppSection,
    browseMode: LibraryBrowseMode,
    selectedGroup: String?,
    activeTemporarySetlist: TemporarySetlistSession?,
    activeSetlist: LibrarySetlist?,
): String = when {
    section == AppSection.SETLISTS -> when {
        activeTemporarySetlist != null -> "this temporary setlist"
        activeSetlist != null -> "\"${activeSetlist.name}\""
        else -> "the full library"
    }
    section == AppSection.SONGBOOKS -> "songbooks"
    else -> when (browseMode) {
        LibraryBrowseMode.ALL -> "the current library view"
        LibraryBrowseMode.BY_COLLECTION -> selectedGroup?.let { "\"$it\"" } ?: "the current library view"
        LibraryBrowseMode.BY_COMPOSER -> selectedGroup?.let { "\"$it\"" } ?: "the current library view"
        LibraryBrowseMode.HIDDEN -> "hidden scores"
    }
}

private fun List<SheetMusicItem>.itemsInBrowseGroup(
    browseMode: LibraryBrowseMode,
    groupName: String,
): List<SheetMusicItem> = filter { item ->
    when (browseMode) {
        LibraryBrowseMode.BY_COLLECTION ->
            item.collection?.trim().equals(groupName, ignoreCase = true)

        LibraryBrowseMode.BY_COMPOSER ->
            item.composer?.trim().equals(groupName, ignoreCase = true)

        else -> false
    }
}

private fun buildSetlistPreviewTitles(
    itemIds: List<String>,
    itemsById: Map<String, SheetMusicItem>,
    emptyText: String,
): String = itemIds
    .mapNotNull(itemsById::get)
    .take(3)
    .joinToString("  •  ") { it.title }
    .ifBlank { emptyText }

private fun buildSongbookMetaLine(songbook: LibrarySongbook): String = buildList {
    add(songbook.itemIds.size.scoreLabel())
    songbook.pageCount?.let { add("$it pages") }
    add("Updated ${formatEpochMillis(songbook.updatedAtEpochMillis)}")
}.joinToString("  |  ")

@Composable
private fun SetlistCardFrame(
    accentBrush: Brush,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = containerColor,
        ),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(accentBrush),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SetlistDetailScaffold(
    title: String,
    subtitle: String,
    itemCount: Int,
    emptyMessage: String,
    items: List<SheetMusicItem>,
    appState: LunarAppState,
    onBack: () -> Unit,
    headerActions: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SetlistBackButton(onBack = onBack)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SummaryPillDark("${itemCount.scoreLabel()}")
            headerActions()
        }
        Box(modifier = Modifier.weight(1f)) {
            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                FlatScoreList(items = items, appState = appState)
            }
        }
    }
}

@Composable
private fun SetlistBackButton(onBack: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.clickable(onClick = onBack),
    ) {
        Text(
            text = "Back",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

private fun buildDetailsLine(item: SheetMusicItem): String {
    val parts = buildList {
        add("Added ${formatEpochMillis(item.dateAddedEpochMillis)}")
        item.lastOpenedEpochMillis?.let { add("Last opened ${formatEpochMillis(it)}") }
        if (item.lastViewedPage > 0) {
            add("Resume page ${item.lastViewedPage + 1}")
        }
    }
    return parts.joinToString("  |  ")
}

internal fun Int.scoreLabel(): String = "$this score${if (this == 1) "" else "s"}"

internal fun formatEpochMillis(value: Long): String {
    val date = Instant.fromEpochMilliseconds(value)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    return "${date.year}-${date.monthNumber.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')}"
}

internal fun LibrarySortOption.label(): String = when (this) {
    LibrarySortOption.TITLE -> "Title"
    LibrarySortOption.COMPOSER -> "Composer"
    LibrarySortOption.DATE_ADDED -> "Date added"
    LibrarySortOption.LAST_OPENED -> "Last opened"
}

internal fun ViewerSupportFilter.label(): String = when (this) {
    ViewerSupportFilter.ALL -> "All viewers"
    ViewerSupportFilter.PDF -> "PDF Viewer"
    ViewerSupportFilter.LILYPOND -> "LilyPond Viewer"
}

internal fun LibraryLayoutMode.label(): String = when (this) {
    LibraryLayoutMode.GRID -> "Gallery"
    LibraryLayoutMode.LIST -> "List"
}

