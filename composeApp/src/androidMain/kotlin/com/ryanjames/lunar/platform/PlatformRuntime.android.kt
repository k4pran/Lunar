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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import com.ryanjames.lunar.library.data.DefaultSheetMusicRepository
import com.ryanjames.lunar.library.data.JsonLibraryStorage
import com.ryanjames.lunar.library.data.OkioStoredDocumentCleaner
import com.ryanjames.lunar.library.data.JsonSyncSettingsStorage
import com.ryanjames.lunar.library.model.ImportedPdfDescriptor
import com.ryanjames.lunar.sync.LibrarySyncManager
import com.ryanjames.lunar.sync.ManagedPdfStore
import com.ryanjames.lunar.sync.SyncHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlin.random.Random

@Composable
actual fun rememberPlatformRuntime(): PlatformRuntime {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val scoresDirectory = remember(context) {
        File(context.filesDir, "lunar/scores").apply { mkdirs() }
    }
    val metadataPath = remember(context) {
        File(context.filesDir, "lunar/metadata/library.json").absolutePath.toPath()
    }
    val syncSettingsPath = remember(context) {
        File(context.filesDir, "lunar/sync/settings.json").absolutePath.toPath()
    }
    val repository = remember(context) {
        DefaultSheetMusicRepository(
            storage = JsonLibraryStorage(
                fileSystem = FileSystem.SYSTEM,
                metadataPath = metadataPath,
            ),
            storedDocumentCleaner = OkioStoredDocumentCleaner(FileSystem.SYSTEM),
        )
    }
    val renderer = remember(context) { AndroidPdfPageRenderer() }
    val pdfStore = remember(scoresDirectory) {
        AndroidManagedPdfStore(scoresDirectory = scoresDirectory)
    }
    val syncHttpClient = remember { AndroidSyncHttpClient() }
    val syncManager = remember(repository, renderer, syncSettingsPath, pdfStore, syncHttpClient) {
        LibrarySyncManager(
            repository = repository,
            settingsStorage = JsonSyncSettingsStorage(
                fileSystem = FileSystem.SYSTEM,
                settingsPath = syncSettingsPath,
            ),
            httpClient = syncHttpClient,
            pdfStore = pdfStore,
            renderer = renderer,
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

    return remember(repository, importer, renderer, syncManager) {
        PlatformRuntime(
            platformName = "Android",
            capabilities = PlatformCapabilities(
                fileImportSupported = true,
                folderImportSupported = true,
                permissionTrackingSupported = true,
                inAppViewingSupported = true,
                statusLine = "Local library storage active",
            ),
            repository = repository,
            importer = importer,
            renderer = renderer,
            syncManager = syncManager,
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
        launcher.launch(arrayOf("application/pdf"))
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

        val documents = buildList {
            uris.forEach { uri ->
                persistReadPermission(uri)
                copyUriToLibrary(
                    uri = uri,
                    originalName = queryDisplayName(uri) ?: "Imported Score.pdf",
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

        val pdfFiles = collectPdfDocuments(root)
        if (pdfFiles.isEmpty()) {
            refreshPermissionState()
            return@withContext ImportRequestResult(emptyList(), "No PDF files were found in the selected folder.")
        }

        val documents = buildList {
            pdfFiles.forEach { file ->
                copyUriToLibrary(
                    uri = file.uri,
                    originalName = file.name ?: "Imported Score.pdf",
                )?.let(::add)
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
    ): ImportedPdfDescriptor? {
        val safeName = safeFileStem(originalName)
        val destination = File(
            scoresDirectory,
            "${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}_$safeName.pdf",
        )

        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        inputStream.use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val pageCount = renderer.inspect(destination.absolutePath)?.pageCount

        return ImportedPdfDescriptor(
            storedPath = destination.absolutePath,
            originalFileName = originalName,
            sourceUri = uri.toString(),
            pageCount = pageCount,
            suggestedTitle = originalName.substringBeforeLast('.'),
        )
    }

    private fun collectPdfDocuments(root: DocumentFile): List<DocumentFile> {
        val documents = mutableListOf<DocumentFile>()

        fun walk(node: DocumentFile) {
            when {
                node.isFile && node.name?.endsWith(".pdf", ignoreCase = true) == true -> documents += node
                node.isDirectory -> node.listFiles().forEach(::walk)
            }
        }

        walk(root)
        return documents
    }

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

private class AndroidSyncHttpClient : SyncHttpClient {
    override suspend fun getText(url: String): String = withContext(Dispatchers.IO) {
        java.net.URL(url).openConnection().apply {
            connectTimeout = 15_000
            readTimeout = 30_000
        }.getInputStream().bufferedReader().use { it.readText() }
    }

    override suspend fun getBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        java.net.URL(url).openConnection().apply {
            connectTimeout = 15_000
            readTimeout = 60_000
        }.getInputStream().use { it.readBytes() }
    }

    override suspend fun postJson(url: String, jsonBody: String, headers: Map<String, String>): String = withContext(Dispatchers.IO) {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
            connectTimeout = 15_000
            readTimeout = 30_000
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

private fun safeFileStem(fileName: String): String = fileName
    .substringBeforeLast('.')
    .replace(Regex("[^A-Za-z0-9_-]+"), "_")
    .trim('_')
    .ifEmpty { "score" }
