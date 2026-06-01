package com.ryanjames.lunar

import com.ryanjames.lunar.composition.CompositionNotation
import com.ryanjames.lunar.composition.JsonCompositionDraftStore
import com.ryanjames.lunar.composition.createCompositionDraft
import com.ryanjames.lunar.composition.withUpdatedContents
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompositionDraftStoreJvmTest {
    @Test
    fun jsonCompositionDraftStorePersistsDrafts() = runBlocking {
        val tempDirectory = Files.createTempDirectory("lunar-composition-drafts")
        try {
            val draftsPath = tempDirectory.resolve("drafts.json").toString().toPath()
            val draft = createCompositionDraft(
                title = "Prelude",
                composer = "Composer",
                sourceText = "\\version \"2.24.0\"",
                notation = CompositionNotation.LILYPOND,
            )
            val store = JsonCompositionDraftStore(
                fileSystem = FileSystem.SYSTEM,
                draftsPath = draftsPath,
            )

            store.initialize()
            store.upsertDraft(draft)

            val reloadedStore = JsonCompositionDraftStore(
                fileSystem = FileSystem.SYSTEM,
                draftsPath = draftsPath,
            )
            reloadedStore.initialize()
            assertEquals(listOf(draft), reloadedStore.drafts.value)

            val updated = draft.withUpdatedContents(
                title = "Prelude Revised",
                composer = "Composer",
                sourceText = "{ c'2 g' }",
            )
            reloadedStore.upsertDraft(updated)
            assertEquals(updated, reloadedStore.getDraft(draft.id))

            reloadedStore.deleteDraft(draft.id)
            assertTrue(reloadedStore.drafts.value.isEmpty())
        } finally {
            tempDirectory.toFile().deleteRecursively()
        }
    }
}
