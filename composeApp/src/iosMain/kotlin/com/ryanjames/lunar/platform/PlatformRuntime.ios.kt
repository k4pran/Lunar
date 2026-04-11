package com.ryanjames.lunar.platform

import androidx.compose.runtime.Composable

@Composable
actual fun rememberPlatformRuntime(): PlatformRuntime = rememberUnsupportedPlatformRuntime(
    platformName = "iOS preview",
    statusLine = "Android and Desktop are the active Milestone 1 targets",
    importMessage = "PDF import is currently enabled on Android and Desktop first.",
)
