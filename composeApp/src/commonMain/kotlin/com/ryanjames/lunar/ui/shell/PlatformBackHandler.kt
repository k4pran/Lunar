package com.ryanjames.lunar.ui.shell

import androidx.compose.runtime.Composable

/** Routes the platform's system Back action to [onBack] while [enabled]; a no-op where there is none. */
@Composable
internal expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
