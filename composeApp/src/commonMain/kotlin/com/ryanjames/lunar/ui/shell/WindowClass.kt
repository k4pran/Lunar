package com.ryanjames.lunar.ui.shell

/** Width buckets that decide which form of the sidebar the app shell shows. */
enum class WindowClass {
    /** Below 600dp: slim top bar with the sidebar in a drawer. */
    COMPACT,

    /** 600dp up to 1023dp: icon rail. */
    MEDIUM,

    /** 1024dp and wider: full sidebar unless the user collapsed it. */
    EXPANDED,
}

internal const val MediumWindowMinWidthDp = 600f
internal const val ExpandedWindowMinWidthDp = 1024f

fun windowClassFor(widthDp: Float): WindowClass = when {
    widthDp < MediumWindowMinWidthDp -> WindowClass.COMPACT
    widthDp < ExpandedWindowMinWidthDp -> WindowClass.MEDIUM
    else -> WindowClass.EXPANDED
}

/** The form the sidebar takes for a window class and the user's collapse preference. */
enum class SidebarForm {
    DRAWER,
    RAIL,
    EXPANDED,
}

fun sidebarFormFor(
    windowClass: WindowClass,
    collapsed: Boolean,
): SidebarForm = when (windowClass) {
    WindowClass.COMPACT -> SidebarForm.DRAWER
    WindowClass.MEDIUM -> SidebarForm.RAIL
    WindowClass.EXPANDED -> if (collapsed) SidebarForm.RAIL else SidebarForm.EXPANDED
}
