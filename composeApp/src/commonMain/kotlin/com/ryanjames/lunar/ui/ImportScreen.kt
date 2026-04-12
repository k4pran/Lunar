package com.ryanjames.lunar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryanjames.lunar.library.data.CloudGoogleDriveSource
import com.ryanjames.lunar.library.data.CloudLibrarySource
import com.ryanjames.lunar.library.data.CloudSupabaseSource
import com.ryanjames.lunar.library.data.LibrarySource
import com.ryanjames.lunar.library.data.LocalFilesSource
import com.ryanjames.lunar.library.data.LocalFolderSource
import com.ryanjames.lunar.platform.ImporterState
import com.ryanjames.lunar.platform.LibraryCacheSnapshot
import com.ryanjames.lunar.platform.PlatformRuntime
import com.ryanjames.lunar.sync.CloudSyncState

@Composable
fun ImportScreen(
    runtime: PlatformRuntime,
    importerState: ImporterState,
    syncState: CloudSyncState,
    libraryCount: Int,
    appState: LunarAppState,
    modifier: Modifier = Modifier,
) {
    val sources by runtime.sourceRegistry.sources.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    var showLocalDialog by remember { mutableStateOf(false) }
    var showCloudDialog by remember { mutableStateOf(false) }
    var cacheSnapshot by remember { mutableStateOf<LibraryCacheSnapshot?>(null) }
    var cacheInspectionError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(libraryCount, sources.size, syncState.lastRefreshEpochMillis) {
        runCatching { runtime.cacheInspector.inspect() }
            .onSuccess {
                cacheSnapshot = it
                cacheInspectionError = null
            }
            .onFailure { error ->
                cacheInspectionError = error.message ?: "Cache details are unavailable."
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Hero header
        Surface(
            color = Color.Transparent,
            tonalElevation = 0.dp,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF1F4F6B),
                                Color(0xFF176A8A),
                            )
                        ),
                        shape = MaterialTheme.shapes.extraLarge,
                    )
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Sources",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = Color.White,
                )
                Text(
                    text = "Add local or cloud sources to build your library. Each source contributes scores that are combined into a single browsable collection.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.82f),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ImportSummaryPill("$libraryCount scores in library")
                    ImportSummaryPill("${sources.size} source${if (sources.size == 1) "" else "s"}")
                }
                importerState.statusMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }
            }
        }

        // Add source buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { showLocalDialog = true },
                modifier = Modifier.weight(1f),
                enabled = runtime.capabilities.fileImportSupported && !appState.importInProgress,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1F4F6B),
                    disabledContainerColor = Color(0xFF4D7C99).copy(alpha = 0.38f),
                ),
            ) {
                Text(
                    "Add local source",
                    color = Color.White,
                )
            }
            Button(
                onClick = { showCloudDialog = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF176A8A),
                    disabledContainerColor = Color(0xFF4D7C99).copy(alpha = 0.38f),
                ),
            ) {
                Text(
                    "Add cloud source",
                    color = Color.White,
                )
            }
        }

        cacheSnapshot?.let { snapshot ->
            Surface(
                color = Color(0xFF1F4F6B).copy(alpha = 0.08f),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "On-device cache",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = Color(0xFF1A3E4F),
                    )
                    Text(
                        text = "Lunar uses a local-first filesystem cache. Library metadata stays on disk, and imported or synced PDFs are kept in managed storage for offline browsing and viewing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ImportSummaryPillDark("${snapshot.cachedPdfCount} cached PDF${if (snapshot.cachedPdfCount == 1) "" else "s"}")
                        ImportSummaryPillDark(snapshot.cachedPdfBytesLabel)
                    }
                    Text(
                        text = buildString {
                            append(if (snapshot.offlineLibraryReady) "Library metadata cached locally" else "Library metadata not cached yet")
                            append(" • ")
                            append(if (snapshot.offlineViewerReady) "Offline viewing ready for cached scores" else "Add or sync scores to enable offline viewing")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Storage: ${snapshot.storageLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    snapshot.cacheRootPath?.takeIf(String::isNotBlank)?.let { cachePath ->
                        Text(
                            text = cachePath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "Unchanged cloud PDFs are reused from the local cache instead of being downloaded again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = {
                            clipboardManager.setText(
                                AnnotatedString(buildCacheDetails(snapshot))
                            )
                        },
                    ) {
                        Text("Copy cache details")
                    }
                }
            }
        }

        cacheInspectionError?.takeIf(String::isNotBlank)?.let { error ->
            Surface(
                color = Color(0xFFB71C1C).copy(alpha = 0.08f),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB71C1C),
                )
            }
        }

        // Configured sources list
        if (sources.isEmpty()) {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "No sources configured",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = Color(0xFF1A3E4F),
                    )
                    Text(
                        text = "Add a local or cloud source above to start building your library.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            sources.forEach { source ->
                SourceCard(
                    source = source,
                    itemCount = runtime.repository.itemCountForSource(source.id),
                    onRemove = { appState.removeSource(source.id) },
                    onRefresh = if (source is CloudLibrarySource) {
                        { appState.refreshCloudSource(source) }
                    } else null,
                    isSyncing = syncState.isRefreshing,
                )
            }
        }

        // Sync status
        if (sources.any { it is CloudLibrarySource }) {
            val hasErrors = syncState.syncErrors.isNotEmpty()
            val statusBg = when {
                hasErrors -> Color(0xFFB71C1C).copy(alpha = 0.08f)
                syncState.isRefreshing -> Color(0xFF1F4F6B).copy(alpha = 0.08f)
                !syncState.lastMessage.isNullOrBlank() -> Color(0xFF176A8A).copy(alpha = 0.10f)
                else -> Color(0xFFA7C6ED).copy(alpha = 0.18f)
            }
            Surface(color = statusBg, shape = MaterialTheme.shapes.medium) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = when {
                            syncState.isRefreshing -> syncState.currentStep ?: "Refreshing..."
                            hasErrors -> "Sync completed with errors"
                            !syncState.lastMessage.isNullOrBlank() -> syncState.lastMessage.orEmpty()
                            else -> "Cloud sources are configured. Press refresh to sync."
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (hasErrors) Color(0xFFB71C1C) else Color(0xFF1A3E4F),
                    )
                    if (syncState.activeSourceLabels.isNotEmpty()) {
                        Text(
                            text = "Active sources: ${syncState.activeSourceLabels.joinToString()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (
                        syncState.isRefreshing ||
                        syncState.discoveredResources > 0 ||
                        syncState.processedResources > 0 ||
                        syncState.visitedFolders > 0
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ImportSummaryPill("Folders ${syncState.visitedFolders}")
                            ImportSummaryPill("Found ${syncState.discoveredResources}")
                            ImportSummaryPill("Ready ${syncState.processedResources}")
                            if (syncState.skippedResources > 0) {
                                ImportSummaryPill("Skipped ${syncState.skippedResources}")
                            }
                        }
                    }
                    if (hasErrors) {
                        syncState.syncErrors.forEach { error ->
                            Text(error, style = MaterialTheme.typography.bodySmall, color = Color(0xFFB71C1C))
                        }
                    }
                    if (syncState.activityLog.isNotEmpty()) {
                        Surface(
                            color = Color.White.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "Activity log",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color(0xFF1A3E4F),
                                )
                                syncState.activityLog.takeLast(12).forEach { entry ->
                                    Text(
                                        text = entry,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    if (hasErrors || syncState.activityLog.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                clipboardManager.setText(
                                    AnnotatedString(buildSyncDetails(syncState))
                                )
                            },
                        ) {
                            Text(if (hasErrors) "Copy sync details" else "Copy activity log")
                        }
                    }
                }
            }

            Button(
                onClick = appState::refreshAllCloudSources,
                enabled = !syncState.isRefreshing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1F4F6B),
                    disabledContainerColor = Color(0xFF4D7C99).copy(alpha = 0.38f),
                ),
            ) {
                Text(
                    if (syncState.isRefreshing) "Refreshing..." else "Refresh all cloud sources",
                    color = if (syncState.isRefreshing) Color.White.copy(alpha = 0.5f) else Color.White,
                )
            }
        }

        if (appState.importActivityLog.isNotEmpty() || appState.importInProgress) {
            Surface(
                color = Color(0xFF176A8A).copy(alpha = 0.08f),
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = appState.importActivityStep ?: if (appState.importInProgress) "Importing..." else "Import activity",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFF1A3E4F),
                    )
                    if (
                        appState.importInProgress ||
                        appState.importDiscoveredResources > 0 ||
                        appState.importProcessedResources > 0 ||
                        appState.importSkippedResources > 0
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ImportSummaryPill("Found ${appState.importDiscoveredResources}")
                            ImportSummaryPill("Imported ${appState.importProcessedResources}")
                            if (appState.importSkippedResources > 0) {
                                ImportSummaryPill("Skipped ${appState.importSkippedResources}")
                            }
                        }
                    }
                    Surface(
                        color = Color.White.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "Import log",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFF1A3E4F),
                            )
                            appState.importActivityLog.takeLast(12).forEach { entry ->
                                Text(
                                    text = entry,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = {
                            clipboardManager.setText(
                                AnnotatedString(buildImportDetails(appState))
                            )
                        },
                    ) {
                        Text("Copy import activity")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        OutlinedButton(
            onClick = { appState.selectSection(AppSection.LIBRARY) },
            modifier = Modifier.widthIn(max = 240.dp),
        ) {
            Text("Go to library")
        }
    }

    // Dialogs
    if (showLocalDialog) {
        AddLocalSourceDialog(
            folderImportSupported = runtime.capabilities.folderImportSupported,
            onDismiss = { showLocalDialog = false },
            onConfirm = { type, label ->
                showLocalDialog = false
                when (type) {
                    LocalSourceType.FILES -> appState.addLocalFilesSource(label)
                    LocalSourceType.FOLDER -> appState.addLocalFolderSource(label)
                }
            },
        )
    }

    if (showCloudDialog) {
        AddCloudSourceDialog(
            googleDriveOAuthSupported = runtime.googleDriveOAuth.isSupported,
            onDismiss = { showCloudDialog = false },
            onConfirm = { source ->
                showCloudDialog = false
                appState.addCloudSource(source)
            },
        )
    }
}

