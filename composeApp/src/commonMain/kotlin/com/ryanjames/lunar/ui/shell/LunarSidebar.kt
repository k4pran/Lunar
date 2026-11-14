package com.ryanjames.lunar.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ryanjames.lunar.library.data.LibrarySnapshot
import com.ryanjames.lunar.library.model.availableCollections
import com.ryanjames.lunar.settings.AppSettings
import com.ryanjames.lunar.sync.CloudSyncState
import com.ryanjames.lunar.ui.AppSection
import com.ryanjames.lunar.ui.LibraryArrangement
import com.ryanjames.lunar.ui.LunarAppState
import com.ryanjames.lunar.ui.LunarTooltip
import com.ryanjames.lunar.ui.design.LunarTestTags
import com.ryanjames.lunar.ui.design.lunarFocusRing
import com.ryanjames.lunar.ui.design.lunarTouchTarget
import com.ryanjames.lunar.ui.sidebarShowsLibraryContext
import kotlin.time.Clock

internal val ExpandedSidebarWidth = 232.dp
internal val SidebarRailWidth = 64.dp
private val CompactDrawerWidth = 280.dp

/**
 * Safe-area insets (status bar, navigation bar, cutouts) each navigation control pads for.
 * Side navigation hugs the start edge for the full height; the compact top bar spans the top.
 */
private val SideNavigationInsetSides = WindowInsetsSides.Start + WindowInsetsSides.Vertical
private val TopBarInsetSides = WindowInsetsSides.Top + WindowInsetsSides.Horizontal

/** Ctrl+B (Cmd+B on macOS) toggles the sidebar between expanded and rail. */
fun isSidebarToggleShortcut(event: KeyEvent): Boolean =
    event.type == KeyEventType.KeyDown &&
        (event.isCtrlPressed || event.isMetaPressed) &&
        event.key == Key.B

/** Escape closes the compact navigation drawer. */
fun isDrawerDismissKey(event: KeyEvent): Boolean =
    event.type == KeyEventType.KeyDown && event.key == Key.Escape

/**
 * The adaptive app shell: a full sidebar, an icon rail or a drawer behind a slim top bar,
 * depending on the window width and the user's collapse preference.
 *
 * [content] receives whether the sidebar's Library context (Arrange by, Collections) is
 * currently reachable, so the screen can show an inline fallback when it is not.
 */
