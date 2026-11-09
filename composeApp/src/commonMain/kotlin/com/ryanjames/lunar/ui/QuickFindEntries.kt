package com.ryanjames.lunar.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.ryanjames.lunar.library.data.LibrarySnapshot
import com.ryanjames.lunar.library.model.LibrarySetlist
import com.ryanjames.lunar.library.model.LibrarySongbook
import com.ryanjames.lunar.library.model.SheetMusicItem
import com.ryanjames.lunar.library.model.quickFind
import com.ryanjames.lunar.library.model.rankQuickFind
import com.ryanjames.lunar.settings.ViewerKeybindings
import com.ryanjames.lunar.settings.ViewerShortcutAction

internal const val QuickFindResultsPerGroup = 6

enum class QuickFindGroup(val label: String) {
    SCORES("Scores"),
    SETLISTS("Setlists"),
    SONGBOOKS("Songbooks"),
    ACTIONS("Actions"),
}

/** Commands the quick find palette can run by name. */
enum class QuickFindAction(
    val label: String,
    val shortcut: ViewerShortcutAction? = null,
) {
    OPEN_RANDOM_SCORE("Open a random score", ViewerShortcutAction.OPEN_RANDOM_SCORE),
    START_METRONOME("Start the metronome"),
    TOGGLE_TWO_PAGE_VIEW("Toggle two-page view", ViewerShortcutAction.TOGGLE_PAGE_VIEW_MODE),
    ADD_FOCUSED_SCORE_TO_SETLIST("Add this score to a setlist"),
    SWITCH_SKIN("Switch skin"),
}

sealed interface QuickFindEntry {
    val key: String
    val group: QuickFindGroup
    val title: String
    val detail: String?

    data class Score(val item: SheetMusicItem) : QuickFindEntry {
        override val key: String get() = "score_${item.id}"
        override val group: QuickFindGroup get() = QuickFindGroup.SCORES
        override val title: String get() = item.title
        override val detail: String? get() = item.composer?.trim()?.takeIf(String::isNotEmpty)
    }

    data class Setlist(val setlist: LibrarySetlist) : QuickFindEntry {
        override val key: String get() = "setlist_${setlist.id}"
        override val group: QuickFindGroup get() = QuickFindGroup.SETLISTS
        override val title: String get() = setlist.name
        override val detail: String? get() = scoreCountLabel(setlist.itemIds.size)
    }

    data class Songbook(val songbook: LibrarySongbook) : QuickFindEntry {
        override val key: String get() = "songbook_${songbook.id}"
        override val group: QuickFindGroup get() = QuickFindGroup.SONGBOOKS
        override val title: String get() = songbook.name
        override val detail: String? get() = scoreCountLabel(songbook.itemIds.size)
    }

    data class Action(
        val action: QuickFindAction,
        val keybinding: String?,
    ) : QuickFindEntry {
        override val key: String get() = "action_${action.name.lowercase()}"
        override val group: QuickFindGroup get() = QuickFindGroup.ACTIONS
        override val title: String get() = action.label
        override val detail: String? get() = keybinding?.let(::viewerShortcutKeyLabel)
    }
}

/** Actions that make sense right now; adding to a setlist needs a focused score. */
internal fun availableQuickFindActions(hasFocusedScore: Boolean): List<QuickFindAction> =
    QuickFindAction.entries.filter { action ->
        action != QuickFindAction.ADD_FOCUSED_SCORE_TO_SETLIST || hasFocusedScore
    }

/** Flattens ranked library content and actions into the palette's display order. */
internal fun buildQuickFindEntries(
    snapshot: LibrarySnapshot,
    query: String,
    actions: List<QuickFindAction>,
    keybindings: ViewerKeybindings,
    limitPerGroup: Int = QuickFindResultsPerGroup,
): List<QuickFindEntry> {
    val results = quickFind(
        items = snapshot.items,
        setlists = snapshot.setlists,
        songbooks = snapshot.songbooks,
        query = query,
        limitPerGroup = limitPerGroup,
    )
    val rankedActions = rankQuickFind(
        candidates = actions,
        query = query,
        fields = { listOf(it.label.lowercase()) },
        label = QuickFindAction::label,
    )
    return buildList {
        results.scores.mapTo(this, QuickFindEntry::Score)
        results.setlists.mapTo(this, QuickFindEntry::Setlist)
        results.songbooks.mapTo(this, QuickFindEntry::Songbook)
        rankedActions.mapTo(this) { action ->
            QuickFindEntry.Action(action, action.shortcut?.let(keybindings::bindingFor))
        }
    }
}

/** Ctrl+K, or Cmd+K on macOS. */
internal fun KeyEvent.isQuickFindShortcut(): Boolean =
    type == KeyEventType.KeyDown && key == Key.K && (isCtrlPressed || isMetaPressed)

private fun scoreCountLabel(count: Int): String = if (count == 1) "1 score" else "$count scores"
