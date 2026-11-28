package com.astracode.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Monochrome Obsidian palette. Component states use semantic content colors,
// so text and icons keep contrast when surfaces or theme choices change.
val AtelierBackground = Color(0xFF050505)
val AtelierSurface = Color(0xFF121212)
val AtelierSurfaceRaised = Color(0xFF1E1E1E)
val AtelierChampagne = Color(0xFFFFFFFF)
val AtelierText = Color(0xFFF5F5F5)
val AtelierMutedText = Color(0xFFBDBDBD)
val AtelierSage = Color(0xFFD4D4D4)
val AtelierBorder = Color(0xFF383838)
val AtelierWarning = Color(0xFFD8D8D8)
val AtelierError = Color(0xFFE7E7E7)
val AtelierErrorSurface = Color(0xFF303030)

private val AtelierColorScheme = darkColorScheme(
    primary = AtelierChampagne,
    onPrimary = AtelierBackground,
    primaryContainer = Color(0xFF292929),
    onPrimaryContainer = AtelierText,
    secondary = AtelierSage,
    onSecondary = AtelierBackground,
    secondaryContainer = Color(0xFF242424),
    onSecondaryContainer = AtelierText,
    tertiary = AtelierWarning,
    onTertiary = AtelierBackground,
    error = AtelierError,
    onError = AtelierBackground,
    errorContainer = AtelierErrorSurface,
    onErrorContainer = AtelierText,
    background = AtelierBackground,
    onBackground = AtelierText,
    surface = AtelierBackground,
    onSurface = AtelierText,
    surfaceVariant = AtelierSurface,
    onSurfaceVariant = AtelierMutedText,
    surfaceTint = AtelierChampagne,
    outline = AtelierBorder,
    outlineVariant = AtelierBorder,
    inverseSurface = AtelierText,
    inverseOnSurface = AtelierBackground,
    inversePrimary = AtelierBackground,
    surfaceDim = AtelierBackground,
    surfaceBright = Color(0xFF333333),
    surfaceContainerLowest = Color(0xFF080808),
    surfaceContainerLow = Color(0xFF101010),
    surfaceContainer = AtelierSurface,
    surfaceContainerHigh = AtelierSurfaceRaised,
    surfaceContainerHighest = Color(0xFF303030),
)

private val AtelierShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun AstraCodeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AtelierColorScheme,
        typography = Typography(),
        shapes = AtelierShapes,
        content = content,
    )
}
