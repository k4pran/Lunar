package com.ryanjames.lunar.platform

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import com.ryanjames.lunar.library.data.DefaultSheetMusicRepository
import com.ryanjames.lunar.library.data.JsonLibraryStorage
import com.ryanjames.lunar.library.data.JsonScoreMetadataStorage
import com.ryanjames.lunar.library.data.OkioStoredDocumentCleaner
import com.ryanjames.lunar.library.data.JsonSourceRegistry
import com.ryanjames.lunar.library.data.StoredDocumentFingerprinter
import com.ryanjames.lunar.library.model.ImportedPdfDescriptor
import com.ryanjames.lunar.library.model.ScoreMetadata
import com.ryanjames.lunar.settings.AppSettings
import com.ryanjames.lunar.settings.JsonAppSettingsStore
import com.ryanjames.lunar.sync.LibrarySyncManager
import com.ryanjames.lunar.sync.ManagedPdfStore
import com.ryanjames.lunar.sync.SyncHttpClient
import com.ryanjames.lunar.sync.UnsupportedGoogleDriveOAuthCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
actual fun rememberPlatformRuntime(): PlatformRuntime {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val appRoot = remember(context) {
        File(context.filesDir, "lunar").apply { mkdirs() }
    }
    val scoresDirectory = remember(context) {
        File(appRoot, "scores").apply { mkdirs() }
    }
    val metadataFile = remember(context) {
        File(appRoot, "metadata/library.json")
    }
    val metadataPath = remember(metadataFile) {
        metadataFile.absolutePath.toPath()
    }
    val scoreMetadataDirectoryPath = remember(context) {
        File(appRoot, "metadata/scores").absolutePath.toPath()
    }
    val sourcesFile = remember(context) {
        File(appRoot, "sources/sources.json")
    }
    val sourcesPath = remember(sourcesFile) {
        sourcesFile.absolutePath.toPath()
    }
    val settingsFile = remember(context) {
        File(appRoot, "settings/app_settings.json")
    }
    val settingsPath = remember(settingsFile) {
        settingsFile.absolutePath.toPath()
    }
    val repository = remember(context) {
        DefaultSheetMusicRepository(
            storage = JsonLibraryStorage(
                fileSystem = FileSystem.SYSTEM,
                metadataPath = metadataPath,
            ),
            scoreMetadataStorage = JsonScoreMetadataStorage(
                fileSystem = FileSystem.SYSTEM,
                metadataDirectory = scoreMetadataDirectoryPath,
            ),
            storedDocumentCleaner = OkioStoredDocumentCleaner(FileSystem.SYSTEM),
            storedDocumentFingerprinter = AndroidStoredDocumentFingerprinter(),
        )
    }
    val renderer = remember(context) { AndroidPdfPageRenderer() }
    val pdfStore = remember(scoresDirectory) {
        AndroidManagedPdfStore(scoresDirectory = scoresDirectory)
    }
    val settingsStore = remember(settingsPath) {
        JsonAppSettingsStore(
            fileSystem = FileSystem.SYSTEM,
            settingsPath = settingsPath,
        )
    }
    val syncHttpClient = remember(settingsStore) {
        AndroidSyncHttpClient(
            settingsProvider = { settingsStore.settings.value }
        )
    }
    val sourceRegistry = remember(sourcesPath) {
        JsonSourceRegistry(
            fileSystem = FileSystem.SYSTEM,
            sourcesPath = sourcesPath,
        )
    }
    val syncManager = remember(repository, renderer, pdfStore, syncHttpClient, sourceRegistry) {
        LibrarySyncManager(
            repository = repository,
            httpClient = syncHttpClient,
            pdfStore = pdfStore,
            renderer = renderer,
            sourceRegistry = sourceRegistry,
        )
    }
    val importer = remember(context, scoresDirectory, renderer) {
        AndroidPdfImporter(
            context = context,
            scoresDirectory = scoresDirectory,
            renderer = renderer,
        )
    }
    val fileLauncher = rememberLauncherForActivityResult(OpenMultipleDocuments()) { uris ->
        scope.launch {
            importer.onDocumentsPicked(uris)
        }
    }
    val folderLauncher = rememberLauncherForActivityResult(OpenDocumentTree()) { uri ->
        scope.launch {
            importer.onFolderPicked(uri)
        }
    }

    SideEffect {
        importer.bindLaunchers(fileLauncher = fileLauncher, folderLauncher = folderLauncher)
    }
    val cacheInspector = remember(appRoot, metadataFile, sourcesFile, scoresDirectory) {
        AndroidLibraryCacheInspector(
            appRoot = appRoot,
            metadataFile = metadataFile,
            sourcesFile = sourcesFile,
            scoresDirectory = scoresDirectory,
        )
    }

    return remember(repository, importer, renderer, syncManager, sourceRegistry, settingsStore, cacheInspector) {
        PlatformRuntime(
            platformName = "Android",
            capabilities = PlatformCapabilities(
                fileImportSupported = true,
                folderImportSupported = true,
                permissionTrackingSupported = true,
                inAppViewingSupported = true,
                scoreDownloadSupported = false,
                songbookCreationSupported = false,
                songbookCoverImageSupported = false,
                statusLine = "Local library storage active",
            ),
            repository = repository,
            importer = importer,
            renderer = renderer,
            lilyPondLiveRenderer = UnsupportedLilyPondLiveRenderer,
            pdfExporter = UnsupportedPdfDocumentExporter,
            songbookBuilder = UnsupportedSongbookPdfBuilder,
            coverImagePicker = UnsupportedCoverImagePicker,
            syncManager = syncManager,
            sourceRegistry = sourceRegistry,
            settingsStore = settingsStore,
            googleDriveOAuth = UnsupportedGoogleDriveOAuthCoordinator,
            cacheInspector = cacheInspector,
        )
    }
}

