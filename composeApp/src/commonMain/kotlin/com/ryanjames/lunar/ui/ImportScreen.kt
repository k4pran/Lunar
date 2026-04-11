package com.ryanjames.lunar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.ryanjames.lunar.library.data.BucketFolderStrategy
import com.ryanjames.lunar.library.data.SyncProviderIds
import com.ryanjames.lunar.platform.ImportPermissionGrant
import com.ryanjames.lunar.platform.ImportPermissionKind
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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
                    text = "Import and Sync",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = Color.White,
                )
                Text(
                    text = "Bring PDFs into Lunar locally, or point the app at a shared cloud catalog so every device can pull the same scores.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.82f),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ImportSummaryPill("$libraryCount scores in library")
                    ImportSummaryPill(runtime.capabilities.statusLine)
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

        CloudSyncCard(
            syncState = syncState,
            appState = appState,
        )

        ImportActionCard(
            title = "Import PDF files",
            body = "Choose one or more PDFs directly from the file picker and copy them into Lunar's local library storage.",
            buttonLabel = if (appState.importInProgress) "Importing..." else "Choose files",
            enabled = runtime.capabilities.fileImportSupported && !appState.importInProgress,
            onClick = appState::importFiles,
        )

        ImportActionCard(
            title = "Import a folder",
            body = "Pick a folder and Lunar will scan it for PDFs. On Android the folder access is persisted so the app can keep track of that grant.",
            buttonLabel = if (appState.importInProgress) "Importing..." else "Choose folder",
            enabled = runtime.capabilities.folderImportSupported && !appState.importInProgress,
            onClick = appState::importFolder,
        )

        PermissionSection(
            permissionTrackingSupported = runtime.capabilities.permissionTrackingSupported,
            trackedPermissions = importerState.trackedPermissions,
        )

        OutlinedButton(
            onClick = { appState.selectSection(AppSection.LIBRARY) },
            modifier = Modifier.widthIn(max = 240.dp),
        ) {
            Text("Go to library")
        }
    }
}

@Composable
private fun CloudSyncCard(
    syncState: CloudSyncState,
    appState: LunarAppState,
) {
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var strategyMenuExpanded by remember { mutableStateOf(false) }
    val selectedProvider = syncState.availableProviders.firstOrNull {
        it.id == syncState.settings.selectedProviderId
    }
    val supabaseSettings = syncState.settings.supabasePublicStorage
    val hasErrors = syncState.syncErrors.isNotEmpty()
    val isSupabase = syncState.settings.selectedProviderId == SyncProviderIds.SUPABASE_PUBLIC_STORAGE

    Card(
        shape = MaterialTheme.shapes.large,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "☁️  Cloud sync",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = Color(0xFF1A3E4F),
            )

            // Provider picker
            androidx.compose.foundation.layout.Box {
                OutlinedButton(onClick = { providerMenuExpanded = true }) {
                    Text("Provider: ${selectedProvider?.displayName ?: "None"}")
                }
                DropdownMenu(
                    expanded = providerMenuExpanded,
                    onDismissRequest = { providerMenuExpanded = false },
                ) {
                    syncState.availableProviders.forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(provider.displayName) },
                            onClick = {
                                providerMenuExpanded = false
                                appState.selectSyncProvider(provider.id)
                            },
                        )
                    }
                }
            }

            // Supabase config fields
            if (isSupabase) {
                Text(
                    text = "Lunar calls the Supabase Storage API to list all PDFs in your bucket and download them automatically. No manifest needed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = supabaseSettings.projectUrl,
                    onValueChange = appState::updateSupabaseProjectUrl,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Project URL") },
                    placeholder = { Text("https://xxxx.supabase.co") },
                )
                OutlinedTextField(
                    value = supabaseSettings.bucketName,
                    onValueChange = appState::updateSupabaseBucketName,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Bucket name") },
                    placeholder = { Text("sheet-music") },
                )
                OutlinedTextField(
                    value = supabaseSettings.anonKey,
                    onValueChange = appState::updateSupabaseAnonKey,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Anon / public API key") },
                    placeholder = { Text("eyJhbGciOi...") },
                )
                OutlinedTextField(
                    value = supabaseSettings.rootDirectory,
                    onValueChange = appState::updateSupabaseRootDirectory,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Root directory (optional)") },
                    placeholder = { Text("scores   or leave empty for whole bucket") },
                )

                // Folder strategy picker
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(
                        onClick = { strategyMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Layout: ${supabaseSettings.folderStrategy.label()}")
                    }
                    DropdownMenu(
                        expanded = strategyMenuExpanded,
                        onDismissRequest = { strategyMenuExpanded = false },
                    ) {
                        BucketFolderStrategy.entries.forEach { strategy ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(strategy.label(), style = MaterialTheme.typography.bodyMedium)
                                        Text(strategy.example(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    strategyMenuExpanded = false
                                    appState.updateSupabaseFolderStrategy(strategy)
                                },
                            )
                        }
                    }
                }
                Text(
                    text = strategyHelpText(supabaseSettings.folderStrategy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Show the resolved scan URL so the user can verify it
                val scanUrl = syncState.lastScanRootUrl
                if (!scanUrl.isNullOrBlank()) {
                    Surface(
                        color = Color(0xFF1F4F6B).copy(alpha = 0.08f),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "Last API scan URL:",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF176A8A),
                            )
                            Text(
                                text = scanUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF1A3E4F),
                            )
                        }
                    }
                }
            }

            // Status area
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
                            syncState.isRefreshing -> "⏳  Refreshing…"
                            hasErrors -> "⚠️  Sync completed with errors"
                            !syncState.lastMessage.isNullOrBlank() -> "✓  ${syncState.lastMessage}"
                            else -> "No sync activity yet. Press Refresh now to start."
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (hasErrors) Color(0xFFB71C1C) else Color(0xFF1A3E4F),
                    )
                    if (hasErrors) {
                        syncState.syncErrors.forEach { error ->
                            Text("• $error", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB71C1C))
                        }
                    }
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = appState::forceSyncRefresh,
                    enabled = !syncState.isRefreshing,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1F4F6B),
                        disabledContainerColor = Color(0xFF4D7C99).copy(alpha = 0.38f),
                    ),
                ) {
                    Text(
                        if (syncState.isRefreshing) "Refreshing…" else "Refresh now",
                        color = if (syncState.isRefreshing) Color.White.copy(alpha = 0.5f) else Color.White,
                    )
                }
                OutlinedButton(onClick = { appState.selectSection(AppSection.LIBRARY) }) {
                    Text("View library")
                }
            }
        }
    }
}

