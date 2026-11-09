package com.ryanjames.lunar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ryanjames.lunar.library.data.LibrarySnapshot
import com.ryanjames.lunar.library.model.ScoreMetadata
import com.ryanjames.lunar.library.model.SheetMusicItem

internal enum class DetailPanelLayout {
    /** A column beside the catalogue on wide windows. */
    SIDE,

    /** A bar under the catalogue below [DetailPanelSideBreakpoint]. */
    BOTTOM,
}

internal val DetailPanelSideBreakpoint = 1200.dp

/**
 * Unsaved edits for one score's details. Owned by the library screen rather than the panel, so a
 * draft survives the panel moving between [DetailPanelLayout.SIDE] and [DetailPanelLayout.BOTTOM].
 */
@Stable
internal class ScoreDetailsDraft(item: SheetMusicItem) {
    var title by mutableStateOf(item.title)
    var composer by mutableStateOf(item.composer.orEmpty())
    var tags by mutableStateOf(item.tags.joinToString(", "))
    var collection by mutableStateOf(item.collection.orEmpty())
    var isFavorite by mutableStateOf(item.isFavorite)
}

/** A fresh draft whenever the focused score changes or its editor is opened or closed. */
@Composable
internal fun rememberScoreDetailsDraft(item: SheetMusicItem?, editingItemId: String?): ScoreDetailsDraft? =
    remember(item?.id, editingItemId) { item?.let(::ScoreDetailsDraft) }

// --- Pure helpers ------------------------------------------------------------

/** "Open at page N" when the score was left past page 1, otherwise just "Open". */
internal fun openScoreLabel(item: SheetMusicItem): String =
    if (item.lastViewedPage > 0) "Open at page ${item.lastViewedPage + 1}" else "Open"

/** "Last on page N of M, opened <relative time>", or a short note for scores that were never opened. */
internal fun scoreResumeLine(item: SheetMusicItem, nowEpochMillis: Long): String {
    val openedAt = item.lastOpenedEpochMillis
        ?: return item.pageCount?.let { "Not opened yet, ${pageCountLabel(it)}" } ?: "Not opened yet"
    val page = buildString {
        append("Last on page ${item.lastViewedPage + 1}")
        item.pageCount?.let { append(" of $it") }
    }
    return "$page, opened ${relativeTimeLabel(openedAt, nowEpochMillis)}"
}

/** Short caption for the Jump back in strip: "Page N of M, <relative time>". */
internal fun jumpBackInCaption(item: SheetMusicItem, nowEpochMillis: Long): String {
    val page = buildString {
        append("Page ${item.lastViewedPage + 1}")
        item.pageCount?.let { append(" of $it") }
    }
    val openedAt = item.lastOpenedEpochMillis ?: return page
    return "$page, ${relativeTimeLabel(openedAt, nowEpochMillis)}"
}

internal fun relativeTimeLabel(thenEpochMillis: Long, nowEpochMillis: Long): String {
    val elapsedMinutes = (nowEpochMillis - thenEpochMillis).coerceAtLeast(0L) / 60_000L
    val hours = elapsedMinutes / 60L
    val days = hours / 24L
    return when {
        elapsedMinutes < 1L -> "just now"
        elapsedMinutes < 60L -> "${plural(elapsedMinutes, "minute")} ago"
        hours < 24L -> "${plural(hours, "hour")} ago"
        days == 1L -> "yesterday"
        days < 7L -> "$days days ago"
        days < 30L -> "${plural(days / 7L, "week")} ago"
        days < 365L -> "${plural(days / 30L, "month")} ago"
        else -> "${plural(days / 365L, "year")} ago"
    }
}

private fun plural(count: Long, unit: String): String = "$count $unit${if (count == 1L) "" else "s"}"

private fun pageCountLabel(count: Int): String = "$count page${if (count == 1) "" else "s"}"