private class AndroidPdfImporter(
    private val context: Context,
    private val scoresDirectory: File,
    private val renderer: PdfPageRenderer,
) : PdfImporter {
    private val importerState = MutableStateFlow(
        ImporterState(
            statusMessage = "Persisted Android document permissions are tracked here for imported files and folders.",
        )
    )

    private var fileLauncher: ManagedActivityResultLauncher<Array<String>, List<Uri>>? = null
    private var folderLauncher: ManagedActivityResultLauncher<Uri?, Uri?>? = null
    private var pendingFileContinuation: kotlin.coroutines.Continuation<ImportRequestResult>? = null
    private var pendingFolderContinuation: kotlin.coroutines.Continuation<ImportRequestResult>? = null

    override val state: StateFlow<ImporterState> = importerState.asStateFlow()

    init {
        refreshPermissionState()
    }

    fun bindLaunchers(
        fileLauncher: ManagedActivityResultLauncher<Array<String>, List<Uri>>,
        folderLauncher: ManagedActivityResultLauncher<Uri?, Uri?>,
    ) {
        this.fileLauncher = fileLauncher
        this.folderLauncher = folderLauncher
        refreshPermissionState()
    }

    suspend fun onDocumentsPicked(uris: List<Uri>) {
        val continuation = pendingFileContinuation ?: return
        pendingFileContinuation = null
        continuation.resume(processUris(uris))
    }

    suspend fun onFolderPicked(uri: Uri?) {
        val continuation = pendingFolderContinuation ?: return
        pendingFolderContinuation = null
        continuation.resume(processTreeUri(uri))
    }

    override suspend fun importPdfFiles(): ImportRequestResult = suspendCancellableCoroutine { continuation ->
        val launcher = fileLauncher
        if (launcher == null) {
            continuation.resume(ImportRequestResult(emptyList(), "Android file picker unavailable."))
            return@suspendCancellableCoroutine
        }

        pendingFileContinuation = continuation
        launcher.launch(arrayOf("application/pdf", "application/json"))
    }

    override suspend fun importPdfFolder(): ImportRequestResult = suspendCancellableCoroutine { continuation ->
        val launcher = folderLauncher
        if (launcher == null) {
            continuation.resume(ImportRequestResult(emptyList(), "Android folder picker unavailable."))
            return@suspendCancellableCoroutine
        }

        pendingFolderContinuation = continuation
        launcher.launch(null)
    }

    private suspend fun processUris(uris: List<Uri>): ImportRequestResult = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) {
            return@withContext ImportRequestResult(emptyList())
        }

        val selectedEntries = uris.mapNotNull { uri ->
            persistReadPermission(uri)
            val name = queryDisplayName(uri)
                ?: uri.lastPathSegment
                ?: return@mapNotNull null
            AndroidImportEntry(
                uri = uri,
                name = name,
            )
        }
        val scoreEntries = selectedEntries.filter { entry ->
            isAndroidScoreFileName(entry.name)
        }
        val metadataByScoreUri = matchAndroidMetadataEntries(
            scoreEntries = scoreEntries,
            metadataEntries = selectedEntries.filter { entry -> isAndroidMetadataFileName(entry.name) },
        )
        val documents = buildList {
            scoreEntries.forEach { entry ->
                copyUriToLibrary(
                    uri = entry.uri,
                    originalName = entry.name,
                    scoreMetadata = metadataByScoreUri[entry.uri],
                )?.let(::add)
            }
        }

        refreshPermissionState()

        ImportRequestResult(
            documents = documents,
            notice = if (documents.isEmpty()) "No readable PDFs were imported from the selected files." else null,
        )
    }

    private suspend fun processTreeUri(treeUri: Uri?): ImportRequestResult = withContext(Dispatchers.IO) {
        if (treeUri == null) {
            return@withContext ImportRequestResult(emptyList())
        }

        persistReadPermission(treeUri)
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext ImportRequestResult(emptyList(), "The selected folder could not be read.")

        val entriesByFolder = collectImportEntriesByFolder(root)
        val totalScoreCount = entriesByFolder.values.sumOf { entries ->
            entries.count { entry -> isAndroidScoreFileName(entry.name) }
        }
        if (totalScoreCount == 0) {
            refreshPermissionState()
            return@withContext ImportRequestResult(emptyList(), "No PDF files were found in the selected folder.")
        }

        val documents = buildList {
            entriesByFolder.values.forEach { entries ->
                val scoreEntries = entries.filter { entry -> isAndroidScoreFileName(entry.name) }
                val metadataByScoreUri = matchAndroidMetadataEntries(
                    scoreEntries = scoreEntries,
                    metadataEntries = entries.filter { entry -> isAndroidMetadataFileName(entry.name) },
                )
                scoreEntries.forEach { entry ->
                    copyUriToLibrary(
                        uri = entry.uri,
                        originalName = entry.name,
                        scoreMetadata = metadataByScoreUri[entry.uri],
                    )?.let(::add)
                }
            }
        }

        refreshPermissionState()

        ImportRequestResult(
            documents = documents,
            notice = if (documents.isEmpty()) {
                "No readable PDFs were imported from ${root.name ?: "the selected folder"}."
            } else {
                null
            },
        )
    }

    private suspend fun copyUriToLibrary(
        uri: Uri,
        originalName: String,
        scoreMetadata: ScoreMetadata? = null,
    ): ImportedPdfDescriptor? {
        val safeName = safeFileStem(originalName)
        val destination = File(
            scoresDirectory,
            "${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}_$safeName.pdf",
        )

        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val contentFingerprint = copyToManagedPdf(input = inputStream, destination = destination)

        val pageCount = renderer.inspect(destination.absolutePath)?.pageCount

        return ImportedPdfDescriptor(
            storedPath = destination.absolutePath,
            originalFileName = originalName,
            sourceUri = uri.toString(),
            contentFingerprint = contentFingerprint,
            pageCount = pageCount,
            suggestedTitle = originalName.substringBeforeLast('.'),
            scoreMetadata = scoreMetadata,
        )
    }

    private fun copyToManagedPdf(
        input: java.io.InputStream,
        destination: File,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        input.use { source ->
            destination.outputStream().use { output ->
                val buffer = ByteArray(COPY_BUFFER_SIZE_BYTES)
                while (true) {
                    val read = source.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
            }
        }
        return digest.digest().toHexString()
    }

    private fun collectImportEntriesByFolder(root: DocumentFile): Map<String, List<AndroidImportEntry>> {
        val entriesByFolder = linkedMapOf<String, MutableList<AndroidImportEntry>>()

        fun walk(node: DocumentFile) {
            when {
                node.isDirectory -> node.listFiles().forEach(::walk)
                node.isFile -> {
                    val name = node.name ?: return
                    if (!isAndroidScoreFileName(name) && !isAndroidMetadataFileName(name)) {
                        return
                    }
                    entriesByFolder
                        .getOrPut(node.uri.buildFolderKey()) { mutableListOf() }
                        .add(AndroidImportEntry(uri = node.uri, name = name))
                }
            }
        }

        walk(root)
        return entriesByFolder
    }

    private fun matchAndroidMetadataEntries(
        scoreEntries: List<AndroidImportEntry>,
        metadataEntries: List<AndroidImportEntry>,
    ): Map<Uri, ScoreMetadata> {
        if (scoreEntries.isEmpty() || metadataEntries.isEmpty()) {
            return emptyMap()
        }

        val metadataByStem = metadataEntries.associateBy { entry ->
            entry.name.substringBeforeLast('.').lowercase()
        }
        val singleMetadata = metadataEntries.singleOrNull()
            ?.takeIf { scoreEntries.size == 1 }
        val metadataCache = mutableMapOf<Uri, ScoreMetadata?>()
        val matches = mutableMapOf<Uri, ScoreMetadata>()

        scoreEntries.forEach { scoreEntry ->
            val matchedEntry = metadataByStem[scoreEntry.name.substringBeforeLast('.').lowercase()]
                ?: singleMetadata
            val metadata = matchedEntry?.let { entry ->
                metadataCache.getOrPut(entry.uri) {
                    parseAndroidScoreMetadata(entry.uri)
                }
            }
            if (metadata != null) {
                matches[scoreEntry.uri] = metadata
            }
        }

        return matches
    }

    private fun parseAndroidScoreMetadata(uri: Uri): ScoreMetadata? = runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            androidScoreMetadataJson.decodeFromString(
                ScoreMetadata.serializer(),
                reader.readText(),
            )
        }
    }.getOrNull()

    private fun refreshPermissionState() {
        val trackedPermissions = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { permission ->
                val uri = permission.uri
                val isFolder = DocumentsContract.isTreeUri(uri)
                ImportPermissionGrant(
                    id = uri.toString(),
                    label = resolvePermissionLabel(uri, isFolder),
                    kind = if (isFolder) ImportPermissionKind.FOLDER else ImportPermissionKind.FILE,
                    persisted = true,
                    detail = if (isFolder) {
                        "Folder access is retained so Lunar can re-read PDF files from this source later."
                    } else {
                        "Document access was persisted when the PDF was imported."
                    },
                )
            }
            .sortedBy { it.label.lowercase() }

        importerState.value = importerState.value.copy(
            trackedPermissions = trackedPermissions,
        )
    }

    private fun resolvePermissionLabel(uri: Uri, isFolder: Boolean): String {
        val document = if (isFolder) {
            DocumentFile.fromTreeUri(context, uri)
        } else {
            DocumentFile.fromSingleUri(context, uri)
        }

        return document?.name
            ?: queryDisplayName(uri)
            ?: uri.lastPathSegment
            ?: uri.toString()
    }

    private fun queryDisplayName(uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
        cursor.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            return if (it.moveToFirst() && nameIndex >= 0) it.getString(nameIndex) else null
        }
    }

    private fun persistReadPermission(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }
}

