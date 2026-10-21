package org.holidaylink.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * The chat backgrounds a user can pick from.
 *
 * Telegram lets you choose from a gallery of built-in wallpapers and then override the
 * choice for a single conversation. HolidayLink does the same: a set of hand-picked
 * gradients and patterns ([WallpaperPresets]) that work in light and dark mode, one
 * default for every chat, and an optional per-chat override.
 */
enum class WallpaperStyle { GRADIENT, DOTS, RINGS, DIAGONAL, GRID, SPARKLE, WAVES }

data class WallpaperPreset(
    val id: String,
    val label: String,
    val colors: List<Color>,
    val style: WallpaperStyle = WallpaperStyle.GRADIENT,
    /** Telegram-style living background: the gradient slowly drifts instead of sitting still. */
    val animated: Boolean = false
)

/** The built-in gallery. The first entry is the plain app background. */
val WallpaperPresets: List<WallpaperPreset> = listOf(
    WallpaperPreset(
        "default", "Default",
        listOf(Color(0xFFF6F8FC), Color(0xFFEAF0F9)),
        WallpaperStyle.GRADIENT
    ),
    WallpaperPreset(
        "aurora", "Aurora",
        listOf(Color(0xFF1E3A8A), Color(0xFF0891B2), Color(0xFF7C3AED)),
        WallpaperStyle.GRADIENT,
        animated = true
    ),
    WallpaperPreset(
        "sunset", "Sunset",
        listOf(Color(0xFFF97316), Color(0xFFDB2777), Color(0xFF7C2D12)),
        WallpaperStyle.GRADIENT,
        animated = true
    ),
    WallpaperPreset(
        "ocean", "Ocean",
        listOf(Color(0xFF0369A1), Color(0xFF0EA5E9), Color(0xFF082F49)),
        WallpaperStyle.WAVES,
        animated = true
    ),
    WallpaperPreset(
        "forest", "Forest",
        listOf(Color(0xFF166534), Color(0xFF22C55E), Color(0xFF14532D)),
        WallpaperStyle.DOTS
    ),
    WallpaperPreset(
        "lavender", "Lavender",
        listOf(Color(0xFF6D28D9), Color(0xFFA78BFA), Color(0xFF4C1D95)),
        WallpaperStyle.RINGS,
        animated = true
    ),
    WallpaperPreset(
        "mono", "Mono",
        listOf(Color(0xFF111827), Color(0xFF4B5563)),
        WallpaperStyle.GRID
    ),
    WallpaperPreset(
        "sand", "Sand",
        listOf(Color(0xFFFDE68A), Color(0xFFF59E0B), Color(0xFFFBBF24)),
        WallpaperStyle.DIAGONAL
    ),
    WallpaperPreset(
        "mint", "Mint",
        listOf(Color(0xFF0F766E), Color(0xFF5EEAD4), Color(0xFF134E4A)),
        WallpaperStyle.SPARKLE
    ),
    WallpaperPreset(
        "rose", "Rose",
        listOf(Color(0xFF9F1239), Color(0xFFFB7185), Color(0xFF4C0519)),
        WallpaperStyle.DOTS
    ),
    WallpaperPreset(
        "sky", "Sky",
        listOf(Color(0xFF0C4A6E), Color(0xFF38BDF8), Color(0xFF075985)),
        WallpaperStyle.WAVES,
        animated = true
    ),
    WallpaperPreset(
        "paper", "Paper",
        listOf(Color(0xFFFAF7F0), Color(0xFFE7DFD0), Color(0xFFD6C9B4)),
        WallpaperStyle.GRID
    ),
    WallpaperPreset(
        "midnight", "Midnight",
        listOf(Color(0xFF0F172A), Color(0xFF1D4ED8), Color(0xFF312E81)),
        WallpaperStyle.GRADIENT,
        animated = true
    ),
    WallpaperPreset(
        "coral", "Coral",
        listOf(Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFFDBA74)),
        WallpaperStyle.DIAGONAL,
        animated = true
    )
)

/** The id of the built-in background that means "no pattern at all". */
const val WALLPAPER_DEFAULT = "default"

/** Looks a preset up by id, falling back to the default background. */
fun wallpaperPreset(id: String?): WallpaperPreset =
    WallpaperPresets.firstOrNull { it.id == id } ?: WallpaperPresets.first()

/**
 * Paints a preset: a soft gradient plus the pattern that gives it its character.
 * Kept as vector drawing so it costs nothing and never needs downloading.
 *
 * Telegram-style "living" presets ([WallpaperPreset.animated]) slowly drift: the gradient
 * rotates through its hues while two soft glow blobs glide across the screen. Everything
 * is a handful of cheap canvas draws per frame, and the picker swatches opt out via
 * `animate = false` so a wall of thumbnails doesn't spin on its own.
 */
