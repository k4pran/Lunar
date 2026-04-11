package com.ryanjames.lunar.platform

import androidx.compose.runtime.Composable

@Composable
actual fun rememberPlatformRuntime(): PlatformRuntime = rememberUnsupportedPlatformRuntime(
    platformName = "Web preview",
    statusLine = "Web support is planned after the Android/Desktop foundation",
    importMessage = "This build keeps the shared shell intact, but PDF import and viewing are not wired for web yet.",
)
