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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryanjames.lunar.platform.ImportPermissionGrant
import com.ryanjames.lunar.platform.ImportPermissionKind
import com.ryanjames.lunar.platform.ImporterState
import com.ryanjames.lunar.platform.PlatformRuntime

@Composable
fun ImportScreen(
    runtime: PlatformRuntime,
    importerState: ImporterState,
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
                    text = "📥  Import Scores",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = Color.White,
                )
                Text(
                    text = "Bring PDFs into Lunar from individual files or a folder, then jump straight back into the library.",
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
                text = "Tracked Access",
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
