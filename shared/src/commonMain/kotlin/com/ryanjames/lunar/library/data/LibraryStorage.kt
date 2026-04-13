package com.ryanjames.lunar.library.data

import com.ryanjames.lunar.library.model.LibrarySetlist
import com.ryanjames.lunar.library.model.SheetMusicItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.buffer

data class StoredLibraryData(
    val items: List<SheetMusicItem> = emptyList(),
    val setlists: List<LibrarySetlist> = emptyList(),
)

interface LibraryStorage {
    suspend fun readLibraryData(): StoredLibraryData

    suspend fun writeLibraryData(data: StoredLibraryData)

    suspend fun readItems(): List<SheetMusicItem> = readLibraryData().items

    suspend fun writeItems(items: List<SheetMusicItem>) {
        val current = readLibraryData()
        writeLibraryData(current.copy(items = items))
    }
}

class InMemoryLibraryStorage(
    initialItems: List<SheetMusicItem> = emptyList(),
    initialSetlists: List<LibrarySetlist> = emptyList(),
) : LibraryStorage {
    private var data: StoredLibraryData = StoredLibraryData(
        items = initialItems,
        setlists = initialSetlists,
    )

    override suspend fun readLibraryData(): StoredLibraryData = data

    override suspend fun writeLibraryData(data: StoredLibraryData) {
        this.data = data
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
    override suspend fun readLibraryData(): StoredLibraryData = withContext(Dispatchers.Default) {
        if (fileSystem.metadataOrNull(metadataPath) == null) {
            return@withContext StoredLibraryData()
        }

        val payload = fileSystem.source(metadataPath).buffer().use { source ->
            source.readUtf8()
        }
        if (payload.isBlank()) {
            return@withContext StoredLibraryData()
        }

        json.decodeFromString(PersistedLibrary.serializer(), payload).toStoredLibraryData()
    }

    override suspend fun writeLibraryData(data: StoredLibraryData) {
        withContext(Dispatchers.Default) {
            metadataPath.parent?.let(fileSystem::createDirectories)
            val payload = json.encodeToString(
                PersistedLibrary.serializer(),
                PersistedLibrary(
                    items = data.items,
                    setlists = data.setlists,
                ),
            )
            fileSystem.sink(metadataPath).buffer().use { sink ->
                sink.writeUtf8(payload)
            }
        }
    }
}

@Serializable
private data class PersistedLibrary(
    val items: List<SheetMusicItem> = emptyList(),
    val setlists: List<LibrarySetlist> = emptyList(),
)

private fun PersistedLibrary.toStoredLibraryData(): StoredLibraryData = StoredLibraryData(
    items = items,
    setlists = setlists,
)
