package com.ryanjames.lunar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
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
import com.ryanjames.lunar.platform.formatStorageSize
import com.ryanjames.lunar.settings.AppSettings
import com.ryanjames.lunar.settings.CacheLimitPreset
import com.ryanjames.lunar.settings.limitBytes
import com.ryanjames.lunar.sync.CloudSyncState
import com.ryanjames.lunar.sync.supportedCloudProviders
import com.ryanjames.lunar.ui.design.LunarButton
import com.ryanjames.lunar.ui.design.LunarButtonStyle
import com.ryanjames.lunar.ui.design.LunarIcon
import com.ryanjames.lunar.ui.design.LunarIconKind
import com.ryanjames.lunar.ui.design.LunarTestTags
import com.ryanjames.lunar.ui.design.PageHeading
import com.ryanjames.lunar.ui.design.Popover
import com.ryanjames.lunar.ui.design.SmallCapsLabel
import com.ryanjames.lunar.ui.design.TruncatedText
import com.ryanjames.lunar.ui.design.lunarTokens
import kotlin.time.Clock

/** The side column sits beside the source list once there is room for both. */
private val SourcesSideColumnMinWindow = 900.dp
private val SourcesSideColumnWidth = 320.dp

@Composable
fun ImportScreen(
    runtime: PlatformRuntime,
    importerState: ImporterState,
    syncState: CloudSyncState,
    settings: AppSettings,
    libraryCount: Int,
    appState: LunarAppState,
    modifier: Modifier = Modifier,
) {
    val sources by runtime.sourceRegistry.sources.collectAsState()
    val tokens = lunarTokens()

    var addingCloudProviderId by remember { mutableStateOf<String?>(null) }
    var editingCloudSource by remember { mutableStateOf<CloudLibrarySource?>(null) }
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.colors.bg)
            .testTag(LunarTestTags.SOURCES_PANEL),
    ) {
        val sourceList: @Composable (Modifier) -> Unit = { columnModifier ->
            SourceListColumn(
                runtime = runtime,
                importerState = importerState,
                syncState = syncState,
                sources = sources,
                libraryCount = libraryCount,
                appState = appState,
                onAddCloudSource = { providerId -> addingCloudProviderId = providerId },
                onEditCloudSource = { source -> editingCloudSource = source },
                modifier = columnModifier,
            )
        }
        val sideColumn: @Composable (Modifier) -> Unit = { columnModifier ->
            SourcesSideColumn(
                syncState = syncState,
                settings = settings,
                cacheSnapshot = cacheSnapshot,
                cacheInspectionError = cacheInspectionError,
                appState = appState,
                modifier = columnModifier,
            )
        }

        if (maxWidth >= SourcesSideColumnMinWindow) {
            Row(modifier = Modifier.fillMaxSize()) {
                sourceList(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = tokens.spacing.xxl, vertical = tokens.spacing.xl),
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(tokens.colors.rule),
                )
                sideColumn(
                    Modifier
                        .width(SourcesSideColumnWidth)
                        .fillMaxHeight()
                        .background(tokens.colors.panel)
                        .verticalScroll(rememberScrollState())
                        .padding(tokens.spacing.xl),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.xl),
            ) {
                sourceList(Modifier.fillMaxWidth())
                sideColumn(Modifier.fillMaxWidth())
            }
        }
    }

    addingCloudProviderId?.let { providerId ->
        AddCloudSourceDialog(
            googleDriveOAuthSupported = runtime.googleDriveOAuth.isSupported,
            initialProviderId = providerId,
            onDismiss = { addingCloudProviderId = null },
            onConfirm = { source ->
                addingCloudProviderId = null
                appState.addCloudSource(source)
            },
        )
    }

    editingCloudSource?.let { source ->
        AddCloudSourceDialog(
            googleDriveOAuthSupported = runtime.googleDriveOAuth.isSupported,
            existingSource = source,
            onDismiss = { editingCloudSource = null },
            onConfirm = { updatedSource ->
                editingCloudSource = null
                appState.updateCloudSource(updatedSource)
            },
        )
    }
}

