package com.ryanjames.lunar.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toComposeImageBitmap
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
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.max
import kotlin.random.Random

@Composable
actual fun rememberPlatformRuntime(): PlatformRuntime {
    val appRoot = remember { desktopAppRootDirectory() }
    val metadataPath = remember(appRoot) {
        File(appRoot, "metadata/library.json").absolutePath.toPath()
    }
    val syncSettingsPath = remember(appRoot) {
        File(appRoot, "sync/settings.json").absolutePath.toPath()
    }
    val scoresDirectory = remember(appRoot) {
        File(appRoot, "scores").apply { mkdirs() }
    }
    val repository = remember(appRoot) {
        DefaultSheetMusicRepository(
            storage = JsonLibraryStorage(
                fileSystem = FileSystem.SYSTEM,
                metadataPath = metadataPath,
            ),
            storedDocumentCleaner = OkioStoredDocumentCleaner(FileSystem.SYSTEM),
        )
    }
    val renderer = remember { DesktopPdfPageRenderer() }
    val pdfStore = remember(scoresDirectory) {
        DesktopManagedPdfStore(scoresDirectory = scoresDirectory)
    }
    val syncHttpClient = remember { DesktopSyncHttpClient() }
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
    val importer = remember(scoresDirectory, renderer) {
        DesktopPdfImporter(
            scoresDirectory = scoresDirectory,
            renderer = renderer,
        )
    }

    return remember(repository, importer, renderer, syncManager, appRoot) {
        PlatformRuntime(
            platformName = System.getProperty("os.name") ?: "Desktop JVM",
            capabilities = PlatformCapabilities(
                fileImportSupported = true,
                folderImportSupported = true,
                permissionTrackingSupported = false,
                inAppViewingSupported = true,
                statusLine = "Library stored in ${appRoot.name}",
            ),
            repository = repository,
            importer = importer,
            renderer = renderer,
            syncManager = syncManager,
        )
    }
}

private class DesktopPdfImporter(
    private val scoresDirectory: File,
    private val renderer: PdfPageRenderer,
) : PdfImporter {
    private val importerState = MutableStateFlow(
        ImporterState(
            statusMessage = "Desktop imports use the local file system directly, so no persisted SAF permissions are needed.",
        )
    )

    override val state: StateFlow<ImporterState> = importerState.asStateFlow()

    override suspend fun importPdfFiles(): ImportRequestResult {
        val files = withContext(Dispatchers.Swing) { selectFiles() }
        if (files.isEmpty()) {
            return ImportRequestResult(emptyList())
        }

        val documents = withContext(Dispatchers.IO) {
            buildList {
                files.forEach { file ->
                    copyFileToLibrary(file)?.let(::add)
                }
            }
        }

        return ImportRequestResult(
            documents = documents,
            notice = if (documents.isEmpty()) "No readable PDFs were imported from the selected files." else null,
        )
    }

    override suspend fun importPdfFolder(): ImportRequestResult {
        val folder = withContext(Dispatchers.Swing) { selectFolder() }
            ?: return ImportRequestResult(emptyList())

        val documents = withContext(Dispatchers.IO) {
            buildList {
                folder.walkTopDown()
                    .filter { it.isFile && it.extension.equals("pdf", ignoreCase = true) }
                    .forEach { file ->
                        copyFileToLibrary(file)?.let(::add)
                    }
            }
        }

        return ImportRequestResult(
            documents = documents,
            notice = when {
                documents.isEmpty() -> "No PDF files were found in the selected folder."
                else -> null
            },
        )
    }

    private suspend fun copyFileToLibrary(file: File): ImportedPdfDescriptor? {
        if (!file.exists()) {
            return null
        }

        val safeStem = safeFileStem(file.name)
        val destination = File(
            scoresDirectory,
            "${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}_$safeStem.pdf",
        )

        Files.copy(
            file.toPath(),
            destination.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )

        val pageCount = renderer.inspect(destination.absolutePath)?.pageCount

        return ImportedPdfDescriptor(
            storedPath = destination.absolutePath,
            originalFileName = file.name,
            sourceUri = file.toURI().toString(),
            pageCount = pageCount,
            suggestedTitle = file.nameWithoutExtension,
        )
    }

    private fun selectFiles(): List<File> {
        val chooser = JFileChooser().apply {
            dialogTitle = "Import sheet music PDFs"
            isMultiSelectionEnabled = true
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = FileNameExtensionFilter("PDF files", "pdf")
        }

        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFiles.toList()
        } else {
            emptyList()
        }
    }

    private fun selectFolder(): File? {
        val chooser = JFileChooser().apply {
            dialogTitle = "Select a folder of sheet music PDFs"
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isMultiSelectionEnabled = false
        }

        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile
        } else {
            null
        }
    }
}

private class DesktopPdfPageRenderer : PdfPageRenderer {
    override suspend fun inspect(documentPath: String): PdfDocumentInfo? = withContext(Dispatchers.IO) {
        val file = File(documentPath)
        if (!file.exists()) {
            return@withContext null
        }

        Loader.loadPDF(file).use { document ->
            PdfDocumentInfo(pageCount = document.numberOfPages)
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

        Loader.loadPDF(file).use { document ->
            if (pageIndex !in 0 until document.numberOfPages) {
                return@withContext null
            }

            val renderer = PDFRenderer(document)
            val pageSize = document.getPage(pageIndex).mediaBox
            val scale = max(targetWidth, 1200).toFloat() / pageSize.width
            val image = renderer.renderImage(pageIndex, scale)

            RenderedPdfPage(
                pageIndex = pageIndex,
                pageCount = document.numberOfPages,
                aspectRatio = image.width.toFloat() / image.height.toFloat(),
                image = image.toComposeImageBitmap(),
            )
        }
    }
}

private class DesktopSyncHttpClient : SyncHttpClient {
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

private class DesktopManagedPdfStore(
    private val scoresDirectory: File,
) : ManagedPdfStore {
    override suspend fun savePdf(
        originalFileName: String,
        contents: ByteArray,
    ): String = withContext(Dispatchers.IO) {
        val safeStem = safeFileStem(originalFileName)
        val destination = File(
            scoresDirectory,
            "${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}_$safeStem.pdf",
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

private fun desktopAppRootDirectory(): File {
    val appData = System.getenv("APPDATA")
    val root = if (!appData.isNullOrBlank()) {
        File(appData, "Lunar")
    } else {
        File(System.getProperty("user.home"), ".lunar")
    }
    root.mkdirs()
    return root
}

private fun safeFileStem(fileName: String): String = fileName
    .substringBeforeLast('.')
    .replace(Regex("[^A-Za-z0-9_-]+"), "_")
    .trim('_')
    .ifEmpty { "score" }
