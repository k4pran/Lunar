package com.ryanjames.lunar.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.ryanjames.lunar.library.data.DefaultSheetMusicRepository
import com.ryanjames.lunar.library.data.JsonLibraryStorage
import com.ryanjames.lunar.library.data.OkioStoredDocumentCleaner
import com.ryanjames.lunar.library.data.JsonSourceRegistry
import com.ryanjames.lunar.library.data.StoredDocumentFingerprinter
import com.ryanjames.lunar.library.model.ImportedPdfDescriptor
import com.ryanjames.lunar.library.model.PdfDocumentReference
import com.ryanjames.lunar.settings.AppSettings
import com.ryanjames.lunar.settings.JsonAppSettingsStore
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
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

@Composable
actual fun rememberPlatformRuntime(): PlatformRuntime {
    val appRoot = remember { desktopAppRootDirectory() }
    val metadataPath = remember(appRoot) {
        File(appRoot, "metadata/library.json").absolutePath.toPath()
    }
    val sourcesPath = remember(appRoot) {
        File(appRoot, "sources/sources.json").absolutePath.toPath()
    }
    val settingsPath = remember(appRoot) {
        File(appRoot, "settings/app_settings.json").absolutePath.toPath()
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
            storedDocumentFingerprinter = DesktopStoredDocumentFingerprinter(),
        )
    }
    val renderer = remember { DesktopPdfPageRenderer() }
    val pdfExporter = remember { DesktopPdfDocumentExporter() }
    val songbookBuilder = remember(scoresDirectory) {
        DesktopSongbookPdfBuilder(scoresDirectory = scoresDirectory)
    }
    val coverImagePicker = remember { DesktopCoverImagePicker() }
    val pdfStore = remember(scoresDirectory) {
        DesktopManagedPdfStore(scoresDirectory = scoresDirectory)
    }
    val settingsStore = remember(settingsPath) {
        JsonAppSettingsStore(
            fileSystem = FileSystem.SYSTEM,
            settingsPath = settingsPath,
        )
    }
    val syncHttpClient = remember(settingsStore) {
        DesktopSyncHttpClient(
            settingsProvider = { settingsStore.settings.value }
        )
    }
    val googleDriveOAuth = remember(syncHttpClient) {
        DesktopGoogleDriveOAuthCoordinator(httpClient = syncHttpClient)
    }
    val syncManager = remember(repository, renderer, pdfStore, syncHttpClient) {
        LibrarySyncManager(
            repository = repository,
            httpClient = syncHttpClient,
            pdfStore = pdfStore,
            renderer = renderer,
        )
    }
    val importer = remember(scoresDirectory) {
        DesktopPdfImporter(
            scoresDirectory = scoresDirectory,
        )
    }
    val sourceRegistry = remember(sourcesPath) {
        JsonSourceRegistry(
            fileSystem = FileSystem.SYSTEM,
            sourcesPath = sourcesPath,
        )
    }
    val cacheInspector = remember(appRoot, metadataPath, sourcesPath, scoresDirectory) {
        DesktopLibraryCacheInspector(
            appRoot = appRoot,
            metadataFile = File(appRoot, "metadata/library.json"),
            sourcesFile = File(appRoot, "sources/sources.json"),
            scoresDirectory = scoresDirectory,
        )
    }

    return remember(
        repository,
        importer,
        renderer,
        pdfExporter,
        songbookBuilder,
        coverImagePicker,
        syncManager,
        sourceRegistry,
        settingsStore,
        googleDriveOAuth,
        appRoot,
        cacheInspector,
    ) {
        PlatformRuntime(
            platformName = System.getProperty("os.name") ?: "Desktop JVM",
            capabilities = PlatformCapabilities(
                fileImportSupported = true,
                folderImportSupported = true,
                localImageImportSupported = true,
                permissionTrackingSupported = false,
                inAppViewingSupported = true,
                scoreDownloadSupported = true,
                songbookCreationSupported = true,
                songbookCoverImageSupported = true,
                statusLine = "Library stored in ${appRoot.name}",
            ),
            repository = repository,
            importer = importer,
            renderer = renderer,
            pdfExporter = pdfExporter,
            songbookBuilder = songbookBuilder,
            coverImagePicker = coverImagePicker,
            syncManager = syncManager,
            sourceRegistry = sourceRegistry,
            settingsStore = settingsStore,
            googleDriveOAuth = googleDriveOAuth,
            cacheInspector = cacheInspector,
        )
    }
}

