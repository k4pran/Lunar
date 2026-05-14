package com.ryanjames.lunar.platform

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.ryanjames.lunar.composition.JsonCompositionDraftStore
import com.ryanjames.lunar.library.data.DefaultSheetMusicRepository
import com.ryanjames.lunar.library.data.JsonLibraryStorage
import com.ryanjames.lunar.library.data.JsonScoreMetadataStorage
import com.ryanjames.lunar.library.data.OkioStoredDocumentCleaner
import com.ryanjames.lunar.library.data.JsonSourceRegistry
import com.ryanjames.lunar.library.data.StoredDocumentFingerprinter
import com.ryanjames.lunar.library.model.ImportedPdfDescriptor
import com.ryanjames.lunar.library.model.PdfDocumentReference
import com.ryanjames.lunar.library.model.ScoreMetadata
import com.ryanjames.lunar.library.model.ScoreMetadataComposer
import com.ryanjames.lunar.library.model.ScoreMetadataSource
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
import kotlinx.serialization.json.Json
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
import java.io.IOException
import java.net.URI
import java.nio.file.Files
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
    val scoreMetadataDirectoryPath = remember(appRoot) {
        File(appRoot, "metadata/scores").absolutePath.toPath()
    }
    val sourcesPath = remember(appRoot) {
        File(appRoot, "sources/sources.json").absolutePath.toPath()
    }
    val settingsPath = remember(appRoot) {
        File(appRoot, "settings/app_settings.json").absolutePath.toPath()
    }
    val compositionDraftsPath = remember(appRoot) {
        File(appRoot, "compositions/drafts.json").absolutePath.toPath()
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
            scoreMetadataStorage = JsonScoreMetadataStorage(
                fileSystem = FileSystem.SYSTEM,
                metadataDirectory = scoreMetadataDirectoryPath,
            ),
            storedDocumentCleaner = OkioStoredDocumentCleaner(FileSystem.SYSTEM),
            storedDocumentFingerprinter = DesktopStoredDocumentFingerprinter(),
        )
    }
    val renderer = remember { DesktopPdfPageRenderer() }
    val lilyPondLiveRenderer = remember(appRoot) {
        DesktopLilyPondLiveRenderer(
            previewDirectory = File(appRoot, "lilypond-live").apply { mkdirs() },
        )
    }
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
    val sourceRegistry = remember(sourcesPath) {
        JsonSourceRegistry(
            fileSystem = FileSystem.SYSTEM,
            sourcesPath = sourcesPath,
        )
    }
    val compositionStore = remember(compositionDraftsPath) {
        JsonCompositionDraftStore(
            fileSystem = FileSystem.SYSTEM,
            draftsPath = compositionDraftsPath,
        )
    }
    val compositionPdfImporter = remember(scoresDirectory) {
        DesktopCompositionPdfImporter(scoresDirectory = scoresDirectory)
    }
    val syncManager = remember(repository, renderer, pdfStore, syncHttpClient, sourceRegistry, googleDriveOAuth) {
        LibrarySyncManager(
            repository = repository,
            httpClient = syncHttpClient,
            pdfStore = pdfStore,
            renderer = renderer,
            sourceRegistry = sourceRegistry,
            googleDriveOAuth = googleDriveOAuth,
        )
    }
    val importer = remember(scoresDirectory) {
        DesktopPdfImporter(
            scoresDirectory = scoresDirectory,
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
        lilyPondLiveRenderer,
        pdfExporter,
        songbookBuilder,
        coverImagePicker,
        syncManager,
        sourceRegistry,
        compositionStore,
        compositionPdfImporter,
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
                lilyPondImportSupported = true,
                lilyPondLiveViewingSupported = true,
                museScoreImportSupported = true,
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
            lilyPondLiveRenderer = lilyPondLiveRenderer,
            pdfExporter = pdfExporter,
            songbookBuilder = songbookBuilder,
            coverImagePicker = coverImagePicker,
            syncManager = syncManager,
            sourceRegistry = sourceRegistry,
            compositionStore = compositionStore,
            compositionPdfImporter = compositionPdfImporter,
            settingsStore = settingsStore,
            googleDriveOAuth = googleDriveOAuth,
            cacheInspector = cacheInspector,
        )
    }
}

