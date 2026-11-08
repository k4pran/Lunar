package com.ryanjames.lunar.ui.design

import com.ryanjames.lunar.settings.AppSkin
import com.ryanjames.lunar.ui.AppSection
import com.ryanjames.lunar.ui.LibraryBrowseMode

/**
 * Stable test tags for structural lookups, so UI tests survive wording changes. Prefer these over
 * matching visible text when a test only needs to find a control or a region.
 */
object LunarTestTags {
    const val STARTUP_SCREEN = "startup_screen"
    const val NAVIGATION = "navigation"
    const val LIBRARY_SEARCH = "library_search"
    const val LIBRARY_PANEL = "library_panel"
    const val SETTINGS_PANEL = "settings_panel"
    const val SETTINGS_APPEARANCE_PANEL = "settings_appearance_panel"

    fun navigationItem(section: AppSection): String = "navigation_item_${section.name.lowercase()}"

    fun libraryBrowseTab(mode: LibraryBrowseMode): String = "library_browse_tab_${mode.name.lowercase()}"

    fun libraryRow(itemId: String): String = "library_row_$itemId"

    fun setlistRow(setlistId: String): String = "setlist_row_$setlistId"

    fun songbookRow(songbookId: String): String = "songbook_row_$songbookId"

    fun skinOption(skin: AppSkin): String = "skin_option_${skin.name.lowercase()}"
}
