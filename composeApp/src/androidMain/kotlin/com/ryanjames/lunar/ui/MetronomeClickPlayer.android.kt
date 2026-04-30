package com.ryanjames.lunar.ui

import androidx.compose.runtime.Composable

@Composable
actual fun rememberMetronomeClickPlayer(): MetronomeClickPlayer = SilentMetronomeClickPlayer
