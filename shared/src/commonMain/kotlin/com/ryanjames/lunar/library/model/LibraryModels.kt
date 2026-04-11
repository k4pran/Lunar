package com.ryanjames.lunar.library.model

import kotlinx.serialization.Serializable

@Serializable
data class PdfDocumentReference(
    val storedPath: String,
    val originalFileName: String,
    val sourceUri: String? = null,
    val mimeType: String = "application/pdf",
)

@Serializable
data class SheetMusicItem(
    val id: String,
    val title: String,
    val composer: String? = null,
    val tags: List<String> = emptyList(),
    val collection: String? = null,
    val isFavorite: Boolean = false,
    val document: PdfDocumentReference,
    val dateAddedEpochMillis: Long,
    val lastOpenedEpochMillis: Long? = null,
    val lastViewedPage: Int = 0,
    val pageCount: Int? = null,
)

data class ImportedPdfDescriptor(
    val storedPath: String,
    val originalFileName: String,
    val sourceUri: String? = null,
    val pageCount: Int? = null,
    val suggestedTitle: String = originalFileName.substringBeforeLast('.'),
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