@Composable
private fun SourceCard(
    source: LibrarySource,
    itemCount: Int,
    onRemove: () -> Unit,
    onRefresh: (() -> Unit)?,
    isSyncing: Boolean,
) {
    val typeLabel = when (source) {
        is LocalFilesSource -> "Local files"
        is LocalFolderSource -> "Local folder"
        is CloudSupabaseSource -> "Supabase"
        is CloudGoogleDriveSource -> "Google Drive"
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = source.label,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = Color(0xFF1A3E4F),
                )
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF176A8A),
                )
            }

            Text(
                text = "$itemCount score${if (itemCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (source) {
                is CloudSupabaseSource -> {
                    if (source.settings.projectUrl.isNotBlank()) {
                        Text(
                            text = "${source.settings.projectUrl} / ${source.settings.bucketName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is CloudGoogleDriveSource -> {
                    Text(
                        text = "${source.settings.roots.size} Drive root${if (source.settings.roots.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (source.settings.refreshToken.isNotBlank()) {
                            "OAuth connected"
                        } else {
                            "Desktop sign-in required"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    source.settings.roots.take(2).forEach { root ->
                        Text(
                            text = root.label.ifBlank { root.folderId },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> Unit
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onRefresh != null) {
                    OutlinedButton(
                        onClick = onRefresh,
                        enabled = !isSyncing,
                    ) {
                        Text(if (isSyncing) "Syncing..." else "Refresh")
                    }
                }
                TextButton(
                    onClick = onRemove,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFFB71C1C),
                    ),
                ) {
                    Text("Remove")
                }
            }
        }
    }
}