private fun BucketFolderStrategy.label(): String = when (this) {
    BucketFolderStrategy.FLAT -> "Flat  (root/*.pdf)"
    BucketFolderStrategy.COMPOSER -> "By composer  (root/{composer}/*.pdf)"
    BucketFolderStrategy.COLLECTION -> "By collection  (root/{collection}/*.pdf)"
    BucketFolderStrategy.COMPOSER_COLLECTION -> "Composer → Collection  (root/{composer}/{collection}/*.pdf)"
    BucketFolderStrategy.INSTRUMENT -> "By instrument  (root/{instrument}/*.pdf)"
    BucketFolderStrategy.DATE_ADDED -> "By date  (root/{YYYY-MM}/*.pdf)"
}

private fun BucketFolderStrategy.example(): String = when (this) {
    BucketFolderStrategy.FLAT -> "scores/Für Elise.pdf"
    BucketFolderStrategy.COMPOSER -> "scores/Beethoven/Für Elise.pdf"
    BucketFolderStrategy.COLLECTION -> "scores/Piano Classics/Für Elise.pdf"
    BucketFolderStrategy.COMPOSER_COLLECTION -> "scores/Beethoven/Piano Classics/Für Elise.pdf"
    BucketFolderStrategy.INSTRUMENT -> "scores/Piano/Für Elise.pdf"
    BucketFolderStrategy.DATE_ADDED -> "scores/2024-03/Für Elise.pdf"
}

private fun strategyHelpText(strategy: BucketFolderStrategy): String = when (strategy) {
    BucketFolderStrategy.FLAT -> "All PDFs sit directly inside the root directory. Titles come from file names."
    BucketFolderStrategy.COMPOSER -> "One sub-folder per composer. The folder name becomes the composer field."
    BucketFolderStrategy.COLLECTION -> "One sub-folder per collection or album. The folder name becomes the collection field."
    BucketFolderStrategy.COMPOSER_COLLECTION -> "Two levels: composer → collection → PDFs. Both fields are inferred from folder names."
    BucketFolderStrategy.INSTRUMENT -> "One sub-folder per instrument. The folder name is added as a tag."
    BucketFolderStrategy.DATE_ADDED -> "One sub-folder per month (YYYY-MM). Used to preserve approximate date-added ordering."
}

@Composable
private fun ImportActionCard(
    title: String,
    body: String,
    buttonLabel: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = Color(0xFF1A3E4F),
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                enabled = enabled,
                onClick = onClick,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1F4F6B),
                    disabledContainerColor = Color(0xFF4D7C99).copy(alpha = 0.38f),
                ),
            ) {
                Text(buttonLabel, color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun PermissionSection(
    permissionTrackingSupported: Boolean,
    trackedPermissions: List<ImportPermissionGrant>,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Tracked access",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Text(
                text = if (permissionTrackingSupported) {
                    "Persisted Android document and folder grants show up here after import."
                } else {
                    "This target does not need persisted SAF-style permissions for import."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (trackedPermissions.isEmpty()) {
                Text(
                    text = if (permissionTrackingSupported) {
                        "No persisted file or folder permissions are being tracked yet."
                    } else {
                        "Nothing to track here yet."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                trackedPermissions.forEach { permission ->
                    PermissionCard(permission = permission)
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(permission: ImportPermissionGrant) {
    Surface(
        color = Color(0xFFA7C6ED).copy(alpha = 0.3f),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = permission.label,
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF1A3E4F),
            )
            Text(
                text = when (permission.kind) {
                    ImportPermissionKind.FILE -> "File access"
                    ImportPermissionKind.FOLDER -> "Folder access"
                },
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF176A8A),
            )
            permission.detail?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
