package com.profynus.music.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.profynus.music.R

/**
 * Sora (SIL Open Font License 1.1), bundled in res/font so it works offline.
 * Wide geometric letterforms with round, open shapes. They sit well next to the thin neon
 * line-art of the logo, and tracked-out capitals look good as a wordmark.
 */
val Sora = FontFamily(
    Font(R.font.sora_regular, FontWeight.Normal),
    Font(R.font.sora_medium, FontWeight.Medium),
    Font(R.font.sora_semibold, FontWeight.SemiBold),
    Font(R.font.sora_bold, FontWeight.Bold),
)

// One line to change if you later want a calmer body face (e.g. Inter) for long track titles.
private val DisplayFamily: FontFamily = Sora
private val BodyFamily: FontFamily = Sora

private fun style(
    family: FontFamily,
    weight: FontWeight,
    size: Int,
    line: Int,
    tracking: Double = 0.0,
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

/** Sora runs wide, so sizes are a notch smaller than stock Material and tracking is tighter. */
internal val ProfynusTypography = Typography(
    displayLarge = style(DisplayFamily, FontWeight.Bold, 52, 60, -1.0),
    displayMedium = style(DisplayFamily, FontWeight.Bold, 42, 50, -0.8),
    displaySmall = style(DisplayFamily, FontWeight.Bold, 32, 38, -0.6),
    headlineLarge = style(DisplayFamily, FontWeight.Bold, 30, 36, -0.5),
    headlineMedium = style(DisplayFamily, FontWeight.Bold, 26, 32, -0.4),
    headlineSmall = style(DisplayFamily, FontWeight.Bold, 22, 28, -0.3),
    titleLarge = style(DisplayFamily, FontWeight.SemiBold, 19, 26, -0.1),
    titleMedium = style(DisplayFamily, FontWeight.SemiBold, 15, 22),
    titleSmall = style(DisplayFamily, FontWeight.SemiBold, 14, 20),
    bodyLarge = style(BodyFamily, FontWeight.Normal, 15, 24),
    bodyMedium = style(BodyFamily, FontWeight.Normal, 13, 20),
    bodySmall = style(BodyFamily, FontWeight.Normal, 12, 16, 0.1),
    labelLarge = style(BodyFamily, FontWeight.SemiBold, 14, 20, 0.1),
    labelMedium = style(BodyFamily, FontWeight.SemiBold, 11, 16, 1.4),
    labelSmall = style(BodyFamily, FontWeight.SemiBold, 10, 14, 1.0),
)

/** "PROFYNUS" lettering: Sora SemiBold with wide tracking. */
internal val ProfynusWordmarkStyle: TextStyle =
    style(DisplayFamily, FontWeight.SemiBold, 16, 20, 4.0)