@Composable
private fun ImportSummaryPill(text: String) {
    Surface(
        color = Color.White.copy(alpha = 0.22f),
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
        )
    }
}

@Composable
private fun ImportSummaryPillDark(text: String) {
    Surface(
        color = Color(0xFF1F4F6B).copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF1A3E4F),
        )
    }
}

private fun buildSyncDetails(syncState: CloudSyncState): String = buildString {
    syncState.currentStep?.takeIf(String::isNotBlank)?.let { step ->
        appendLine("Step: $step")
    }
    syncState.lastMessage?.takeIf(String::isNotBlank)?.let { message ->
        appendLine("Summary: $message")
    }
    appendLine("Visited folders: ${syncState.visitedFolders}")
    appendLine("Found resources: ${syncState.discoveredResources}")
    appendLine("Processed resources: ${syncState.processedResources}")
    appendLine("Skipped resources: ${syncState.skippedResources}")
    syncState.activeSourceLabels.takeIf { it.isNotEmpty() }?.let { labels ->
        appendLine("Active sources: ${labels.joinToString()}")
    }
    if (syncState.syncErrors.isNotEmpty()) {
        if (isNotEmpty()) appendLine()
        appendLine("Errors:")
        syncState.syncErrors.forEach { error ->
            appendLine(error)
        }
    }
    if (syncState.activityLog.isNotEmpty()) {
        if (isNotEmpty()) appendLine()
        appendLine("Activity log:")
        syncState.activityLog.forEach { entry ->
            appendLine(entry)
        }
    }
}

private fun buildCacheDetails(cacheSnapshot: LibraryCacheSnapshot): String = buildString {
    appendLine("Storage: ${cacheSnapshot.storageLabel}")
    cacheSnapshot.cacheRootPath?.takeIf(String::isNotBlank)?.let { cachePath ->
        appendLine("Cache root: $cachePath")
    }
    appendLine("Library metadata cached: ${cacheSnapshot.metadataCached}")
    appendLine("Source registry cached: ${cacheSnapshot.sourceRegistryCached}")
    appendLine("Cached PDFs: ${cacheSnapshot.cachedPdfCount}")
    appendLine("Cached PDF size: ${cacheSnapshot.cachedPdfBytesLabel}")
    appendLine("Offline library ready: ${cacheSnapshot.offlineLibraryReady}")
    appendLine("Offline viewer ready: ${cacheSnapshot.offlineViewerReady}")
}

private fun buildImportDetails(appState: LunarAppState): String = buildString {
    appState.importActivityStep?.takeIf(String::isNotBlank)?.let { step ->
        appendLine("Step: $step")
    }
    appendLine("Found resources: ${appState.importDiscoveredResources}")
    appendLine("Imported resources: ${appState.importProcessedResources}")
    appendLine("Skipped resources: ${appState.importSkippedResources}")
    if (appState.importActivityLog.isNotEmpty()) {
        appendLine()
        appendLine("Import log:")
        appState.importActivityLog.forEach { entry ->
            appendLine(entry)
        }
    }
}
