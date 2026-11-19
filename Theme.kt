package com.hybridmusic.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object SpicetifyCrimsonPalette {
    val ObsidianVoid = Color(0xFF070406)
    val VelvetShadow = Color(0xFF12070B)
    val CrimsonCore = Color(0xFFFF1E42)
    val CrimsonBright = Color(0xFFFF4D6D)
    val RubyDeep = Color(0xFF9E0B28)
    val RoseNeon = Color(0xFFFF85A1)
    val PureIce = Color(0xFFF9F4F6)
    val SilverMist = Color(0xFFB8A6AE)
    val GlassSurfaceTop = Color(0x26FFFFFF)
    val GlassSurfaceMid = Color(0x73180A10)
    val GlassSurfaceBottom = Color(0x990D0508)
    val GlassBorderSubtle = Color(0x2BFFFFFF)
    val GlassBorderCrimson = Color(0x88FF1E42)
    val OnlineEmerald = Color(0xFF2EC4B6)
    val OfflineCrimson = Color(0xFFFF3355)
}

private val SpicetifyCrimsonColors = darkColorScheme(
    primary = SpicetifyCrimsonPalette.CrimsonCore,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF4A0A18),
    onPrimaryContainer = Color(0xFFFFD9DF),
    secondary = SpicetifyCrimsonPalette.CrimsonBright,
    onSecondary = Color(0xFF2B000A),
    secondaryContainer = Color(0xFF3A0D18),
    onSecondaryContainer = Color(0xFFFFD9E0),
    tertiary = SpicetifyCrimsonPalette.RoseNeon,
    onTertiary = Color(0xFF380012),
    background = SpicetifyCrimsonPalette.ObsidianVoid,
    onBackground = SpicetifyCrimsonPalette.PureIce,
    surface = Color(0xFF11070B),
    onSurface = SpicetifyCrimsonPalette.PureIce,
    surfaceVariant = Color(0xFF1E0D14),
    onSurfaceVariant = SpicetifyCrimsonPalette.SilverMist,
    error = SpicetifyCrimsonPalette.OfflineCrimson,
    errorContainer = Color(0x55660D1E),
    onErrorContainer = Color(0xFFFFD9E0)
)

@Composable
fun HybridMusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SpicetifyCrimsonColors,
        content = content
    )
}
