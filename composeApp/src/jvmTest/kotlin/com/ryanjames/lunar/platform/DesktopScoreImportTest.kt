package com.ryanjames.lunar.platform

import org.apache.pdfbox.Loader
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

private fun File.sha256(): String = MessageDigest
    .getInstance("SHA-256")
    .digest(readBytes())
    .joinToString(separator = "") { byte ->
        byte.toUByte().toString(16).padStart(2, '0')
    }