@Composable
private fun SourceListColumn(
    runtime: PlatformRuntime,
    importerState: ImporterState,
    syncState: CloudSyncState,
    sources: List<LibrarySource>,
    libraryCount: Int,
    appState: LunarAppState,
    onAddCloudSource: (providerId: String) -> Unit,
    onEditCloudSource: (CloudLibrarySource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = lunarTokens()
    val nowEpochMillis = remember(syncState, sources) { Clock.System.now().toEpochMilliseconds() }
    val hasCloudSources = sources.any { it is CloudLibrarySource }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
            verticalAlignment = Alignment.Bottom,
        ) {
            PageHeading(
                title = "Sources",
                subtitle = "$libraryCount ${plural(libraryCount, "score")} from " +
                    "${sources.size} ${plural(sources.size, "source")}",
                modifier = Modifier.weight(1f),
            )
            if (hasCloudSources) {
                LunarButton(
                    text = if (syncState.isRefreshing) "Refreshing..." else "Refresh all",
                    onClick = appState::refreshAllCloudSources,
                    style = LunarButtonStyle.SECONDARY,
                    enabled = !syncState.isRefreshing,
                )
            }
            AddSourceMenu(
                runtime = runtime,
                localImportEnabled = !appState.importInProgress,
                onAddFiles = { appState.addLocalFilesSource("") },
                onAddFolder = { appState.addLocalFolderSource("") },
                onAddCloudSource = onAddCloudSource,
            )
        }

        importerState.statusMessage?.takeIf(String::isNotBlank)?.let { message ->
            Text(
                text = message,
                style = tokens.typography.bodySmall,
                color = tokens.colors.muted,
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = tokens.colors.strong)
            if (sources.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = tokens.spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
                ) {
                    Text(
                        text = "No sources yet",
                        style = tokens.typography.heading,
                        color = tokens.colors.ink,
                    )
                    Text(
                        text = "Use Add a source to bring in files or a folder from this device, or connect Supabase or Google Drive.",
                        style = tokens.typography.bodySmall,
                        color = tokens.colors.muted,
                    )
                }
            } else {
                sources.forEachIndexed { index, source ->
                    if (index > 0) HorizontalDivider(color = tokens.colors.rule)
                    SourceRow(
                        source = source,
                        itemCount = runtime.repository.itemCountForSource(source.id),
                        status = sourceStatusLine(source, syncState, nowEpochMillis),
                        actionsEnabled = !syncState.isRefreshing,
                        onRefresh = (source as? CloudLibrarySource)?.let { cloud ->
                            { appState.refreshCloudSource(cloud) }
                        },
                        onEdit = (source as? CloudLibrarySource)?.let { cloud ->
                            { onEditCloudSource(cloud) }
                        },
                        onRemove = { appState.removeSource(source.id) },
                    )
                }
            }
            HorizontalDivider(color = tokens.colors.rule)
        }
    }
}

/**
 * "Add a source": Files and Folder go straight to the system picker; the cloud providers open
 * their connection form.
 */
