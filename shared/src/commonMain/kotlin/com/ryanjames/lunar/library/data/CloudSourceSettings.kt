package com.ryanjames.lunar.library.data

import kotlinx.serialization.Serializable

object SyncProviderIds {
    const val SUPABASE_PUBLIC_STORAGE = "supabase_public_storage"
    const val GOOGLE_DRIVE = "google_drive"
}

// How PDFs are laid out beneath a configured import root.
// Lunar uses this to infer title, composer, collection, and tags from the file path.
@Serializable
enum class CloudPathStrategy {
    FLAT,
    COMPOSER,
    COLLECTION,
    COMPOSER_COLLECTION,
    INSTRUMENT,
    DATE_ADDED,
}

@Serializable
data class SupabasePublicStorageSettings(
    val projectUrl: String = "",
    val bucketName: String = "",
    val rootDirectory: String = "",
    val folderStrategy: CloudPathStrategy = CloudPathStrategy.FLAT,
    val anonKey: String = "",
    val uploadEnabled: Boolean = false,
)

@Serializable
data class GoogleDriveImportRoot(
    val id: String,
    val label: String = "",
    val folderId: String = "",
    val folderStrategy: CloudPathStrategy = CloudPathStrategy.FLAT,
)

@Serializable
data class GoogleDriveStorageSettings(
    val apiKey: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
    val refreshToken: String = "",
    val accessToken: String = "",
    val roots: List<GoogleDriveImportRoot> = emptyList(),
    val uploadEnabled: Boolean = false,
    val uploadRoot: GoogleDriveImportRoot? = null,
)
