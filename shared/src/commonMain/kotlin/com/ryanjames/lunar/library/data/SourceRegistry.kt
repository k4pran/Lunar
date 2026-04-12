package com.ryanjames.lunar.library.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.buffer
import kotlin.random.Random
import kotlin.time.Clock

@Serializable
sealed interface LibrarySource {
    val id: String
    val label: String
    val addedAtEpochMillis: Long
}

@Serializable
sealed interface CloudLibrarySource : LibrarySource

@Serializable
@SerialName("local_files")
data class LocalFilesSource(
    override val id: String,
    override val label: String,
    override val addedAtEpochMillis: Long,
    val itemCount: Int = 0,
) : LibrarySource

@Serializable
@SerialName("local_folder")
data class LocalFolderSource(
    override val id: String,
    override val label: String,
    override val addedAtEpochMillis: Long,
    val folderPath: String? = null,
    val itemCount: Int = 0,
) : LibrarySource

@Serializable
@SerialName("cloud_supabase")
data class CloudSupabaseSource(
    override val id: String,
    override val label: String,
    override val addedAtEpochMillis: Long,
    val settings: SupabasePublicStorageSettings = SupabasePublicStorageSettings(),
) : CloudLibrarySource

@Serializable
@SerialName("cloud_google_drive")
data class CloudGoogleDriveSource(
    override val id: String,
    override val label: String,
    override val addedAtEpochMillis: Long,
    val settings: GoogleDriveStorageSettings = GoogleDriveStorageSettings(),
) : CloudLibrarySource

@Serializable
data class PersistedSources(
    val sources: List<LibrarySource> = emptyList(),
)

interface SourceRegistry {
    val sources: StateFlow<List<LibrarySource>>

    suspend fun initialize()
    suspend fun addSource(source: LibrarySource)
    suspend fun removeSource(sourceId: String)
    suspend fun updateSource(source: LibrarySource)
    fun getSource(sourceId: String): LibrarySource?
}

class InMemorySourceRegistry(
    initialSources: List<LibrarySource> = emptyList(),
) : SourceRegistry {
    private val _sources = MutableStateFlow(initialSources)
    override val sources: StateFlow<List<LibrarySource>> = _sources.asStateFlow()

    override suspend fun initialize() = Unit

    override suspend fun addSource(source: LibrarySource) {
        _sources.value = _sources.value + source
    }

    override suspend fun removeSource(sourceId: String) {
        _sources.value = _sources.value.filterNot { it.id == sourceId }
    }

    override suspend fun updateSource(source: LibrarySource) {
        _sources.value = _sources.value.map { if (it.id == source.id) source else it }
    }

    override fun getSource(sourceId: String): LibrarySource? =
        _sources.value.firstOrNull { it.id == sourceId }
}

class JsonSourceRegistry(
    private val fileSystem: FileSystem,
    private val sourcesPath: Path,
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    },
) : SourceRegistry {
    private val mutex = Mutex()
    private val _sources = MutableStateFlow<List<LibrarySource>>(emptyList())
    private var initialized = false

    override val sources: StateFlow<List<LibrarySource>> = _sources.asStateFlow()

    override suspend fun initialize() {
        mutex.withLock {
            if (initialized) return
            _sources.value = readFromDisk()
            initialized = true
        }
    }

    override suspend fun addSource(source: LibrarySource) {
        ensureInitialized()
        mutex.withLock {
            val updated = _sources.value + source
            writeToDisk(updated)
            _sources.value = updated
        }
    }

    override suspend fun removeSource(sourceId: String) {
        ensureInitialized()
        mutex.withLock {
            val updated = _sources.value.filterNot { it.id == sourceId }
            writeToDisk(updated)
            _sources.value = updated
        }
    }

    override suspend fun updateSource(source: LibrarySource) {
        ensureInitialized()
        mutex.withLock {
            val updated = _sources.value.map { if (it.id == source.id) source else it }
            writeToDisk(updated)
            _sources.value = updated
        }
    }

    override fun getSource(sourceId: String): LibrarySource? =
        _sources.value.firstOrNull { it.id == sourceId }

    private suspend fun ensureInitialized() {
        if (!initialized) initialize()
    }

    private suspend fun readFromDisk(): List<LibrarySource> = withContext(Dispatchers.Default) {
        if (fileSystem.metadataOrNull(sourcesPath) == null) return@withContext emptyList()
        val payload = fileSystem.source(sourcesPath).buffer().use { it.readUtf8() }
        if (payload.isBlank()) return@withContext emptyList()
        json.decodeFromString(PersistedSources.serializer(), payload).sources
    }

    private suspend fun writeToDisk(sources: List<LibrarySource>) {
        withContext(Dispatchers.Default) {
            sourcesPath.parent?.let(fileSystem::createDirectories)
            val payload = json.encodeToString(PersistedSources.serializer(), PersistedSources(sources))
            fileSystem.sink(sourcesPath).buffer().use { it.writeUtf8(payload) }
        }
    }
}

fun generateSourceId(): String {
    val now = Clock.System.now().toEpochMilliseconds()
    return "src_${now}_${Random.nextInt(1000, 9999)}"
}
