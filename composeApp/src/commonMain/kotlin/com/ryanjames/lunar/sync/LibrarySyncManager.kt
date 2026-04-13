package com.ryanjames.lunar.sync

import com.ryanjames.lunar.library.data.CloudGoogleDriveSource
import com.ryanjames.lunar.library.data.CloudLibrarySource
import com.ryanjames.lunar.library.data.CloudPathStrategy
import com.ryanjames.lunar.library.data.CloudSupabaseSource
import com.ryanjames.lunar.library.data.GoogleDriveImportRoot
import com.ryanjames.lunar.library.data.GoogleDriveStorageSettings
import com.ryanjames.lunar.library.data.LibrarySource
import com.ryanjames.lunar.library.data.SheetMusicRepository
import com.ryanjames.lunar.library.data.SupabasePublicStorageSettings
import com.ryanjames.lunar.library.data.SyncProviderIds
import com.ryanjames.lunar.library.data.SyncStatus
import com.ryanjames.lunar.library.data.SyncedSheetMusicDescriptor
import com.ryanjames.lunar.platform.PdfPageRenderer
import com.ryanjames.lunar.library.model.DuplicateMatchReason
import com.ryanjames.lunar.library.model.collectionDuplicateKey
import com.ryanjames.lunar.library.model.composerDuplicateKey
import com.ryanjames.lunar.library.model.SheetMusicItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock

data class CloudLibraryProviderOption(
    val id: String,
    val displayName: String,
    val description: String,
)

data class CloudSyncState(
    val availableProviders: List<CloudLibraryProviderOption> = supportedCloudProviders(),
    val isRefreshing: Boolean = false,
    val lastMessage: String? = null,
    val currentStep: String? = null,
    val lastRefreshEpochMillis: Long? = null,
    val syncErrors: List<String> = emptyList(),
    val activeSourceLabels: List<String> = emptyList(),
    val discoveredResources: Int = 0,
    val processedResources: Int = 0,
    val skippedResources: Int = 0,
    val visitedFolders: Int = 0,
    val activityLog: List<String> = emptyList(),
)

interface SyncHttpClient {
    suspend fun getText(url: String, headers: Map<String, String> = emptyMap()): String

    suspend fun getBytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray

    suspend fun postJson(
        url: String,
        jsonBody: String,
        headers: Map<String, String> = emptyMap(),
    ): String = throw UnsupportedOperationException("postJson is not implemented for this sync client.")

    suspend fun postForm(
        url: String,
        formBody: String,
        headers: Map<String, String> = emptyMap(),
    ): String = throw UnsupportedOperationException("postForm is not implemented for this sync client.")
}

interface ManagedPdfStore {
    suspend fun savePdf(originalFileName: String, contents: ByteArray): String

    suspend fun exists(storedPath: String): Boolean
}

object NoOpManagedPdfStore : ManagedPdfStore {
    override suspend fun savePdf(originalFileName: String, contents: ByteArray): String =
        throw UnsupportedOperationException("Managed PDF storage is unavailable on this target.")

    override suspend fun exists(storedPath: String): Boolean = false
}

class FailingSyncHttpClient(private val message: String) : SyncHttpClient {
    override suspend fun getText(url: String, headers: Map<String, String>): String =
        throw UnsupportedOperationException(message)

    override suspend fun getBytes(url: String, headers: Map<String, String>): ByteArray =
        throw UnsupportedOperationException(message)

    override suspend fun postJson(url: String, jsonBody: String, headers: Map<String, String>): String =
        throw UnsupportedOperationException(message)

    override suspend fun postForm(url: String, formBody: String, headers: Map<String, String>): String =
        throw UnsupportedOperationException(message)
}

@Serializable
private data class SupabaseStorageObject(
    val name: String,
    val id: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
private data class GoogleDriveListResponse(
    @SerialName("nextPageToken") val nextPageToken: String? = null,
    val files: List<GoogleDriveFile> = emptyList(),
)

@Serializable
private data class GoogleDriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val modifiedTime: String? = null,
    val webViewLink: String? = null,
)

private data class CachedAccessToken(
    val token: String,
    val expiresAtEpochMillis: Long,
)

private data class SourceRefreshOutcome(
    val sourceLabel: String,
    val syncedCount: Int,
    val errors: List<String> = emptyList(),
)

private data class GoogleDriveScanOutcome(
    val descriptors: List<SyncedSheetMusicDescriptor>,
    val errors: List<String> = emptyList(),
)

private data class Metadata(
    val title: String,
    val composer: String?,
    val collection: String?,
    val tags: List<String>,
)

private data class GoogleDriveFolderNode(
    val folderId: String,
    val folderSegments: List<String>,
)

