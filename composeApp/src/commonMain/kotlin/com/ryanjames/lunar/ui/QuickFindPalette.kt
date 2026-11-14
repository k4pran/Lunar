package com.ryanjames.lunar.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ryanjames.lunar.ui.design.LunarIcon
import com.ryanjames.lunar.ui.design.LunarIconKind
import com.ryanjames.lunar.ui.design.LunarTestTags
import com.ryanjames.lunar.ui.design.SmallCapsLabel
import com.ryanjames.lunar.ui.design.lunarTokens

/**
 * The Ctrl+K palette: a search field over scores, setlists, songbooks and actions. Up/Down move
 * the highlight, Enter runs it, and Escape closes the palette without doing anything.
 */
@Composable
internal fun QuickFindPalette(
    entriesFor: (String) -> List<QuickFindEntry>,
    onSelect: (QuickFindEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val entries = remember(query, entriesFor) { entriesFor(query) }
    var highlighted by remember(entries) { mutableIntStateOf(0) }
    val rows = remember(entries) { quickFindRows(entries) }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val tokens = lunarTokens()

    LaunchedEffect(highlighted, rows) {
        val rowIndex = rows.indexOfFirst { it is QuickFindRow.Item && it.index == highlighted }
        if (rowIndex >= 0) listState.scrollToItem((rowIndex - 1).coerceAtLeast(0))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                modifier = Modifier
                    .padding(top = maxHeight * 0.12f, start = tokens.spacing.lg, end = tokens.spacing.lg)
                    .width((maxWidth - tokens.spacing.lg * 2).coerceAtMost(560.dp))
                    .heightIn(max = (maxHeight * 0.7f).coerceAtLeast(240.dp))
                    .testTag(LunarTestTags.QUICK_FIND_PANEL),
                shape = tokens.shapes.large,
                color = tokens.colors.panel,
                contentColor = tokens.colors.ink,
                border = BorderStroke(1.dp, tokens.colors.rule),
                shadowElevation = 8.dp,
            ) {
                Column {
                    QuickFindInput(
                        query = query,
                        onQueryChange = { query = it },
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    Key.DirectionDown -> {
                                        if (entries.isNotEmpty()) highlighted = (highlighted + 1) % entries.size
                                        true
                                    }

                                    Key.DirectionUp -> {
                                        if (entries.isNotEmpty()) {
                                            highlighted = (highlighted - 1 + entries.size) % entries.size
                                        }
                                        true
                                    }

                                    Key.Enter, Key.NumPadEnter -> {
                                        entries.getOrNull(highlighted)?.let(onSelect)
                                        true
                                    }

                                    Key.Escape -> {
                                        onDismiss()
                                        true
                                    }

                                    else -> false
                                }
                            },
                    )
                    if (entries.isEmpty()) {
                        Text(
                            text = "Nothing matches “${query.trim()}”.",
                            style = tokens.typography.bodySmall,
                            color = tokens.colors.muted,
                            modifier = Modifier.padding(tokens.spacing.lg),
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(rows, key = QuickFindRow::key) { row ->
                                when (row) {
                                    is QuickFindRow.Header -> SmallCapsLabel(
                                        text = row.group.label,
                                        modifier = Modifier.padding(
                                            start = tokens.spacing.lg,
                                            end = tokens.spacing.lg,
                                            top = tokens.spacing.md,
                                            bottom = tokens.spacing.xs,
                                        ),
                                    )

                                    is QuickFindRow.Item -> QuickFindResultRow(
                                        entry = row.entry,
                                        highlighted = row.index == highlighted,
                                        onClick = { onSelect(row.entry) },
                                    )
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
private fun QuickFindInput(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = lunarTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.colors.field)
            .padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LunarIcon(kind = LunarIconKind.SEARCH, contentDescription = null, tint = tokens.colors.muted)
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "Find a score, setlist, songbook or action",
                    style = tokens.typography.body,
                    color = tokens.colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = tokens.typography.body.copy(color = tokens.colors.ink),
                cursorBrush = SolidColor(tokens.colors.accent),
                modifier = modifier
                    .fillMaxWidth()
                    .testTag(LunarTestTags.QUICK_FIND_INPUT)
                    .semantics { contentDescription = "Quick find" },
            )
        }
    }
}

@Composable
private fun QuickFindResultRow(
    entry: QuickFindEntry,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val tokens = lunarTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(LunarTestTags.quickFindResult(entry.key))
            .semantics { selected = highlighted }
            .background(if (highlighted) tokens.colors.selection else tokens.colors.panel)
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.title,
            style = tokens.typography.body,
            color = tokens.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        entry.detail?.let { detail ->
            Text(
                text = detail,
                style = if (entry is QuickFindEntry.Action) tokens.typography.label else tokens.typography.bodySmall,
                color = tokens.colors.muted,
                maxLines = 1,
            )
        }
    }
}

private sealed interface QuickFindRow {
    val key: String

    data class Header(val group: QuickFindGroup) : QuickFindRow {
        override val key: String get() = "header_${group.name}"
    }

    data class Item(val entry: QuickFindEntry, val index: Int) : QuickFindRow {
        override val key: String get() = entry.key
    }
}

private fun quickFindRows(entries: List<QuickFindEntry>): List<QuickFindRow> = buildList {
    entries.forEachIndexed { index, entry ->
        if (index == 0 || entries[index - 1].group != entry.group) {
            add(QuickFindRow.Header(entry.group))
        }
        add(QuickFindRow.Item(entry, index))
    }
}
