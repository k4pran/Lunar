package com.ryanjames.lunar.settings

import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import okio.buffer
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppSkinMigrationTest {

    private val settingsPath = "/lunar/app_settings.json".toPath()

    @Test
    fun everyLegacyThemeMapsToASkin() {
        val expected = mapOf(
            AppColorTheme.OCEAN to AppSkin.PAPER,
            AppColorTheme.FOREST to AppSkin.PAPER,
            AppColorTheme.SUNSET to AppSkin.PAPER,
            AppColorTheme.AURORA to AppSkin.PAPER,
            AppColorTheme.DARCULA to AppSkin.MOONLIGHT,
            AppColorTheme.MIDNIGHT to AppSkin.MOONLIGHT,
            AppColorTheme.EMBER to AppSkin.MOONLIGHT,
        )
        assertEquals(AppColorTheme.entries.toSet(), expected.keys)

        expected.forEach { (theme, skin) ->
            val normalized = AppSettings(theme = theme).normalize()
            assertEquals(skin, normalized.skin, "Theme $theme should migrate to $skin")
            assertEquals(skin, AppSettings(theme = theme).resolvedSkin)
        }
    }

    @Test
    fun explicitSkinIsNeverOverwrittenByLegacyTheme() {
        AppColorTheme.entries.forEach { theme ->
            AppSkin.entries.forEach { skin ->
                assertEquals(skin, AppSettings(theme = theme, skin = skin).normalize().skin)
                assertEquals(skin, AppSettings(theme = theme, skin = skin).resolvedSkin)
            }
        }
    }

    @Test
    fun legacySettingsFileMigratesAndSurvivesRoundTrip() = runBlocking {
        val fileSystem = FakeFileSystem()
        fileSystem.writeSettings(
            """
            {
              "defaultViewerPageMode": "TWO_PAGE",
              "theme": "MIDNIGHT",
              "refreshOnLaunch": false
            }
            """.trimIndent()
        )

        val store = JsonAppSettingsStore(fileSystem = fileSystem, settingsPath = settingsPath)
        store.initialize()
        assertEquals(AppSkin.MOONLIGHT, store.settings.value.skin)
        assertEquals(ViewerPageModePreference.TWO_PAGE, store.settings.value.defaultViewerPageMode)
        assertEquals(false, store.settings.value.refreshOnLaunch)

        store.updateSettings { it.copy(autoRefreshSchedule = AutoRefreshSchedule.HOURLY) }
        assertTrue(fileSystem.readSettings().contains("\"skin\": \"MOONLIGHT\""))

        val reloaded = JsonAppSettingsStore(fileSystem = fileSystem, settingsPath = settingsPath)
        reloaded.initialize()
        assertEquals(AppSkin.MOONLIGHT, reloaded.settings.value.skin)
        assertEquals(AppColorTheme.MIDNIGHT, reloaded.settings.value.theme)
        assertEquals(AutoRefreshSchedule.HOURLY, reloaded.settings.value.autoRefreshSchedule)
    }

    @Test
    fun explicitSkinInSettingsFileBeatsLegacyTheme() = runBlocking {
        val fileSystem = FakeFileSystem()
        fileSystem.writeSettings("""{ "theme": "DARCULA", "skin": "PAPER" }""")

        val store = JsonAppSettingsStore(fileSystem = fileSystem, settingsPath = settingsPath)
        store.initialize()
        assertEquals(AppSkin.PAPER, store.settings.value.skin)

        store.updateSettings { it.copy(skin = AppSkin.MOONLIGHT) }
        val reloaded = JsonAppSettingsStore(fileSystem = fileSystem, settingsPath = settingsPath)
        reloaded.initialize()
        assertEquals(AppSkin.MOONLIGHT, reloaded.settings.value.skin)
    }

    @Test
    fun resetRestoresPaperSkin() = runBlocking {
        val fileSystem = FakeFileSystem()
        val store = JsonAppSettingsStore(fileSystem = fileSystem, settingsPath = settingsPath)
        store.updateSettings { it.copy(skin = AppSkin.MOONLIGHT) }

        store.reset()

        assertEquals(AppSkin.PAPER, store.settings.value.skin)
        assertTrue(fileSystem.readSettings().contains("\"skin\": \"PAPER\""))
    }

    private fun FakeFileSystem.writeSettings(payload: String) {
        createDirectories(settingsPath.parent!!)
        sink(settingsPath).buffer().use { it.writeUtf8(payload) }
    }

    private fun FakeFileSystem.readSettings(): String =
        source(settingsPath).buffer().use { it.readUtf8() }
}