class LibrarySyncManager(
    private val repository: SheetMusicRepository,
    private val httpClient: SyncHttpClient,
    private val pdfStore: ManagedPdfStore,
    private val renderer: PdfPageRenderer,
    private val clock: Clock = Clock.System,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val stateFlow = MutableStateFlow(CloudSyncState())
    private val googleAccessTokens = mutableMapOf<String, CachedAccessToken>()
    private var initialized = false

    val state: StateFlow<CloudSyncState> = stateFlow.asStateFlow()

    suspend fun initialize() {
        if (initialized) return
        initialized = true
    }

    fun automaticRefreshIntervalMillis(): Long = DEFAULT_AUTO_REFRESH_INTERVAL_MILLIS

    fun primeGoogleAccessToken(
        sourceId: String,
        accessToken: String,
        expiresAtEpochMillis: Long?,
    ) {
        if (accessToken.isBlank() || expiresAtEpochMillis == null) return
        googleAccessTokens[sourceId] = CachedAccessToken(
            token = accessToken,
            expiresAtEpochMillis = expiresAtEpochMillis,
        )
    }

    suspend fun refresh(force: Boolean) {
        refreshAllSources(emptyList(), force = force)
    }

    suspend fun refreshSource(source: CloudLibrarySource) {
        ensureInitialized()
        if (stateFlow.value.isRefreshing) return

        stateFlow.value = stateFlow.value.copy(
            isRefreshing = true,
            lastMessage = "Refreshing ${source.label}...",
            currentStep = "Preparing ${source.label}",
            syncErrors = emptyList(),
            activeSourceLabels = listOf(source.label),
            discoveredResources = 0,
            processedResources = 0,
            skippedResources = 0,
            visitedFolders = 0,
            activityLog = listOf("Starting refresh for ${source.label}."),
        )
        repository.updateSyncStatus(SyncStatus.Syncing(source.label))

        val refreshedAt = clock.now().toEpochMilliseconds()
        try {
            appendActivity(
                message = "Refreshing source ${source.label}.",
                currentStep = "Refreshing ${source.label}",
            )
            val outcome = refreshSourceInternal(source, refreshedAt)
            finishRefresh(
                refreshedAt = refreshedAt,
                sourceLabels = listOf(source.label),
                outcomes = listOf(outcome),
            )
        } catch (error: Throwable) {
            val message = "${source.label}: ${error.describeSyncFailure()}"
            repository.updateSyncStatus(SyncStatus.Error(source.label, message, refreshedAt))
            stateFlow.value = stateFlow.value.copy(
                isRefreshing = false,
                lastMessage = message,
                currentStep = "Refresh failed",
                syncErrors = listOf(message),
                activeSourceLabels = listOf(source.label),
                activityLog = (stateFlow.value.activityLog + message).takeLast(MAX_ACTIVITY_LOG_ENTRIES),
            )
        }
    }

    suspend fun refreshAllSources(
        sources: List<LibrarySource>,
        force: Boolean = false,
    ) {
        ensureInitialized()
        if (stateFlow.value.isRefreshing) return

        val cloudSources = sources.filterIsInstance<CloudLibrarySource>()
        if (cloudSources.isEmpty()) {
            repository.updateSyncStatus(SyncStatus.LocalOnly)
            if (force) {
                stateFlow.value = stateFlow.value.copy(
                    lastMessage = "No cloud sources configured yet.",
                    syncErrors = emptyList(),
                    activeSourceLabels = emptyList(),
                )
            }
            return
        }

        val sourceLabels = cloudSources.map { it.label }
        stateFlow.value = stateFlow.value.copy(
            isRefreshing = true,
            lastMessage = "Refreshing ${cloudSources.size} cloud source${if (cloudSources.size == 1) "" else "s"}...",
            currentStep = "Preparing cloud refresh",
            syncErrors = emptyList(),
            activeSourceLabels = sourceLabels,
            discoveredResources = 0,
            processedResources = 0,
            skippedResources = 0,
            visitedFolders = 0,
            activityLog = listOf(
                "Starting refresh for ${cloudSources.size} cloud source${if (cloudSources.size == 1) "" else "s"}."
            ),
        )
        repository.updateSyncStatus(SyncStatus.Syncing("Cloud sources"))

        val refreshedAt = clock.now().toEpochMilliseconds()
        val outcomes = mutableListOf<SourceRefreshOutcome>()
        val topLevelErrors = mutableListOf<String>()

        cloudSources.forEach { source ->
            try {
                appendActivity(
                    message = "Refreshing source ${source.label}.",
                    currentStep = "Refreshing ${source.label}",
                )
                outcomes += refreshSourceInternal(source, refreshedAt)
            } catch (error: Throwable) {
                val message = "${source.label}: ${error.describeSyncFailure()}"
                topLevelErrors += message
                appendActivity(
                    message = message,
                    currentStep = "Refresh failed",
                )
            }
        }

        finishRefresh(
            refreshedAt = refreshedAt,
            sourceLabels = sourceLabels,
            outcomes = outcomes,
            topLevelErrors = topLevelErrors,
        )
    }

    private suspend fun finishRefresh(
        refreshedAt: Long,
        sourceLabels: List<String>,
        outcomes: List<SourceRefreshOutcome>,
        topLevelErrors: List<String> = emptyList(),
    ) {
        val syncErrors = outcomes.flatMap { it.errors } + topLevelErrors
        val syncedCount = outcomes.sumOf { it.syncedCount }
        val summary = buildSummary(outcomes, syncedCount, syncErrors)

        val status = when {
            sourceLabels.isEmpty() -> SyncStatus.LocalOnly
            syncErrors.isEmpty() -> SyncStatus.Ready("Cloud sources", refreshedAt)
            else -> SyncStatus.Error("Cloud sources", syncErrors.first(), refreshedAt)
        }
        repository.updateSyncStatus(status)

        stateFlow.value = stateFlow.value.copy(
            isRefreshing = false,
            lastMessage = summary,
            currentStep = if (syncErrors.isEmpty()) "Refresh complete" else "Refresh completed with issues",
            syncErrors = syncErrors,
            lastRefreshEpochMillis = refreshedAt,
            activeSourceLabels = sourceLabels,
            activityLog = (stateFlow.value.activityLog + summary).takeLast(MAX_ACTIVITY_LOG_ENTRIES),
        )
    }

    private suspend fun refreshSourceInternal(
        source: CloudLibrarySource,
        syncedAt: Long,
    ): SourceRefreshOutcome = when (source) {
        is CloudSupabaseSource -> refreshSupabaseSource(source, syncedAt)
        is CloudGoogleDriveSource -> refreshGoogleDriveSource(source, syncedAt)
    }

    private suspend fun refreshSupabaseSource(
        source: CloudSupabaseSource,
        syncedAt: Long,
    ): SourceRefreshOutcome {
        val config = source.settings.normalized()
        val existingItems = repository.library.value.items
        val composerIndex = existingItems.groupByComposerDuplicateKey()
        val collectionIndex = existingItems.groupByCollectionDuplicateKey()
        val acceptedComposerKeys = mutableSetOf<String>()
        val acceptedCollectionKeys = mutableSetOf<String>()
        appendActivity(
            message = "Scanning Supabase bucket ${config.bucketName.ifBlank { "(unknown bucket)" }} for ${source.label}.",
            currentStep = "Scanning ${source.label}",
        )
        if (!config.isComplete()) {
            return SourceRefreshOutcome(
                sourceLabel = source.label,
                syncedCount = 0,
                errors = listOf("${source.label}: configure the project URL and bucket name."),
            )
        }

        val allObjects = listSupabaseBucketObjects(config)
        val pdfObjects = allObjects.filter { it.name.endsWith(".pdf", ignoreCase = true) }
        appendActivity(
            message = "${source.label}: found ${pdfObjects.size} PDF object${if (pdfObjects.size == 1) "" else "s"} in storage.",
            currentStep = "Preparing PDFs from ${source.label}",
            discoveredResourcesDelta = pdfObjects.size,
        )
        val scoreErrors = mutableListOf<String>()
        val descriptors = buildList {
            pdfObjects.forEach { obj ->
                try {
                    val relativePath = if (config.rootDirectory.isBlank()) {
                        obj.name
                    } else {
                        obj.name.removePrefix(config.rootDirectory).trimStart('/')
                    }
                    val relativeSegments = relativePath.split('/').filter(String::isNotEmpty)
                    val folderSegments = relativeSegments.dropLast(1)
                    val fileName = obj.name.substringAfterLast('/')
                    val fileNameNoExt = fileName.substringBeforeLast('.')
                    val metadata = resolveMetadata(
                        fileNameNoExt = fileNameNoExt,
                        folderSegments = folderSegments,
                        strategy = config.folderStrategy,
                    )
                    val duplicateReason = findDuplicateReason(
                        title = metadata.title,
                        composer = metadata.composer,
                        collection = metadata.collection,
                        composerIndex = composerIndex,
                        collectionIndex = collectionIndex,
                        acceptedComposerKeys = acceptedComposerKeys,
                        acceptedCollectionKeys = acceptedCollectionKeys,
                        ignoreExisting = { existing ->
                            existing.syncMetadata?.providerId == source.id &&
                                existing.syncMetadata?.remoteId == (obj.id?.takeIf(String::isNotBlank) ?: obj.name)
                        },
                    )
                    if (duplicateReason != null) {
                        appendActivity(
                            message = "${source.label}: skipping duplicate ${fileName} because ${duplicateReason.displayLabel} already exists in the library.",
                            currentStep = "Preparing PDFs from ${source.label}",
                            skippedResourcesDelta = 1,
                        )
                        return@forEach
                    }
                    appendActivity(
                        message = "${source.label}: syncing ${obj.name}.",
                        currentStep = "Downloading PDFs from ${source.label}",
                    )
                    val descriptor = resolveSupabaseDescriptor(
                        source = source,
                        config = config,
                        objectPath = obj.name,
                        remoteId = obj.id?.takeIf(String::isNotBlank) ?: obj.name,
                        remoteVersion = obj.updatedAt ?: obj.name,
                        syncedAt = syncedAt,
                    )
                    add(descriptor)
                    composerDuplicateKey(descriptor.title, descriptor.composer)?.let(acceptedComposerKeys::add)
                    collectionDuplicateKey(descriptor.title, descriptor.collection)?.let(acceptedCollectionKeys::add)
                    appendActivity(
                        message = "${source.label}: ready ${obj.name}.",
                        currentStep = "Downloading PDFs from ${source.label}",
                        processedResourcesDelta = 1,
                    )
                } catch (error: Throwable) {
                    val message = "${source.label}: ${obj.name} - ${error.message ?: "unknown error"}"
                    scoreErrors += message
                    appendActivity(
                        message = message,
                        currentStep = "Downloading PDFs from ${source.label}",
                    )
                }
            }
        }

        repository.applyRemoteSync(
            providerId = source.id,
            providerName = source.label,
            items = descriptors,
            syncedAtEpochMillis = syncedAt,
            sourceId = source.id,
        )

        return SourceRefreshOutcome(
            sourceLabel = source.label,
            syncedCount = descriptors.size,
            errors = scoreErrors,
        )
    }

    private suspend fun refreshGoogleDriveSource(
        source: CloudGoogleDriveSource,
        syncedAt: Long,
    ): SourceRefreshOutcome {
        val config = source.settings.normalized()
        val existingItems = repository.library.value.items
        val composerIndex = existingItems.groupByComposerDuplicateKey()
        val collectionIndex = existingItems.groupByCollectionDuplicateKey()
        val acceptedComposerKeys = mutableSetOf<String>()
        val acceptedCollectionKeys = mutableSetOf<String>()
        appendActivity(
            message = "${source.label}: preparing Google Drive sync for ${config.roots.size} root${if (config.roots.size == 1) "" else "s"}.",
            currentStep = "Preparing ${source.label}",
        )
        if (!config.isComplete()) {
            return SourceRefreshOutcome(
                sourceLabel = source.label,
                syncedCount = 0,
                errors = listOf(
                    "${source.label}: add at least one root and complete Google Drive sign-in.",
                ),
            )
        }

        val accessToken = resolveGoogleAccessToken(source, config)
        val headers = buildMap {
            put("Authorization", "Bearer $accessToken")
            put("Accept", "application/json")
        }

        val descriptorsById = linkedMapOf<String, SyncedSheetMusicDescriptor>()
        val scoreErrors = mutableListOf<String>()
        val visitedRootIds = mutableSetOf<String>()

        config.roots.forEach { root ->
            if (root.folderId.isBlank()) {
                val message = "${source.label}: one Google Drive root is missing its folder ID."
                scoreErrors += message
                appendActivity(
                    message = message,
                    currentStep = "Scanning ${source.label}",
                )
                return@forEach
            }

            if (!visitedRootIds.add(root.folderId)) {
                appendActivity(
                    message = "${source.label}: skipping duplicate root ${root.displayLabel()}.",
                    currentStep = "Scanning ${source.label}",
                )
                return@forEach
            }

            try {
                appendActivity(
                    message = "${source.label}: scanning root ${root.displayLabel()}.",
                    currentStep = "Scanning ${source.label}",
                )
                val descriptors = listGoogleDriveDescriptors(
                    source = source,
                    config = config,
                    root = root,
                    headers = headers,
                    syncedAt = syncedAt,
                    composerIndex = composerIndex,
                    collectionIndex = collectionIndex,
                    acceptedComposerKeys = acceptedComposerKeys,
                    acceptedCollectionKeys = acceptedCollectionKeys,
                )
                descriptors.descriptors.forEach { descriptor ->
                    descriptorsById.putIfAbsent(descriptor.remoteId, descriptor)
                }
                scoreErrors += descriptors.errors
                appendActivity(
                    message = "${source.label}: root ${root.displayLabel()} produced ${descriptors.descriptors.size} PDF${if (descriptors.descriptors.size == 1) "" else "s"}.",
                    currentStep = "Scanning ${source.label}",
                )
            } catch (error: Throwable) {
                val rootLabel = root.displayLabel()
                val message = "${source.label}: $rootLabel - ${error.describeSyncFailure()}"
                scoreErrors += message
                appendActivity(
                    message = message,
                    currentStep = "Scanning ${source.label}",
                )
            }
        }

        repository.applyRemoteSync(
            providerId = source.id,
            providerName = source.label,
            items = descriptorsById.values.toList(),
            syncedAtEpochMillis = syncedAt,
            sourceId = source.id,
        )

        return SourceRefreshOutcome(
            sourceLabel = source.label,
            syncedCount = descriptorsById.size,
            errors = scoreErrors,
        )
    }

    private suspend fun listSupabaseBucketObjects(
        config: SupabasePublicStorageSettings,
    ): List<SupabaseStorageObject> {
        val results = mutableListOf<SupabaseStorageObject>()
        val prefixQueue = ArrayDeque<String>()
        prefixQueue.add(config.rootDirectory)

        while (prefixQueue.isNotEmpty()) {
            val prefix = prefixQueue.removeFirst()
            val pageObjects = fetchSupabaseObjectPage(config, prefix)
            pageObjects.forEach { obj ->
                if (obj.name == ".emptyFolderPlaceholder" || obj.name.startsWith(".")) {
                    return@forEach
                }

                val fullPath = if (prefix.isEmpty()) obj.name else "$prefix/${obj.name}"
                if (obj.id == null) {
                    prefixQueue.add(fullPath)
                } else {
                    results += obj.copy(name = fullPath)
                }
            }
        }

        return results
    }

    private suspend fun fetchSupabaseObjectPage(
        config: SupabasePublicStorageSettings,
        prefix: String,
    ): List<SupabaseStorageObject> {
        val url = "${config.projectUrl.trimEnd('/')}/storage/v1/object/list/${config.bucketName.trim()}"
        val body = """{"prefix":"$prefix","limit":1000,"offset":0,"sortBy":{"column":"name","order":"asc"}}"""
        val headers = buildMap {
            put("Content-Type", "application/json")
            put("Accept", "application/json")
            if (config.anonKey.isNotBlank()) {
                put("apikey", config.anonKey.trim())
                put("Authorization", "Bearer ${config.anonKey.trim()}")
            }
        }
        val responseText = httpClient.postJson(url, body, headers)
        return json.decodeFromString(ListSerializer(SupabaseStorageObject.serializer()), responseText)
    }

    private suspend fun resolveSupabaseDescriptor(
        source: CloudSupabaseSource,
        config: SupabasePublicStorageSettings,
        objectPath: String,
        remoteId: String,
        remoteVersion: String,
        syncedAt: Long,
    ): SyncedSheetMusicDescriptor {
        val fileName = objectPath.substringAfterLast('/')
        val fileNameNoExt = fileName.substringBeforeLast('.')
        val relativePath = if (config.rootDirectory.isBlank()) {
            objectPath
        } else {
            objectPath.removePrefix(config.rootDirectory).trimStart('/')
        }
        val relativeSegments = relativePath.split('/').filter(String::isNotEmpty)
        val folderSegments = relativeSegments.dropLast(1)
        val metadata = resolveMetadata(
            fileNameNoExt = fileNameNoExt,
            folderSegments = folderSegments,
            strategy = config.folderStrategy,
        )
        val downloadUrl = buildSupabasePublicUrl(
            projectUrl = config.projectUrl,
            bucketName = config.bucketName,
            objectPath = objectPath,
        )

        return resolveManagedDescriptor(
            providerId = source.id,
            remoteId = remoteId,
            remoteVersion = remoteVersion,
            originalFileName = fileName,
            sourceUri = downloadUrl,
            metadata = metadata,
            dateAddedEpochMillis = if (config.folderStrategy == CloudPathStrategy.DATE_ADDED) {
                parseDateFolder(folderSegments.lastOrNull())
            } else {
                null
            },
            syncedAt = syncedAt,
        ) {
            httpClient.getBytes(downloadUrl)
        }
    }

    private suspend fun listGoogleDriveDescriptors(
        source: CloudGoogleDriveSource,
        config: GoogleDriveStorageSettings,
        root: GoogleDriveImportRoot,
        headers: Map<String, String>,
        syncedAt: Long,
        composerIndex: Map<String, List<SheetMusicItem>>,
        collectionIndex: Map<String, List<SheetMusicItem>>,
        acceptedComposerKeys: MutableSet<String>,
        acceptedCollectionKeys: MutableSet<String>,
    ): GoogleDriveScanOutcome {
        val descriptors = mutableListOf<SyncedSheetMusicDescriptor>()
        val errors = mutableListOf<String>()
        val queue = ArrayDeque<GoogleDriveFolderNode>()
        queue.add(GoogleDriveFolderNode(folderId = root.folderId, folderSegments = emptyList()))
        val visitedFolderIds = mutableSetOf<String>()

        folderLoop@ while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (!visitedFolderIds.add(node.folderId)) {
                appendActivity(
                    message = "${source.label}: skipping previously visited folder ${node.displayLabel(root)}.",
                    currentStep = "Scanning ${source.label}",
                )
                continue
            }

            appendActivity(
                message = "${source.label}: scanning folder ${node.displayLabel(root)}.",
                currentStep = "Scanning ${source.label}",
                visitedFoldersDelta = 1,
            )
            var nextPageToken: String? = null

            do {
                val response = try {
                    fetchGoogleDriveFolderPage(
                        folderId = node.folderId,
                        apiKey = config.apiKey,
                        pageToken = nextPageToken,
                        headers = headers,
                    )
                } catch (error: Throwable) {
                    val message = "${source.label}: failed to scan folder ${node.displayLabel(root)} - ${error.describeSyncFailure()}"
                    errors += message
                    appendActivity(
                        message = message,
                        currentStep = "Scanning ${source.label}",
                    )
                    continue@folderLoop
                }
                response.files.forEach { file ->
                    when {
                        file.mimeType == GOOGLE_DRIVE_FOLDER_MIME_TYPE -> {
                            queue.add(
                                GoogleDriveFolderNode(
                                    folderId = file.id,
                                    folderSegments = node.folderSegments + file.name,
                                )
                            )
                        }

                        file.mimeType.equals(PDF_MIME_TYPE, ignoreCase = true) -> {
                                appendActivity(
                                    message = "${source.label}: found PDF ${file.name} in ${node.displayLabel(root)}.",
                                    currentStep = "Downloading PDFs from ${source.label}",
                                    discoveredResourcesDelta = 1,
                                )
                            try {
                                val metadata = resolveMetadata(
                                    fileNameNoExt = file.name.substringBeforeLast('.'),
                                    folderSegments = node.folderSegments,
                                    strategy = root.folderStrategy,
                                )
                                val duplicateReason = findDuplicateReason(
                                    title = metadata.title,
                                    composer = metadata.composer,
                                    collection = metadata.collection,
                                    composerIndex = composerIndex,
                                    collectionIndex = collectionIndex,
                                    acceptedComposerKeys = acceptedComposerKeys,
                                    acceptedCollectionKeys = acceptedCollectionKeys,
                                    ignoreExisting = { existing ->
                                        existing.syncMetadata?.providerId == source.id &&
                                            existing.syncMetadata?.remoteId == file.id
                                    },
                                )
                                if (duplicateReason != null) {
                                    appendActivity(
                                        message = "${source.label}: skipping duplicate ${file.name} because ${duplicateReason.displayLabel} already exists in the library.",
                                        currentStep = "Downloading PDFs from ${source.label}",
                                        skippedResourcesDelta = 1,
                                    )
                                    return@forEach
                                }
                                descriptors += resolveManagedDescriptor(
                                    providerId = source.id,
                                    remoteId = file.id,
                                    remoteVersion = file.modifiedTime,
                                    originalFileName = file.name,
                                    sourceUri = file.webViewLink ?: buildGoogleDriveViewUrl(file.id),
                                    metadata = metadata,
                                    dateAddedEpochMillis = if (root.folderStrategy == CloudPathStrategy.DATE_ADDED) {
                                        parseDateFolder(node.folderSegments.lastOrNull())
                                    } else {
                                        null
                                    },
                                    syncedAt = syncedAt,
                                ) {
                                    httpClient.getBytes(
                                        buildGoogleDriveDownloadUrl(file.id, config.apiKey),
                                        headers = headers,
                                    )
                                }
                                composerDuplicateKey(metadata.title, metadata.composer)?.let(acceptedComposerKeys::add)
                                collectionDuplicateKey(metadata.title, metadata.collection)?.let(acceptedCollectionKeys::add)
                                appendActivity(
                                    message = "${source.label}: ready ${file.name}.",
                                    currentStep = "Downloading PDFs from ${source.label}",
                                    processedResourcesDelta = 1,
                                )
                            } catch (error: Throwable) {
                                val message = "${source.label}: failed to sync ${file.name} in ${node.displayLabel(root)} - ${error.describeSyncFailure()}"
                                errors += message
                                appendActivity(
                                    message = message,
                                    currentStep = "Downloading PDFs from ${source.label}",
                                )
                            }
                        }
                    }
                }
                nextPageToken = response.nextPageToken
            } while (!nextPageToken.isNullOrBlank())
        }

        return GoogleDriveScanOutcome(
            descriptors = descriptors,
            errors = errors,
        )
    }

    private suspend fun fetchGoogleDriveFolderPage(
        folderId: String,
        apiKey: String,
        pageToken: String?,
        headers: Map<String, String>,
    ): GoogleDriveListResponse {
        val filters = "('$folderId' in parents) and trashed = false and (mimeType = '$GOOGLE_DRIVE_FOLDER_MIME_TYPE' or mimeType = '$PDF_MIME_TYPE')"
        val queryParams = buildList {
            add("q=${urlEncode(filters)}")
            add("fields=${urlEncode("nextPageToken,files(id,name,mimeType,modifiedTime,webViewLink)")}")
            add("pageSize=1000")
            add("supportsAllDrives=true")
            add("includeItemsFromAllDrives=true")
            add("orderBy=${urlEncode("name")}")
            if (!pageToken.isNullOrBlank()) add("pageToken=${urlEncode(pageToken)}")
            if (apiKey.isNotBlank()) add("key=${urlEncode(apiKey)}")
        }.joinToString("&")

        val response = httpClient.getText(
            url = "$GOOGLE_DRIVE_FILES_BASE_URL?$queryParams",
            headers = headers,
        )
        return json.decodeFromString(GoogleDriveListResponse.serializer(), response)
    }

    private suspend fun resolveManagedDescriptor(
        providerId: String,
        remoteId: String,
        remoteVersion: String?,
        originalFileName: String,
        sourceUri: String?,
        metadata: Metadata,
        dateAddedEpochMillis: Long?,
        syncedAt: Long,
        download: suspend () -> ByteArray,
    ): SyncedSheetMusicDescriptor {
        val existing = repository.library.value.items.firstOrNull { item ->
            val syncMetadata = item.syncMetadata
            syncMetadata?.providerId == providerId &&
                syncMetadata.remoteId == remoteId
        }

        val storedPath = if (
            existing != null &&
            existing.syncMetadata?.remoteVersion == remoteVersion &&
            pdfStore.exists(existing.document.storedPath)
        ) {
            existing.document.storedPath
        } else {
            pdfStore.savePdf(
                originalFileName = originalFileName,
                contents = download(),
            )
        }

        val resolvedPageCount = existing
            ?.takeIf { it.document.storedPath == storedPath && it.pageCount != null }
            ?.pageCount
            ?: renderer.inspect(storedPath)?.pageCount

        return SyncedSheetMusicDescriptor(
            remoteId = remoteId,
            remoteVersion = remoteVersion,
            storedPath = storedPath,
            originalFileName = originalFileName,
            sourceUri = sourceUri,
            title = metadata.title,
            composer = metadata.composer?.trim()?.ifEmpty { null },
            tags = metadata.tags,
            collection = metadata.collection?.trim()?.ifEmpty { null },
            pageCount = resolvedPageCount,
            dateAddedEpochMillis = dateAddedEpochMillis ?: existing?.dateAddedEpochMillis ?: syncedAt,
        )
    }

    private suspend fun resolveGoogleAccessToken(
        source: CloudGoogleDriveSource,
        config: GoogleDriveStorageSettings,
    ): String {
        val now = clock.now().toEpochMilliseconds()
        val cached = googleAccessTokens[source.id]
        if (cached != null && cached.expiresAtEpochMillis > now + ACCESS_TOKEN_EXPIRY_SKEW_MILLIS) {
            return cached.token
        }

        if (config.refreshToken.isNotBlank() && config.clientId.isNotBlank()) {
            val fields = linkedMapOf(
                "client_id" to config.clientId,
                "refresh_token" to config.refreshToken,
                "grant_type" to "refresh_token",
            )
            if (config.clientSecret.isNotBlank()) {
                fields["client_secret"] = config.clientSecret
            }

            val response = httpClient.postForm(
                url = GOOGLE_OAUTH_TOKEN_URL,
                formBody = buildFormBody(fields),
                headers = mapOf("Accept" to "application/json"),
            )
            val token = json.decodeFromString(GoogleOAuthTokenResponse.serializer(), response)
            googleAccessTokens[source.id] = CachedAccessToken(
                token = token.accessToken,
                expiresAtEpochMillis = tokenExpiryEpochMillis(now, token.expiresInSeconds),
            )
            return token.accessToken
        }

        return config.accessToken.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Google Drive requires an access token or refresh token.")
    }

    private fun resolveMetadata(
        fileNameNoExt: String,
        folderSegments: List<String>,
        strategy: CloudPathStrategy,
    ): Metadata = when (strategy) {
        CloudPathStrategy.FLAT -> Metadata(fileNameNoExt, null, null, emptyList())
        CloudPathStrategy.COMPOSER -> Metadata(
            title = fileNameNoExt,
            composer = folderSegments.lastOrNull(),
            collection = null,
            tags = emptyList(),
        )
        CloudPathStrategy.COLLECTION -> Metadata(
            title = fileNameNoExt,
            composer = null,
            collection = folderSegments.lastOrNull(),
            tags = emptyList(),
        )
        CloudPathStrategy.COMPOSER_COLLECTION -> Metadata(
            title = fileNameNoExt,
            composer = folderSegments.getOrNull(folderSegments.size - 2),
            collection = folderSegments.lastOrNull(),
            tags = emptyList(),
        )
        CloudPathStrategy.INSTRUMENT -> Metadata(
            title = fileNameNoExt,
            composer = null,
            collection = null,
            tags = listOfNotNull(folderSegments.lastOrNull()),
        )
        CloudPathStrategy.DATE_ADDED -> Metadata(
            title = fileNameNoExt,
            composer = null,
            collection = null,
            tags = emptyList(),
        )
    }

    private fun parseDateFolder(folderName: String?): Long? {
        folderName ?: return null
        return runCatching {
            val parts = folderName.split('-')
            val year = parts[0].toInt()
            val month = parts.getOrNull(1)?.toInt() ?: 1
            val day = parts.getOrNull(2)?.toInt() ?: 1
            val daysFromEpoch = (year - 1970) * 365L + (month - 1) * 30L + (day - 1)
            daysFromEpoch * 86_400_000L
        }.getOrNull()
    }

    private fun buildSummary(
        outcomes: List<SourceRefreshOutcome>,
        syncedCount: Int,
        syncErrors: List<String>,
    ): String = when {
        outcomes.isEmpty() && syncErrors.isEmpty() -> "No cloud sources configured yet."
        syncErrors.isEmpty() -> "Synced $syncedCount score${if (syncedCount == 1) "" else "s"} from ${outcomes.size} cloud source${if (outcomes.size == 1) "" else "s"}."
        syncedCount > 0 -> "Synced $syncedCount score${if (syncedCount == 1) "" else "s"} with ${syncErrors.size} issue${if (syncErrors.size == 1) "" else "s"}."
        else -> syncErrors.first()
    }

    private suspend fun ensureInitialized() {
        if (!initialized) initialize()
    }

    private fun appendActivity(
        message: String,
        currentStep: String? = null,
        discoveredResourcesDelta: Int = 0,
        processedResourcesDelta: Int = 0,
        skippedResourcesDelta: Int = 0,
        visitedFoldersDelta: Int = 0,
    ) {
        val trimmed = message.trim()
        if (trimmed.isBlank()) return

        val currentState = stateFlow.value
        stateFlow.value = currentState.copy(
            currentStep = currentStep ?: currentState.currentStep,
            lastMessage = currentState.lastMessage,
            discoveredResources = (currentState.discoveredResources + discoveredResourcesDelta).coerceAtLeast(0),
            processedResources = (currentState.processedResources + processedResourcesDelta).coerceAtLeast(0),
            skippedResources = (currentState.skippedResources + skippedResourcesDelta).coerceAtLeast(0),
            visitedFolders = (currentState.visitedFolders + visitedFoldersDelta).coerceAtLeast(0),
            activityLog = (currentState.activityLog + trimmed).takeLast(MAX_ACTIVITY_LOG_ENTRIES),
        )
    }
}

