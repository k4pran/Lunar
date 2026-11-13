package com.ryanjames.lunar.library.model

import kotlinx.serialization.Serializable

@Serializable
data class PdfDocumentReference(
    val storedPath: String,
    val originalFileName: String,
    val sourceUri: String? = null,
    val contentFingerprint: String? = null,
    val mimeType: String = "application/pdf",
)

@Serializable
data class RemoteSyncMetadata(
    val providerId: String,
    val providerName: String,
    val remoteId: String,
    val remoteVersion: String? = null,
    val lastSyncedEpochMillis: Long? = null,
)

@Serializable
data class SheetMusicItem(
    val id: String,
    val title: String,
    val composer: String? = null,
    val tags: List<String> = emptyList(),
    val collection: String? = null,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val document: PdfDocumentReference,
    val dateAddedEpochMillis: Long,
    val lastOpenedEpochMillis: Long? = null,
    val lastViewedPage: Int = 0,
    val pageCount: Int? = null,
    val syncMetadata: RemoteSyncMetadata? = null,
    val syncTargets: List<RemoteSyncMetadata> = emptyList(),
    val sourceId: String? = null,
)

@Serializable
data class LibrarySetlist(
    val id: String,
    val name: String,
    val itemIds: List<String> = emptyList(),
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class LibrarySongbook(
    val id: String,
    val name: String,
    val itemIds: List<String> = emptyList(),
    val document: PdfDocumentReference,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val pageCount: Int? = null,
    /**
     * True when the PDF starts with a generated cover page before the first score. Null for
     * songbooks saved before this was recorded, which may or may not have a cover.
     */
    val hasCover: Boolean? = null,
    /**
     * The scores in the order their pages are bound, including repeats. [itemIds] is distinct, so
     * it can't describe a PDF that appends the same score twice. Null when the order is unknown,
     * for songbooks saved before it was recorded.
     */
    val boundItemIds: List<String>? = null,
)

data class ImportedPdfDescriptor(
    val storedPath: String,
    val originalFileName: String,
    val sourceUri: String? = null,
    val contentFingerprint: String? = null,
    val pageCount: Int? = null,
    val suggestedTitle: String = originalFileName.substringBeforeLast('.'),
    val scoreMetadata: ScoreMetadata? = null,
)

enum class LibrarySortOption {
    TITLE,
    COMPOSER,
    DATE_ADDED,
    LAST_OPENED,
}

enum class SortDirection {
    ASCENDING,
    DESCENDING,
}
