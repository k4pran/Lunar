package com.ryanjames.lunar.ui.shell

import com.ryanjames.lunar.sync.CloudSyncState
import com.ryanjames.lunar.ui.AppSection
import com.ryanjames.lunar.ui.design.LunarTestTags

internal data class SidebarDestination(
    val section: AppSection,
    val label: String,
    val icon: ShellIcon,
)

/** Fixed top part of the sidebar, in display order. Settings sits apart at the bottom. */
internal val SidebarPrimaryDestinations = listOf(
    SidebarDestination(AppSection.LIBRARY, "Library", ShellIcon.LIBRARY),
    SidebarDestination(AppSection.SETLISTS, "Setlists", ShellIcon.SETLISTS),
    SidebarDestination(AppSection.SONGBOOKS, "Songbooks", ShellIcon.SONGBOOKS),
    SidebarDestination(AppSection.COMPOSE, "Compose", ShellIcon.COMPOSE),
    SidebarDestination(AppSection.IMPORT, "Sources", ShellIcon.SOURCES),
)

internal val SidebarSettingsDestination = SidebarDestination(AppSection.SETTINGS, "Settings", ShellIcon.SETTINGS)

internal val AppSection.sidebarLabel: String
    get() = (SidebarPrimaryDestinations + SidebarSettingsDestination).first { it.section == this }.label

internal fun sidebarNavTag(section: AppSection): String = LunarTestTags.navigationItem(section)

/** Stable palette slot for a collection name so its swatch colour never shifts between runs. */
internal fun collectionSwatchSlot(name: String, slotCount: Int): Int {
    require(slotCount > 0) { "slotCount must be positive." }
    val hash = name.trim().lowercase().fold(7) { acc, char -> acc * 31 + char.code }
    return ((hash % slotCount) + slotCount) % slotCount
}

/** One-line sync status shown at the bottom of the sidebar. */
internal fun sidebarSyncLine(
    syncState: CloudSyncState,
    nowEpochMillis: Long,
): String {
    if (syncState.isRefreshing) return "Syncing..."
    if (syncState.syncErrors.isNotEmpty()) {
        val count = syncState.syncErrors.size
        return "Sync issue${if (count == 1) "" else "s"} ($count)"
    }
    val last = syncState.lastRefreshEpochMillis ?: return "Not synced yet"
    val minutes = ((nowEpochMillis - last).coerceAtLeast(0L)) / 60_000L
    return when {
        minutes < 1 -> "Synced just now"
        minutes < 60 -> "Synced $minutes min ago"
        minutes < 60 * 24 -> "Synced ${minutes / 60} h ago"
        else -> "Synced ${minutes / (60 * 24)} d ago"
    }
}