private class AndroidPdfPageRenderer : PdfPageRenderer {
    override suspend fun inspect(documentPath: String): PdfDocumentInfo? = withContext(Dispatchers.IO) {
        val file = File(documentPath)
        if (!file.exists()) {
            return@withContext null
        }

        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                PdfDocumentInfo(pageCount = renderer.pageCount)
            }
        }
    }

    override suspend fun renderPage(
        documentPath: String,
        pageIndex: Int,
        targetWidth: Int,
    ): RenderedPdfPage? = withContext(Dispatchers.IO) {
        val file = File(documentPath)
        if (!file.exists()) {
            return@withContext null
        }

        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (pageIndex !in 0 until renderer.pageCount) {
                    return@withContext null
                }

                renderer.openPage(pageIndex).use { page ->
                    val width = targetWidth.coerceAtLeast(960)
                    val scale = width.toFloat() / page.width.coerceAtLeast(1)
                    val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    RenderedPdfPage(
                        pageIndex = pageIndex,
                        pageCount = renderer.pageCount,
                        aspectRatio = width.toFloat() / height.toFloat(),
                        image = bitmap.asImageBitmap(),
                    )
                }
            }
        }
    }
}

private class AndroidSyncHttpClient(
    private val settingsProvider: () -> AppSettings,
) : SyncHttpClient {
    override suspend fun getText(url: String, headers: Map<String, String>): String = withContext(Dispatchers.IO) {
        val settings = settingsProvider()
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.apply {
            connectTimeout = settings.cloudConnectTimeoutMillis
            readTimeout = settings.cloudReadTimeoutMillis
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw java.io.IOException("HTTP $responseCode from GET $url: $errorBody")
        }
        connection.inputStream.bufferedReader().use { it.readText() }
    }

    override suspend fun getBytes(url: String, headers: Map<String, String>): ByteArray = withContext(Dispatchers.IO) {
        val settings = settingsProvider()
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.apply {
            connectTimeout = settings.cloudConnectTimeoutMillis
            readTimeout = settings.cloudReadTimeoutMillis
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw java.io.IOException("HTTP $responseCode from GET $url: $errorBody")
        }
        connection.inputStream.use { it.readBytes() }
    }

    override suspend fun postJson(url: String, jsonBody: String, headers: Map<String, String>): String = withContext(Dispatchers.IO) {
        val settings = settingsProvider()
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
            connectTimeout = settings.cloudConnectTimeoutMillis
            readTimeout = settings.cloudReadTimeoutMillis
            doOutput = true
        }
        connection.outputStream.bufferedWriter().use { it.write(jsonBody) }
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw java.io.IOException("HTTP $responseCode from POST $url: $errorBody")
        }
        connection.inputStream.bufferedReader().use { it.readText() }
    }

    override suspend fun postForm(url: String, formBody: String, headers: Map<String, String>): String = withContext(Dispatchers.IO) {
        val settings = settingsProvider()
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
            connectTimeout = settings.cloudConnectTimeoutMillis
            readTimeout = settings.cloudReadTimeoutMillis
            doOutput = true
        }
        connection.outputStream.bufferedWriter().use { it.write(formBody) }
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw java.io.IOException("HTTP $responseCode from POST $url: $errorBody")
        }
        connection.inputStream.bufferedReader().use { it.readText() }
    }
}