private val SUPABASE_PUBLIC_PROVIDER = CloudLibraryProviderOption(
    id = SyncProviderIds.SUPABASE_PUBLIC_STORAGE,
    displayName = "Supabase public storage",
    description = "Scan a Supabase Storage bucket and download PDFs automatically.",
)

private val GOOGLE_DRIVE_PROVIDER = CloudLibraryProviderOption(
    id = SyncProviderIds.GOOGLE_DRIVE,
    displayName = "Google Drive",
    description = "Sync PDFs from one or more Google Drive folders using Drive API access.",
)

fun supportedCloudProviders(): List<CloudLibraryProviderOption> = listOf(
    SUPABASE_PUBLIC_PROVIDER,
    GOOGLE_DRIVE_PROVIDER,
)

fun rememberNoOpLibrarySyncManager(
    repository: SheetMusicRepository,
    renderer: PdfPageRenderer,
): LibrarySyncManager = LibrarySyncManager(
    repository = repository,
    httpClient = FailingSyncHttpClient("Cloud sync is unavailable on this target."),
    pdfStore = NoOpManagedPdfStore,
    renderer = renderer,
)

private fun SupabasePublicStorageSettings.normalized(): SupabasePublicStorageSettings = copy(
    projectUrl = projectUrl.trim(),
    bucketName = bucketName.trim(),
    rootDirectory = rootDirectory.trim().trim('/'),
    anonKey = anonKey.trim(),
)

