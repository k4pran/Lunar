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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

    var showLocalDialog by remember { mutableStateOf(false) }
    var showCloudDialog by remember { mutableStateOf(false) }

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
                            syncState.isRefreshing -> "Refreshing..."
                            hasErrors -> "Sync completed with errors"
                            !syncState.lastMessage.isNullOrBlank() -> syncState.lastMessage.orEmpty()
                            else -> "Cloud sources are configured. Press refresh to sync."
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (hasErrors) Color(0xFFB71C1C) else Color(0xFF1A3E4F),
                    )
                    if (hasErrors) {
                        syncState.syncErrors.forEach { error ->
                            Text(error, style = MaterialTheme.typography.bodySmall, color = Color(0xFFB71C1C))
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
