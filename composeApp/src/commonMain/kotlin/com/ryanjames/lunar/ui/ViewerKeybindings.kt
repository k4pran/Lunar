package com.ryanjames.lunar.ui

import androidx.compose.ui.input.key.Key
import com.ryanjames.lunar.settings.ViewerKeybindings
import com.ryanjames.lunar.settings.ViewerShortcutAction

internal fun ViewerShortcutAction.displayLabel(): String = when (this) {
    ViewerShortcutAction.TOGGLE_FULLSCREEN -> "Toggle fullscreen"
    ViewerShortcutAction.NEXT_PAGE -> "Next page"
    ViewerShortcutAction.PREVIOUS_PAGE -> "Previous page"
    ViewerShortcutAction.TOGGLE_FAVORITE -> "Add to favorites"
    ViewerShortcutAction.ZOOM_IN -> "Zoom in"
    ViewerShortcutAction.ZOOM_OUT -> "Zoom out"
    ViewerShortcutAction.TOGGLE_PAGE_VIEW_MODE -> "Toggle 1-page / 2-page view"
}

internal fun ViewerShortcutAction.supportingText(): String? = when (this) {
    ViewerShortcutAction.TOGGLE_FAVORITE ->
        "Applies to score viewers. Songbooks stay read-only."

    else -> null
}

internal fun ViewerKeybindings.actionForKeyId(keyId: String?): ViewerShortcutAction? =
    keyId?.let { selectedKeyId ->
        ViewerShortcutAction.entries.firstOrNull { action ->
            bindingFor(action) == selectedKeyId
        }
    }

internal fun viewerShortcutKeyId(key: Key): String? =
    SupportedViewerKeys.firstOrNull { entry -> entry.key == key }?.id

internal fun viewerShortcutKeyLabel(keyId: String?): String = when {
    keyId == null -> "Unbound"
    else -> SupportedViewerKeys.firstOrNull { entry -> entry.id == keyId }?.label ?: keyId
}

private data class ViewerSupportedKey(
    val id: String,
    val label: String,
    val key: Key,
)

private val SupportedViewerKeys = buildList {
    add(ViewerSupportedKey("ArrowLeft", "Left Arrow", Key.DirectionLeft))
    add(ViewerSupportedKey("ArrowRight", "Right Arrow", Key.DirectionRight))
    add(ViewerSupportedKey("ArrowUp", "Up Arrow", Key.DirectionUp))
    add(ViewerSupportedKey("ArrowDown", "Down Arrow", Key.DirectionDown))
    add(ViewerSupportedKey("Enter", "Enter", Key.Enter))
    add(ViewerSupportedKey("Escape", "Escape", Key.Escape))
    add(ViewerSupportedKey("Space", "Space", Key.Spacebar))
    add(ViewerSupportedKey("Minus", "Minus", Key.Minus))
    add(ViewerSupportedKey("Equal", "Equals / Plus", Key.Equals))
    add(ViewerSupportedKey("PageUp", "Page Up", Key.PageUp))
    add(ViewerSupportedKey("PageDown", "Page Down", Key.PageDown))
    add(ViewerSupportedKey("Home", "Home", Key.MoveHome))
    add(ViewerSupportedKey("End", "End", Key.MoveEnd))
    add(ViewerSupportedKey("F1", "F1", Key.F1))
    add(ViewerSupportedKey("F2", "F2", Key.F2))
    add(ViewerSupportedKey("F3", "F3", Key.F3))
    add(ViewerSupportedKey("F4", "F4", Key.F4))
    add(ViewerSupportedKey("F5", "F5", Key.F5))
    add(ViewerSupportedKey("F6", "F6", Key.F6))
    add(ViewerSupportedKey("F7", "F7", Key.F7))
    add(ViewerSupportedKey("F8", "F8", Key.F8))
    add(ViewerSupportedKey("F9", "F9", Key.F9))
    add(ViewerSupportedKey("F10", "F10", Key.F10))
    add(ViewerSupportedKey("F11", "F11", Key.F11))
    add(ViewerSupportedKey("F12", "F12", Key.F12))
    add(ViewerSupportedKey("0", "0", Key.Zero))
    add(ViewerSupportedKey("1", "1", Key.One))
    add(ViewerSupportedKey("2", "2", Key.Two))
    add(ViewerSupportedKey("3", "3", Key.Three))
    add(ViewerSupportedKey("4", "4", Key.Four))
    add(ViewerSupportedKey("5", "5", Key.Five))
    add(ViewerSupportedKey("6", "6", Key.Six))
    add(ViewerSupportedKey("7", "7", Key.Seven))
    add(ViewerSupportedKey("8", "8", Key.Eight))
    add(ViewerSupportedKey("9", "9", Key.Nine))
    add(ViewerSupportedKey("A", "A", Key.A))
    add(ViewerSupportedKey("B", "B", Key.B))
    add(ViewerSupportedKey("C", "C", Key.C))
    add(ViewerSupportedKey("D", "D", Key.D))
    add(ViewerSupportedKey("E", "E", Key.E))
    add(ViewerSupportedKey("F", "F", Key.F))
    add(ViewerSupportedKey("G", "G", Key.G))
    add(ViewerSupportedKey("H", "H", Key.H))
    add(ViewerSupportedKey("I", "I", Key.I))
    add(ViewerSupportedKey("J", "J", Key.J))
    add(ViewerSupportedKey("K", "K", Key.K))
    add(ViewerSupportedKey("L", "L", Key.L))
    add(ViewerSupportedKey("M", "M", Key.M))
    add(ViewerSupportedKey("N", "N", Key.N))
    add(ViewerSupportedKey("O", "O", Key.O))
    add(ViewerSupportedKey("P", "P", Key.P))
    add(ViewerSupportedKey("Q", "Q", Key.Q))
    add(ViewerSupportedKey("R", "R", Key.R))
    add(ViewerSupportedKey("S", "S", Key.S))
    add(ViewerSupportedKey("T", "T", Key.T))
    add(ViewerSupportedKey("U", "U", Key.U))
    add(ViewerSupportedKey("V", "V", Key.V))
    add(ViewerSupportedKey("W", "W", Key.W))
    add(ViewerSupportedKey("X", "X", Key.X))
    add(ViewerSupportedKey("Y", "Y", Key.Y))
    add(ViewerSupportedKey("Z", "Z", Key.Z))
}
