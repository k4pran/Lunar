package com.ryanjames.lunar.library.model

enum class DuplicateMatchReason(
    val displayLabel: String,
) {
    TITLE_AND_COMPOSER("same title and composer"),
    TITLE_AND_COLLECTION("same title and collection"),
}

fun composerDuplicateKey(
    title: String,
    composer: String?,
): String? {
    val normalizedTitle = normalizeDuplicateValue(title)
    val normalizedComposer = normalizeDuplicateValue(composer)
    if (normalizedTitle == null || normalizedComposer == null) {
        return null
    }
    return "$normalizedTitle|$normalizedComposer"
}

fun collectionDuplicateKey(
    title: String,
    collection: String?,
): String? {
    val normalizedTitle = normalizeDuplicateValue(title)
    val normalizedCollection = normalizeDuplicateValue(collection)
    if (normalizedTitle == null || normalizedCollection == null) {
        return null
    }
    return "$normalizedTitle|$normalizedCollection"
}

private fun normalizeDuplicateValue(value: String?): String? =
    value
        ?.trim()
        ?.ifEmpty { null }
        ?.lowercase()
