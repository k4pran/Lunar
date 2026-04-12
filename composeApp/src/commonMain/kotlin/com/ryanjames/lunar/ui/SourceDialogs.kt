package com.ryanjames.lunar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryanjames.lunar.library.data.CloudGoogleDriveSource
import com.ryanjames.lunar.library.data.CloudLibrarySource
import com.ryanjames.lunar.library.data.CloudPathStrategy
import com.ryanjames.lunar.library.data.CloudSupabaseSource
import com.ryanjames.lunar.library.data.GoogleDriveImportRoot
import com.ryanjames.lunar.library.data.GoogleDriveStorageSettings
import com.ryanjames.lunar.library.data.SupabasePublicStorageSettings
import com.ryanjames.lunar.library.data.SyncProviderIds
import com.ryanjames.lunar.library.data.generateSourceId
import com.ryanjames.lunar.sync.supportedCloudProviders
import kotlin.time.Clock

enum class LocalSourceType {
    FILES,
    FOLDER,
}

private data class EditableGoogleDriveRoot(
    val id: String,
    val label: String = "",
    val folderRef: String = "",
    val folderStrategy: CloudPathStrategy = CloudPathStrategy.FLAT,
)

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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

                LocalSourceOption(
                    selected = selectedType == LocalSourceType.FILES,
                    enabled = true,
                    title = "Individual files",
                    subtitle = "Pick one or more PDF files",
                    onSelect = { selectedType = LocalSourceType.FILES },
                )

                LocalSourceOption(
                    selected = selectedType == LocalSourceType.FOLDER,
                    enabled = folderImportSupported,
                    title = "Folder",
                    subtitle = if (folderImportSupported) {
                        "Scan a folder for all PDFs"
                    } else {
                        "Folder import not supported on this platform"
                    },
                    onSelect = {
                        if (folderImportSupported) selectedType = LocalSourceType.FOLDER
                    },
                )
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

