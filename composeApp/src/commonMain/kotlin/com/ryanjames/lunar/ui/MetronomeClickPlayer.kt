package com.ryanjames.lunar.ui

import androidx.compose.runtime.Composable

interface MetronomeClickPlayer {
    fun playClick(accented: Boolean)

    fun stop() = Unit
}

object SilentMetronomeClickPlayer : MetronomeClickPlayer {
    override fun playClick(accented: Boolean) = Unit
}

@Composable
expect fun rememberMetronomeClickPlayer(): MetronomeClickPlayer