@Composable
internal fun LunarAppShell(
    appState: LunarAppState,
    snapshot: LibrarySnapshot,
    syncState: CloudSyncState,
    settings: AppSettings,
    modifier: Modifier = Modifier,
    content: @Composable (sidebarContextVisible: Boolean) -> Unit,
) {
    val collapsed = appState.isSidebarCollapsed(settings)
    val syncLine = sidebarSyncLine(syncState, Clock.System.now().toEpochMilliseconds())
    var drawerOpen by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onPreviewKeyEvent { event ->
                if (isSidebarToggleShortcut(event)) {
                    appState.toggleSidebarCollapsed()
                    true
                } else if (drawerOpen && isDrawerDismissKey(event)) {
                    drawerOpen = false
                    true
                } else {
                    false
                }
            },
    ) {
        val windowClass = windowClassFor(maxWidth.value)
        val form = sidebarFormFor(windowClass, collapsed)
        if (form != SidebarForm.DRAWER && drawerOpen) {
            drawerOpen = false
        }

        // The content slot keeps one position in the tree for every form, so resizing the
        // window or toggling the sidebar never recreates the current screen.
        Row(modifier = Modifier.fillMaxSize()) {
            when (form) {
                SidebarForm.EXPANDED -> {
                    LunarSidebar(
                        appState = appState,
                        snapshot = snapshot,
                        syncLine = syncLine,
                        onToggleCollapse = appState::toggleSidebarCollapsed,
                        modifier = Modifier.width(ExpandedSidebarWidth).fillMaxHeight().testTag(LunarTestTags.NAVIGATION),
                    )
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                SidebarForm.RAIL -> {
                    LunarSidebarRail(
                        selectedSection = appState.selectedSection,
                        onSelectSection = appState::selectSection,
                        onToggleCollapse = if (windowClass == WindowClass.EXPANDED) {
                            appState::toggleSidebarCollapsed
                        } else {
                            null
                        },
                        modifier = Modifier.width(SidebarRailWidth).fillMaxHeight().testTag(LunarTestTags.NAVIGATION),
                    )
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                SidebarForm.DRAWER -> Unit
            }
            // The navigation controls pad for the insets on their edges, so consume those here
            // and the content's own Scaffold does not apply them a second time.
            val navigationInsets = WindowInsets.safeDrawing.only(
                if (form == SidebarForm.DRAWER) WindowInsetsSides.Top else WindowInsetsSides.Start,
            )
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (form == SidebarForm.DRAWER) {
                    CompactTopBar(
                        title = appState.selectedSection.sidebarLabel,
                        onOpenMenu = { drawerOpen = true },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .consumeWindowInsets(navigationInsets),
                ) {
                    content(form != SidebarForm.RAIL && sidebarShowsLibraryContext(appState.selectedSection))
                }
            }
        }

        // System Back (Android) closes the drawer instead of leaving the app.
        PlatformBackHandler(enabled = drawerOpen) { drawerOpen = false }
        if (drawerOpen) {
            SidebarDrawer(
                appState = appState,
                snapshot = snapshot,
                syncLine = syncLine,
                onDismiss = { drawerOpen = false },
            )
        }
    }
}

@Composable
private fun SidebarDrawer(
    appState: LunarAppState,
    snapshot: LibrarySnapshot,
    syncLine: String,
    onDismiss: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                .clickable(onClick = onDismiss)
                .semantics { contentDescription = "Close navigation" },
        )
        Surface(
            modifier = Modifier
                .width(CompactDrawerWidth)
                .fillMaxHeight()
                .testTag(SidebarDrawerTag),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 8.dp,
        ) {
            LunarSidebar(
                appState = appState,
                snapshot = snapshot,
                syncLine = syncLine,
                onToggleCollapse = null,
                onNavigated = onDismiss,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

internal const val SidebarDrawerTag = "sidebar-drawer"

@Composable
private fun CompactTopBar(
    title: String,
    onOpenMenu: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(LunarTestTags.NAVIGATION)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(TopBarInsetSides))
                .height(52.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShellIconButton(
                icon = ShellIcon.MENU,
                description = "Open navigation",
                onClick = onOpenMenu,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Expanded sidebar: text-only navigation, plus the Library context when Library is open. */
@Composable
internal fun LunarSidebar(
    appState: LunarAppState,
    snapshot: LibrarySnapshot,
    syncLine: String,
    onToggleCollapse: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onNavigated: () -> Unit = {},
) {
    val selectedSection = appState.selectedSection
    val selectSection: (AppSection) -> Unit = { section ->
        appState.selectSection(section)
        onNavigated()
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(SideNavigationInsetSides))
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Lunar",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (onToggleCollapse != null) {
                ShellIconButton(
                    icon = ShellIcon.COLLAPSE,
                    description = "Collapse sidebar",
                    onClick = onToggleCollapse,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SidebarPrimaryDestinations.forEach { destination ->
                SidebarTextItem(
                    label = destination.label,
                    selected = selectedSection == destination.section,
                    onClick = { selectSection(destination.section) },
                    modifier = Modifier.testTag(sidebarNavTag(destination.section)),
                )
            }
            if (sidebarShowsLibraryContext(selectedSection)) {
                LibrarySidebarContext(
                    appState = appState,
                    snapshot = snapshot,
                    onNavigated = onNavigated,
                )
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        SidebarTextItem(
            label = SidebarSettingsDestination.label,
            selected = selectedSection == AppSection.SETTINGS,
            onClick = { selectSection(AppSection.SETTINGS) },
            modifier = Modifier.testTag(sidebarNavTag(AppSection.SETTINGS)),
        )
        Text(
            text = syncLine,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp, top = 8.dp),
        )
    }
}

@Composable
private fun ColumnScope.LibrarySidebarContext(
    appState: LunarAppState,
    snapshot: LibrarySnapshot,
    onNavigated: () -> Unit,
) {
    val collections = remember(snapshot.items) {
        snapshot.items.filterNot { it.isHidden }.availableCollections().toList()
    }
    val swatchColors = collectionSwatchColors()

    SidebarSectionLabel("Arrange by")
    LibraryArrangement.entries.forEach { arrangement ->
        SidebarTextItem(
            label = arrangement.label,
            selected = appState.libraryArrangement == arrangement,
            onClick = {
                appState.selectArrangement(arrangement)
                onNavigated()
            },
            modifier = Modifier.testTag("sidebar-arrange-${arrangement.name}"),
            compact = true,
        )
    }

    if (collections.isNotEmpty()) {
        SidebarSectionLabel("Collections")
        collections.forEach { collection ->
            SidebarTextItem(
                label = collection,
                selected = appState.query.selectedCollection?.equals(collection, ignoreCase = true) == true,
                onClick = {
                    appState.selectCollection(collection)
                    onNavigated()
                },
                compact = true,
                leading = {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(swatchColors[collectionSwatchSlot(collection, swatchColors.size)]),
                    )
                },
            )
        }
    }
}

@Composable
private fun collectionSwatchColors(): List<Color> {
    val scheme = MaterialTheme.colorScheme
    return listOf(
        scheme.primary,
        scheme.secondary,
        scheme.tertiary,
        scheme.error,
        scheme.inversePrimary,
        scheme.outline,
    )
}

@Composable
private fun SidebarSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SidebarTextItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val container = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val foreground = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (compact) Modifier else Modifier.lunarTouchTarget())
            .lunarFocusRing(MaterialTheme.shapes.small)
            .clip(MaterialTheme.shapes.small)
            .background(container)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Tab
                this.selected = selected
            }
            .padding(horizontal = 12.dp, vertical = if (compact) 6.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        leading?.invoke()
        Text(
            text = label,
            style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Collapsed rail: icons only, with the label in a tooltip and as the content description. */
@Composable
internal fun LunarSidebarRail(
    selectedSection: AppSection,
    onSelectSection: (AppSection) -> Unit,
    onToggleCollapse: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(SideNavigationInsetSides))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (onToggleCollapse != null) {
            ShellIconButton(
                icon = ShellIcon.EXPAND,
                description = "Expand sidebar",
                onClick = onToggleCollapse,
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
        // The primary destinations scroll so short windows (landscape phones) still leave room
        // for Settings, which stays pinned to the bottom.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SidebarPrimaryDestinations.forEach { destination ->
                RailItem(
                    destination = destination,
                    selected = selectedSection == destination.section,
                    onClick = { onSelectSection(destination.section) },
                )
            }
        }
        RailItem(
            destination = SidebarSettingsDestination,
            selected = selectedSection == AppSection.SETTINGS,
            onClick = { onSelectSection(AppSection.SETTINGS) },
        )
    }
}

@Composable
private fun RailItem(
    destination: SidebarDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val foreground = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    LunarTooltip(destination.label) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .lunarFocusRing(MaterialTheme.shapes.small)
                .clip(MaterialTheme.shapes.small)
                .background(container)
                .clickable(onClick = onClick)
                .testTag(sidebarNavTag(destination.section))
                .semantics {
                    contentDescription = destination.label
                    role = Role.Tab
                    this.selected = selected
                },
            contentAlignment = Alignment.Center,
        ) {
            ShellIconGlyph(icon = destination.icon, color = foreground)
        }
    }
}

@Composable
private fun ShellIconButton(
    icon: ShellIcon,
    description: String,
    onClick: () -> Unit,
) {
    LunarTooltip(description) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .lunarFocusRing(MaterialTheme.shapes.small)
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onClick)
                .semantics {
                    contentDescription = description
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            ShellIconGlyph(icon = icon, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
