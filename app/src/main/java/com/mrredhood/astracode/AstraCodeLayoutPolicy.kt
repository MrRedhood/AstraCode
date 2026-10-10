package com.mrredhood.astracode

internal enum class AstraCodeNavigationMode { BottomBar, NavigationRail }

/** A single breakpoint policy used by navigation and deterministic layout tests. */
internal object AstraCodeLayoutPolicy {
    const val NAVIGATION_RAIL_MIN_WIDTH_DP = 600f

    fun usesBottomNavigation(availableWidthDp: Float): Boolean =
        availableWidthDp < NAVIGATION_RAIL_MIN_WIDTH_DP

    fun navigationMode(availableWidthDp: Float): AstraCodeNavigationMode =
        if (usesBottomNavigation(availableWidthDp)) {
            AstraCodeNavigationMode.BottomBar
        } else {
            AstraCodeNavigationMode.NavigationRail
        }
}