@Composable
private fun AddSourceMenu(
    runtime: PlatformRuntime,
    localImportEnabled: Boolean,
    onAddFiles: () -> Unit,
    onAddFolder: () -> Unit,
    onAddCloudSource: (providerId: String) -> Unit,
) {
    val tokens = lunarTokens()
    val capabilities = runtime.capabilities
    var expanded by remember { mutableStateOf(false) }

    Box {
        LunarButton(
            text = "Add a source",
            onClick = { expanded = true },
            icon = LunarIconKind.CHEVRON_DOWN,
            modifier = Modifier.testTag(LunarTestTags.SOURCES_ADD_MENU),
        )
        Popover(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            SmallCapsLabel(
                text = "On this device",
                modifier = Modifier.padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.xs),
            )
            AddSourceMenuItem(
                key = "files",
                title = "Files",
                subtitle = localFileImportDescription(
                    localImageImportSupported = capabilities.localImageImportSupported,
                    lilyPondImportSupported = capabilities.lilyPondImportSupported,
                    museScoreImportSupported = capabilities.museScoreImportSupported,
                ),
                icon = LunarIconKind.FOLDER,
                enabled = capabilities.fileImportSupported && localImportEnabled,
                onClick = {
                    expanded = false
                    onAddFiles()
                },
            )
            AddSourceMenuItem(
                key = "folder",
                title = "Folder",
                subtitle = localFolderImportDescription(
                    folderImportSupported = capabilities.folderImportSupported,
                    localImageImportSupported = capabilities.localImageImportSupported,
                    lilyPondImportSupported = capabilities.lilyPondImportSupported,
                    museScoreImportSupported = capabilities.museScoreImportSupported,
                ),
                icon = LunarIconKind.FOLDER,
                enabled = capabilities.folderImportSupported && localImportEnabled,
                onClick = {
                    expanded = false
                    onAddFolder()
                },
            )
            HorizontalDivider(
                color = tokens.colors.rule,
                modifier = Modifier.padding(vertical = tokens.spacing.xs),
            )
            SmallCapsLabel(
                text = "Cloud",
                modifier = Modifier.padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.xs),
            )
            supportedCloudProviders().forEach { provider ->
                AddSourceMenuItem(
                    key = provider.id,
                    title = provider.displayName,
                    subtitle = provider.description,
                    icon = LunarIconKind.CLOUD,
                    enabled = true,
                    onClick = {
                        expanded = false
                        onAddCloudSource(provider.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun AddSourceMenuItem(
    key: String,
    title: String,
    subtitle: String,
    icon: LunarIconKind,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tokens = lunarTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(LunarTestTags.addSourceOption(key))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        LunarIcon(
            kind = icon,
            contentDescription = null,
            size = 16.dp,
            tint = if (enabled) tokens.colors.ink2 else tokens.colors.faint,
            modifier = Modifier.padding(top = tokens.spacing.xxs),
        )
        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.xxs)) {
            Text(
                text = title,
                style = tokens.typography.body,
                color = if (enabled) tokens.colors.ink else tokens.colors.muted,
            )
            Text(
                text = subtitle,
                style = tokens.typography.bodySmall,
                color = tokens.colors.muted,
            )
        }
    }
}

/** One ruled row per source: type, location, score count, its own status line, and actions. */
@Composable
private fun SourceRow(
    source: LibrarySource,
    itemCount: Int,
    status: SourceStatusLine,
    actionsEnabled: Boolean,
    onRefresh: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    val tokens = lunarTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(LunarTestTags.sourceRow(source.id))
            .padding(vertical = tokens.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LunarIcon(
            kind = source.iconKind(),
            contentDescription = null,
            tint = tokens.colors.muted,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.xxs),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TruncatedText(
                    text = source.label,
                    style = tokens.typography.body.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f, fill = false),
                )
                SmallCapsLabel(text = source.typeLabel())
            }
            TruncatedText(
                text = source.locationLabel(),
                style = tokens.typography.bodySmall,
                color = tokens.colors.ink2,
            )
            Text(
                text = status.text,
                style = tokens.typography.bodySmall,
                color = when (status.tone) {
                    SourceStatusTone.NEUTRAL -> tokens.colors.muted
                    SourceStatusTone.ACTIVE -> tokens.colors.accent
                    SourceStatusTone.ERROR -> tokens.colors.danger
                },
                modifier = Modifier.testTag(LunarTestTags.sourceStatus(source.id)),
            )
        }
        Text(
            text = "$itemCount ${plural(itemCount, "score")}",
            style = tokens.typography.bodySmall,
            color = tokens.colors.ink2,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onRefresh?.let {
                LunarButton(
                    text = "Refresh",
                    onClick = it,
                    style = LunarButtonStyle.TEXT,
                    enabled = actionsEnabled,
                )
            }
            onEdit?.let {
                LunarButton(
                    text = "Edit",
                    onClick = it,
                    style = LunarButtonStyle.TEXT,
                    enabled = actionsEnabled,
                )
            }
            TextButton(
                onClick = onRemove,
                shape = tokens.shapes.medium,
                colors = ButtonDefaults.textButtonColors(contentColor = tokens.colors.danger),
            ) {
                Text(text = "Remove", style = tokens.typography.button)
            }
        }
    }
}

@Composable
private fun SourcesSideColumn(
    syncState: CloudSyncState,
    settings: AppSettings,
    cacheSnapshot: LibraryCacheSnapshot?,
    cacheInspectionError: String?,
    appState: LunarAppState,
    modifier: Modifier = Modifier,
) {
    val tokens = lunarTokens()
    Column(
        modifier = modifier.testTag(LunarTestTags.SOURCES_SIDE_PANEL),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.xl),
    ) {
        CacheUsageSection(
            snapshot = cacheSnapshot,
            settings = settings,
            inspectionError = cacheInspectionError,
        )
        cacheSnapshot?.let { OfflineReadinessSection(it) }
        ActivitySection(syncState = syncState, appState = appState)
    }
}