@Composable
fun WallpaperBackdrop(presetId: String, modifier: Modifier = Modifier, animate: Boolean = true) {
    val preset = wallpaperPreset(presetId)
    val phase = if (animate && preset.animated) {
        val transition = rememberInfiniteTransition(label = "wallpaper")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 14_000, easing = LinearEasing)),
            label = "wallpaper-phase"
        ).value
    } else {
        0f
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val colors = preset.colors
        // The gradient's endpoints sweep a slow circle, so the colours keep moving.
        val angle = phase * (2f * Math.PI).toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val reach = maxOf(w, h) * 0.9f
        val start = Offset(cx + cos(angle) * reach, cy + sin(angle) * reach)
        val end = Offset(cx - cos(angle) * reach, cy - sin(angle) * reach)
        val brush = Brush.linearGradient(colors, start, end)
        drawRect(brush)
        if (phase != 0f) {
            // Two drifting glow blobs for depth (the "animated wallpapers" feel).
            val glow = colors[1]
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(glow.copy(alpha = 0.35f), glow.copy(alpha = 0f)),
                    center = Offset(cx + cos(angle) * w * 0.4f, cy + sin(angle * 1.3f) * h * 0.4f),
                    radius = maxOf(w, h) * 0.5f
                ),
                radius = maxOf(w, h) * 0.5f,
                center = Offset(cx + cos(angle) * w * 0.4f, cy + sin(angle * 1.3f) * h * 0.4f)
            )
            val glow2 = colors[colors.size - 1]
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(glow2.copy(alpha = 0.28f), glow2.copy(alpha = 0f)),
                    center = Offset(cx - cos(angle * 0.7f) * w * 0.45f, cy - sin(angle) * h * 0.45f),
                    radius = maxOf(w, h) * 0.45f
                ),
                radius = maxOf(w, h) * 0.45f,
                center = Offset(cx - cos(angle * 0.7f) * w * 0.45f, cy - sin(angle) * h * 0.45f)
            )
        }
        val light = Color.White.copy(alpha = 0.12f)
        when (preset.style) {
            WallpaperStyle.DOTS -> {
                val step = 46f
                var y = step / 2
                while (y < h) {
                    var x = step / 2
                    while (x < w) {
                        drawCircle(light, radius = 3.2f, center = Offset(x, y))
                        x += step
                    }
                    y += step
                }
            }
            WallpaperStyle.RINGS -> {
                val step = 120f
                var y = step / 3
                while (y < h) {
                    var x = step / 3
                    while (x < w) {
                        drawCircle(Color.White.copy(alpha = 0.10f), radius = step / 3, center = Offset(x, y), style = Stroke(width = 2.5f))
                        x += step
                    }
                    y += step
                }
            }
            WallpaperStyle.DIAGONAL -> {
                val step = 34f
                var i = -h
                while (i < w + h) {
                    drawLine(
                        Color.White.copy(alpha = 0.10f),
                        Offset(i, h),
                        Offset(i + h, 0f),
                        strokeWidth = 6f
                    )
                    i += step
                }
            }
            WallpaperStyle.GRID -> {
                val step = 54f
                var x = 0f
                while (x < w) {
                    drawLine(Color.White.copy(alpha = 0.08f), Offset(x, 0f), Offset(x, h), strokeWidth = 1.4f)
                    x += step
                }
                var y = 0f
                while (y < h) {
                    drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, y), Offset(w, y), strokeWidth = 1.4f)
                    y += step
                }
            }
            WallpaperStyle.SPARKLE -> {
                val step = 78f
                var y = step / 2
                var row = 0
                while (y < h) {
                    var x = if (row % 2 == 0) step / 2 else step
                    while (x < w) {
                        drawCircle(Color.White.copy(alpha = 0.22f), radius = 2.6f, center = Offset(x, y))
                        x += step
                    }
                    y += step
                    row++
                }
            }
            WallpaperStyle.WAVES -> {
                val step = 70f
                var y = step
                while (y < h + step) {
                    drawArc(
                        Color.White.copy(alpha = 0.10f),
                        startAngle = 200f,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(-step / 2, y - step / 2),
                        size = androidx.compose.ui.geometry.Size(step, step),
                        style = Stroke(width = 2.5f)
                    )
                    y += step
                }
            }
            WallpaperStyle.GRADIENT -> Unit
        }
    }
}

/** A small square preview of a preset, used in the pickers. */
@Composable
fun WallpaperSwatch(
    preset: WallpaperPreset,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
    ) {
        WallpaperBackdrop(preset.id, Modifier.fillMaxSize(), animate = false)
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Text(
            preset.label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * The wallpaper picker used from Settings (default for every chat) and from a chat
 * (this conversation only).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperSheet(
    current: String,
    title: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    extra: (@Composable () -> Unit)? = null
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Pick a background. It stays on this phone only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().height(320.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(WallpaperPresets, key = { it.id }) { preset ->
                    WallpaperSwatch(preset, preset.id == current) {
                        onPick(preset.id)
                        onDismiss()
                    }
                }
            }
            if (extra != null) {
                Spacer(Modifier.height(10.dp))
                extra()
            }
        }
    }
}
