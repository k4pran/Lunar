package com.ryanjames.lunar.ui.design

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ryanjames.lunar.composeapp.generated.resources.Res
import com.ryanjames.lunar.composeapp.generated.resources.lucide_book_open
import com.ryanjames.lunar.composeapp.generated.resources.lucide_check
import com.ryanjames.lunar.composeapp.generated.resources.lucide_chevron_down
import com.ryanjames.lunar.composeapp.generated.resources.lucide_chevron_left
import com.ryanjames.lunar.composeapp.generated.resources.lucide_chevron_right
import com.ryanjames.lunar.composeapp.generated.resources.lucide_chevron_up
import com.ryanjames.lunar.composeapp.generated.resources.lucide_cloud
import com.ryanjames.lunar.composeapp.generated.resources.lucide_columns_2
import com.ryanjames.lunar.composeapp.generated.resources.lucide_ellipsis
import com.ryanjames.lunar.composeapp.generated.resources.lucide_eye_off
import com.ryanjames.lunar.composeapp.generated.resources.lucide_folder
import com.ryanjames.lunar.composeapp.generated.resources.lucide_folder_input
import com.ryanjames.lunar.composeapp.generated.resources.lucide_grip_vertical
import com.ryanjames.lunar.composeapp.generated.resources.lucide_hard_drive
import com.ryanjames.lunar.composeapp.generated.resources.lucide_library_big
import com.ryanjames.lunar.composeapp.generated.resources.lucide_list_music
import com.ryanjames.lunar.composeapp.generated.resources.lucide_list_plus
import com.ryanjames.lunar.composeapp.generated.resources.lucide_metronome
import com.ryanjames.lunar.composeapp.generated.resources.lucide_panel_left_close
import com.ryanjames.lunar.composeapp.generated.resources.lucide_panel_left_open
import com.ryanjames.lunar.composeapp.generated.resources.lucide_pen_line
import com.ryanjames.lunar.composeapp.generated.resources.lucide_search
import com.ryanjames.lunar.composeapp.generated.resources.lucide_settings
import com.ryanjames.lunar.composeapp.generated.resources.lucide_star
import com.ryanjames.lunar.composeapp.generated.resources.lucide_zoom_in
import com.ryanjames.lunar.composeapp.generated.resources.lucide_zoom_out
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.vectorResource

/**
 * The app icon set, drawn from Lucide (ISC licence, see composeResources/files/licenses). Add new
 * icons here rather than drawing them on a Canvas so every screen shares one stroke style.
 */
enum class LunarIconKind(internal val resource: DrawableResource) {
    LIBRARY(Res.drawable.lucide_library_big),
    SETLISTS(Res.drawable.lucide_list_music),
    SONGBOOKS(Res.drawable.lucide_book_open),
    COMPOSE(Res.drawable.lucide_pen_line),
    SOURCES(Res.drawable.lucide_folder_input),
    SETTINGS(Res.drawable.lucide_settings),
    SIDEBAR_COLLAPSE(Res.drawable.lucide_panel_left_close),
    SIDEBAR_EXPAND(Res.drawable.lucide_panel_left_open),
    SEARCH(Res.drawable.lucide_search),
    TWO_PAGE(Res.drawable.lucide_columns_2),
    ZOOM_IN(Res.drawable.lucide_zoom_in),
    ZOOM_OUT(Res.drawable.lucide_zoom_out),
    METRONOME(Res.drawable.lucide_metronome),
    STAR(Res.drawable.lucide_star),
    ADD_TO_LIST(Res.drawable.lucide_list_plus),
    HIDE(Res.drawable.lucide_eye_off),
    MORE(Res.drawable.lucide_ellipsis),
    CHEVRON_LEFT(Res.drawable.lucide_chevron_left),
    CHEVRON_RIGHT(Res.drawable.lucide_chevron_right),
    CHEVRON_UP(Res.drawable.lucide_chevron_up),
    CHEVRON_DOWN(Res.drawable.lucide_chevron_down),
    DRAG_HANDLE(Res.drawable.lucide_grip_vertical),
    CHECK(Res.drawable.lucide_check),
    FOLDER(Res.drawable.lucide_folder),
    CLOUD(Res.drawable.lucide_cloud),
    DRIVE(Res.drawable.lucide_hard_drive),
}

/**
 * Renders a [LunarIconKind] tinted with the current content colour. Pass a [contentDescription]
 * when the icon is the only label on a control; leave it null for decorative icons.
 */
@Composable
fun LunarIcon(
    kind: LunarIconKind,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        imageVector = vectorResource(kind.resource),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}
