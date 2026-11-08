package com.ryanjames.lunar

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.ryanjames.lunar.ui.shell.isSidebarToggleShortcut
import kotlinx.coroutines.flow.MutableSharedFlow
import java.awt.Dimension

private val DefaultWindowSize = DpSize(1280.dp, 800.dp)
private val MinimumWindowSize = Dimension(480, 560)

fun main() = application {
    val sidebarToggleRequests = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Lunar",
        state = rememberWindowState(size = DefaultWindowSize),
        onPreviewKeyEvent = { event ->
            if (isSidebarToggleShortcut(event)) {
                sidebarToggleRequests.tryEmit(Unit)
                true
            } else {
                false
            }
        },
    ) {
        LaunchedEffect(window) {
            window.minimumSize = MinimumWindowSize
        }
        App(sidebarToggleRequests = sidebarToggleRequests)
    }
}
