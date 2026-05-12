package com.ryanjames.lunar.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun rememberPlatformRuntime(): PlatformRuntime = rememberUnsupportedPlatformRuntime(
    platformName = "Web preview",
    statusLine = "Web support is planned after the Android/Desktop foundation",
    importMessage = "This build keeps the shared shell intact, but PDF import and viewing are not wired for web yet.",
)

actual fun Modifier.externalScoreDropTarget(
    enabled: Boolean,
    onDroppedPaths: (List<String>) -> Unit,
): Modifier = this