// --- Panel -------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScoreDetailPanel(
    item: SheetMusicItem,
    snapshot: LibrarySnapshot,
    appState: LunarAppState,
    layout: DetailPanelLayout,
    draft: ScoreDetailsDraft,
    nowEpochMillis: Long,
    onTextInputFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val editing = appState.editingItemId == item.id
    val showingInfo = appState.infoItemId == item.id
    val targetIds = listOf(item.id)

    Surface(
        modifier = modifier.testTag(LibraryTestTags.DetailPanel),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (layout) {
                DetailPanelLayout.SIDE -> {
                    ScoreThumbnail(
                        item = item,
                        cache = appState.thumbnailCache,
                        modifier = Modifier
                            .width(168.dp)
                            .height(220.dp)
                            .clip(MaterialTheme.shapes.small)
                            .align(Alignment.CenterHorizontally),
                    )
                    DetailHeading(item = item, appState = appState)
                }

                DetailPanelLayout.BOTTOM -> Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    ScoreThumbnail(
                        item = item,
                        cache = appState.thumbnailCache,
                        modifier = Modifier
                            .width(64.dp)
                            .height(84.dp)
                            .clip(MaterialTheme.shapes.small),
                    )
                    DetailHeading(item = item, appState = appState, modifier = Modifier.weight(1f))
                }
            }

            Text(
                text = scoreResumeLine(item, nowEpochMillis),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { appState.openScore(item) }) {
                    Text(openScoreLabel(item))
                }
                if (item.lastViewedPage > 0) {
                    OutlinedButton(onClick = { appState.openScore(item, fromTop = true) }) {
                        Text("From the top")
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                if (!item.isHidden) {
                    Box {
                        DetailAction(label = "Add to setlist", onClick = appState::showSetlistPicker)
                        AddToSetlistPopover(
                            expanded = appState.setlistPickerVisible && appState.selectedScoreIds.isEmpty(),
                            targetIds = targetIds,
                            setlists = snapshot.setlists,
                            onDismiss = appState::dismissSetlistPicker,
                            onAddToSetlist = { setlistId -> appState.addSelectionToSetlist(setlistId, targetIds) },
                            onCreateSetlist = { name -> appState.createSetlist(name, targetIds) },
                        )
                    }
                    if (appState.canCreateSongbooks) {
                        Box {
                            DetailAction(label = "Add to songbook", onClick = appState::showSongbookPicker)
                            AddToSongbookPopover(
                                expanded = appState.songbookPickerVisible && appState.selectedScoreIds.isEmpty(),
                                targetIds = targetIds,
                                songbooks = snapshot.songbooks,
                                onDismiss = appState::dismissSongbookPicker,
                                onAddToSongbook = { songbookId ->
                                    appState.addSelectionToSongbook(songbookId, listOf(item))
                                },
                                onNewSongbook = appState::showNewSongbookDialog,
                            )
                        }
                    }
                }
                DetailAction(
                    label = if (editing) "Close editor" else "Edit details",
                    contentDescription = "Edit ${item.title}",
                    onClick = { if (editing) appState.dismissEditor() else appState.startEditing(item.id) },
                )
                DetailAction(
                    label = if (showingInfo) "Fewer details" else "More details",
                    contentDescription = "Show info for ${item.title}",
                    onClick = { if (showingInfo) appState.dismissScoreInfo() else appState.showScoreInfo(item.id) },
                )
                if (appState.canDownloadScores) {
                    DetailAction(
                        label = "Download",
                        contentDescription = "Download ${item.title}",
                        onClick = { appState.downloadScore(item) },
                    )
                }
                if (item.isHidden) {
                    DetailAction(
                        label = "Restore",
                        contentDescription = "Restore ${item.title}",
                        onClick = { appState.restoreScore(item.id) },
                    )
                } else {
                    DetailAction(
                        label = "Hide",
                        contentDescription = "Hide ${item.title}",
                        onClick = { appState.hideScore(item.id) },
                    )
                }
                DetailAction(
                    label = "Delete",
                    contentDescription = "Delete ${item.title}",
                    danger = true,
                    onClick = { appState.requestDelete(item.id) },
                )
            }

            if (editing) {
                ScoreDetailsEditor(
                    draft = draft,
                    onTextInputFocusChanged = onTextInputFocusChanged,
                    onCancel = appState::dismissEditor,
                    onSave = { title, composer, tags, collection, isFavorite ->
                        appState.saveMetadata(
                            itemId = item.id,
                            title = title,
                            composer = composer,
                            tags = tags,
                            collection = collection,
                            isFavorite = isFavorite,
                        )
                    },
                )
            }

            if (showingInfo) {
                ScoreInfoDetails(
                    item = item,
                    metadata = appState.infoMetadata,
                    loading = appState.infoMetadataLoading,
                    errorMessage = appState.infoMetadataError,
                )
            }
        }
    }
}

