package com.ryanjames.lunar.library.data

import com.ryanjames.lunar.library.model.SheetMusicItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.buffer

interface LibraryStorage {
    suspend fun readItems(): List<SheetMusicItem>
    suspend fun writeItems(items: List<SheetMusicItem>)
}

class InMemoryLibraryStorage(
    initialItems: List<SheetMusicItem> = emptyList(),
) : LibraryStorage {
    private var items: List<SheetMusicItem> = initialItems

    override suspend fun readItems(): List<SheetMusicItem> = items

    override suspend fun writeItems(items: List<SheetMusicItem>) {
        this.items = items
    }
}

class JsonLibraryStorage(
    private val fileSystem: FileSystem,
    private val metadataPath: Path,
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) : LibraryStorage {
    override suspend fun readItems(): List<SheetMusicItem> = withContext(Dispatchers.Default) {
        if (fileSystem.metadataOrNull(metadataPath) == null) {
            return@withContext emptyList()
        }

        val payload = fileSystem.source(metadataPath).buffer().use { source ->
            source.readUtf8()
        }
        if (payload.isBlank()) {
            return@withContext emptyList()
        }

        json.decodeFromString(PersistedLibrary.serializer(), payload).items
    }

    override suspend fun writeItems(items: List<SheetMusicItem>) {
        withContext(Dispatchers.Default) {
            metadataPath.parent?.let(fileSystem::createDirectories)
            val payload = json.encodeToString(PersistedLibrary.serializer(), PersistedLibrary(items))
            fileSystem.sink(metadataPath).buffer().use { sink ->
                sink.writeUtf8(payload)
            }
        }
    }
}

@Serializable
private data class PersistedLibrary(
    val items: List<SheetMusicItem> = emptyList(),
)
