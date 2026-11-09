package com.ryanjames.lunar.ui

import com.ryanjames.lunar.library.data.CloudGoogleDriveSource
import com.ryanjames.lunar.library.data.CloudLibrarySource
import com.ryanjames.lunar.library.data.CloudSupabaseSource
import com.ryanjames.lunar.library.data.LibrarySource
import com.ryanjames.lunar.library.data.LocalFilesSource
import com.ryanjames.lunar.library.data.LocalFolderSource
import com.ryanjames.lunar.sync.CloudSyncState
import com.ryanjames.lunar.sync.SourceSyncStatus

enum class SourceStatusTone {
    NEUTRAL,
    ACTIVE,
    ERROR,
}

/** The one-line status shown under a source row, such as "Synced 2 minutes ago". */
data class SourceStatusLine(
    val text: String,
    val tone: SourceStatusTone,
)

fun LibrarySource.typeLabel(): String = when (this) {
    is LocalFilesSource -> "Local files"
    is LocalFolderSource -> "Local folder"
    is CloudSupabaseSource -> "Supabase"
    is CloudGoogleDriveSource -> "Google Drive"
}

/** Where the source reads from, for display. Long values are truncated by the row. */
fun LibrarySource.locationLabel(): String = when (this) {
    is LocalFilesSource -> "Individual files on this device"
    is LocalFolderSource -> folderPath?.takeIf(String::isNotBlank) ?: "Folder on this device"
    is CloudSupabaseSource -> listOf(
        settings.projectUrl.trim().trimEnd('/'),
        settings.bucketName.trim(),
        settings.rootDirectory.trim().trim('/'),
    ).filter(String::isNotBlank).joinToString("/").ifBlank { "Not configured" }
    is CloudGoogleDriveSource -> settings.roots
        .map { root -> root.label.ifBlank { root.folderId } }
        .filter(String::isNotBlank)
        .joinToString(", ")
        .ifBlank { "No Drive folders yet" }
}

/**
 * Builds the status line for one source from its own recorded sync outcome, so a failing source
 * reports its error on its own row rather than in a shared list.
 */
fun sourceStatusLine(
    source: LibrarySource,
    syncState: CloudSyncState,
    nowEpochMillis: Long,
): SourceStatusLine {
    if (source !is CloudLibrarySource) {
        return SourceStatusLine(
            text = "On this device, added ${formatRelativeTime(source.addedAtEpochMillis, nowEpochMillis)}",
            tone = SourceStatusTone.NEUTRAL,
        )
    }
    if (syncState.isRefreshing && source.label in syncState.activeSourceLabels) {
        return SourceStatusLine("Syncing...", SourceStatusTone.ACTIVE)
    }
    val status: SourceSyncStatus? = syncState.sourceStatuses[source.id]
    status?.lastError?.let { error ->
        return SourceStatusLine(
            text = if (isReconnectError(error)) "Sign-in expired, reconnect" else error,
            tone = SourceStatusTone.ERROR,
        )
    }
    status?.lastSyncedEpochMillis?.let { syncedAt ->
        return SourceStatusLine(
            text = "Synced ${formatRelativeTime(syncedAt, nowEpochMillis)}",
            tone = SourceStatusTone.NEUTRAL,
        )
    }
    if (source is CloudGoogleDriveSource &&
        source.settings.refreshToken.isBlank() &&
        source.settings.accessToken.isBlank()
    ) {
        return SourceStatusLine("Sign-in required, press Refresh to connect", SourceStatusTone.NEUTRAL)
    }
    return SourceStatusLine("Not synced yet", SourceStatusTone.NEUTRAL)
}

/** True when the error means the saved credentials no longer work and the user must sign in again. */
internal fun isReconnectError(error: String): Boolean {
    val normalized = error.lowercase()
    return "invalid_grant" in normalized ||
        "http 401" in normalized ||
        "requires an access token or refresh token" in normalized
}

/** "just now", "2 minutes ago", "3 hours ago", "5 days ago". Future times read as "just now". */
fun formatRelativeTime(
    thenEpochMillis: Long,
    nowEpochMillis: Long,
): String {
    val elapsedSeconds = ((nowEpochMillis - thenEpochMillis) / 1000L).coerceAtLeast(0L)
    val minutes = elapsedSeconds / 60L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
        minutes < 1L -> "just now"
        hours < 1L -> plural(minutes, "minute")
        days < 1L -> plural(hours, "hour")
        else -> plural(days, "day")
    }
}

private fun plural(count: Long, unit: String): String =
    "$count $unit${if (count == 1L) "" else "s"} ago"
