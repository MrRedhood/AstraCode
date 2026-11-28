package com.astracode.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** Shared monochrome palette for drawers and reusable editor components. */
object AstraColors {
    val Primary = Color(0xFFFFFFFF)
    val Secondary = Color(0xFFD4D4D4)
    val Tertiary = Color(0xFFEAEAEA)
    val Warning = Color(0xFFD0D0D0)
    val Error = Color(0xFFE7E7E7)

    val Dark = Color(0xFF050505)
    val Drawer = Color(0xFF121212)
    val Card = Color(0xFF1E1E1E)
    val Border = Color(0xFF383838)
    val Selected = Color(0xFF292929)
    val Hover = Color(0xFF242424)
    val Text = Color(0xFFF5F5F5)
    val Muted = Color(0xFFBDBDBD)

    val Inset = Color(0xFF080808)
    val Dock = Color(0xFF101010)
    val BufferHeader = Color(0xFF101010)
    val CardGradientStart = Color(0xFF171717)
    val IconBadgeStart = Color(0xFF292929)
    val IconBadgeEnd = Color(0xFF171717)
    val HighlightedWorkspace = Color(0xFF292929)

    // File badges retain a clear hierarchy without introducing unrelated hues.
    val RustBadgeBg = Color(0xFF303030)
    val RustBadgeFg = Color(0xFFFFFFFF)
    val TomlBadgeBg = Color(0xFF383838)
    val TomlBadgeFg = Color(0xFFFFFFFF)

    val DrawerScrim = Color(0xCC000000)
}

object AstraFonts {
    val Mono: FontFamily = FontFamily.Monospace
    val Sans: FontFamily = FontFamily.SansSerif
}

private val AstraDarkScheme = darkColorScheme(
    primary = AstraColors.Primary,
    onPrimary = AstraColors.Dark,
    primaryContainer = AstraColors.Selected,
    onPrimaryContainer = AstraColors.Text,
    secondary = AstraColors.Secondary,
    onSecondary = AstraColors.Dark,
    secondaryContainer = AstraColors.Hover,
    onSecondaryContainer = AstraColors.Text,
    tertiary = AstraColors.Tertiary,
    onTertiary = AstraColors.Dark,
    error = AstraColors.Error,
    onError = AstraColors.Dark,
    errorContainer = Color(0xFF303030),
    onErrorContainer = AstraColors.Text,
    background = AstraColors.Dark,
    onBackground = AstraColors.Text,
    surface = AstraColors.Dark,
    onSurface = AstraColors.Text,
    surfaceVariant = AstraColors.Drawer,
    onSurfaceVariant = AstraColors.Muted,
    surfaceTint = AstraColors.Primary,
    outline = AstraColors.Border,
    outlineVariant = AstraColors.Border,
    inverseSurface = AstraColors.Text,
    inverseOnSurface = AstraColors.Dark,
    inversePrimary = AstraColors.Dark,
    surfaceDim = AstraColors.Dark,
    surfaceBright = Color(0xFF333333),
    surfaceContainerLowest = AstraColors.Inset,
    surfaceContainerLow = AstraColors.Dock,
    surfaceContainer = AstraColors.Drawer,
    surfaceContainerHigh = AstraColors.Card,
    surfaceContainerHighest = Color(0xFF303030),
)

@Composable
fun AstraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AstraDarkScheme, content = content)
}