private fun SupabasePublicStorageSettings.isComplete(): Boolean =
    projectUrl.isNotBlank() && bucketName.isNotBlank()

private fun GoogleDriveStorageSettings.normalized(): GoogleDriveStorageSettings = copy(
    apiKey = apiKey.trim(),
    clientId = clientId.trim(),
    clientSecret = clientSecret.trim(),
    refreshToken = refreshToken.trim(),
    accessToken = accessToken.trim(),
    roots = roots.mapNotNull { root ->
        val folderId = root.folderId.trim()
        if (folderId.isBlank()) {
            null
        } else {
            root.copy(
                label = root.label.trim(),
                folderId = folderId,
            )
        }
    },
)

private fun GoogleDriveStorageSettings.isComplete(): Boolean =
    roots.isNotEmpty() && (
        accessToken.isNotBlank() ||
            (clientId.isNotBlank() && refreshToken.isNotBlank())
        )

private fun List<SheetMusicItem>.groupByComposerDuplicateKey(): Map<String, List<SheetMusicItem>> =
    mapNotNull { item ->
        composerDuplicateKey(item.title, item.composer)?.let { key -> key to item }
    }.groupBy({ it.first }, { it.second })

private fun List<SheetMusicItem>.groupByCollectionDuplicateKey(): Map<String, List<SheetMusicItem>> =
    mapNotNull { item ->
        collectionDuplicateKey(item.title, item.collection)?.let { key -> key to item }
    }.groupBy({ it.first }, { it.second })

