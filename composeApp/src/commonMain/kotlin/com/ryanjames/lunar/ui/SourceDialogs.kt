package com.ryanjames.lunar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryanjames.lunar.library.data.BucketFolderStrategy
import com.ryanjames.lunar.library.data.CloudSupabaseSource
import com.ryanjames.lunar.library.data.SupabasePublicStorageSettings
import com.ryanjames.lunar.library.data.generateSourceId
import kotlin.time.Clock

// ─── Local source dialog ──────────────────────────────────────────────────────

enum class LocalSourceType {
    FILES,
    FOLDER,
}

@Composable
fun AddLocalSourceDialog(
    folderImportSupported: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (type: LocalSourceType, label: String) -> Unit,
) {
    var selectedType by remember { mutableStateOf(LocalSourceType.FILES) }
    var label by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add local source",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Choose how to import local PDFs into your library.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Label (optional)") },
                    placeholder = { Text("e.g. My sheet music") },
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    RadioButton(
                        selected = selectedType == LocalSourceType.FILES,
                        onClick = { selectedType = LocalSourceType.FILES },
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text("Individual files", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Pick one or more PDF files",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    RadioButton(
                        selected = selectedType == LocalSourceType.FOLDER,
                        onClick = { if (folderImportSupported) selectedType = LocalSourceType.FOLDER },
                        enabled = folderImportSupported,
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            "Folder",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (folderImportSupported) Color.Unspecified
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Text(
                            if (folderImportSupported) "Scan a folder for all PDFs"
                            else "Folder import not supported on this platform",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedType, label) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F4F6B)),
            ) {
                Text("Choose files", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

// ─── Cloud source dialog ──────────────────────────────────────────────────────

@Composable
fun AddCloudSourceDialog(
    onDismiss: () -> Unit,
    onConfirm: (CloudSupabaseSource) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var projectUrl by remember { mutableStateOf("") }
    var bucketName by remember { mutableStateOf("") }
    var anonKey by remember { mutableStateOf("") }
    var rootDirectory by remember { mutableStateOf("") }
    var folderStrategy by remember { mutableStateOf(BucketFolderStrategy.FLAT) }
    var strategyMenuExpanded by remember { mutableStateOf(false) }

    val isValid = projectUrl.isNotBlank() && bucketName.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add cloud source",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Connect to a Supabase Storage bucket to sync PDFs into your library.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Label (optional)") },
                    placeholder = { Text("e.g. My cloud scores") },
                )

                OutlinedTextField(
                    value = projectUrl,
                    onValueChange = { projectUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Project URL") },
                    placeholder = { Text("https://xxxx.supabase.co") },
                )

                OutlinedTextField(
                    value = bucketName,
                    onValueChange = { bucketName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Bucket name") },
                    placeholder = { Text("sheet-music") },
                )

                OutlinedTextField(
                    value = anonKey,
                    onValueChange = { anonKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Anon / public API key") },
                    placeholder = { Text("eyJhbGciOi...") },
                )

                OutlinedTextField(
                    value = rootDirectory,
                    onValueChange = { rootDirectory = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Root directory (optional)") },
                    placeholder = { Text("scores   or leave empty") },
                )

                // Folder strategy picker
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(
                        onClick = { strategyMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Layout: ${folderStrategy.displayLabel()}")
                    }
                    DropdownMenu(
                        expanded = strategyMenuExpanded,
                        onDismissRequest = { strategyMenuExpanded = false },
                    ) {
                        BucketFolderStrategy.entries.forEach { strategy ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(strategy.displayLabel(), style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            strategy.displayExample(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onClick = {
                                    strategyMenuExpanded = false
                                    folderStrategy = strategy
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val source = CloudSupabaseSource(
                        id = generateSourceId(),
                        label = label.ifBlank { "Supabase: $bucketName" },
                        addedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                        settings = SupabasePublicStorageSettings(
                            projectUrl = projectUrl.trim(),
                            bucketName = bucketName.trim(),
                            rootDirectory = rootDirectory.trim().trimEnd('/'),
                            folderStrategy = folderStrategy,
                            anonKey = anonKey.trim(),
                        ),
                    )
                    onConfirm(source)
                },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F4F6B)),
            ) {
                Text("Connect", color = if (isValid) Color.White else Color.White.copy(alpha = 0.5f))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun BucketFolderStrategy.displayLabel(): String = when (this) {
    BucketFolderStrategy.FLAT -> "Flat  (root/*.pdf)"
    BucketFolderStrategy.COMPOSER -> "By composer"
    BucketFolderStrategy.COLLECTION -> "By collection"
    BucketFolderStrategy.COMPOSER_COLLECTION -> "Composer → Collection"
    BucketFolderStrategy.INSTRUMENT -> "By instrument"
    BucketFolderStrategy.DATE_ADDED -> "By date"
}

private fun BucketFolderStrategy.displayExample(): String = when (this) {
    BucketFolderStrategy.FLAT -> "scores/Für Elise.pdf"
    BucketFolderStrategy.COMPOSER -> "scores/Beethoven/Für Elise.pdf"
    BucketFolderStrategy.COLLECTION -> "scores/Piano Classics/Für Elise.pdf"
    BucketFolderStrategy.COMPOSER_COLLECTION -> "scores/Beethoven/Piano Classics/Für Elise.pdf"
    BucketFolderStrategy.INSTRUMENT -> "scores/Piano/Für Elise.pdf"
    BucketFolderStrategy.DATE_ADDED -> "scores/2024-03/Für Elise.pdf"
}

