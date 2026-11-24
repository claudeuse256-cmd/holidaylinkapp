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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import org.holidaylink.app.data.AppSettings
import kotlin.math.cos
import kotlin.math.sin

/**
 * The chat backgrounds a user can pick from.
 *
 * Telegram's gallery is the model here: a soft two-tone wash with a quiet repeating doodle
 * printed over it, not a loud rainbow gradient. Every preset works in light and dark themes
 * because the pattern ink flips with the base colour, and everything is vector-drawn so it
 * costs nothing and never needs downloading.
 */
enum class WallpaperStyle { GRADIENT, DOODLE, TILES, DOTS, RINGS, DIAGONAL, GRID, SPARKLE, WAVES }

data class WallpaperPreset(
    val id: String,
    val label: String,
    val colors: List<Color>,
    val style: WallpaperStyle = WallpaperStyle.DOODLE,
    /** Telegram-style living background: the wash slowly drifts instead of sitting still. */
    val animated: Boolean = false
)

/** The built-in gallery. The first entry is the default design used by every chat. */
val WallpaperPresets: List<WallpaperPreset> = listOf(
    // Telegram-like classic chat background: soft blue-green tint with doodle pattern.
    WallpaperPreset(
        "default", "Classic",
        listOf(Color(0xFFDFE9F2), Color(0xFFE9F3EC), Color(0xFFD8E6F0)),
        WallpaperStyle.DOODLE,
        animated = false
    ),
    WallpaperPreset(
        "sky", "Sky",
        listOf(Color(0xFFA9BFD6), Color(0xFFC3D4E4), Color(0xFF9DB4CE)),
        WallpaperStyle.DOODLE
    ),
    WallpaperPreset(
        "sage", "Sage",
        listOf(Color(0xFFC2CFB4), Color(0xFFD6DFC8), Color(0xFFB4C3A4)),
        WallpaperStyle.DOODLE
    ),
    WallpaperPreset(
        "sand", "Sand",
        listOf(Color(0xFFE6D9C3), Color(0xFFF0E6D4), Color(0xFFDACBB2)),
        WallpaperStyle.TILES
    ),
    WallpaperPreset(
        "rose", "Rose",
        listOf(Color(0xFFE6C9C9), Color(0xFFF1DCDC), Color(0xFFDCB9B9)),
        WallpaperStyle.DOODLE
    ),
    WallpaperPreset(
        "mint", "Mint",
        listOf(Color(0xFFBFDCD2), Color(0xFFD4E8E0), Color(0xFFAECFC4)),
        WallpaperStyle.TILES
    ),
    WallpaperPreset(
        "lavender", "Lavender",
        listOf(Color(0xFFCFC6E0), Color(0xFFDED7EC), Color(0xFFBFB4D6)),
        WallpaperStyle.DOODLE
    ),
    WallpaperPreset(
        "peach", "Peach",
        listOf(Color(0xFFEED0BF), Color(0xFFF7E1D4), Color(0xFFE4BFA9)),
        WallpaperStyle.DOODLE
    ),
    WallpaperPreset(
        "paper", "Paper",
        listOf(Color(0xFFF1EDE4), Color(0xFFF7F4EE), Color(0xFFE8E2D6)),
        WallpaperStyle.GRID
    ),
    WallpaperPreset(
        "mono", "Mono",
        listOf(Color(0xFFE3E5E8), Color(0xFFEFF0F2), Color(0xFFDCDEE2)),
        WallpaperStyle.TILES
    ),
    WallpaperPreset(
        "night", "Night",
        listOf(Color(0xFF1A2430), Color(0xFF22303E), Color(0xFF16202A)),
        WallpaperStyle.DOODLE
    ),
    WallpaperPreset(
        "midnight", "Midnight",
        listOf(Color(0xFF16233A), Color(0xFF1E3150), Color(0xFF121C2D)),
        WallpaperStyle.DOODLE
    ),
    WallpaperPreset(
        "forest", "Forest",
        listOf(Color(0xFF1B2A22), Color(0xFF23382C), Color(0xFF16241C)),
        WallpaperStyle.DOODLE
    ),
    WallpaperPreset(
        "aurora", "Aurora",
        listOf(Color(0xFF23325C), Color(0xFF12566E), Color(0xFF3B2A63)),
        WallpaperStyle.DOODLE,
        animated = true
    ),
    WallpaperPreset(
        "sunset", "Sunset",
        listOf(Color(0xFF7A3B34), Color(0xFF9C4A52), Color(0xFF5C2C33)),
        WallpaperStyle.DOODLE,
        animated = true
    ),
    WallpaperPreset(
        "ocean", "Ocean",
        listOf(Color(0xFF12455C), Color(0xFF1A6480), Color(0xFF0E3648)),
        WallpaperStyle.WAVES,
        animated = true
    ),
    WallpaperPreset(
        "coral", "Coral",
        listOf(Color(0xFF8C4A3C), Color(0xFFA85E4A), Color(0xFF6F372C)),
        WallpaperStyle.TILES,
        animated = true
    ),
    WallpaperPreset(
        "graphite", "Graphite",
        listOf(Color(0xFF23262B), Color(0xFF2E3238), Color(0xFF1B1E22)),
        WallpaperStyle.GRID
    )
)

