package com.ryanjames.lunar.ui

import com.ryanjames.lunar.createTestLunarAppState
import com.ryanjames.lunar.createTestPlatformRuntime
import com.ryanjames.lunar.settings.AppSettings
import com.ryanjames.lunar.settings.DefaultViewerChromeAutoHideSeconds
import com.ryanjames.lunar.settings.JsonAppSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SidebarAndLayoutSettingsTest {

    @Test
    fun enteringComposeCollapsesTheSidebarOnlyWhileThere() = runBlocking {
        val runtime = createTestPlatformRuntime()
        val appState = createTestLunarAppState(scope = this, runtime = runtime)
        val settings = runtime.settingsStore.settings.value

        assertFalse(appState.isSidebarCollapsed(settings))

        appState.selectSection(AppSection.COMPOSE)
        assertTrue(appState.isSidebarCollapsed(settings))

        appState.toggleSidebarCollapsed()
        assertFalse(appState.isSidebarCollapsed(settings))
        assertFalse(runtime.settingsStore.settings.value.sidebarCollapsed)

        appState.selectSection(AppSection.LIBRARY)
        assertNull(appState.temporarySidebarCollapsed)
        assertFalse(appState.isSidebarCollapsed(runtime.settingsStore.settings.value))
    }

    @Test
    fun startCollapsedSettingIsUsedOutsideCompose() = runBlocking {
        val collapsed = AppSettings(sidebarCollapsed = true)
        val appState = createTestLunarAppState(
            scope = this,
            runtime = createTestPlatformRuntime(initialSettings = collapsed),
        )

        assertTrue(appState.isSidebarCollapsed(collapsed))
        appState.selectSection(AppSection.SETTINGS)
        assertTrue(appState.isSidebarCollapsed(collapsed))
        assertFalse(appState.isSidebarCollapsed(collapsed.copy(sidebarCollapsed = false)))
    }

    @Test
    fun sidebarAndToolbarSettingsPersistThroughTheStore() = runBlocking {
        val fileSystem = FakeFileSystem()
        val path = "/lunar/app_settings.json".toPath()
        val store = JsonAppSettingsStore(fileSystem = fileSystem, settingsPath = path)
        store.initialize()
        assertEquals(DefaultViewerChromeAutoHideSeconds, store.settings.value.viewerChromeAutoHideSeconds)

        val runtime = createTestPlatformRuntime().copy(settingsStore = store)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val appState = createTestLunarAppState(scope = scope, runtime = runtime)
            appState.updateStartSidebarCollapsed(true)
            appState.updateViewerChromeAutoHideSeconds(null)
            withTimeout(5_000L) {
                while (
                    !store.settings.value.sidebarCollapsed ||
                    store.settings.value.viewerChromeAutoHideSeconds != null
                ) {
                    delay(10L)
                }
            }
        } finally {
            scope.cancel()
        }

        val reloaded = JsonAppSettingsStore(fileSystem = fileSystem, settingsPath = path)
        reloaded.initialize()
        assertTrue(reloaded.settings.value.sidebarCollapsed)
        assertNull(reloaded.settings.value.viewerChromeAutoHideSeconds)
        assertNull(reloaded.settings.value.viewerChromeAutoHideMillis)
    }

    @Test
    fun viewerToolbarDelayIsClampedToMillis() {
        assertEquals(5_000L, AppSettings(viewerChromeAutoHideSeconds = 5).viewerChromeAutoHideMillis)
        assertEquals(60_000L, AppSettings(viewerChromeAutoHideSeconds = 600).viewerChromeAutoHideMillis)
    }
}
