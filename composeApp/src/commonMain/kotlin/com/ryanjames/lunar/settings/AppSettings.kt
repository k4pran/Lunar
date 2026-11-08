package com.ryanjames.lunar.settings

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val defaultViewerPageMode: ViewerPageModePreference = ViewerPageModePreference.SINGLE_PAGE,
    val defaultLibraryLayout: LibraryLayoutPreference = LibraryLayoutPreference.LIST,
    val viewerKeybindings: ViewerKeybindings = ViewerKeybindings(),
    /** Legacy colour theme, kept so older settings files still decode. Superseded by [skin]. */
    val theme: AppColorTheme = AppColorTheme.OCEAN,
    val skin: AppSkin? = null,
    val refreshOnLaunch: Boolean = true,
    val autoRefreshSchedule: AutoRefreshSchedule = AutoRefreshSchedule.MINUTES_15,
    val cacheLimit: CacheLimitPreset = CacheLimitPreset.GB_2,
    val cloudConnectTimeoutSeconds: Int = 15,
    val cloudReadTimeoutSeconds: Int = 60,
    val sidebarCollapsed: Boolean = false,
) {
    val cloudConnectTimeoutMillis: Int
        get() = cloudConnectTimeoutSeconds.coerceIn(5, 120) * 1000

    val cloudReadTimeoutMillis: Int
        get() = cloudReadTimeoutSeconds.coerceIn(15, 300) * 1000

    /** The skin to render with, falling back to the legacy theme mapping before [normalize] runs. */
    val resolvedSkin: AppSkin
        get() = skin ?: theme.legacySkin()
}

@Serializable
data class ViewerKeybindings(
    val toggleFullscreen: String? = "F11",
    val nextPage: String? = "ArrowRight",
    val previousPage: String? = "ArrowLeft",
    val toggleFavorite: String? = "F",
    val zoomIn: String? = "ArrowUp",
    val zoomOut: String? = "ArrowDown",
    val togglePageViewMode: String? = "V",
    val openRandomScore: String? = null,
) {
    fun bindingFor(action: ViewerShortcutAction): String? = when (action) {
        ViewerShortcutAction.TOGGLE_FULLSCREEN -> toggleFullscreen
        ViewerShortcutAction.NEXT_PAGE -> nextPage
        ViewerShortcutAction.PREVIOUS_PAGE -> previousPage
        ViewerShortcutAction.TOGGLE_FAVORITE -> toggleFavorite
        ViewerShortcutAction.ZOOM_IN -> zoomIn
        ViewerShortcutAction.ZOOM_OUT -> zoomOut
        ViewerShortcutAction.TOGGLE_PAGE_VIEW_MODE -> togglePageViewMode
        ViewerShortcutAction.OPEN_RANDOM_SCORE -> openRandomScore
    }

    fun withBinding(
        action: ViewerShortcutAction,
        keyId: String?,
    ): ViewerKeybindings = when (action) {
        ViewerShortcutAction.TOGGLE_FULLSCREEN -> copy(toggleFullscreen = keyId)
        ViewerShortcutAction.NEXT_PAGE -> copy(nextPage = keyId)
        ViewerShortcutAction.PREVIOUS_PAGE -> copy(previousPage = keyId)
        ViewerShortcutAction.TOGGLE_FAVORITE -> copy(toggleFavorite = keyId)
        ViewerShortcutAction.ZOOM_IN -> copy(zoomIn = keyId)
        ViewerShortcutAction.ZOOM_OUT -> copy(zoomOut = keyId)
        ViewerShortcutAction.TOGGLE_PAGE_VIEW_MODE -> copy(togglePageViewMode = keyId)
        ViewerShortcutAction.OPEN_RANDOM_SCORE -> copy(openRandomScore = keyId)
    }

    fun clear(action: ViewerShortcutAction): ViewerKeybindings = withBinding(
        action = action,
        keyId = null,
    )
}

@Serializable
enum class ViewerShortcutAction {
    TOGGLE_FULLSCREEN,
    NEXT_PAGE,
    PREVIOUS_PAGE,
    TOGGLE_FAVORITE,
    ZOOM_IN,
    ZOOM_OUT,
    TOGGLE_PAGE_VIEW_MODE,
    OPEN_RANDOM_SCORE,
}

internal val LegacyDefaultViewerKeybindings = ViewerKeybindings(
    toggleFullscreen = "F11",
    nextPage = "ArrowRight",
    previousPage = "ArrowLeft",
    toggleFavorite = "F",
    zoomIn = "Equal",
    zoomOut = "Minus",
    togglePageViewMode = "V",
    openRandomScore = null,
)

internal fun AppSettings.normalize(): AppSettings {
    var normalized = this
    if (normalized.viewerKeybindings == LegacyDefaultViewerKeybindings) {
        normalized = normalized.copy(viewerKeybindings = ViewerKeybindings())
    }
    if (normalized.skin == null) {
        normalized = normalized.copy(skin = normalized.theme.legacySkin())
    }
    return normalized
}

@Serializable
enum class ViewerPageModePreference {
    SINGLE_PAGE,
    TWO_PAGE,
}

@Serializable
enum class LibraryLayoutPreference {
    LIST,
    GRID,
}

@Serializable
enum class AppColorTheme {
    OCEAN,
    FOREST,
    SUNSET,
    AURORA,
    DARCULA,
    MIDNIGHT,
    EMBER,
}

@Serializable
enum class AppSkin {
    PAPER,
    MOONLIGHT,
}

/** Maps a pre-skin colour theme onto the closest skin: light themes become Paper, dark ones Moonlight. */
fun AppColorTheme.legacySkin(): AppSkin = when (this) {
    AppColorTheme.OCEAN,
    AppColorTheme.FOREST,
    AppColorTheme.SUNSET,
    AppColorTheme.AURORA -> AppSkin.PAPER

    AppColorTheme.DARCULA,
    AppColorTheme.MIDNIGHT,
    AppColorTheme.EMBER -> AppSkin.MOONLIGHT
}

@Serializable
enum class AutoRefreshSchedule {
    DISABLED,
    MINUTES_5,
    MINUTES_15,
    MINUTES_30,
    HOURLY,
    DAILY,
}

val AutoRefreshSchedule.intervalMillis: Long?
    get() = when (this) {
        AutoRefreshSchedule.DISABLED -> null
        AutoRefreshSchedule.MINUTES_5 -> 5 * 60_000L
        AutoRefreshSchedule.MINUTES_15 -> 15 * 60_000L
        AutoRefreshSchedule.MINUTES_30 -> 30 * 60_000L
        AutoRefreshSchedule.HOURLY -> 60 * 60_000L
        AutoRefreshSchedule.DAILY -> 24 * 60 * 60_000L
    }

@Serializable
enum class CacheLimitPreset {
    MB_512,
    GB_1,
    GB_2,
    GB_4,
    GB_8,
    UNLIMITED,
}

val CacheLimitPreset.limitBytes: Long?
    get() = when (this) {
        CacheLimitPreset.MB_512 -> 512L * 1024L * 1024L
        CacheLimitPreset.GB_1 -> 1024L * 1024L * 1024L
        CacheLimitPreset.GB_2 -> 2L * 1024L * 1024L * 1024L
        CacheLimitPreset.GB_4 -> 4L * 1024L * 1024L * 1024L
        CacheLimitPreset.GB_8 -> 8L * 1024L * 1024L * 1024L
        CacheLimitPreset.UNLIMITED -> null
    }
