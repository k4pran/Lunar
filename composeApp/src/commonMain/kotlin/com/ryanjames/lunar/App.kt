package com.ryanjames.lunar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ryanjames.lunar.platform.rememberPlatformRuntime
import com.ryanjames.lunar.ui.AppSection
import com.ryanjames.lunar.ui.FullscreenViewerScreen
import com.ryanjames.lunar.ui.ImportScreen
import com.ryanjames.lunar.ui.LibraryScreen
import com.ryanjames.lunar.ui.ViewerScreen
import com.ryanjames.lunar.ui.rememberLunarAppState
import kotlinx.coroutines.delay

@Composable
fun App() {
    val runtime = rememberPlatformRuntime()
    val appState = rememberLunarAppState(runtime)
    val snapshot by runtime.repository.library.collectAsState()
    val importerState by runtime.importer.state.collectAsState()
    val syncState by runtime.syncManager.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val previewItem = snapshot.items.firstOrNull { it.id == appState.previewItemId }
    val fullscreenItem = snapshot.items.firstOrNull { it.id == appState.fullscreenItemId }

    LaunchedEffect(runtime.repository, runtime.syncManager) {
        runtime.repository.initialize()
        runtime.syncManager.initialize()
        runtime.syncManager.refresh(force = false)

        while (true) {
            delay(runtime.syncManager.automaticRefreshIntervalMillis())
            runtime.syncManager.refresh(force = false)
        }
    }

    LaunchedEffect(appState.bannerMessage) {
        val message = appState.bannerMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        appState.clearBanner()
    }

    LunarTheme {
        if (fullscreenItem != null) {
            FullscreenViewerScreen(
                runtime = runtime,
                item = fullscreenItem,
                onBack = appState::closeFullscreen,
                onToggleFavorite = { appState.toggleFavorite(fullscreenItem.id) },
                onPageChanged = { pageIndex ->
                    appState.updateViewerProgress(fullscreenItem.id, pageIndex)
                },
                onPageCountResolved = { pageCount ->
                    appState.updateViewerPageCount(fullscreenItem.id, pageCount)
                },
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background,
                                Color(0xFFCCDDE9),
                            )
                        )
                    ),
            ) {
                Scaffold(
                    containerColor = Color.Transparent,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        BottomNavigationPanel(
                            selectedSection = appState.selectedSection,
                            onSelectSection = appState::selectSection,
                        )
                    },
                ) { innerPadding ->
                    when (appState.selectedSection) {
                        AppSection.IMPORT -> ImportScreen(
                            runtime = runtime,
                            importerState = importerState,
                            syncState = syncState,
                            libraryCount = snapshot.items.size,
                            appState = appState,
                            modifier = Modifier.padding(innerPadding),
                        )

                        AppSection.LIBRARY -> LibraryScreen(
                            runtime = runtime,
                            snapshot = snapshot,
                            appState = appState,
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }

                if (previewItem != null) {
                    Dialog(
                        onDismissRequest = appState::closePreview,
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth(0.96f)
                                .fillMaxHeight(0.92f),
                            shape = MaterialTheme.shapes.extraLarge,
                            tonalElevation = 10.dp,
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.weight(1f)) {
                                    ViewerScreen(
                                        runtime = runtime,
                                        item = previewItem,
                                        onBack = appState::closePreview,
                                        onToggleFavorite = { appState.toggleFavorite(previewItem.id) },
                                        onPageChanged = { pageIndex ->
                                            appState.updateViewerProgress(previewItem.id, pageIndex)
                                        },
                                        onPageCountResolved = { pageCount ->
                                            appState.updateViewerPageCount(previewItem.id, pageCount)
                                        },
                                        backButtonLabel = "Close",
                                    )
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    shape = MaterialTheme.shapes.extraLarge,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        androidx.compose.material3.OutlinedButton(
                                            onClick = appState::closePreview,
                                        ) {
                                            Text("Close")
                                        }
                                        androidx.compose.material3.Button(
                                            onClick = { appState.openFullscreen(previewItem.id) },
                                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF1F4F6B),
                                            ),
                                        ) {
                                            Text("View Fullscreen", color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNavigationPanel(
    selectedSection: AppSection,
    onSelectSection: (AppSection) -> Unit,
) {
    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BottomNavItem(
                title = "Sources",
                selected = selectedSection == AppSection.IMPORT,
                onClick = { onSelectSection(AppSection.IMPORT) },
            )
            BottomNavItem(
                title = "Library",
                selected = selectedSection == AppSection.LIBRARY,
                onClick = { onSelectSection(AppSection.LIBRARY) },
            )
        }
    }
}

@Composable
private fun RowScope.BottomNavItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }

    Surface(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick),
        color = containerColor,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun LunarTheme(content: @Composable () -> Unit) {
    val colorScheme = lightColorScheme(
        primary = Color(0xFF1F4F6B),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFA7C6ED),
        onPrimaryContainer = Color(0xFF0D2B36),
        secondary = Color(0xFF4D7C99),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFF6A9BD1),
        onSecondaryContainer = Color(0xFF0D2B36),
        tertiary = Color(0xFF176A8A),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFB8D8EC),
        onTertiaryContainer = Color(0xFF0D2B36),
        background = Color(0xFFE8F2FA),
        onBackground = Color(0xFF0D2B36),
        surface = Color(0xFFF0F7FC),
        onSurface = Color(0xFF1A3E4F),
        surfaceVariant = Color(0xFFCCDDE9),
        onSurfaceVariant = Color(0xFF2A6E7C),
        outline = Color(0xFF4C8FD5),
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography.copy(
            displayLarge = TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = 57.sp,
                lineHeight = 64.sp,
            ),
            headlineMedium = TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = 28.sp,
                lineHeight = 34.sp,
            ),
            titleLarge = TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = 22.sp,
                lineHeight = 28.sp,
            ),
            bodyLarge = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.SansSerif,
            ),
            bodyMedium = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.SansSerif,
            ),
            labelLarge = MaterialTheme.typography.labelLarge.copy(
                fontFamily = FontFamily.SansSerif,
            ),
        ),
        content = content,
    )
}
