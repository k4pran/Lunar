package com.ryanjames.lunar.library.data

import com.ryanjames.lunar.library.model.ScoreMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path
import okio.buffer

interface ScoreMetadataStorage {
    suspend fun readMetadata(itemId: String): ScoreMetadata?

    suspend fun writeMetadata(itemId: String, metadata: ScoreMetadata)

    suspend fun deleteMetadata(itemId: String)
}

class InMemoryScoreMetadataStorage(
    initialMetadata: Map<String, ScoreMetadata> = emptyMap(),
) : ScoreMetadataStorage {
    private val metadataByItemId = initialMetadata.toMutableMap()

    override suspend fun readMetadata(itemId: String): ScoreMetadata? = metadataByItemId[itemId]

    override suspend fun writeMetadata(itemId: String, metadata: ScoreMetadata) {
        metadataByItemId[itemId] = metadata
    }

    override suspend fun deleteMetadata(itemId: String) {
        metadataByItemId.remove(itemId)
    }
}

class JsonScoreMetadataStorage(
    private val fileSystem: FileSystem,
    private val metadataDirectory: Path,
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) : ScoreMetadataStorage {
    override suspend fun readMetadata(itemId: String): ScoreMetadata? = withContext(Dispatchers.Default) {
        val path = metadataPathForItemId(itemId)
        if (fileSystem.metadataOrNull(path) == null) {
            return@withContext null
        }

        val payload = fileSystem.source(path).buffer().use { source ->
            source.readUtf8()
        }
        if (payload.isBlank()) {
            return@withContext null
        }

        json.decodeFromString(ScoreMetadata.serializer(), payload)
    }

    override suspend fun writeMetadata(itemId: String, metadata: ScoreMetadata) {
        withContext(Dispatchers.Default) {
            fileSystem.createDirectories(metadataDirectory)
            val payload = json.encodeToString(ScoreMetadata.serializer(), metadata)
            fileSystem.sink(metadataPathForItemId(itemId)).buffer().use { sink ->
                sink.writeUtf8(payload)
            }
        }
    }

    override suspend fun deleteMetadata(itemId: String) {
        withContext(Dispatchers.Default) {
            val path = metadataPathForItemId(itemId)
            if (fileSystem.metadataOrNull(path) != null) {
                fileSystem.delete(path, mustExist = false)
            }
        }
    }

    private fun metadataPathForItemId(itemId: String): Path =
        metadataDirectory / "${itemId.encodeUtf8().hex()}.json"
}