private class DesktopPdfImporter(
    private val scoresDirectory: File,
    private val lilyPondCompiler: DesktopLilyPondCompiler = SystemDesktopLilyPondCompiler,
    private val museScoreConverter: DesktopMuseScoreConverter = SystemDesktopMuseScoreConverter,
) : PdfImporter {
    private val importerState = MutableStateFlow(
        ImporterState(
            statusMessage = "Desktop imports use the local file system directly. PDFs, LilyPond files, MuseScore files, and images are converted into managed PDFs for viewing, so no persisted SAF permissions are needed.",
        )
    )

    override val state: StateFlow<ImporterState> = importerState.asStateFlow()

    override suspend fun importPdfFiles(): ImportRequestResult {
        val files = withContext(Dispatchers.Swing) { selectFiles() }
        if (files.isEmpty()) {
            return ImportRequestResult(emptyList())
        }

        val documents = withContext(Dispatchers.IO) {
            importDesktopScoreFiles(
                files = files,
                scoresDirectory = scoresDirectory,
                lilyPondCompiler = lilyPondCompiler,
                museScoreConverter = museScoreConverter,
            )
        }

        return ImportRequestResult(
            documents = documents,
            notice = if (documents.isEmpty()) {
                "No readable PDF, LilyPond, MuseScore, or image files were imported from the selected files."
            } else {
                null
            },
        )
    }

    override suspend fun importPdfFolder(): ImportRequestResult {
        val folder = withContext(Dispatchers.Swing) { selectFolder() }
            ?: return ImportRequestResult(emptyList())

        val documents = withContext(Dispatchers.IO) {
            importDesktopScoreFiles(
                files = folder.walkTopDown()
                    .filter(::isSupportedDesktopImportFile)
                    .toList(),
                scoresDirectory = scoresDirectory,
                lilyPondCompiler = lilyPondCompiler,
                museScoreConverter = museScoreConverter,
            )
        }

        return ImportRequestResult(
            documents = documents,
            notice = when {
                documents.isEmpty() -> "No PDF, LilyPond, MuseScore, or image files were found in the selected folder."
                else -> null
            },
        )
    }

    override suspend fun importDroppedPaths(paths: List<String>): ImportRequestResult {
        if (paths.isEmpty()) {
            return ImportRequestResult(emptyList())
        }

        val documents = withContext(Dispatchers.IO) {
            importDesktopDroppedScorePaths(
                pathOrUris = paths,
                scoresDirectory = scoresDirectory,
                lilyPondCompiler = lilyPondCompiler,
                museScoreConverter = museScoreConverter,
            )
        }

        return ImportRequestResult(
            documents = documents,
            notice = when {
                documents.isEmpty() -> "No PDF, LilyPond, MuseScore, or image files were found in the dropped files."
                else -> null
            },
        )
    }

    private fun selectFiles(): List<File> {
        val chooser = JFileChooser().apply {
            dialogTitle = "Import sheet music files"
            isMultiSelectionEnabled = true
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = FileNameExtensionFilter("Score files", *DesktopChooserScoreExtensions.toTypedArray())
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

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.externalScoreDropTarget(
    enabled: Boolean,
    onDroppedPaths: (List<String>) -> Unit,
): Modifier {
    if (!enabled) {
        return this
    }

    return dragAndDropTarget(
        shouldStartDragAndDrop = { event -> event.dragData() is DragData.FilesList },
        target = object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val files = (event.dragData() as? DragData.FilesList)
                    ?.readFiles()
                    .orEmpty()
                if (files.isEmpty()) {
                    return false
                }

                onDroppedPaths(files)
                return true
            }
        },
    )
}

internal fun importDesktopScoreFile(
    file: File,
    scoresDirectory: File,
    scoreMetadata: ScoreMetadata? = null,
    lilyPondCompiler: DesktopLilyPondCompiler = SystemDesktopLilyPondCompiler,
    museScoreConverter: DesktopMuseScoreConverter = SystemDesktopMuseScoreConverter,
): ImportedPdfDescriptor? {
    if (!isSupportedDesktopScoreFile(file)) {
        return null
    }

    val destination = nextManagedPdfFile(
        directory = scoresDirectory,
        baseName = file.name,
    )
    val contentFingerprint = fingerprintFile(file)
    val normalizedExtension = file.extension.lowercase()

    val pageCount = when {
        normalizedExtension == "pdf" -> {
            copyFileToManagedPdf(source = file, destination = destination)
            loadPdfPageCount(destination)
        }

        normalizedExtension in DesktopSupportedImageExtensions -> {
            writeImageFileToManagedPdf(source = file, destination = destination) ?: return null
            1
        }

        normalizedExtension in DesktopSupportedLilyPondExtensions -> {
            lilyPondCompiler.renderToPdf(source = file, destination = destination)
            loadPdfPageCount(destination)
                ?: throw IllegalStateException("LilyPond rendered ${file.name}, but Lunar could not read the generated PDF.")
        }

        normalizedExtension in DesktopSupportedMuseScoreExtensions -> {
            museScoreConverter.renderToPdf(source = file, destination = destination)
            loadPdfPageCount(destination)
                ?: throw IllegalStateException("MuseScore converted ${file.name}, but Lunar could not read the generated PDF.")
        }

        else -> return null
    }

    return ImportedPdfDescriptor(
        storedPath = destination.absolutePath,
        originalFileName = when {
            normalizedExtension == "pdf" -> file.name
            normalizedExtension in DesktopSupportedImageExtensions -> safeExportFileName(file.name)
            normalizedExtension in DesktopSupportedLilyPondExtensions -> file.name
            normalizedExtension in DesktopSupportedMuseScoreExtensions -> file.name
            else -> safeExportFileName(file.name)
        },
        sourceUri = file.toURI().toString(),
        contentFingerprint = contentFingerprint,
        pageCount = pageCount,
        suggestedTitle = file.nameWithoutExtension,
        scoreMetadata = scoreMetadata,
    )
}

internal fun importDesktopScoreFiles(
    files: List<File>,
    scoresDirectory: File,
    lilyPondCompiler: DesktopLilyPondCompiler = SystemDesktopLilyPondCompiler,
    museScoreConverter: DesktopMuseScoreConverter = SystemDesktopMuseScoreConverter,
): List<ImportedPdfDescriptor> {
    val scoreFiles = files.filter(::isSupportedDesktopScoreFile)
    if (scoreFiles.isEmpty()) {
        return emptyList()
    }

    val metadataByScoreFile = resolveDesktopMetadataSidecars(scoreFiles)
    return scoreFiles.mapNotNull { file ->
        importDesktopScoreFile(
            file = file,
            scoresDirectory = scoresDirectory,
            scoreMetadata = metadataByScoreFile[file],
            lilyPondCompiler = lilyPondCompiler,
            museScoreConverter = museScoreConverter,
        )
    }
}

internal fun importDesktopDroppedScorePaths(
    pathOrUris: List<String>,
    scoresDirectory: File,
    lilyPondCompiler: DesktopLilyPondCompiler = SystemDesktopLilyPondCompiler,
    museScoreConverter: DesktopMuseScoreConverter = SystemDesktopMuseScoreConverter,
): List<ImportedPdfDescriptor> {
    val importFiles = pathOrUris
        .mapNotNull(::desktopFileFromPathOrUri)
        .flatMap { file ->
            when {
                file.isDirectory -> file.walkTopDown()
                    .filter(::isSupportedDesktopImportFile)
                    .toList()

                isSupportedDesktopImportFile(file) -> listOf(file)
                else -> emptyList()
            }
        }
        .distinctBy(File::stableImportKey)

    return importDesktopScoreFiles(
        files = importFiles,
        scoresDirectory = scoresDirectory,
        lilyPondCompiler = lilyPondCompiler,
        museScoreConverter = museScoreConverter,
    )
}

internal fun interface DesktopLilyPondCompiler {
    fun renderToPdf(source: File, destination: File)
}

internal object SystemDesktopLilyPondCompiler : DesktopLilyPondCompiler {
    override fun renderToPdf(source: File, destination: File) {
        val workingDirectory = source.parentFile ?: source.absoluteFile.parentFile ?: File(".")
        val temporaryDirectory = Files.createTempDirectory(
            destination.parentFile.toPath(),
            "lilypond-render-",
        ).toFile()

        try {
            val outputBase = File(temporaryDirectory, destination.nameWithoutExtension)
            val process = try {
                ProcessBuilder(
                    listOf(
                        "lilypond",
                        "--pdf",
                        "-dno-point-and-click",
                        "--output",
                        outputBase.absolutePath,
                        source.name,
                    )
                )
                    .directory(workingDirectory)
                    .redirectErrorStream(true)
                    .start()
            } catch (error: IOException) {
                throw IllegalStateException(
                    "LilyPond imports require the `lilypond` command to be installed and available on PATH.",
                    error,
                )
            }

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            val renderedPdf = File("${outputBase.absolutePath}.pdf")
            if (exitCode != 0 || !renderedPdf.exists()) {
                val detail = summarizeLilyPondOutput(output)
                val baseMessage = if (exitCode != 0) {
                    "LilyPond could not render ${source.name}."
                } else {
                    "LilyPond did not produce a PDF for ${source.name}."
                }
                throw IllegalStateException(
                    detail?.let { "$baseMessage $it" } ?: baseMessage
                )
            }

            renderedPdf.copyTo(destination, overwrite = true)
        } finally {
            temporaryDirectory.deleteRecursively()
        }
    }
}

internal fun interface DesktopMuseScoreConverter {
    fun renderToPdf(source: File, destination: File)
}

internal object SystemDesktopMuseScoreConverter : DesktopMuseScoreConverter {
    override fun renderToPdf(source: File, destination: File) {
        val temporaryDirectory = Files.createTempDirectory(
            destination.parentFile.toPath(),
            "musescore-render-",
        ).toFile()

        try {
            val renderedPdf = File(temporaryDirectory, destination.name)
            val results = museScoreCommandCandidates().map { command ->
                runMuseScoreCommand(
                    command = command,
                    source = source,
                    renderedPdf = renderedPdf,
                )
            }
            if (results.none { it.succeeded && renderedPdf.exists() }) {
                val detail = results
                    .firstOrNull { it.detail.isNotBlank() }
                    ?.detail
                throw IllegalStateException(
                    detail?.let { "MuseScore could not convert ${source.name}. $it" }
                        ?: "MuseScore imports require the `musescore`, `mscore`, `MuseScore4`, or `MuseScore3` command to be installed and available on PATH.",
                )
            }

            renderedPdf.copyTo(destination, overwrite = true)
        } finally {
            temporaryDirectory.deleteRecursively()
        }
    }

    private fun runMuseScoreCommand(
        command: String,
        source: File,
        renderedPdf: File,
    ): MuseScoreCommandResult {
        if (isAbsoluteMuseScorePath(command) && !File(command).exists()) {
            return MuseScoreCommandResult(succeeded = false)
        }

        return try {
            if (renderedPdf.exists()) {
                renderedPdf.delete()
            }
            val process = ProcessBuilder(
                listOf(
                    command,
                    "-o",
                    renderedPdf.absolutePath,
                    source.absolutePath,
                )
            )
                .directory(source.parentFile ?: source.absoluteFile.parentFile ?: File("."))
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            MuseScoreCommandResult(
                succeeded = exitCode == 0,
                detail = summarizeMuseScoreOutput(output),
            )
        } catch (error: IOException) {
            MuseScoreCommandResult(
                succeeded = false,
                detail = if (isAbsoluteMuseScorePath(command)) error.message.orEmpty() else "",
            )
        }
    }
}

private data class MuseScoreCommandResult(
    val succeeded: Boolean,
    val detail: String = "",
)

private fun resolveDesktopMetadataSidecars(
    scoreFiles: List<File>,
): Map<File, ScoreMetadata> {
    val metadataCache = mutableMapOf<File, ScoreMetadata?>()
    val metadataByScoreFile = mutableMapOf<File, ScoreMetadata>()

    scoreFiles.groupBy { it.parentFile?.absolutePath.orEmpty() }
        .values
        .forEach { filesInDirectory ->
            val parentDirectory = filesInDirectory.firstOrNull()?.parentFile
            val siblingFiles = parentDirectory
                ?.listFiles()
                ?.toList()
                .orEmpty()
            val scoreSiblings = siblingFiles.filter(::isSupportedDesktopScoreFile)
            val metadataSiblings = siblingFiles.filter(::isSupportedDesktopMetadataFile)
            val metadataByStem = metadataSiblings.associateBy { sibling ->
                sibling.nameWithoutExtension.lowercase()
            }
            val singleMetadata = metadataSiblings.singleOrNull()
                ?.takeIf { scoreSiblings.size == 1 }

            filesInDirectory.forEach { scoreFile ->
                val matchedMetadataFile = metadataByStem[scoreFile.nameWithoutExtension.lowercase()]
                    ?: singleMetadata
                val metadata = matchedMetadataFile?.let { file ->
                    metadataCache.getOrPut(file) {
                        parseDesktopScoreMetadata(file)
                    }
                }
                if (metadata != null) {
                    metadataByScoreFile[scoreFile] = metadata
                }
            }
        }

    return metadataByScoreFile
}

private fun parseDesktopScoreMetadata(file: File): ScoreMetadata? = runCatching {
    desktopScoreMetadataJson.decodeFromString(
        ScoreMetadata.serializer(),
        file.readText(),
    )
}.getOrNull()

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

internal class DesktopLilyPondLiveRenderer(
    private val previewDirectory: File,
    private val lilyPondCompiler: DesktopLilyPondCompiler = SystemDesktopLilyPondCompiler,
) : LilyPondLiveRenderer {
    override suspend fun loadSource(document: PdfDocumentReference): LilyPondSourceSnapshot? =
        withContext(Dispatchers.IO) {
            val sourceFile = lilyPondSourceFile(document) ?: return@withContext null
            val sourceText = sourceFile.readText()
            LilyPondSourceSnapshot(
                sourceText = sourceText,
                revision = lilyPondSourceRevision(sourceFile, sourceText),
                displayName = sourceFile.name,
                sourcePath = sourceFile.absolutePath,
            )
        }

    override suspend fun renderSource(source: LilyPondSourceSnapshot): LilyPondLiveRenderResult =
        withContext(Dispatchers.IO) {
            if (source.sourceText.isBlank()) {
                throw IllegalStateException("The LilyPond source is empty.")
            }

            previewDirectory.mkdirs()
            val sourceKey = listOf(
                source.displayName,
                source.sourcePath.orEmpty(),
                source.revision,
                source.sourceText,
            ).joinToString(separator = "\n").encodeToByteArray().sha256()
            val previewStem = "live_${safeFileStem(source.displayName)}_${sourceKey.take(12)}"
            val destination = File(previewDirectory, "$previewStem.pdf")
            loadPdfPageCount(destination)?.let { pageCount ->
                return@withContext LilyPondLiveRenderResult(
                    documentPath = destination.absolutePath,
                    pageCount = pageCount,
                )
            }

            val sourceFile = liveRenderSourceFile(
                source = source,
                previewStem = previewStem,
            )
            try {
                lilyPondCompiler.renderToPdf(source = sourceFile.file, destination = destination)
            } finally {
                if (sourceFile.temporary) {
                    sourceFile.file.delete()
                }
            }
            val pageCount = loadPdfPageCount(destination)
                ?: throw IllegalStateException(
                    "LilyPond rendered ${source.displayName}, but Lunar could not read the live preview PDF."
                )

            LilyPondLiveRenderResult(
                documentPath = destination.absolutePath,
                pageCount = pageCount,
            )
        }

    private fun liveRenderSourceFile(
        source: LilyPondSourceSnapshot,
        previewStem: String,
    ): LiveLilyPondSourceFile {
        val fileBackedSource = source.sourcePath
            ?.let(::File)
            ?.takeIf { file -> file.exists() && isDesktopLilyPondFileName(file.name) }
            ?.takeIf { file -> runCatching { file.readText() == source.sourceText }.getOrDefault(false) }
        if (fileBackedSource != null) {
            return LiveLilyPondSourceFile(file = fileBackedSource, temporary = false)
        }

        val scratchDirectory = source.sourcePath
            ?.let(::File)
            ?.parentFile
            ?.takeIf { directory -> directory.isDirectory && directory.canWrite() }
            ?: previewDirectory
        return File(scratchDirectory, ".$previewStem.ly").also { file ->
            file.writeText(source.sourceText)
        }.let { file -> LiveLilyPondSourceFile(file = file, temporary = true) }
    }
}

private data class LiveLilyPondSourceFile(
    val file: File,
    val temporary: Boolean,
)

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

private fun isSupportedDesktopImportFile(file: File): Boolean =
    isSupportedDesktopScoreFile(file) || isSupportedDesktopMetadataFile(file)

private fun isSupportedDesktopScoreFile(file: File): Boolean =
    file.isFile && file.extension.lowercase() in DesktopSupportedScoreExtensions

private fun isSupportedDesktopMetadataFile(file: File): Boolean =
    file.isFile && file.extension.equals("json", ignoreCase = true)

private fun desktopFileFromPathOrUri(pathOrUri: String): File? {
    val value = pathOrUri.trim().trim('"')
    if (value.isBlank()) {
        return null
    }

    return runCatching {
        if (value.startsWith("file:", ignoreCase = true)) {
            File(URI(value))
        } else {
            File(value)
        }
    }.getOrNull()
        ?.takeIf { it.exists() }
}

private fun lilyPondSourceFile(document: PdfDocumentReference): File? {
    if (!isDesktopLilyPondFileName(document.originalFileName)) {
        return null
    }

    return document.sourceUri
        ?.let(::desktopFileFromPathOrUri)
        ?.takeIf { file -> file.isFile && isDesktopLilyPondFileName(file.name) }
}

private fun isDesktopLilyPondFileName(fileName: String): Boolean =
    fileName.substringAfterLast('.', "").lowercase() in DesktopSupportedLilyPondExtensions

private fun lilyPondSourceRevision(
    sourceFile: File,
    sourceText: String,
): String {
    val path = runCatching { sourceFile.canonicalPath }.getOrDefault(sourceFile.absolutePath)
    val contentHash = sourceText.encodeToByteArray().sha256()
    return "$path:${sourceFile.lastModified()}:${sourceFile.length()}:$contentHash"
}

private fun File.stableImportKey(): String =
    runCatching { canonicalFile.absolutePath }.getOrDefault(absolutePath)

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

private fun summarizeLilyPondOutput(output: String): String? {
    val lines = output.lines()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .takeLast(4)
    return lines.joinToString(" ").takeIf(String::isNotBlank)
}

private fun summarizeMuseScoreOutput(output: String): String =
    output.lines()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .takeLast(4)
        .joinToString(" ")
        .trim()

private fun museScoreCommandCandidates(): List<String> = buildList {
    sequenceOf(
        "LUNAR_MUSESCORE_COMMAND",
        "MUSESCORE_COMMAND",
        "MUSESCORE_PATH",
    )
        .mapNotNull { name -> System.getenv(name)?.trim()?.trim('"')?.takeIf(String::isNotBlank) }
        .forEach(::add)

    add("musescore")
    add("mscore")
    add("MuseScore4")
    add("MuseScore3")
    add("MuseScore4.exe")
    add("MuseScore3.exe")

    commonMuseScoreInstallPaths().forEach(::add)
}.distinct()

private fun commonMuseScoreInstallPaths(): List<String> {
    val programFiles = listOfNotNull(
        System.getenv("ProgramFiles"),
        System.getenv("ProgramFiles(x86)"),
        System.getenv("LOCALAPPDATA")?.let { "$it\\Programs" },
    ).distinct()

    return programFiles.flatMap { root ->
        listOf(
            "$root\\MuseScore 4\\bin\\MuseScore4.exe",
            "$root\\MuseScore 3\\bin\\MuseScore3.exe",
            "$root\\MuseScore 4\\MuseScore4.exe",
            "$root\\MuseScore 3\\MuseScore3.exe",
        )
    }
}

private fun isAbsoluteMuseScorePath(command: String): Boolean =
    File(command).isAbsolute || Regex("^[A-Za-z]:[\\\\/].+").matches(command)

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

private class DesktopCompositionPdfImporter(
    private val scoresDirectory: File,
) : CompositionPdfImporter {
    override suspend fun importRenderedComposition(
        request: CompositionPdfImportRequest,
    ): ImportedPdfDescriptor = withContext(Dispatchers.IO) {
        val source = File(request.documentPath)
        if (!source.exists()) {
            throw IOException("Rendered composition PDF was not found.")
        }

        val originalFileName = safeExportFileName("${request.title}.pdf")
        val destination = nextManagedPdfFile(scoresDirectory, originalFileName)
        destination.parentFile?.mkdirs()
        source.copyTo(destination, overwrite = false)
        val pageCount = request.pageCount ?: loadPdfPageCount(destination)

        ImportedPdfDescriptor(
            storedPath = destination.absolutePath,
            originalFileName = originalFileName,
            sourceUri = "lunar-compose:${request.draftId}",
            contentFingerprint = fingerprintFile(destination),
            pageCount = pageCount,
            suggestedTitle = request.title,
            scoreMetadata = ScoreMetadata(
                title = request.title,
                composer = ScoreMetadataComposer(name = request.composer),
                pageCount = pageCount,
                source = ScoreMetadataSource(
                    filename = originalFileName,
                    fileType = "pdf",
                    url = "lunar-compose:${request.draftId}",
                ),
            ),
        )
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
private val DesktopSupportedImageExtensions = setOf("png", "jpg", "jpeg")
private val DesktopSupportedLilyPondExtensions = setOf("ly", "ily", "lyi")
private val DesktopSupportedMuseScoreExtensions = setOf("mscz", "mscx")
private val DesktopSupportedScoreExtensions =
    setOf("pdf") + DesktopSupportedImageExtensions + DesktopSupportedLilyPondExtensions + DesktopSupportedMuseScoreExtensions
private val DesktopChooserScoreExtensions =
    listOf("pdf", "ly", "ily", "lyi", "mscz", "mscx", "png", "jpg", "jpeg")
private val desktopScoreMetadataJson = Json { ignoreUnknownKeys = true }

private fun ByteArray.toHexString(): String = joinToString(separator = "") { byte ->
    byte.toUByte().toString(16).padStart(2, '0')
}

private fun ByteArray.sha256(): String = MessageDigest
    .getInstance("SHA-256")
    .digest(this)
    .toHexString()