@Composable
private fun CacheUsageSection(
    snapshot: LibraryCacheSnapshot?,
    settings: AppSettings,
    inspectionError: String?,
) {
    val tokens = lunarTokens()
    val clipboardManager = LocalClipboardManager.current
    SideSection(title = "On-device cache") {
        inspectionError?.takeIf(String::isNotBlank)?.let { error ->
            Text(text = error, style = tokens.typography.bodySmall, color = tokens.colors.danger)
        }
        if (snapshot == null) {
            if (inspectionError.isNullOrBlank()) {
                Text(text = "Checking the cache...", style = tokens.typography.bodySmall, color = tokens.colors.muted)
            }
            return@SideSection
        }
        val limitBytes = settings.cacheLimit.limitBytes
        val overBudget = limitBytes != null && snapshot.cachedPdfBytes > limitBytes
        Text(
            text = if (limitBytes == null) {
                "${snapshot.cachedPdfBytesLabel} used, no budget"
            } else {
                "${snapshot.cachedPdfBytesLabel} of ${settings.cacheLimit.label()}"
            },
            style = tokens.typography.body,
            color = tokens.colors.ink,
        )
        if (limitBytes != null) {
            CacheBudgetBar(
                fraction = (snapshot.cachedPdfBytes.toDouble() / limitBytes.toDouble()).toFloat(),
                overBudget = overBudget,
            )
        }
        Text(
            text = "${snapshot.cachedPdfCount} cached ${plural(snapshot.cachedPdfCount, "PDF")} in ${snapshot.storageLabel}",
            style = tokens.typography.bodySmall,
            color = tokens.colors.muted,
        )
        snapshot.cacheRootPath?.takeIf(String::isNotBlank)?.let { cachePath ->
            TruncatedText(text = cachePath, style = tokens.typography.bodySmall, color = tokens.colors.muted)
        }
        if (overBudget && limitBytes != null) {
            Text(
                text = "${formatStorageSize(snapshot.cachedPdfBytes - limitBytes)} over the preferred budget.",
                style = tokens.typography.bodySmall,
                color = tokens.colors.danger,
            )
        }
        LunarButton(
            text = "Copy cache details",
            onClick = { clipboardManager.setText(AnnotatedString(buildCacheDetails(snapshot, settings))) },
            style = LunarButtonStyle.TEXT,
        )
    }
}

@Composable
private fun CacheBudgetBar(
    fraction: Float,
    overBudget: Boolean,
) {
    val tokens = lunarTokens()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(tokens.colors.faint, tokens.shapes.extraSmall),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .background(
                    color = if (overBudget) tokens.colors.danger else tokens.colors.accent,
                    shape = tokens.shapes.extraSmall,
                ),
        )
    }
}

@Composable
private fun OfflineReadinessSection(snapshot: LibraryCacheSnapshot) {
    SideSection(title = "Offline") {
        ReadinessLine(
            ready = snapshot.offlineLibraryReady,
            readyText = "Library metadata cached locally",
            notReadyText = "Library metadata not cached yet",
        )
        ReadinessLine(
            ready = snapshot.offlineViewerReady,
            readyText = "Offline viewing ready for cached scores",
            notReadyText = "Add or sync scores to enable offline viewing",
        )
    }
}

@Composable
private fun ReadinessLine(
    ready: Boolean,
    readyText: String,
    notReadyText: String,
) {
    val tokens = lunarTokens()
    Row(
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ready) {
            LunarIcon(kind = LunarIconKind.CHECK, contentDescription = null, size = 14.dp, tint = tokens.colors.accent)
        } else {
            Box(
                modifier = Modifier
                    .width(14.dp)
                    .height(1.dp)
                    .background(tokens.colors.muted),
            )
        }
        Text(
            text = if (ready) readyText else notReadyText,
            style = tokens.typography.bodySmall,
            color = if (ready) tokens.colors.ink2 else tokens.colors.muted,
        )
    }
}

