package com.mrredhood.astracode

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography roles from AstraCode_Font_System_v1.
 *
 * The provided pack is a typography specification, not a binary font distribution.
 * Use platform fallbacks until license-reviewed TTF files are intentionally bundled:
 * Space Grotesk -> system sans-serif for brand/display, Inter -> system sans-serif for UI,
 * JetBrains Mono -> system monospace for source, terminal output, diffs and logs.
 */
internal object AstraCodeTypography {
    val DisplayFont = FontFamily.SansSerif
    val BodyFont = FontFamily.SansSerif
    val CodeFont = FontFamily.Monospace

    val Scale = Typography(
        displayLarge = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp),
        displayMedium = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
        displaySmall = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
        headlineLarge = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
        headlineMedium = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
        headlineSmall = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
        titleLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
        titleMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
        titleSmall = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        bodyLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
        bodyMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
        labelLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
        labelMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp)
    )
}