/** The id of the built-in background that means "the default design". */
const val WALLPAPER_DEFAULT = "default"

/** Looks a preset up by id, falling back to the default background. */
fun wallpaperPreset(id: String?): WallpaperPreset =
    WallpaperPresets.firstOrNull { it.id == id } ?: WallpaperPresets.first()

/** True when the preset's wash is light, so the pattern ink should be dark. */
private fun WallpaperPreset.isLight(): Boolean = colors.first().luminance() > 0.45f

/**
 * Paints a preset: a soft wash plus the pattern that gives it its character.
 * Kept as vector drawing so it costs nothing and never needs downloading.
 *
 * Telegram-style "living" presets ([WallpaperPreset.animated]) slowly drift: the wash
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
        val reach = maxOf(w, h) * 0.75f
        val start = Offset(cx + cos(angle) * reach, cy + sin(angle) * reach)
        val end = Offset(cx - cos(angle) * reach, cy - sin(angle) * reach)
        drawRect(Brush.linearGradient(colors, start, end))
        if (phase != 0f) {
            // Two drifting glow blobs for depth (the "animated wallpapers" feel).
            val glow = colors[1]
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(glow.copy(alpha = 0.30f), glow.copy(alpha = 0f)),
                    center = Offset(cx + cos(angle) * w * 0.4f, cy + sin(angle * 1.3f) * h * 0.4f),
                    radius = maxOf(w, h) * 0.5f
                ),
                radius = maxOf(w, h) * 0.5f,
                center = Offset(cx + cos(angle) * w * 0.4f, cy + sin(angle * 1.3f) * h * 0.4f)
            )
            val glow2 = colors[colors.size - 1]
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(glow2.copy(alpha = 0.24f), glow2.copy(alpha = 0f)),
                    center = Offset(cx - cos(angle * 0.7f) * w * 0.45f, cy - sin(angle) * h * 0.45f),
                    radius = maxOf(w, h) * 0.45f
                ),
                radius = maxOf(w, h) * 0.45f,
                center = Offset(cx - cos(angle * 0.7f) * w * 0.45f, cy - sin(angle) * h * 0.45f)
            )
        }
        // Light washes take dark ink, dark washes take white ink, both at doodle strength.
        val ink = if (preset.isLight()) Color(0xFF526B78).copy(alpha = 0.075f) else Color.White.copy(alpha = 0.085f)
        when (preset.style) {
            WallpaperStyle.DOODLE -> {
                // Small, spaced line-art doodles like a messaging-app wallpaper.
                // Keep motifs around 8–12dp on a typical phone instead of oversized shapes.
                val cell = 82f
                var row = 0
                var y = cell * 0.50f
                while (y < h + cell) {
                    var x = if (row % 2 == 0) cell * 0.35f else cell * 0.85f
                    var col = 0
                    while (x < w + cell) {
                        doodleMotif(Offset(x, y), 11.5f, row * 7 + col, ink)
                        x += cell
                        col++
                    }
                    y += cell
                    row++
                }
            }
            WallpaperStyle.TILES -> {
                val cell = 64f
                var row = 0
                var y = 0f
                while (y < h + cell) {
                    var x = if (row % 2 == 0) 0f else -cell / 2
                    while (x < w + cell) {
                        drawRoundRect(
                            color = ink,
                            topLeft = Offset(x + 3f, y + 3f),
                            size = Size(cell - 6f, cell - 6f),
                            cornerRadius = CornerRadius(cell * 0.22f)
                        )
                        x += cell
                    }
                    y += cell
                    row++
                }
            }
            WallpaperStyle.DOTS -> {
                val step = 46f
                var y = step / 2
                while (y < h) {
                    var x = step / 2
                    while (x < w) {
                        drawCircle(ink, radius = 2.6f, center = Offset(x, y))
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
                        drawCircle(ink, radius = step / 3, center = Offset(x, y), style = Stroke(width = 2.2f))
                        x += step
                    }
                    y += step
                }
            }
            WallpaperStyle.DIAGONAL -> {
                val step = 34f
                var i = -h
                while (i < w + h) {
                    drawLine(ink, Offset(i, h), Offset(i + h, 0f), strokeWidth = 6f)
                    i += step
                }
            }
            WallpaperStyle.GRID -> {
                val step = 54f
                var x = 0f
                while (x < w) {
                    drawLine(ink, Offset(x, 0f), Offset(x, h), strokeWidth = 1.2f)
                    x += step
                }
                var y = 0f
                while (y < h) {
                    drawLine(ink, Offset(0f, y), Offset(w, y), strokeWidth = 1.2f)
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
                        drawCircle(ink, radius = 2.4f, center = Offset(x, y))
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
                        ink,
                        startAngle = 200f,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(-step / 2, y - step / 2),
                        size = Size(step, step),
                        style = Stroke(width = 2.2f)
                    )
                    y += step
                }
            }
            WallpaperStyle.GRADIENT -> Unit
        }
    }
}

/**
 * One doodle stamp: a ring, a plus, a diamond, a star, a crescent or a triangle, picked
 * from [index] so neighbouring cells never repeat the same shape.
 */
