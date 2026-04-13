package com.ryanjames.lunar.settings

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val defaultViewerPageMode: ViewerPageModePreference = ViewerPageModePreference.SINGLE_PAGE,
    val defaultLibraryLayout: LibraryLayoutPreference = LibraryLayoutPreference.LIST,
    val theme: AppColorTheme = AppColorTheme.OCEAN,
    val refreshOnLaunch: Boolean = true,
    val autoRefreshSchedule: AutoRefreshSchedule = AutoRefreshSchedule.MINUTES_15,
    val cacheLimit: CacheLimitPreset = CacheLimitPreset.GB_2,
    val cloudConnectTimeoutSeconds: Int = 15,
    val cloudReadTimeoutSeconds: Int = 60,
) {
    val cloudConnectTimeoutMillis: Int
        get() = cloudConnectTimeoutSeconds.coerceIn(5, 120) * 1000

    val cloudReadTimeoutMillis: Int
        get() = cloudReadTimeoutSeconds.coerceIn(15, 300) * 1000
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