private fun findDuplicateReason(
    title: String,
    composer: String?,
    collection: String?,
    composerIndex: Map<String, List<SheetMusicItem>>,
    collectionIndex: Map<String, List<SheetMusicItem>>,
    acceptedComposerKeys: Set<String>,
    acceptedCollectionKeys: Set<String>,
    ignoreExisting: (SheetMusicItem) -> Boolean = { false },
): DuplicateMatchReason? {
    val composerKey = composerDuplicateKey(title, composer)
    if (composerKey != null) {
        val existingComposerMatch = composerIndex[composerKey]
            ?.firstOrNull { existing -> !ignoreExisting(existing) }
        if (existingComposerMatch != null || composerKey in acceptedComposerKeys) {
            return DuplicateMatchReason.TITLE_AND_COMPOSER
        }
    }

    val collectionKey = collectionDuplicateKey(title, collection)
    if (collectionKey != null) {
        val existingCollectionMatch = collectionIndex[collectionKey]
            ?.firstOrNull { existing -> !ignoreExisting(existing) }
        if (existingCollectionMatch != null || collectionKey in acceptedCollectionKeys) {
            return DuplicateMatchReason.TITLE_AND_COLLECTION
        }
    }

    return null
}

private fun GoogleDriveImportRoot.displayLabel(): String =
    label.ifBlank { folderId }