@Composable
private fun LocalSourceOption(
    selected: Boolean,
    enabled: Boolean,
    title: String,
    subtitle: String,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            enabled = enabled,
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun AddCloudSourceDialog(
    googleDriveOAuthSupported: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (CloudLibrarySource) -> Unit,
) {
    val providers = remember { supportedCloudProviders() }
    var selectedProviderId by remember { mutableStateOf(SyncProviderIds.SUPABASE_PUBLIC_STORAGE) }
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf("") }

    var projectUrl by remember { mutableStateOf("") }
    var bucketName by remember { mutableStateOf("") }
    var anonKey by remember { mutableStateOf("") }
    var rootDirectory by remember { mutableStateOf("") }
    var supabaseStrategy by remember { mutableStateOf(CloudPathStrategy.FLAT) }

    var driveApiKey by remember { mutableStateOf("") }
    var driveClientId by remember { mutableStateOf("") }
    var driveClientSecret by remember { mutableStateOf("") }
    val driveRoots = remember { mutableStateListOf(EditableGoogleDriveRoot(id = generateSourceId())) }

    val isSupabaseSelected = selectedProviderId == SyncProviderIds.SUPABASE_PUBLIC_STORAGE
    val validDriveRoots = driveRoots.mapNotNull { root ->
        val folderId = extractGoogleDriveFolderId(root.folderRef)
        if (folderId.isNullOrBlank()) null else root to folderId
    }
    val isValid = if (isSupabaseSelected) {
        projectUrl.isNotBlank() && bucketName.isNotBlank()
    } else {
        googleDriveOAuthSupported &&
            validDriveRoots.isNotEmpty() &&
            driveClientId.isNotBlank()
    }

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
                    text = "Connect a cloud source and merge its PDFs into the shared library.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Box {
                    OutlinedButton(
                        onClick = { providerMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            providers.firstOrNull { it.id == selectedProviderId }?.displayName ?: "Choose a provider",
                        )
                    }
                    DropdownMenu(
                        expanded = providerMenuExpanded,
                        onDismissRequest = { providerMenuExpanded = false },
                    ) {
                        providers.forEach { provider ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(provider.displayName)
                                        Text(
                                            provider.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onClick = {
                                    selectedProviderId = provider.id
                                    providerMenuExpanded = false
                                },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Label (optional)") },
                    placeholder = {
                        Text(if (isSupabaseSelected) "e.g. Shared cloud bucket" else "e.g. Main Drive library")
                    },
                )

                if (isSupabaseSelected) {
                    SupabaseFields(
                        projectUrl = projectUrl,
                        onProjectUrlChange = { projectUrl = it },
                        bucketName = bucketName,
                        onBucketNameChange = { bucketName = it },
                        anonKey = anonKey,
                        onAnonKeyChange = { anonKey = it },
                        rootDirectory = rootDirectory,
                        onRootDirectoryChange = { rootDirectory = it },
                        folderStrategy = supabaseStrategy,
                        onFolderStrategyChange = { supabaseStrategy = it },
                    )
                } else {
                    GoogleDriveFields(
                        apiKey = driveApiKey,
                        onApiKeyChange = { driveApiKey = it },
                        clientId = driveClientId,
                        onClientIdChange = { driveClientId = it },
                        clientSecret = driveClientSecret,
                        onClientSecretChange = { driveClientSecret = it },
                        oauthSupported = googleDriveOAuthSupported,
                        roots = driveRoots,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val source = if (isSupabaseSelected) {
                        CloudSupabaseSource(
                            id = generateSourceId(),
                            label = label.ifBlank { "Supabase: $bucketName" },
                            addedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                            settings = SupabasePublicStorageSettings(
                                projectUrl = projectUrl.trim(),
                                bucketName = bucketName.trim(),
                                rootDirectory = rootDirectory.trim().trim('/'),
                                folderStrategy = supabaseStrategy,
                                anonKey = anonKey.trim(),
                            ),
                        )
                    } else {
                        CloudGoogleDriveSource(
                            id = generateSourceId(),
                            label = label.ifBlank { "Google Drive" },
                            addedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                            settings = GoogleDriveStorageSettings(
                                apiKey = driveApiKey.trim(),
                                clientId = driveClientId.trim(),
                                clientSecret = driveClientSecret.trim(),
                                roots = validDriveRoots.map { (root, folderId) ->
                                    GoogleDriveImportRoot(
                                        id = root.id,
                                        label = root.label.trim(),
                                        folderId = folderId,
                                        folderStrategy = root.folderStrategy,
                                    )
                                },
                            ),
                        )
                    }
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

@Composable
private fun SupabaseFields(
    projectUrl: String,
    onProjectUrlChange: (String) -> Unit,
    bucketName: String,
    onBucketNameChange: (String) -> Unit,
    anonKey: String,
    onAnonKeyChange: (String) -> Unit,
    rootDirectory: String,
    onRootDirectoryChange: (String) -> Unit,
    folderStrategy: CloudPathStrategy,
    onFolderStrategyChange: (CloudPathStrategy) -> Unit,
) {
    Text(
        text = "Supabase scans a Storage bucket and infers metadata from folder layout.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OutlinedTextField(
        value = projectUrl,
        onValueChange = onProjectUrlChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Project URL") },
        placeholder = { Text("https://xxxx.supabase.co") },
    )

    OutlinedTextField(
        value = bucketName,
        onValueChange = onBucketNameChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Bucket name") },
        placeholder = { Text("sheet-music") },
    )

    OutlinedTextField(
        value = anonKey,
        onValueChange = onAnonKeyChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Anon / public API key") },
        placeholder = { Text("eyJhbGciOi...") },
    )

    OutlinedTextField(
        value = rootDirectory,
        onValueChange = onRootDirectoryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("Root directory (optional)") },
        placeholder = { Text("scores or leave empty") },
    )

    StrategyPicker(
        label = "Layout",
        strategy = folderStrategy,
        onStrategySelected = onFolderStrategyChange,
    )
}

@Composable
private fun GoogleDriveFields(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    clientId: String,
    onClientIdChange: (String) -> Unit,
    clientSecret: String,
    onClientSecretChange: (String) -> Unit,
    oauthSupported: Boolean,
    roots: MutableList<EditableGoogleDriveRoot>,
) {
    Text(
        text = "Google Drive uses a desktop OAuth sign-in. Add one or more root folders, paste your desktop OAuth client details, and Lunar will open the browser when you press Connect.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OutlinedTextField(
        value = apiKey,
        onValueChange = onApiKeyChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("API key (optional)") },
        placeholder = { Text("AIza...") },
    )

    OutlinedTextField(
        value = clientId,
        onValueChange = onClientIdChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("OAuth client ID") },
        placeholder = { Text("xxxx.apps.googleusercontent.com") },
    )

    OutlinedTextField(
        value = clientSecret,
        onValueChange = onClientSecretChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text("OAuth client secret (optional)") },
        placeholder = { Text("GOCSPX-...") },
    )

    Text(
        text = if (oauthSupported) {
            "Do not paste a refresh token. Lunar requests offline access during the first Google sign-in, stores the returned refresh token locally, and uses it for later background sync."
        } else {
            "Google Drive sign-in is currently available on the desktop build. Add this source from Windows or Linux to complete the OAuth connection."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        roots.forEachIndexed { index, root ->
            GoogleDriveRootEditor(
                root = root,
                onRootChange = { updated -> roots[index] = updated },
                onRemove = {
                    if (roots.size > 1) {
                        roots.removeAt(index)
                    } else {
                        roots[index] = EditableGoogleDriveRoot(id = root.id)
                    }
                },
            )
        }

        OutlinedButton(
            onClick = { roots += EditableGoogleDriveRoot(id = generateSourceId()) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Add Google Drive root")
        }
    }
}

@Composable
private fun GoogleDriveRootEditor(
    root: EditableGoogleDriveRoot,
    onRootChange: (EditableGoogleDriveRoot) -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = root.label,
            onValueChange = { onRootChange(root.copy(label = it)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Root label (optional)") },
            placeholder = { Text("e.g. Composer library") },
        )

        OutlinedTextField(
            value = root.folderRef,
            onValueChange = { onRootChange(root.copy(folderRef = it)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Folder ID or Google Drive folder URL") },
            placeholder = { Text("https://drive.google.com/drive/folders/...") },
        )

        StrategyPicker(
            label = "Root layout",
            strategy = root.folderStrategy,
            onStrategySelected = { onRootChange(root.copy(folderStrategy = it)) },
        )

        TextButton(
            onClick = onRemove,
            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFB71C1C)),
        ) {
            Text("Remove root")
        }
    }
}

@Composable
private fun StrategyPicker(
    label: String,
    strategy: CloudPathStrategy,
    onStrategySelected: (CloudPathStrategy) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(
            onClick = { menuExpanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("$label: ${strategy.displayLabel()}")
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            CloudPathStrategy.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.displayLabel(), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                option.displayExample(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        menuExpanded = false
                        onStrategySelected(option)
                    },
                )
            }
        }
    }
}

private fun CloudPathStrategy.displayLabel(): String = when (this) {
    CloudPathStrategy.FLAT -> "Flat (root/*.pdf)"
    CloudPathStrategy.COMPOSER -> "By composer"
    CloudPathStrategy.COLLECTION -> "By collection"
    CloudPathStrategy.COMPOSER_COLLECTION -> "Composer to collection"
    CloudPathStrategy.INSTRUMENT -> "By instrument"
    CloudPathStrategy.DATE_ADDED -> "By date"
}

private fun CloudPathStrategy.displayExample(): String = when (this) {
    CloudPathStrategy.FLAT -> "scores/Fur Elise.pdf"
    CloudPathStrategy.COMPOSER -> "scores/Beethoven/Fur Elise.pdf"
    CloudPathStrategy.COLLECTION -> "scores/Piano Classics/Fur Elise.pdf"
    CloudPathStrategy.COMPOSER_COLLECTION -> "scores/Beethoven/Piano Classics/Fur Elise.pdf"
    CloudPathStrategy.INSTRUMENT -> "scores/Piano/Fur Elise.pdf"
    CloudPathStrategy.DATE_ADDED -> "scores/2024-03/Fur Elise.pdf"
}

private fun extractGoogleDriveFolderId(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return null

    val folderMatch = Regex("/folders/([A-Za-z0-9_-]+)").find(trimmed)
    if (folderMatch != null) {
        return folderMatch.groupValues[1]
    }

    val queryMatch = Regex("[?&]id=([A-Za-z0-9_-]+)").find(trimmed)
    if (queryMatch != null) {
        return queryMatch.groupValues[1]
    }

    return trimmed
}
