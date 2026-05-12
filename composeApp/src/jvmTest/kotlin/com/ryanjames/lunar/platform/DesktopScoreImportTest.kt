package com.ryanjames.lunar.platform

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopScoreImportTest {

    @Test
    fun importDesktopScoreFileConvertsPngIntoSinglePagePdf() {
        val root = Files.createTempDirectory("lunar-desktop-import-test").toFile()
        try {
            val scoresDirectory = File(root, "scores").apply { mkdirs() }
            val sourceFile = File(root, "moon_river.png")
            writeTestImage(sourceFile, width = 180, height = 320)

            val descriptor = importDesktopScoreFile(
                file = sourceFile,
                scoresDirectory = scoresDirectory,
            )

            assertNotNull(descriptor)
            assertEquals("moon_river.pdf", descriptor.originalFileName)
            assertEquals("moon_river", descriptor.suggestedTitle)
            assertEquals(1, descriptor.pageCount)
            assertEquals(sourceFile.sha256(), descriptor.contentFingerprint)
            assertTrue(descriptor.storedPath.endsWith(".pdf"))

            val generatedPdf = File(descriptor.storedPath)
            assertTrue(generatedPdf.exists())

            Loader.loadPDF(generatedPdf).use { document ->
                assertEquals(1, document.numberOfPages)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun importDesktopScoreFilesMatchesSingleJsonToSingleScoreFolder() {
        val root = Files.createTempDirectory("lunar-desktop-import-metadata-test").toFile()
        try {
            val scoresDirectory = File(root, "scores").apply { mkdirs() }
            val sourceFile = File(root, "bell_piece.png")
            writeTestImage(sourceFile, width = 180, height = 320)
            File(root, "metadata.json").writeText(
                """
                {
                  "schemaId": "score-metadata",
                  "schemaVersion": "1.0",
                  "id": "bell-piece",
                  "title": "The Bell",
                  "composer": {
                    "name": "Holger Stief"
                  }
                }
                """.trimIndent()
            )

            val descriptor = importDesktopScoreFiles(
                files = listOf(sourceFile),
                scoresDirectory = scoresDirectory,
            ).single()

            assertEquals("The Bell", descriptor.scoreMetadata?.title)
            assertEquals("Holger Stief", descriptor.scoreMetadata?.composer?.name)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun importDesktopScoreFilesUsesSameStemJsonWhenFolderHasMultipleScores() {
        val root = Files.createTempDirectory("lunar-desktop-import-same-stem-test").toFile()
        try {
            val scoresDirectory = File(root, "scores").apply { mkdirs() }
            val preludeFile = File(root, "prelude_in_c.png")
            val nocturneFile = File(root, "nocturne.png")
            writeTestImage(preludeFile, width = 180, height = 320)
            writeTestImage(nocturneFile, width = 180, height = 320)
            File(root, "prelude_in_c.json").writeText(
                """
                {
                  "schemaId": "score-metadata",
                  "schemaVersion": "1.0",
                  "id": "prelude-in-c",
                  "title": "Prelude in C",
                  "composer": {
                    "name": "J. S. Bach"
                  }
                }
                """.trimIndent()
            )

            val descriptors = importDesktopScoreFiles(
                files = listOf(preludeFile, nocturneFile),
                scoresDirectory = scoresDirectory,
            ).associateBy { it.originalFileName }

            assertEquals("Prelude in C", descriptors.getValue("prelude_in_c.pdf").scoreMetadata?.title)
            assertEquals(null, descriptors.getValue("nocturne.pdf").scoreMetadata)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun importDesktopDroppedScorePathsExpandsFoldersAndFileUris() {
        val root = Files.createTempDirectory("lunar-desktop-drop-import-test").toFile()
        try {
            val scoresDirectory = File(root, "scores").apply { mkdirs() }
            val dropFolder = File(root, "drop").apply { mkdirs() }
            val nestedFolder = File(dropFolder, "nested").apply { mkdirs() }
            val imageFile = File(nestedFolder, "autumn_leaves.png")
            writeTestImage(imageFile, width = 180, height = 320)
            File(nestedFolder, "autumn_leaves.json").writeText(
                """
                {
                  "schemaId": "score-metadata",
                  "schemaVersion": "1.0",
                  "id": "autumn-leaves",
                  "title": "Autumn Leaves",
                  "composer": {
                    "name": "Joseph Kosma"
                  }
                }
                """.trimIndent()
            )
            val directPdf = File(root, "blue_bossa.pdf")
            writeTestPdf(directPdf)

            val descriptors = importDesktopDroppedScorePaths(
                pathOrUris = listOf(dropFolder.toURI().toString(), directPdf.absolutePath),
                scoresDirectory = scoresDirectory,
            ).associateBy { it.suggestedTitle }

            assertEquals(setOf("autumn_leaves", "blue_bossa"), descriptors.keys)
            assertEquals("Autumn Leaves", descriptors.getValue("autumn_leaves").scoreMetadata?.title)
            assertEquals("Joseph Kosma", descriptors.getValue("autumn_leaves").scoreMetadata?.composer?.name)
            assertEquals(1, descriptors.getValue("blue_bossa").pageCount)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun importDesktopScoreFileRendersLilyPondIntoManagedPdf() {
        val root = Files.createTempDirectory("lunar-desktop-import-lilypond-test").toFile()
        try {
            val scoresDirectory = File(root, "scores").apply { mkdirs() }
            val sourceFile = File(root, "moon_river.ly").apply {
                writeText(
                    """
                    \version "2.24.0"
                    { c'4 d' e' f' }
                    """.trimIndent()
                )
            }

            val descriptor = importDesktopScoreFile(
                file = sourceFile,
                scoresDirectory = scoresDirectory,
                lilyPondCompiler = DesktopLilyPondCompiler { _, destination ->
                    writeTestPdf(destination)
                },
            )

            assertNotNull(descriptor)
            assertEquals("moon_river.ly", descriptor.originalFileName)
            assertEquals("moon_river", descriptor.suggestedTitle)
            assertEquals(1, descriptor.pageCount)
            assertEquals(sourceFile.sha256(), descriptor.contentFingerprint)
            assertTrue(descriptor.storedPath.endsWith(".pdf"))

            val generatedPdf = File(descriptor.storedPath)
            assertTrue(generatedPdf.exists())

            Loader.loadPDF(generatedPdf).use { document ->
                assertEquals(1, document.numberOfPages)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun importDesktopScoreFileConvertsMuseScoreIntoManagedPdf() {
        val root = Files.createTempDirectory("lunar-desktop-import-musescore-test").toFile()
        try {
            val scoresDirectory = File(root, "scores").apply { mkdirs() }
            val sourceFile = File(root, "blue_bossa.mscz").apply {
                writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
            }

            val descriptor = importDesktopScoreFile(
                file = sourceFile,
                scoresDirectory = scoresDirectory,
                museScoreConverter = DesktopMuseScoreConverter { _, destination ->
                    writeTestPdf(destination)
                },
            )

            assertNotNull(descriptor)
            assertEquals("blue_bossa.mscz", descriptor.originalFileName)
            assertEquals("blue_bossa", descriptor.suggestedTitle)
            assertEquals(1, descriptor.pageCount)
            assertEquals(sourceFile.sha256(), descriptor.contentFingerprint)
            assertTrue(descriptor.storedPath.endsWith(".pdf"))

            val generatedPdf = File(descriptor.storedPath)
            assertTrue(generatedPdf.exists())

            Loader.loadPDF(generatedPdf).use { document ->
                assertEquals(1, document.numberOfPages)
            }
        } finally {
            root.deleteRecursively()
        }
    }
}

private fun writeTestImage(
    destination: File,
    width: Int,
    height: Int,
) {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val graphics = image.createGraphics()
    try {
        graphics.color = Color.WHITE
        graphics.fillRect(0, 0, width, height)
        graphics.color = Color.BLACK
        graphics.drawRect(12, 12, width - 24, height - 24)
        graphics.drawLine(20, height / 3, width - 20, height / 3)
        graphics.drawLine(20, height / 2, width - 20, height / 2)
    } finally {
        graphics.dispose()
    }
    ImageIO.write(image, "png", destination)
}

private fun writeTestPdf(destination: File) {
    PDDocument().use { document ->
        document.addPage(PDPage())
        document.save(destination)
    }
}

private fun File.sha256(): String = MessageDigest
    .getInstance("SHA-256")
    .digest(readBytes())
    .joinToString(separator = "") { byte ->
        byte.toUByte().toString(16).padStart(2, '0')
    }