private class DesktopPdfImporter(
    private val scoresDirectory: File,
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
                    importDesktopScoreFile(file = file, scoresDirectory = scoresDirectory)?.let(::add)
                }
            }
        }

        return ImportRequestResult(
            documents = documents,
            notice = if (documents.isEmpty()) {
                "No readable PDF or image files were imported from the selected files."
            } else {
                null
            },
        )
    }

    override suspend fun importPdfFolder(): ImportRequestResult {
        val folder = withContext(Dispatchers.Swing) { selectFolder() }
            ?: return ImportRequestResult(emptyList())

        val documents = withContext(Dispatchers.IO) {
            buildList {
                folder.walkTopDown()
                    .filter(::isSupportedDesktopScoreFile)
                    .forEach { file ->
                        importDesktopScoreFile(file = file, scoresDirectory = scoresDirectory)?.let(::add)
                    }
            }
        }

        return ImportRequestResult(
            documents = documents,
            notice = when {
                documents.isEmpty() -> "No PDF or image files were found in the selected folder."
                else -> null
            },
        )
    }

    private fun selectFiles(): List<File> {
        val chooser = JFileChooser().apply {
            dialogTitle = "Import sheet music files"
            isMultiSelectionEnabled = true
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = FileNameExtensionFilter("Score files", "pdf", "png", "jpg", "jpeg")
        }

        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFiles.toList()
        } else {
            emptyList()
        }
    }

    private fun selectFolder(): File? {
        val chooser = JFileChooser().apply {
            dialogTitle = "Select a folder of sheet music files"
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

internal fun importDesktopScoreFile(
    file: File,
    scoresDirectory: File,
): ImportedPdfDescriptor? {
    if (!isSupportedDesktopScoreFile(file)) {
        return null
    }

    val destination = nextManagedPdfFile(
        directory = scoresDirectory,
        baseName = file.name,
    )
    val contentFingerprint = fingerprintFile(file)

    val pageCount = if (file.extension.equals("pdf", ignoreCase = true)) {
        copyFileToManagedPdf(source = file, destination = destination)
        loadPdfPageCount(destination)
    } else {
        writeImageFileToManagedPdf(source = file, destination = destination) ?: return null
        1
    }

    return ImportedPdfDescriptor(
        storedPath = destination.absolutePath,
        originalFileName = if (file.extension.equals("pdf", ignoreCase = true)) {
            file.name
        } else {
            safeExportFileName(file.name)
        },
        sourceUri = file.toURI().toString(),
        contentFingerprint = contentFingerprint,
        pageCount = pageCount,
        suggestedTitle = file.nameWithoutExtension,
    )
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

private class DesktopPdfDocumentExporter : PdfDocumentExporter {
    override suspend fun export(
        documentPath: String,
        suggestedFileName: String,
    ): String = withContext(Dispatchers.IO) {
        val source = File(documentPath)
        if (!source.exists()) {
            throw IllegalStateException("The local PDF copy is unavailable.")
        }

        val downloadsDirectory = desktopDownloadsDirectory()
        val destination = nextAvailableExportFile(
            directory = downloadsDirectory,
            suggestedFileName = suggestedFileName,
        )
        source.copyTo(destination, overwrite = false)

        "${downloadsDirectory.name}/${destination.name}"
    }
}

private class DesktopSongbookPdfBuilder(
    private val scoresDirectory: File,
) : SongbookPdfBuilder {
    override suspend fun buildSongbook(
        songbookName: String,
        appendedDocumentPaths: List<String>,
        existingSongbookPath: String?,
        coverImage: SelectedCoverImage?,
    ): SongbookBuildResult = withContext(Dispatchers.IO) {
        val normalizedName = songbookName.trim().ifEmpty { "Songbook" }
        val sourcePaths = appendedDocumentPaths
            .map(String::trim)
            .filter(String::isNotEmpty)
        if (existingSongbookPath.isNullOrBlank() && sourcePaths.isEmpty()) {
            throw IllegalStateException("Select at least one score to build a songbook.")
        }

        val mergedDocument = PDDocument()
        try {
            coverImage?.let { addCoverPage(mergedDocument, it) }
            existingSongbookPath
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { appendPdf(mergedDocument, it) }
            sourcePaths.forEach { path ->
                appendPdf(mergedDocument, path)
            }

            if (mergedDocument.numberOfPages == 0) {
                throw IllegalStateException("The songbook PDF is empty.")
            }

            val bytes = ByteArrayOutputStream().use { output ->
                mergedDocument.save(output)
                output.toByteArray()
            }
            val fingerprint = bytes.sha256()
            val destination = nextManagedPdfFile(
                directory = scoresDirectory,
                baseName = normalizedName,
            )
            destination.outputStream().use { output ->
                output.write(bytes)
            }

            SongbookBuildResult(
                document = PdfDocumentReference(
                    storedPath = destination.absolutePath,
                    originalFileName = safeExportFileName(normalizedName),
                    contentFingerprint = fingerprint,
                ),
                pageCount = mergedDocument.numberOfPages,
            )
        } finally {
            mergedDocument.close()
        }
    }

    private fun appendPdf(target: PDDocument, documentPath: String) {
        val source = File(documentPath)
        if (!source.exists()) {
            throw IllegalStateException("A source PDF is unavailable: ${source.name}")
        }

        Loader.loadPDF(source).use { incoming ->
            incoming.pages.forEach { page ->
                target.importPage(page)
            }
        }
    }

    private fun addCoverPage(
        document: PDDocument,
        coverImage: SelectedCoverImage,
    ) {
        val bufferedImage = ByteArrayInputStream(coverImage.bytes).use { input ->
            ImageIO.read(input)
        } ?: throw IllegalStateException("The selected cover image could not be read.")
        val page = PDPage(PDRectangle.LETTER)
        document.addPage(page)

        val pageWidth = page.mediaBox.width
        val pageHeight = page.mediaBox.height
        val margin = 24f
        val availableWidth = pageWidth - margin * 2
        val availableHeight = pageHeight - margin * 2
        val scale = min(
            availableWidth / bufferedImage.width.coerceAtLeast(1).toFloat(),
            availableHeight / bufferedImage.height.coerceAtLeast(1).toFloat(),
        )
        val drawWidth = bufferedImage.width * scale
        val drawHeight = bufferedImage.height * scale
        val startX = (pageWidth - drawWidth) / 2f
        val startY = (pageHeight - drawHeight) / 2f
        val image = LosslessFactory.createFromImage(document, bufferedImage)

        PDPageContentStream(document, page).use { contentStream ->
            contentStream.drawImage(image, startX, startY, drawWidth, drawHeight)
        }
    }
}

private class DesktopCoverImagePicker : CoverImagePicker {
    override suspend fun pickCoverImage(): SelectedCoverImage? {
        val selectedFile = withContext(Dispatchers.Swing) { selectCoverImageFile() } ?: return null
        return withContext(Dispatchers.IO) {
            SelectedCoverImage(
                displayName = selectedFile.name,
                bytes = selectedFile.readBytes(),
            )
        }
    }

    private fun selectCoverImageFile(): File? {
        val chooser = JFileChooser().apply {
            dialogTitle = "Choose a songbook cover image"
            isMultiSelectionEnabled = false
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = FileNameExtensionFilter("Image files", "png", "jpg", "jpeg")
        }

        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile
        } else {
            null
        }
    }
}

private class DesktopSyncHttpClient(
    private val settingsProvider: () -> AppSettings,
) : SyncHttpClient {
    override suspend fun getText(url: String, headers: Map<String, String>): String = withContext(Dispatchers.IO) {
        val settings = settingsProvider()
        val connection = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
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
        val connection = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
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
        val connection = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
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
        val connection = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
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

private class DesktopStoredDocumentFingerprinter : StoredDocumentFingerprinter {
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

private class DesktopLibraryCacheInspector(
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
            storageLabel = "Desktop filesystem cache",
            cacheRootPath = appRoot.absolutePath,
            metadataCached = metadataFile.exists(),
            sourceRegistryCached = sourcesFile.exists(),
            cachedPdfCount = cachedPdfFiles.size,
            cachedPdfBytes = cachedPdfFiles.sumOf(File::length),
        )
    }
}

private fun isSupportedDesktopScoreFile(file: File): Boolean =
    file.isFile && file.extension.lowercase() in DesktopSupportedScoreExtensions

private fun copyFileToManagedPdf(
    source: File,
    destination: File,
) {
    source.inputStream().use { input ->
        destination.outputStream().use { output ->
            val buffer = ByteArray(COPY_BUFFER_SIZE_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
            }
        }
    }
}

private fun writeImageFileToManagedPdf(
    source: File,
    destination: File,
): File? {
    val bufferedImage = ImageIO.read(source) ?: return null
    val imageWidth = bufferedImage.width.coerceAtLeast(1).toFloat()
    val imageHeight = bufferedImage.height.coerceAtLeast(1).toFloat()
    val scale = 1000f / max(imageWidth, imageHeight)
    val pageWidth = imageWidth * scale
    val pageHeight = imageHeight * scale

    val document = PDDocument()
    try {
        val page = PDPage(PDRectangle(pageWidth, pageHeight))
        document.addPage(page)
        val image = LosslessFactory.createFromImage(document, bufferedImage)
        PDPageContentStream(document, page).use { contentStream ->
            contentStream.drawImage(image, 0f, 0f, pageWidth, pageHeight)
        }
        document.save(destination)
        return destination
    } finally {
        document.close()
    }
}

private fun fingerprintFile(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(COPY_BUFFER_SIZE_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().toHexString()
}

private fun loadPdfPageCount(file: File): Int? = runCatching {
    Loader.loadPDF(file).use { document -> document.numberOfPages }
}.getOrNull()

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

private fun desktopDownloadsDirectory(): File {
    val preferred = File(System.getProperty("user.home"), "Downloads")
    if (preferred.exists() || preferred.mkdirs()) {
        return preferred
    }

    return File(desktopAppRootDirectory(), "exports").apply { mkdirs() }
}

private fun nextAvailableExportFile(
    directory: File,
    suggestedFileName: String,
): File {
    val normalizedName = safeExportFileName(suggestedFileName)
    val stem = normalizedName.substringBeforeLast('.')
    val extension = normalizedName.substringAfterLast('.', "")
    var candidate = File(directory, normalizedName)
    var suffix = 2

    while (candidate.exists()) {
        val nextName = if (extension.isBlank()) {
            "${stem}_$suffix"
        } else {
            "${stem}_$suffix.$extension"
        }
        candidate = File(directory, nextName)
        suffix += 1
    }

    return candidate
}

private fun nextManagedPdfFile(
    directory: File,
    baseName: String,
): File {
    val safeStem = safeFileStem(baseName)
    return File(
        directory,
        "${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}_$safeStem.pdf",
    )
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

private fun safeExportFileName(fileName: String): String {
    val originalStem = fileName
        .substringBeforeLast('.')
        .trim()
        .ifEmpty { "score" }
    val sanitizedStem = originalStem
        .replace(Regex("[\\\\/:*?\"<>|]+"), "_")
        .trim()
        .ifEmpty { "score" }
    return "$sanitizedStem.pdf"
}

private const val COPY_BUFFER_SIZE_BYTES = 8_192
private val DesktopSupportedScoreExtensions = setOf("pdf", "png", "jpg", "jpeg")

private fun ByteArray.toHexString(): String = joinToString(separator = "") { byte ->
    byte.toUByte().toString(16).padStart(2, '0')
}

private fun ByteArray.sha256(): String = MessageDigest
    .getInstance("SHA-256")
    .digest(this)
    .toHexString()
