package com.ryanjames.lunar.ui.design

import com.ryanjames.lunar.settings.AppSkin
import com.ryanjames.lunar.ui.AppSection
import com.ryanjames.lunar.ui.LibraryArrangement

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
    const val SOURCES_PANEL = "sources_panel"
    const val SOURCES_ADD_MENU = "sources_add_menu"
    const val SOURCES_ADD_MENU_LIST = "sources_add_menu_list"
    const val SOURCES_SIDE_PANEL = "sources_side_panel"
    const val COMPOSE_SAVE_TO_LIBRARY = "compose_save_to_library"
    const val QUICK_FIND_PANEL = "quick_find_panel"
    const val QUICK_FIND_INPUT = "quick_find_input"

    fun navigationItem(section: AppSection): String = "navigation_item_${section.name.lowercase()}"

    fun libraryArrangementTab(arrangement: LibraryArrangement): String =
        "library_arrangement_tab_${arrangement.name.lowercase()}"

    fun libraryRow(itemId: String): String = "library_row_$itemId"

    fun setlistRow(setlistId: String): String = "setlist_row_$setlistId"

    fun songbookRow(songbookId: String): String = "songbook_row_$songbookId"

    fun quickFindResult(entryKey: String): String = "quick_find_result_$entryKey"

    fun skinOption(skin: AppSkin): String = "skin_option_${skin.name.lowercase()}"

    fun addSourceOption(option: String): String = "add_source_option_${option.lowercase()}"

    fun sourceRow(sourceId: String): String = "source_row_$sourceId"

    fun sourceStatus(sourceId: String): String = "source_status_$sourceId"

    fun sourceRowActions(sourceId: String): String = "source_row_actions_$sourceId"

    fun settingsTab(tab: String): String = "settings_tab_${tab.lowercase()}"

    fun settingsRow(key: String): String = "settings_row_$key"
}
