package com.hybridmusic.player.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hybridmusic.player.ui.theme.SpicetifyCrimsonPalette

@Composable
fun SpicetifyCrimsonGlassBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "spicetify_crimson_bg")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "crimson_ambient"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1. Spicetify Deep Obsidian-Crimson Base Gradient
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF19060B),
                    Color(0xFF0C0407),
                    Color(0xFF070305),
                    Color(0xFF0E0407)
                )
            )
        )

        // 2. Top-Left Spicetify Dynamic Crimson Header Glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x52FF1E42),
                    Color(0x249E0B28),
                    Color.Transparent
                ),
                center = Offset(w * 0.22f, h * 0.12f),
                radius = w * 0.88f * glowPulse
            ),
            radius = w * 0.88f * glowPulse,
            center = Offset(w * 0.22f, h * 0.12f)
        )

        // 3. Mid-Right Ruby Ambient Light Refraction
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x33D90429),
                    Color(0x18590D22),
                    Color.Transparent
                ),
                center = Offset(w * 0.88f, h * 0.46f),
                radius = w * 0.72f
            ),
            radius = w * 0.72f,
            center = Offset(w * 0.88f, h * 0.46f)
        )

        // 4. Bottom Dock Crimson Under-Glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x38FF1E42),
                    Color(0x14800F2F),
                    Color.Transparent
                ),
                center = Offset(w * 0.50f, h * 0.96f),
                radius = w * 0.85f
            ),
            radius = w * 0.85f,
            center = Offset(w * 0.50f, h * 0.96f)
        )
    }
}

@Composable
fun SpicetifyGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    activeCrimson: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val borderBrush = if (activeCrimson) {
        Brush.linearGradient(
            colors = listOf(
                SpicetifyCrimsonPalette.CrimsonCore.copy(alpha = 0.92f),
                Color.White.copy(alpha = 0.28f),
                SpicetifyCrimsonPalette.CrimsonBright.copy(alpha = 0.65f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.22f),
                SpicetifyCrimsonPalette.CrimsonCore.copy(alpha = 0.22f),
                Color.White.copy(alpha = 0.06f)
            )
        )
    }

    val fillBrush = if (activeCrimson) {
        Brush.linearGradient(
            colors = listOf(
                Color(0x38FF1E42),
                Color(0x881C0A10),
                Color(0xA6100508)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0x22FFFFFF),
                Color(0x78170A0F),
                Color(0x990D0508)
            )
        )
    }

    val clickableModifier = if (onClick != null) {
        modifier
            .clip(shape)
            .clickable { onClick() }
    } else {
        modifier.clip(shape)
    }

    Box(
        modifier = clickableModifier
            .background(brush = fillBrush, shape = shape)
            .border(width = 1.dp, brush = borderBrush, shape = shape),
        content = content
    )
}

@Composable
fun NetworkStatusPill(isOnline: Boolean) {
    val accent = if (isOnline) SpicetifyCrimsonPalette.OnlineEmerald else SpicetifyCrimsonPalette.OfflineCrimson
    val text = if (isOnline) "ONLINE" else "OFFLINE"

    Surface(
        shape = RoundedCornerShape(50),
        color = accent.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = text,
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
        }
    }
}

@Composable
fun CrimsonEqualizerBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = SpicetifyCrimsonPalette.CrimsonCore
) {
    val transition = rememberInfiniteTransition(label = "crimson_eq")
    val bar1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "eq1"
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.20f,
        animationSpec = infiniteRepeatable(tween(340), RepeatMode.Reverse),
        label = "eq2"
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "eq3"
    )
    val bar4 by transition.animateFloat(
        initialValue = 0.70f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(380), RepeatMode.Reverse),
        label = "eq4"
    )

    val levels = if (isPlaying) listOf(bar1, bar2, bar3, bar4) else listOf(0.25f, 0.40f, 0.30f, 0.22f)

    Canvas(modifier = modifier.size(width = 20.dp, height = 16.dp)) {
        val barWidth = size.width / 7f
        val gap = barWidth
        levels.forEachIndexed { index, ratio ->
            val x = index * (barWidth + gap) + barWidth / 2f
            val barH = size.height * ratio
            drawLine(
                color = barColor,
                start = Offset(x, size.height),
                end = Offset(x, size.height - barH),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun SpicetifyGlassArtworkFrame(
    imageModel: Any,
    title: String,
    isPlaying: Boolean,
    artworkSize: Dp = 240.dp
) {
    val glowTransition = rememberInfiniteTransition(label = "art_glow")
    val glowAlpha by glowTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "art_aura"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(artworkSize + 20.dp)
            .shadow(
                elevation = if (isPlaying) 24.dp else 10.dp,
                shape = RoundedCornerShape(26.dp),
                ambientColor = SpicetifyCrimsonPalette.CrimsonCore.copy(alpha = glowAlpha),
                spotColor = SpicetifyCrimsonPalette.CrimsonCore.copy(alpha = glowAlpha)
            )
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0x33FFFFFF),
                        Color(0x55FF1E42),
                        Color(0x4414060A)
                    )
                )
            )
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.38f),
                        SpicetifyCrimsonPalette.CrimsonCore.copy(alpha = 0.85f),
                        SpicetifyCrimsonPalette.CrimsonBright.copy(alpha = 0.45f)
                    )
                ),
                shape = RoundedCornerShape(26.dp)
            )
            .padding(10.dp)
    ) {
        AsyncImage(
            model = imageModel,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
        )
    }
}
