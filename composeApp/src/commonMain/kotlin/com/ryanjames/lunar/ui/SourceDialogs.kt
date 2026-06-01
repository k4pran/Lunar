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
import androidx.compose.material3.Checkbox
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
    localImageImportSupported: Boolean,
    lilyPondImportSupported: Boolean,
    museScoreImportSupported: Boolean,
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
                    text = localImportDialogDescription(
                        localImageImportSupported = localImageImportSupported,
                        lilyPondImportSupported = lilyPondImportSupported,
                        museScoreImportSupported = museScoreImportSupported,
                    ),
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
                    subtitle = localFileImportDescription(
                        localImageImportSupported = localImageImportSupported,
                        lilyPondImportSupported = lilyPondImportSupported,
                        museScoreImportSupported = museScoreImportSupported,
                    ),
                    onSelect = { selectedType = LocalSourceType.FILES },
                )

                LocalSourceOption(
                    selected = selectedType == LocalSourceType.FOLDER,
                    enabled = folderImportSupported,
                    title = "Folder",
                    subtitle = localFolderImportDescription(
                        folderImportSupported = folderImportSupported,
                        localImageImportSupported = localImageImportSupported,
                        lilyPondImportSupported = lilyPondImportSupported,
                        museScoreImportSupported = museScoreImportSupported,
                    ),
                    onSelect = {
                        if (folderImportSupported) selectedType = LocalSourceType.FOLDER
                    },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedType, label) },
            ) {
                Text("Choose files")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun localImportDialogDescription(
    localImageImportSupported: Boolean,
    lilyPondImportSupported: Boolean,
    museScoreImportSupported: Boolean,
): String {
    val supportedInputs = localImportInputGroups(
        localImageImportSupported = localImageImportSupported,
        lilyPondImportSupported = lilyPondImportSupported,
        museScoreImportSupported = museScoreImportSupported,
    )
    return "Choose how to import local ${supportedInputs.toNaturalList()} into your library. Matching .json metadata sidecars are imported automatically."
}

private fun localFileImportDescription(
    localImageImportSupported: Boolean,
    lilyPondImportSupported: Boolean,
    museScoreImportSupported: Boolean,
): String {
    val supportedExtensions = buildList {
        add("PDFs")
        if (lilyPondImportSupported) add("LY/ILY/LYI LilyPond files")
        if (museScoreImportSupported) add("MSCZ/MSCX MuseScore files")
        if (localImageImportSupported) add("PNG/JPG/JPEG images")
    }
    return "Pick ${supportedExtensions.toNaturalList()}. Matching .json sidecars are detected automatically."
}

private fun localFolderImportDescription(
    folderImportSupported: Boolean,
    localImageImportSupported: Boolean,
    lilyPondImportSupported: Boolean,
    museScoreImportSupported: Boolean,
): String = when {
    !folderImportSupported ->
        "Folder import not supported on this platform"
    else -> {
        val supportedInputs = localImportInputGroups(
            localImageImportSupported = localImageImportSupported,
            lilyPondImportSupported = lilyPondImportSupported,
            museScoreImportSupported = museScoreImportSupported,
        ) + "matching .json metadata"
        "Scan a folder for ${supportedInputs.toNaturalList(finalSeparator = "and")}"
    }
}

private fun localImportInputGroups(
    localImageImportSupported: Boolean,
    lilyPondImportSupported: Boolean,
    museScoreImportSupported: Boolean,
): List<String> = buildList {
    add("PDFs")
    if (lilyPondImportSupported) add("LilyPond files")
    if (museScoreImportSupported) add("MuseScore files")
    if (localImageImportSupported) add("image files")
}

private fun List<String>.toNaturalList(finalSeparator: String = "or"): String = when (size) {
    0 -> ""
    1 -> single()
    2 -> joinToString(" $finalSeparator ")
    else -> dropLast(1).joinToString(", ") + ", $finalSeparator " + last()
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
    existingSource: CloudLibrarySource? = null,
    onDismiss: () -> Unit,
    onConfirm: (CloudLibrarySource) -> Unit,
) {
    val providers = remember { supportedCloudProviders() }
    val initialProviderId = remember(existingSource) {
        when (existingSource) {
            is CloudGoogleDriveSource -> SyncProviderIds.GOOGLE_DRIVE
            is CloudSupabaseSource -> SyncProviderIds.SUPABASE_PUBLIC_STORAGE
            null -> SyncProviderIds.SUPABASE_PUBLIC_STORAGE
        }
    }
    val isEditing = existingSource != null
    var selectedProviderId by remember(existingSource) { mutableStateOf(initialProviderId) }
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var label by remember(existingSource) { mutableStateOf(existingSource?.label.orEmpty()) }

    val existingSupabaseSource = existingSource as? CloudSupabaseSource
    var projectUrl by remember(existingSource) { mutableStateOf(existingSupabaseSource?.settings?.projectUrl.orEmpty()) }
    var bucketName by remember(existingSource) { mutableStateOf(existingSupabaseSource?.settings?.bucketName.orEmpty()) }
    var anonKey by remember(existingSource) { mutableStateOf(existingSupabaseSource?.settings?.anonKey.orEmpty()) }
    var rootDirectory by remember(existingSource) { mutableStateOf(existingSupabaseSource?.settings?.rootDirectory.orEmpty()) }
    var supabaseStrategy by remember(existingSource) {
        mutableStateOf(existingSupabaseSource?.settings?.folderStrategy ?: CloudPathStrategy.FLAT)
    }

    val existingGoogleDriveSource = existingSource as? CloudGoogleDriveSource
    var driveApiKey by remember(existingSource) { mutableStateOf(existingGoogleDriveSource?.settings?.apiKey.orEmpty()) }
    var driveClientId by remember(existingSource) { mutableStateOf(existingGoogleDriveSource?.settings?.clientId.orEmpty()) }
    var driveClientSecret by remember(existingSource) { mutableStateOf(existingGoogleDriveSource?.settings?.clientSecret.orEmpty()) }
    var driveRefreshToken by remember(existingSource) {
        mutableStateOf(existingGoogleDriveSource?.settings?.refreshToken.orEmpty())
    }
    var driveUploadEnabled by remember(existingSource) {
        mutableStateOf(existingGoogleDriveSource?.settings?.uploadEnabled ?: false)
    }
    var driveUploadRoot by remember(existingSource) {
        val uploadRoot = existingGoogleDriveSource?.settings?.uploadRoot
        mutableStateOf(
            EditableGoogleDriveRoot(
                id = uploadRoot?.id ?: generateSourceId(),
                label = uploadRoot?.label.orEmpty(),
                folderRef = uploadRoot?.folderId.orEmpty(),
                folderStrategy = uploadRoot?.folderStrategy ?: CloudPathStrategy.COMPOSER_COLLECTION,
            )
        )
    }
    val driveRoots = remember(existingSource) {
        mutableStateListOf<EditableGoogleDriveRoot>().apply {
            val existingRoots = existingGoogleDriveSource?.settings?.roots.orEmpty()
            if (existingRoots.isEmpty()) {
                add(EditableGoogleDriveRoot(id = generateSourceId()))
            } else {
                addAll(
                    existingRoots.map { root ->
                        EditableGoogleDriveRoot(
                            id = root.id,
                            label = root.label,
                            folderRef = root.folderId,
                            folderStrategy = root.folderStrategy,
                        )
                    }
                )
            }
        }
    }

    val isSupabaseSelected = selectedProviderId == SyncProviderIds.SUPABASE_PUBLIC_STORAGE
    val validDriveRoots = driveRoots.mapNotNull { root ->
        val folderId = extractGoogleDriveFolderId(root.folderRef)
        if (folderId.isNullOrBlank()) null else root to folderId
    }
    val validDriveUploadRoot = extractGoogleDriveFolderId(driveUploadRoot.folderRef)
        ?.takeIf(String::isNotBlank)
    val isValid = if (isSupabaseSelected) {
        projectUrl.isNotBlank() && bucketName.isNotBlank()
    } else {
        validDriveRoots.isNotEmpty() &&
            driveClientId.isNotBlank() &&
            (googleDriveOAuthSupported || isEditing) &&
            (!driveUploadEnabled || validDriveUploadRoot != null)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "Edit cloud source" else "Add cloud source",
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
                    text = if (isEditing) {
                        "Update this cloud source and save the new settings for future syncs."
                    } else {
                        "Connect a cloud source and merge its PDFs into the shared library."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (isEditing) {
                    Text(
                        text = "Provider: ${providers.firstOrNull { it.id == selectedProviderId }?.displayName.orEmpty()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
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
                        refreshToken = driveRefreshToken,
                        onRefreshTokenChange = { driveRefreshToken = it },
                        oauthSupported = googleDriveOAuthSupported,
                        allowManualRefreshTokenEdit = isEditing,
                        roots = driveRoots,
                        uploadEnabled = driveUploadEnabled,
                        onUploadEnabledChange = { driveUploadEnabled = it },
                        uploadRoot = driveUploadRoot,
                        onUploadRootChange = { driveUploadRoot = it },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val source = if (isSupabaseSelected) {
                        CloudSupabaseSource(
                            id = existingSource?.id ?: generateSourceId(),
                            label = label.ifBlank { "Supabase: $bucketName" },
                            addedAtEpochMillis = existingSource?.addedAtEpochMillis ?: Clock.System.now().toEpochMilliseconds(),
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
                            id = existingSource?.id ?: generateSourceId(),
                            label = label.ifBlank { "Google Drive" },
                            addedAtEpochMillis = existingSource?.addedAtEpochMillis ?: Clock.System.now().toEpochMilliseconds(),
                            settings = GoogleDriveStorageSettings(
                                apiKey = driveApiKey.trim(),
                                clientId = driveClientId.trim(),
                                clientSecret = driveClientSecret.trim(),
                                refreshToken = driveRefreshToken.trim(),
                                accessToken = "",
                                roots = validDriveRoots.map { (root, folderId) ->
                                    GoogleDriveImportRoot(
                                        id = root.id,
                                        label = root.label.trim(),
                                        folderId = folderId,
                                        folderStrategy = root.folderStrategy,
                                    )
                                },
                                uploadEnabled = driveUploadEnabled,
                                uploadRoot = if (driveUploadEnabled && validDriveUploadRoot != null) {
                                    GoogleDriveImportRoot(
                                        id = driveUploadRoot.id,
                                        label = driveUploadRoot.label.trim(),
                                        folderId = validDriveUploadRoot,
                                        folderStrategy = driveUploadRoot.folderStrategy,
                                    )
                                } else {
                                    null
                                },
                            ),
                        )
                    }
                    onConfirm(source)
                },
                enabled = isValid,
            ) {
                Text(if (isEditing) "Save" else "Connect")
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
    refreshToken: String,
    onRefreshTokenChange: (String) -> Unit,
    oauthSupported: Boolean,
    allowManualRefreshTokenEdit: Boolean,
    roots: MutableList<EditableGoogleDriveRoot>,
    uploadEnabled: Boolean,
    onUploadEnabledChange: (Boolean) -> Unit,
    uploadRoot: EditableGoogleDriveRoot,
    onUploadRootChange: (EditableGoogleDriveRoot) -> Unit,
) {
    Text(
        text = if (allowManualRefreshTokenEdit) {
            "Google Drive stores its OAuth details with the source. Update the client settings, refresh token, or root folders here, then refresh the source when you're ready."
        } else {
            "Google Drive uses a desktop OAuth sign-in. Add one or more read roots, paste your desktop OAuth client details, and Lunar will open the browser when you press Connect."
        },
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
        text = if (allowManualRefreshTokenEdit) {
            "You can replace the saved refresh token here. Leave it blank if you want Lunar to ask for a new desktop sign-in the next time you refresh this source."
        } else if (oauthSupported) {
            "Lunar requests offline read and file-write access during the first Google sign-in, stores the returned refresh token locally, and uses it for later background sync."
        } else {
            "Google Drive sign-in is currently available on the desktop build. Add this source from Windows or Linux to complete the OAuth connection."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (allowManualRefreshTokenEdit) {
        OutlinedTextField(
            value = refreshToken,
            onValueChange = onRefreshTokenChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Refresh token") },
            placeholder = { Text("Paste a replacement token or leave blank to reconnect later") },
        )
    }

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

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(
            checked = uploadEnabled,
            onCheckedChange = onUploadEnabledChange,
        )
        Column {
            Text("Upload-sync new local imports")
            Text(
                "Use a dedicated writable Drive folder when you want Lunar to publish a cleaned layout.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (uploadEnabled) {
        Text(
            text = "Upload root",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        GoogleDriveRootEditor(
            root = uploadRoot,
            onRootChange = onUploadRootChange,
            onRemove = { onUploadEnabledChange(false) },
            removeLabel = "Disable upload-sync",
        )
    }
}

@Composable
private fun GoogleDriveRootEditor(
    root: EditableGoogleDriveRoot,
    onRootChange: (EditableGoogleDriveRoot) -> Unit,
    onRemove: () -> Unit,
    removeLabel: String = "Remove root",
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
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Text(removeLabel)
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