private class AndroidStoredDocumentFingerprinter : StoredDocumentFingerprinter {
    override suspend fun fingerprint(storedPath: String): String? = withContext(Dispatchers.IO) {
        val file = File(storedPath)
        if (!file.exists()) {
            return@withContext null
        }

        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(COPY_BUFFER_SIZE_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().toHexString()
    }
}

private class AndroidLibraryCacheInspector(
    private val appRoot: File,
    private val metadataFile: File,
    private val sourcesFile: File,
    private val scoresDirectory: File,
) : LibraryCacheInspector {
    override suspend fun inspect(): LibraryCacheSnapshot = withContext(Dispatchers.IO) {
        val cachedPdfFiles = if (scoresDirectory.exists()) {
            scoresDirectory.walkTopDown()
                .filter { it.isFile && it.extension.equals("pdf", ignoreCase = true) }
                .toList()
        } else {
            emptyList()
        }

        LibraryCacheSnapshot(
            storageLabel = "Android app private storage",
            cacheRootPath = appRoot.absolutePath,
            metadataCached = metadataFile.exists(),
            sourceRegistryCached = sourcesFile.exists(),
            cachedPdfCount = cachedPdfFiles.size,
            cachedPdfBytes = cachedPdfFiles.sumOf(File::length),
        )
    }
}

private class AndroidManagedPdfStore(
    private val scoresDirectory: File,
) : ManagedPdfStore {
    override suspend fun savePdf(
        originalFileName: String,
        contents: ByteArray,
    ): String = withContext(Dispatchers.IO) {
        val safeName = safeFileStem(originalFileName)
        val destination = File(
            scoresDirectory,
            "${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}_$safeName.pdf",
        )
        destination.outputStream().use { output ->
            output.write(contents)
        }
        destination.absolutePath
    }

    override suspend fun exists(storedPath: String): Boolean = withContext(Dispatchers.IO) {
        File(storedPath).exists()
    }
}

private data class AndroidImportEntry(
    val uri: Uri,
    val name: String,
)

private fun safeFileStem(fileName: String): String = fileName
    .substringBeforeLast('.')
    .replace(Regex("[^A-Za-z0-9_-]+"), "_")
    .trim('_')
    .ifEmpty { "score" }

private fun isAndroidScoreFileName(name: String): Boolean =
    name.endsWith(".pdf", ignoreCase = true)

private fun isAndroidMetadataFileName(name: String): Boolean =
    name.endsWith(".json", ignoreCase = true)

private fun Uri.buildFolderKey(): String = toString().substringBeforeLast('/', missingDelimiterValue = toString())

private const val COPY_BUFFER_SIZE_BYTES = 8_192
private val androidScoreMetadataJson = Json { ignoreUnknownKeys = true }

actual fun Modifier.externalScoreDropTarget(
    enabled: Boolean,
    onDroppedPaths: (List<String>) -> Unit,
): Modifier = this

private fun ByteArray.toHexString(): String = joinToString(separator = "") { byte ->
    byte.toUByte().toString(16).padStart(2, '0')
}