private fun GoogleDriveFolderNode.displayLabel(root: GoogleDriveImportRoot): String =
    if (folderSegments.isEmpty()) {
        root.displayLabel()
    } else {
        "${root.displayLabel()}/${folderSegments.joinToString("/")}"
    }

private fun Throwable.describeSyncFailure(): String {
    val message = message?.trim()
    val typeName = this::class.simpleName.orEmpty()
    return when {
        !message.isNullOrBlank() && (typeName.isBlank() || typeName == "IOException" || typeName == "IllegalStateException") -> message
        !message.isNullOrBlank() -> "$typeName: $message"
        typeName.isNotBlank() -> typeName
        else -> "sync failed."
    }
}

private fun buildSupabasePublicUrl(projectUrl: String, bucketName: String, objectPath: String): String =
    "${projectUrl.trimEnd('/')}/storage/v1/object/public/${bucketName.trim()}/${objectPath.trimStart('/')}"

private fun buildGoogleDriveDownloadUrl(fileId: String, apiKey: String): String {
    val baseUrl = "$GOOGLE_DRIVE_FILES_BASE_URL/${urlEncodePathSegment(fileId)}?alt=media&supportsAllDrives=true"
    return if (apiKey.isBlank()) baseUrl else "$baseUrl&key=${urlEncode(apiKey)}"
}

private fun buildGoogleDriveViewUrl(fileId: String): String =
    "https://drive.google.com/file/d/${urlEncodePathSegment(fileId)}/view"

private const val DEFAULT_AUTO_REFRESH_INTERVAL_MILLIS = 5 * 60_000L
private const val ACCESS_TOKEN_EXPIRY_SKEW_MILLIS = 60_000L
private const val MAX_ACTIVITY_LOG_ENTRIES = 250
private const val GOOGLE_DRIVE_FILES_BASE_URL = "https://www.googleapis.com/drive/v3/files"
private const val GOOGLE_DRIVE_FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
private const val PDF_MIME_TYPE = "application/pdf"