private fun DrawScope.doodleMotif(center: Offset, radius: Float, index: Int, ink: Color) {
    val stroke = 1.35f
    when (index % 8) {
        0 -> {
            // Tiny speech bubble.
            val left = center.x - radius
            val top = center.y - radius * 0.68f
            drawRoundRect(ink, Offset(left, top), Size(radius * 1.8f, radius * 1.25f),
                CornerRadius(radius * 0.28f), style = Stroke(stroke))
            val tail = Path().apply {
                moveTo(center.x - radius * 0.35f, center.y + radius * 0.55f)
                lineTo(center.x - radius * 0.62f, center.y + radius * 0.95f)
                lineTo(center.x + radius * 0.05f, center.y + radius * 0.55f)
            }
            drawPath(tail, ink, style = Stroke(stroke))
        }
        1 -> {
            // Heart outline.
            val path = Path().apply {
                moveTo(center.x, center.y + radius * 0.85f)
                cubicTo(center.x - radius * 1.7f, center.y - radius * 0.15f,
                    center.x - radius * 0.7f, center.y - radius * 1.35f, center.x, center.y - radius * 0.45f)
                cubicTo(center.x + radius * 0.7f, center.y - radius * 1.35f,
                    center.x + radius * 1.7f, center.y - radius * 0.15f, center.x, center.y + radius * 0.85f)
            }
            drawPath(path, ink, style = Stroke(stroke))
        }
        2 -> {
            // Paper plane.
            val path = Path().apply {
                moveTo(center.x - radius, center.y - radius * 0.45f)
                lineTo(center.x + radius, center.y - radius)
                lineTo(center.x + radius * 0.2f, center.y + radius)
                lineTo(center.x - radius * 0.05f, center.y + radius * 0.12f)
                close()
                moveTo(center.x - radius * 0.05f, center.y + radius * 0.12f)
                lineTo(center.x + radius, center.y - radius)
            }
            drawPath(path, ink, style = Stroke(stroke))
        }
        3 -> {
            // Smiley face.
            drawCircle(ink, radius * 0.9f, center, style = Stroke(stroke))
            drawCircle(ink, 0.75f, Offset(center.x - radius * 0.3f, center.y - radius * 0.15f))
            drawCircle(ink, 0.75f, Offset(center.x + radius * 0.3f, center.y - radius * 0.15f))
            drawArc(ink, 15f, 150f, false,
                Offset(center.x - radius * 0.42f, center.y - radius * 0.05f),
                Size(radius * 0.84f, radius * 0.7f), style = Stroke(stroke))
        }
        4 -> {
            // Little four-point sparkle.
            val path = Path().apply {
                moveTo(center.x, center.y - radius)
                quadraticTo(center.x + radius * 0.12f, center.y - radius * 0.12f, center.x + radius, center.y)
                quadraticTo(center.x + radius * 0.12f, center.y + radius * 0.12f, center.x, center.y + radius)
                quadraticTo(center.x - radius * 0.12f, center.y + radius * 0.12f, center.x - radius, center.y)
                quadraticTo(center.x - radius * 0.12f, center.y - radius * 0.12f, center.x, center.y - radius)
            }
            drawPath(path, ink, style = Stroke(stroke))
        }
        5 -> {
            // Music note.
            drawLine(ink, Offset(center.x + radius * 0.45f, center.y - radius),
                Offset(center.x + radius * 0.45f, center.y + radius * 0.45f), stroke)
            drawLine(ink, Offset(center.x - radius * 0.25f, center.y - radius * 0.72f),
                Offset(center.x + radius * 0.45f, center.y - radius), stroke)
            drawOval(ink, Offset(center.x - radius * 0.9f, center.y + radius * 0.2f),
                Size(radius * 0.9f, radius * 0.58f), style = Stroke(stroke))
        }
        6 -> {
            // Tiny planet.
            drawCircle(ink, radius * 0.55f, center, style = Stroke(stroke))
            drawOval(ink, Offset(center.x - radius * 1.25f, center.y - radius * 0.35f),
                Size(radius * 2.5f, radius * 0.7f), style = Stroke(stroke))
        }
        else -> {
            // Small sparkle dots.
            drawCircle(ink, radius * 0.22f, center)
            drawCircle(ink, radius * 0.12f, Offset(center.x + radius * 1.1f, center.y - radius * 0.65f))
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
        // The caption has to stay readable on both a pale and a near-black wash.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(5.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    if (preset.isLight()) Color.White.copy(alpha = 0.65f)
                    else Color.Black.copy(alpha = 0.35f)
                )
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Text(
                preset.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (preset.isLight()) Color(0xFF1F2933) else Color.White
            )
        }
    }
}

