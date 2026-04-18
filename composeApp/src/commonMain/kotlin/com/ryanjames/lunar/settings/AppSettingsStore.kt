package com.ryanjames.lunar.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.buffer

interface AppSettingsStore {
    val settings: StateFlow<AppSettings>

    suspend fun initialize()
    suspend fun updateSettings(transform: (AppSettings) -> AppSettings)
    suspend fun reset()
}

class InMemoryAppSettingsStore(
    initialSettings: AppSettings = AppSettings(),
) : AppSettingsStore {
    private val state = MutableStateFlow(initialSettings)

    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    override suspend fun initialize() = Unit

    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }

    override suspend fun reset() {
        state.value = AppSettings()
    }
}

class JsonAppSettingsStore(
    private val fileSystem: FileSystem,
    private val settingsPath: Path,
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) : AppSettingsStore {
    private val mutex = Mutex()
    private val state = MutableStateFlow(AppSettings())
    private var initialized = false

    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    override suspend fun initialize() {
        mutex.withLock {
            if (initialized) return
            state.value = readFromDisk().normalize()
            initialized = true
        }
    }

    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        ensureInitialized()
        mutex.withLock {
            val updated = transform(state.value).normalize()
            writeToDisk(updated)
            state.value = updated
        }
    }

    override suspend fun reset() {
        ensureInitialized()
        mutex.withLock {
            val defaults = AppSettings()
            writeToDisk(defaults)
            state.value = defaults
        }
    }

    private suspend fun ensureInitialized() {
        if (!initialized) initialize()
    }

    private suspend fun readFromDisk(): AppSettings = withContext(Dispatchers.Default) {
        if (fileSystem.metadataOrNull(settingsPath) == null) {
            return@withContext AppSettings()
        }

        val payload = fileSystem.source(settingsPath).buffer().use { it.readUtf8() }
        if (payload.isBlank()) {
            return@withContext AppSettings()
        }

        json.decodeFromString(AppSettings.serializer(), payload)
    }

    private suspend fun writeToDisk(settings: AppSettings) {
        withContext(Dispatchers.Default) {
            settingsPath.parent?.let(fileSystem::createDirectories)
            val payload = json.encodeToString(AppSettings.serializer(), settings)
            fileSystem.sink(settingsPath).buffer().use { it.writeUtf8(payload) }
        }
    }
}
