package com.profynus.music.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

private val Void = Color(0xFF050B13)
private val TextWhite = Color(0xFFF5FAFF)
private val MutedText = Color(0xFF9CB2C7)
private val BaseOutline = Color(0xFF254056)

data class ProfynusPalette(val id: String, val title: String, val accent: Color, val secondary: Color)

/** Phase-1 curated Android catalog; sync exact HEX values from desktop's catalog later. */
object ProfynusPalettes {
    val all: List<ProfynusPalette> = listOf(
        ProfynusPalette("profynus_cyan", "Profynus Cyan", Color(0xFF15CCE3), Color(0xFF2E8BFF)),
        ProfynusPalette("electric_blue", "Electric Blue", Color(0xFF3F91FF), Color(0xFF86B9FF)),
        ProfynusPalette("deep_ocean", "Deep Ocean", Color(0xFF1D9AB5), Color(0xFF286BE5)),
        ProfynusPalette("northern_lights", "Northern Lights", Color(0xFF35DAAA), Color(0xFF4D80FF)),
        ProfynusPalette("mint_fresh", "Mint Fresh", Color(0xFF60E6B2), Color(0xFF08AFC8)),
        ProfynusPalette("forest_pulse", "Forest Pulse", Color(0xFF5EC86A), Color(0xFF159B8C)),
        ProfynusPalette("lime_wire", "Lime Wire", Color(0xFFB2ED56), Color(0xFF3ECFAF)),
        ProfynusPalette("emerald", "Emerald", Color(0xFF30D691), Color(0xFF4FB38E)),
        ProfynusPalette("violet_wave", "Violet Wave", Color(0xFF9E7DFF), Color(0xFF5D73FF)),
        ProfynusPalette("cosmic_purple", "Cosmic Purple", Color(0xFFBE73F4), Color(0xFF5487FF)),
        ProfynusPalette("ultraviolet", "Ultraviolet", Color(0xFF8459FF), Color(0xFFD386FF)),
        ProfynusPalette("lavender", "Lavender", Color(0xFFC0ACFF), Color(0xFF958BFF)),
        ProfynusPalette("rose_glow", "Rose Glow", Color(0xFFFA75B9), Color(0xFFA17BFC)),
        ProfynusPalette("neon_pink", "Neon Pink", Color(0xFFFF59AD), Color(0xFFB778F4)),
        ProfynusPalette("ruby", "Ruby", Color(0xFFF66D83), Color(0xFFFF8D6D)),
        ProfynusPalette("sunset", "Sunset", Color(0xFFFF9769), Color(0xFFF75D97)),
        ProfynusPalette("amber", "Amber", Color(0xFFFFB84A), Color(0xFFFF7D4D)),
        ProfynusPalette("solar", "Solar", Color(0xFFFFD75D), Color(0xFFFC9C54)),
        ProfynusPalette("copper", "Copper", Color(0xFFECA77C), Color(0xFFD96B68)),
        ProfynusPalette("ice", "Ice", Color(0xFFB0EDFF), Color(0xFF57BDFF)),
        ProfynusPalette("silver", "Silver", Color(0xFFCEDDEB), Color(0xFF91A8BD)),
        ProfynusPalette("arctic", "Arctic", Color(0xFF70E8F6), Color(0xFF8FEED6)),
        ProfynusPalette("monochrome", "Monochrome", Color(0xFFF5F7FA), Color(0xFF9DADBF)),
        ProfynusPalette("golden_hour", "Golden Hour", Color(0xFFF6C978), Color(0xFFE88B66)),
    )
}

/** Dark text on light accents, white text on dark accents. */
internal fun Color.readableOn(): Color =
    if (luminance() > 0.5f) Color(0xFF061018) else Color.White

/**
 * Every Material 3 role is derived from the palette. Surfaces carry a faint tint of the accent,
 * so cards, nav, chips, switches and dialogs all follow the selected palette automatically.
 */
private fun profynusColorScheme(accent: Color, secondary: Color): ColorScheme {
    fun surface(amount: Float) = lerp(Void, accent, amount)
    val tertiary = lerp(accent, secondary, 0.5f)
    return darkColorScheme(
        primary = accent,
        onPrimary = accent.readableOn(),
        primaryContainer = lerp(Void, accent, 0.30f),
        onPrimaryContainer = lerp(accent, Color.White, 0.65f),
        inversePrimary = lerp(accent, Color.Black, 0.25f),
        secondary = secondary,
        onSecondary = secondary.readableOn(),
        secondaryContainer = lerp(Void, secondary, 0.26f),
        onSecondaryContainer = lerp(secondary, Color.White, 0.65f),
        tertiary = tertiary,
        onTertiary = tertiary.readableOn(),
        tertiaryContainer = lerp(Void, tertiary, 0.26f),
        onTertiaryContainer = lerp(tertiary, Color.White, 0.65f),
        background = surface(0.02f),
        onBackground = TextWhite,
        surface = surface(0.03f),
        onSurface = TextWhite,
        surfaceVariant = surface(0.10f),
        onSurfaceVariant = lerp(MutedText, accent, 0.10f),
        surfaceTint = accent,
        outline = lerp(BaseOutline, accent, 0.25f),
        outlineVariant = surface(0.14f),
        surfaceDim = Void,
        surfaceBright = surface(0.15f),
        surfaceContainerLowest = surface(0.015f),
        surfaceContainerLow = surface(0.04f),
        surfaceContainer = surface(0.065f),
        surfaceContainerHigh = surface(0.09f),
        surfaceContainerHighest = surface(0.115f),
    )
}

// Typography (Sora) now lives in Type.kt.

private val ProfynusShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun ProfynusTheme(palette: ProfynusPalette, content: @Composable () -> Unit) {
    // Palette switches cross-fade instead of snapping.
    val spec = tween<Color>(durationMillis = 500)
    val accent by animateColorAsState(palette.accent, spec, label = "accent")
    val secondary by animateColorAsState(palette.secondary, spec, label = "secondary")
    MaterialTheme(
        colorScheme = profynusColorScheme(accent, secondary),
        typography = ProfynusTypography,
        shapes = ProfynusShapes,
        content = content,
    )
}