/**
 * The wallpaper picker used from Settings (default for the whole app) and from a chat's
 * info page (this conversation only).
 *
 * Nothing is applied until "Set background" is pressed: tapping a design only updates the
 * live preview, so the user can see it with sample messages and dial the brightness down
 * before committing.
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
    var preview by remember(current) { mutableStateOf(current) }
    val brightness = AppSettings.state.wallpaperBrightness
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Pick a background and see it first. It stays on this phone only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            WallpaperPreview(preset = preview, brightness = brightness)

            Spacer(Modifier.height(14.dp))
            Text(
                "Brightness",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.DarkMode,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = brightness,
                    onValueChange = { AppSettings.setWallpaperBrightness(it) },
                    valueRange = 0.35f..1f,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                )
                Icon(
                    Icons.Default.LightMode,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "Lower brightness darkens the background so messages stay readable.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().height(260.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(WallpaperPresets, key = { it.id }) { preset ->
                    WallpaperSwatch(preset, preset.id == preview) { preview = preset.id }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(tr("Cancel"))
                }
                Button(
                    onClick = { onPick(preview); onDismiss() },
                    modifier = Modifier.weight(1f)
                ) { Text("Set background") }
            }
            if (extra != null) {
                Spacer(Modifier.height(10.dp))
                extra()
            }
        }
    }
}

/**
 * The "here is what your chats will look like" card: real artwork, the brightness dim on top,
 * and a couple of fake bubbles plus a composer so the contrast can be judged before applying.
 */
@Composable
fun WallpaperPreview(preset: String, brightness: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(18.dp))
    ) {
        WallpaperBackdrop(preset, Modifier.fillMaxSize())
        val dim = (1f - brightness).coerceIn(0f, 0.65f)
        if (dim > 0.01f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        }
        Column(
            Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PreviewBubble(text = "Hey! Are we still on for Friday?", mine = false)
            PreviewBubble(text = "Yes, 6pm at the usual place.", mine = true)
            PreviewBubble(text = "Perfect, see you there.", mine = false)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.75f))
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(
                    "Message",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B7280)
                )
            }
        }
    }
}

@Composable
private fun PreviewBubble(text: String, mine: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Box(
            Modifier.widthIn(max = 210.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 14.dp, topEnd = 14.dp,
                        bottomStart = if (mine) 14.dp else 4.dp,
                        bottomEnd = if (mine) 4.dp else 14.dp
                    )
                )
                .background(if (mine) Color(0xFFDCF8C6) else Color.White)
                .padding(horizontal = 11.dp, vertical = 7.dp)
        ) {
            Text(text, style = MaterialTheme.typography.bodySmall, color = Color(0xFF111827))
        }
    }
}
