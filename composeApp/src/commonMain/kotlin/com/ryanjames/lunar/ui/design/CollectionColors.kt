package com.ryanjames.lunar.ui.design

import androidx.compose.ui.graphics.Color
import com.ryanjames.lunar.settings.AppSkin

/** Number of colour slots available to collections (setlists, songbooks, tags). */
const val CollectionColorSlotCount: Int = 8

/**
 * Picks a stable palette slot for a collection name. Case and surrounding whitespace are ignored
 * so renaming "Jazz " to "jazz" keeps its colour.
 */
fun collectionColorSlot(
    name: String,
    slotCount: Int = CollectionColorSlotCount,
): Int {
    require(slotCount > 0) { "slotCount must be positive." }
    // FNV-1a keeps the mapping identical on every platform, unlike String.hashCode on some targets.
    var hash = 0x811C9DC5.toInt()
    name.trim().lowercase().forEach { char ->
        hash = hash xor char.code
        hash *= 0x01000193
    }
    return (hash.toUInt() % slotCount.toUInt()).toInt()
}

private val PaperCollectionPalette = listOf(
    Color(0xFF8C2F22),
    Color(0xFF2F5A7A),
    Color(0xFF4F6B3A),
    Color(0xFF9A6A1F),
    Color(0xFF6B3F6E),
    Color(0xFF2F6E6A),
    Color(0xFF8A4A5C),
    Color(0xFF5C5348),
)

private val MoonlightCollectionPalette = listOf(
    Color(0xFFD2B87F),
    Color(0xFF8FB3D1),
    Color(0xFFA5C08A),
    Color(0xFFE0A86A),
    Color(0xFFC3A0D0),
    Color(0xFF86C7C0),
    Color(0xFFE09AAE),
    Color(0xFFB5ADA0),
)

fun collectionPalette(skin: AppSkin): List<Color> = when (skin) {
    AppSkin.PAPER -> PaperCollectionPalette
    AppSkin.MOONLIGHT -> MoonlightCollectionPalette
}

fun collectionColor(
    skin: AppSkin,
    name: String,
): Color {
    val palette = collectionPalette(skin)
    return palette[collectionColorSlot(name, palette.size)]
}