@Composable
private fun DetailHeading(
    item: SheetMusicItem,
    appState: LunarAppState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Top,
        ) {
            TruncatedText(
                text = item.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                modifier = Modifier.weight(1f),
                textModifier = Modifier.testTag(LibraryTestTags.DetailTitle),
            )
            TextButton(
                onClick = { appState.toggleFavorite(item.id) },
                modifier = Modifier.semantics {
                    contentDescription = if (item.isFavorite) {
                        "Remove ${item.title} from favorites"
                    } else {
                        "Mark ${item.title} as favorite"
                    }
                },
            ) {
                Text(
                    text = if (item.isFavorite) "★" else "☆",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (item.isFavorite) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        Text(
            text = item.composer?.takeIf(String::isNotBlank) ?: "Composer not set",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ViewerSupportBadge(item = item)
            item.collection?.trim()?.takeIf(String::isNotEmpty)?.let { collection ->
                CollectionLabel(name = collection)
            }
        }
    }
}

@Composable
private fun DetailAction(
    label: String,
    onClick: () -> Unit,
    contentDescription: String? = null,
    danger: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        modifier = if (contentDescription != null) {
            Modifier.semantics { this.contentDescription = contentDescription }
        } else {
            Modifier
        },
    ) {
        Text(
            text = label,
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ScoreDetailsEditor(
    draft: ScoreDetailsDraft,
    onTextInputFocusChanged: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onSave: (title: String, composer: String, tags: String, collection: String, isFavorite: Boolean) -> Unit,
) {
    val fieldModifier = Modifier
        .fillMaxWidth()
        .onFocusChanged { onTextInputFocusChanged(it.hasFocus) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallCapsText("Edit details")
        OutlinedTextField(
            value = draft.title,
            onValueChange = { draft.title = it },
            label = { Text("Title") },
            modifier = fieldModifier,
            singleLine = true,
        )
        OutlinedTextField(
            value = draft.composer,
            onValueChange = { draft.composer = it },
            label = { Text("Composer") },
            modifier = fieldModifier,
            singleLine = true,
        )
        OutlinedTextField(
            value = draft.collection,
            onValueChange = { draft.collection = it },
            label = { Text("Collection") },
            modifier = fieldModifier,
            singleLine = true,
        )
        OutlinedTextField(
            value = draft.tags,
            onValueChange = { draft.tags = it },
            label = { Text("Tags (comma separated)") },
            modifier = fieldModifier,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = draft.isFavorite, onCheckedChange = { draft.isFavorite = it })
            Text("Favourite", style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = draft.title.isNotBlank(),
                onClick = { onSave(draft.title, draft.composer, draft.tags, draft.collection, draft.isFavorite) },
            ) {
                Text("Save")
            }
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun ScoreInfoDetails(
    item: SheetMusicItem,
    metadata: ScoreMetadata?,
    loading: Boolean,
    errorMessage: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SmallCapsText("More details")
        when {
            loading -> Text(
                text = "Loading score info...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            !errorMessage.isNullOrBlank() -> Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )

            metadata == null -> Text(
                text = "No metadata is available for this score yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> ScoreMetadataFields(item = item, metadata = metadata)
        }
    }
}

@Composable
private fun ScoreMetadataFields(
    item: SheetMusicItem,
    metadata: ScoreMetadata,
) {
    val hasWorkSection = metadata.alternativeTitles.isNotEmpty() ||
        metadata.catalogueNumber.isNotBlank() ||
        metadata.opusNumber.isNotBlank() ||
        metadata.workNumber.isNotBlank() ||
        metadata.yearComposed != null ||
        metadata.movements.isNotEmpty()
    val hasMusicalDetailsSection = metadata.genre.isNotBlank() ||
        metadata.form.isNotBlank() ||
        metadata.stylePeriod.isNotBlank() ||
        metadata.instrumentation.isNotEmpty() ||
        metadata.key.isNotBlank() ||
        metadata.timeSignature.isNotBlank() ||
        metadata.tempoMarkings.isNotEmpty() ||
        metadata.difficulty.isNotBlank() ||
        metadata.pageCount != null ||
        metadata.durationSeconds != null
    val hasPublicationSection = metadata.publisher.isNotBlank() ||
        metadata.editor.isNotBlank() ||
        metadata.arranger.isNotBlank() ||
        metadata.edition.isNotBlank() ||
        metadata.language.isNotBlank()

    scoreMetadataComposerLine(metadata)?.let { ScoreInfoField("Composer", it) }
    scoreInfoValue(metadata.subtitle)?.let { ScoreInfoField("Subtitle", it) }

    if (hasWorkSection) {
        ScoreInfoSection("Work")
        scoreInfoValue(metadata.alternativeTitles.joinToString(", "))?.let {
            ScoreInfoField("Alternative titles", it)
        }
        scoreInfoValue(metadata.catalogueNumber)?.let { ScoreInfoField("Catalogue number", it) }
        scoreInfoValue(metadata.opusNumber)?.let { ScoreInfoField("Opus number", it) }
        scoreInfoValue(metadata.workNumber)?.let { ScoreInfoField("Work number", it) }
        metadata.yearComposed?.let { ScoreInfoField("Year composed", it.toString()) }
        scoreInfoValue(metadata.movements.joinToString(", "))?.let { ScoreInfoField("Movements", it) }
    }

    if (hasMusicalDetailsSection) {
        ScoreInfoSection("Musical details")
        scoreInfoValue(metadata.genre)?.let { ScoreInfoField("Genre", it) }
        scoreInfoValue(metadata.form)?.let { ScoreInfoField("Form", it) }
        scoreInfoValue(metadata.stylePeriod)?.let { ScoreInfoField("Style period", it) }
        scoreInfoValue(metadata.instrumentation.joinToString(", "))?.let {
            ScoreInfoField("Instrumentation", it)
        }
        scoreInfoValue(metadata.key)?.let { ScoreInfoField("Key", it) }
        scoreInfoValue(metadata.timeSignature)?.let { ScoreInfoField("Time signature", it) }
        scoreInfoValue(metadata.tempoMarkings.joinToString(", "))?.let {
            ScoreInfoField("Tempo markings", it)
        }
        scoreInfoValue(metadata.difficulty)?.let { ScoreInfoField("Difficulty", it) }
        metadata.pageCount?.let { ScoreInfoField("Pages", it.toString()) }
        metadata.durationSeconds?.let { ScoreInfoField("Duration", "${it}s") }
    }

    if (hasPublicationSection) {
        ScoreInfoSection("Publication")
        scoreInfoValue(metadata.publisher)?.let { ScoreInfoField("Publisher", it) }
        scoreInfoValue(metadata.editor)?.let { ScoreInfoField("Editor", it) }
        scoreInfoValue(metadata.arranger)?.let { ScoreInfoField("Arranger", it) }
        scoreInfoValue(metadata.edition)?.let { ScoreInfoField("Edition", it) }
        scoreInfoValue(metadata.language)?.let { ScoreInfoField("Language", it) }
    }

    ScoreInfoSection("Library")
    scoreInfoValue(item.collection)?.let { ScoreInfoField("Collection", it) }
    if (item.isFavorite) {
        ScoreInfoField("Favorite", "Yes")
    }
    if (item.isHidden) {
        ScoreInfoField("Hidden", "Yes")
    }
    ScoreInfoField("Added", formatEpochMillis(item.dateAddedEpochMillis))
    item.lastOpenedEpochMillis?.let { ScoreInfoField("Last opened", formatEpochMillis(it)) }

    ScoreInfoSection("Source")
    scoreInfoValue(metadata.source.filename.ifBlank { item.document.originalFileName })?.let {
        ScoreInfoField("Filename", it)
    }
    scoreInfoValue(metadata.source.fileType)?.let { ScoreInfoField("File type", it) }
    scoreInfoValue(metadata.source.url.ifBlank { item.document.sourceUri.orEmpty() })?.let {
        ScoreInfoField("URL", it)
    }
    ScoreInfoField("Schema", "${metadata.schemaId} ${metadata.schemaVersion}")

    scoreInfoValue(metadata.tags.joinToString(", "))?.let {
        ScoreInfoSection("Tags")
        ScoreInfoField("Keywords", it)
    }
    scoreInfoValue(metadata.notes)?.let {
        ScoreInfoSection("Notes")
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ScoreInfoSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ScoreInfoField(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun scoreMetadataComposerLine(metadata: ScoreMetadata): String? {
    val composerName = scoreInfoValue(metadata.composer.name) ?: return null
    val lifespan = when {
        metadata.composer.birthYear != null && metadata.composer.deathYear != null ->
            "${metadata.composer.birthYear}-${metadata.composer.deathYear}"

        metadata.composer.birthYear != null ->
            "born ${metadata.composer.birthYear}"

        metadata.composer.deathYear != null ->
            "died ${metadata.composer.deathYear}"

        else -> null
    }
    return listOfNotNull(composerName, lifespan).joinToString(" ")
}

private fun scoreInfoValue(value: String?): String? = value
    ?.trim()
    ?.takeIf(String::isNotEmpty)
