package com.ryanjames.lunar.composition

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.buffer
import kotlin.random.Random
import kotlin.time.Clock

@Serializable
enum class CompositionNotation {
    LILYPOND,
}

@Serializable
data class CompositionDraft(
    val id: String,
    val title: String,
    val composer: String,
    val notation: CompositionNotation = CompositionNotation.LILYPOND,
    val sourceText: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val importedItemId: String? = null,
)

interface CompositionDraftStore {
    val drafts: StateFlow<List<CompositionDraft>>

    suspend fun initialize()
    suspend fun upsertDraft(draft: CompositionDraft)
    suspend fun deleteDraft(draftId: String)
    fun getDraft(draftId: String): CompositionDraft?
}

class InMemoryCompositionDraftStore(
    initialDrafts: List<CompositionDraft> = emptyList(),
) : CompositionDraftStore {
    private val _drafts = MutableStateFlow(initialDrafts)

    override val drafts: StateFlow<List<CompositionDraft>> = _drafts.asStateFlow()

    override suspend fun initialize() = Unit

    override suspend fun upsertDraft(draft: CompositionDraft) {
        _drafts.value = upsertDraftInList(_drafts.value, draft)
    }

    override suspend fun deleteDraft(draftId: String) {
        _drafts.value = _drafts.value.filterNot { it.id == draftId }
    }

    override fun getDraft(draftId: String): CompositionDraft? =
        _drafts.value.firstOrNull { it.id == draftId }
}

class JsonCompositionDraftStore(
    private val fileSystem: FileSystem,
    private val draftsPath: Path,
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) : CompositionDraftStore {
    private val mutex = Mutex()
    private val _drafts = MutableStateFlow<List<CompositionDraft>>(emptyList())
    private var initialized = false

    override val drafts: StateFlow<List<CompositionDraft>> = _drafts.asStateFlow()

    override suspend fun initialize() {
        mutex.withLock {
            if (initialized) return
            _drafts.value = readFromDisk()
            initialized = true
        }
    }

    override suspend fun upsertDraft(draft: CompositionDraft) {
        ensureInitialized()
        mutex.withLock {
            val updated = upsertDraftInList(_drafts.value, draft)
            writeToDisk(updated)
            _drafts.value = updated
        }
    }

    override suspend fun deleteDraft(draftId: String) {
        ensureInitialized()
        mutex.withLock {
            val updated = _drafts.value.filterNot { it.id == draftId }
            writeToDisk(updated)
            _drafts.value = updated
        }
    }

    override fun getDraft(draftId: String): CompositionDraft? =
        _drafts.value.firstOrNull { it.id == draftId }

    private suspend fun ensureInitialized() {
        if (!initialized) initialize()
    }

    private suspend fun readFromDisk(): List<CompositionDraft> = withContext(Dispatchers.Default) {
        if (fileSystem.metadataOrNull(draftsPath) == null) return@withContext emptyList()
        val payload = fileSystem.source(draftsPath).buffer().use { source -> source.readUtf8() }
        if (payload.isBlank()) return@withContext emptyList()
        json.decodeFromString(PersistedCompositionDrafts.serializer(), payload).drafts
    }

    private suspend fun writeToDisk(drafts: List<CompositionDraft>) {
        withContext(Dispatchers.Default) {
            draftsPath.parent?.let(fileSystem::createDirectories)
            val payload = json.encodeToString(
                PersistedCompositionDrafts.serializer(),
                PersistedCompositionDrafts(drafts = drafts),
            )
            fileSystem.sink(draftsPath).buffer().use { sink -> sink.writeUtf8(payload) }
        }
    }
}

fun createCompositionDraft(
    title: String,
    composer: String,
    sourceText: String,
    notation: CompositionNotation = CompositionNotation.LILYPOND,
): CompositionDraft {
    val now = Clock.System.now().toEpochMilliseconds()
    return CompositionDraft(
        id = generateCompositionDraftId(now),
        title = title,
        composer = composer,
        notation = notation,
        sourceText = sourceText,
        createdAtEpochMillis = now,
        updatedAtEpochMillis = now,
    )
}

fun CompositionDraft.withUpdatedContents(
    title: String,
    composer: String,
    sourceText: String,
    notation: CompositionNotation = this.notation,
    importedItemId: String? = this.importedItemId,
): CompositionDraft = copy(
    title = title,
    composer = composer,
    notation = notation,
    sourceText = sourceText,
    importedItemId = importedItemId,
    updatedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
)

private fun upsertDraftInList(
    drafts: List<CompositionDraft>,
    draft: CompositionDraft,
): List<CompositionDraft> {
    val existingIndex = drafts.indexOfFirst { it.id == draft.id }
    return if (existingIndex >= 0) {
        drafts.toMutableList().also { it[existingIndex] = draft }
    } else {
        drafts + draft
    }
}

private fun generateCompositionDraftId(now: Long = Clock.System.now().toEpochMilliseconds()): String =
    "composition_${now}_${Random.nextInt(1000, 9999)}"

@Serializable
private data class PersistedCompositionDrafts(
    val drafts: List<CompositionDraft> = emptyList(),
)