@Composable
private fun ActivitySection(
    syncState: CloudSyncState,
    appState: LunarAppState,
) {
    val tokens = lunarTokens()
    val clipboardManager = LocalClipboardManager.current
    val hasSyncActivity = syncState.isRefreshing || syncState.activityLog.isNotEmpty()
    val hasImportActivity = appState.importInProgress || appState.importActivityLog.isNotEmpty()

    SideSection(title = "Activity") {
        if (!hasSyncActivity && !hasImportActivity) {
            Text(text = "No activity yet.", style = tokens.typography.bodySmall, color = tokens.colors.muted)
            return@SideSection
        }
        if (hasSyncActivity) {
            ActivityLog(
                heading = when {
                    syncState.isRefreshing -> syncState.currentStep ?: "Refreshing..."
                    else -> syncState.lastMessage ?: "Sync"
                },
                counts = if (
                    syncState.isRefreshing ||
                    syncState.discoveredResources > 0 ||
                    syncState.visitedFolders > 0
                ) {
                    buildList {
                        add("Folders ${syncState.visitedFolders}")
                        add("found ${syncState.discoveredResources}")
                        add("ready ${syncState.processedResources}")
                        if (syncState.skippedResources > 0) add("skipped ${syncState.skippedResources}")
                    }.joinToString(", ")
                } else {
                    null
                },
                entries = syncState.activityLog,
            )
            LunarButton(
                text = if (syncState.syncErrors.isNotEmpty()) "Copy sync details" else "Copy activity log",
                onClick = { clipboardManager.setText(AnnotatedString(buildSyncDetails(syncState))) },
                style = LunarButtonStyle.TEXT,
            )
        }
        if (hasImportActivity) {
            ActivityLog(
                heading = appState.importActivityStep ?: if (appState.importInProgress) "Importing..." else "Import",
                counts = buildList {
                    add("Found ${appState.importDiscoveredResources}")
                    add("imported ${appState.importProcessedResources}")
                    if (appState.importSkippedResources > 0) add("skipped ${appState.importSkippedResources}")
                }.joinToString(", "),
                entries = appState.importActivityLog,
            )
            LunarButton(
                text = "Copy import activity",
                onClick = { clipboardManager.setText(AnnotatedString(buildImportDetails(appState))) },
                style = LunarButtonStyle.TEXT,
            )
        }
    }
}

@Composable
private fun ActivityLog(
    heading: String,
    counts: String?,
    entries: List<String>,
) {
    val tokens = lunarTokens()
    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
        Text(text = heading, style = tokens.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = tokens.colors.ink)
        counts?.let { Text(text = it, style = tokens.typography.bodySmall, color = tokens.colors.muted) }
        entries.takeLast(MaxActivityEntries).forEach { entry ->
            Text(text = entry, style = tokens.typography.bodySmall, color = tokens.colors.ink2)
        }
    }
}

@Composable
private fun SideSection(
    title: String,
    content: @Composable () -> Unit,
) {
    val tokens = lunarTokens()
    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)) {
        SmallCapsLabel(text = title)
        HorizontalDivider(color = tokens.colors.rule)
        content()
    }
}

private const val MaxActivityEntries = 12

private fun LibrarySource.iconKind(): LunarIconKind = when (this) {
    is LocalFilesSource, is LocalFolderSource -> LunarIconKind.FOLDER
    is CloudSupabaseSource -> LunarIconKind.CLOUD
    is CloudGoogleDriveSource -> LunarIconKind.DRIVE
}

private fun plural(count: Int, noun: String): String = if (count == 1) noun else "${noun}s"

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

private fun buildCacheDetails(
    cacheSnapshot: LibraryCacheSnapshot,
    settings: AppSettings,
): String = buildString {
    appendLine("Storage: ${cacheSnapshot.storageLabel}")
    cacheSnapshot.cacheRootPath?.takeIf(String::isNotBlank)?.let { cachePath ->
        appendLine("Cache root: $cachePath")
    }
    appendLine("Library metadata cached: ${cacheSnapshot.metadataCached}")
    appendLine("Source registry cached: ${cacheSnapshot.sourceRegistryCached}")
    appendLine("Cached PDFs: ${cacheSnapshot.cachedPdfCount}")
    appendLine("Cached PDF size: ${cacheSnapshot.cachedPdfBytesLabel}")
    appendLine("Preferred cache budget: ${settings.cacheLimit.label()}")
    appendLine("Offline library ready: ${cacheSnapshot.offlineLibraryReady}")
    appendLine("Offline viewer ready: ${cacheSnapshot.offlineViewerReady}")
}

internal fun CacheLimitPreset.label(): String = when (this) {
    CacheLimitPreset.MB_512 -> "512 MB"
    CacheLimitPreset.GB_1 -> "1 GB"
    CacheLimitPreset.GB_2 -> "2 GB"
    CacheLimitPreset.GB_4 -> "4 GB"
    CacheLimitPreset.GB_8 -> "8 GB"
    CacheLimitPreset.UNLIMITED -> "Unlimited"
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
