package org.holidaylink.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// ---------------------------------------------------------------- logo & avatar

/** HolidayLink brand gradient: blue -> cyan -> violet. Used for the logo and accents. */
val HolidayLinkGradient: List<Color> = listOf(
    Color(0xFF2563EB),
    Color(0xFF06B6D4),
    Color(0xFF7C3AED)
)

/**
 * The HolidayLink app mark: a real Material "link" glyph on the brand gradient.
 * Deliberately not a copy of any third-party messaging logo.
 */
@Composable
fun HolidayLinkLogo(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
            .background(Brush.linearGradient(HolidayLinkGradient)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Link,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

/**
 * Circular avatar. Uses a real photo when [photo] resolves, otherwise deterministic
 * initials tinted from the name so a person always looks the same colour.
 */
@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    photo: String? = null,
    online: Boolean? = null,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, photo) {
        // Network + decode must stay off the main thread or every remote
        // photo silently fails and falls back to initials.
        value = withContext(kotlinx.coroutines.Dispatchers.IO) {
            photo?.takeIf { it.isNotBlank() }?.let { src ->
                avatarMemoryCache.get(src) ?: run {
                    // Remote photos go through MediaCache so the file stays on the phone and
                    // the avatar survives with no internet instead of reverting to initials.
                    val loaded = if (src.startsWith("http")) {
                        org.holidaylink.app.net.MediaCache.bitmap(context, src)
                    } else {
                        decodeAvatar(context, src)
                    }
                    loaded?.also { bmp -> avatarMemoryCache.put(src, bmp) }
                }
            }
        }
    }

    val initial = name.trim().firstOrNull()?.uppercase() ?: "?"
    val tint = avatarTint(name)
    val shape = CircleShape

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .background(tint),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = name,
                modifier = Modifier.fillMaxSize().clip(shape),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = initial,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.42f).sp
            )
        }
        if (online != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.28f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(size * 0.04f)
                    .clip(CircleShape)
                    .background(if (online) Color(0xFF22C55E) else Color(0xFF94A3B8))
            )
        }
    }
}

private fun avatarTint(seed: String): Color {
    val palette = listOf(
        Color(0xFF2563EB), Color(0xFF0891B2), Color(0xFF7C3AED),
        Color(0xFFDB2777), Color(0xFFEA580C), Color(0xFF059669)
    )
    val hash = abs(seed.trim().lowercase().hashCode())
    return palette[hash % palette.size]
}

/** Small in-memory photo cache so lists don't re-download avatars per row. */
private val avatarMemoryCache = android.util.LruCache<String, android.graphics.Bitmap>(48)

/** Decodes a downscaled bitmap from a content:// or file:// URI or an http(s) URL. */
private fun decodeAvatar(context: Context, source: String): android.graphics.Bitmap? = runCatching {
    val target = 256
    val uri = Uri.parse(source)
    if (source.startsWith("http")) {
        java.net.URL(source).openStream().use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    } else {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, bounds)
            var sample = 1
            while (bounds.outWidth / sample > target || bounds.outHeight / sample > target) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        }
    }
}.getOrNull()

// ---------------------------------------------------------------- saving media

/**
 * Permission-aware "Save to gallery" action shared by the photo and video viewers.
 *
 * Pass either a decoded image or a downloaded file. Android 10+ writes straight into
 * MediaStore; on Android 9 and older the legacy storage permission is asked for first and
 * the save retried once it is granted, so the user only has to tap Save once.
 */
@Composable
fun rememberSaveToGallery(): (bitmap: Bitmap?, file: java.io.File?) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pendingFile by remember { mutableStateOf<java.io.File?>(null) }

    fun write(bitmap: Bitmap?, file: java.io.File?) {
        if (bitmap == null && file == null) {
            Toast.makeText(context, "Nothing to save yet", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            val ok = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    if (bitmap != null) org.holidaylink.app.media.GallerySave.bitmap(context, bitmap)
                    else org.holidaylink.app.media.GallerySave.video(context, file!!)
                }.getOrDefault(false)
            }
            Toast.makeText(
                context,
                if (ok) "Saved to your gallery" else "Saving failed. Try again.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) write(pendingBitmap, pendingFile)
        else {
            Toast.makeText(
                context,
                "Storage access is needed to save on this Android version",
                Toast.LENGTH_LONG
            ).show()
        }
        pendingBitmap = null
        pendingFile = null
    }

    return { bitmap, file ->
        val needsPermission = org.holidaylink.app.media.GallerySave.needsLegacyPermission() &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingBitmap = bitmap
            pendingFile = file
            launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            write(bitmap, file)
        }
    }
}

// ---------------------------------------------------------------- photo viewer

/** Larger cache for full-screen previews, separate from the tiny avatar cache. */
private val photoViewerCache = android.util.LruCache<String, android.graphics.Bitmap>(16)

/** Loads an image at (near) full resolution from a URL or a content/file URI. */
private suspend fun loadFullBitmap(context: Context, source: String): android.graphics.Bitmap? = runCatching {
    if (source.startsWith("http")) {
        // Kept on disk after the first view, so a saved wallpaper or photo still shows
        // when the phone has no internet.
        org.holidaylink.app.net.MediaCache.fetch(context, source)
            ?.let { BitmapFactory.decodeFile(it.absolutePath) }
    } else {
        context.contentResolver.openInputStream(Uri.parse(source))?.use { BitmapFactory.decodeStream(it) }
    }
}.getOrNull()

/**
 * WhatsApp-style media viewer: black full-screen canvas, tap to close, double-tap and
 * pinch to zoom, drag to pan. Used for avatars, status and in-chat photos.
 */
@Composable
fun PhotoViewer(url: String?, title: String = "", onDismiss: () -> Unit) {
    val source = url?.takeIf { it.isNotBlank() } ?: return
    val context = LocalContext.current
    val saveToGallery = rememberSaveToGallery()
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, source) {
        value = withContext(kotlinx.coroutines.Dispatchers.IO) {
            photoViewerCache.get(source)
                ?: loadFullBitmap(context, source)?.also { photoViewerCache.put(source, it) }
        }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 8f)
        offset = if (scale > 1f) offset + panChange else Offset.Zero
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onDismiss() },
                        onDoubleTap = {
                            scale = if (scale > 1f) 1f else 2.5f
                            offset = Offset.Zero
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            val image = bitmap
            if (image != null) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .transformable(transformState)
                )
            } else {
                CircularProgressIndicator(color = Color.White)
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
            ) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
            IconButton(
                onClick = { saveToGallery(image, null) },
                enabled = image != null,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            ) { Icon(Icons.Default.Download, "Save to gallery", tint = Color.White) }
        }
    }
}

// ---------------------------------------------------------------- typing indicator

/**
 * The three pulsing dots shown while somebody else is writing.
 *
 * Each dot runs the same loop a third of a cycle behind the one before it, so the row reads
 * as movement travelling left to right instead of all three flashing at once.
 */
@Composable
fun TypingDots(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    dotSize: Dp = 6.dp
) {
    val transition = rememberInfiniteTransition(label = "typing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "typingPhase"
    )
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val lift = kotlin.math.sin(((phase + index / 3f) % 1f) * Math.PI).toFloat()
            Box(
                Modifier
                    .size(dotSize)
                    .offset(y = (-3f * lift).dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.3f + 0.7f * lift))
            )
        }
    }
}

// ---------------------------------------------------------------- chat wallpaper

/**
 * Draws the user's chat wallpaper (or a themed base) behind the message list.
 *
 * A personal photo and a built-in design are alternatives rather than layers: picking a photo
 * clears the preset (see `AppSettings.setChatWallpaper`), so whichever one the user chose last
 * is the one drawn. [brightness] darkens the artwork without touching it: a bright photo
 * otherwise makes white text unreadable.
 */
@Composable
fun ChatWallpaper(
    uri: String?,
    solid: Boolean,
    modifier: Modifier = Modifier,
    preset: String? = null,
    brightness: Float = org.holidaylink.app.data.AppSettings.state.wallpaperBrightness
) {
    Box(modifier) {
        WallpaperArt(uri = uri, solid = solid, preset = preset, modifier = Modifier.fillMaxSize())
        val dim = (1f - brightness).coerceIn(0f, 0.65f)
        if (dim > 0.01f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        }
    }
}

@Composable
private fun WallpaperArt(uri: String?, solid: Boolean, preset: String?, modifier: Modifier) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val source = uri?.takeIf { it.isNotBlank() }
    val chosen = preset?.takeIf { it.isNotBlank() }
    if (chosen != null && chosen != WALLPAPER_DEFAULT) {
        WallpaperBackdrop(chosen, modifier)
        return
    }
    if (solid) {
        Box(modifier.background(scheme.surfaceContainerLowest))
        return
    }
    if (source == null) {
        // Nothing personal picked: show the built-in design rather than a blank canvas.
        WallpaperBackdrop(chosen ?: WALLPAPER_DEFAULT, modifier)
        return
    }
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, source) {
        value = withContext(kotlinx.coroutines.Dispatchers.IO) {
            photoViewerCache.get(source) ?: loadFullBitmap(context, source)?.also { photoViewerCache.put(source, it) }
        }
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        WallpaperBackdrop(WALLPAPER_DEFAULT, modifier)
    }
}

// ---------------------------------------------------------------- states

// ---------------------------------------------------------------- skeleton states

/**
 * Moving highlight used by every skeleton block: a soft band sweeping left to right.
 * A pulsing alpha (the old approach) reads as a broken screen; a sweep reads as loading.
 */
@Composable
fun shimmerBrush(): Brush {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1150, easing = androidx.compose.animation.core.LinearEasing)
        ),
        label = "shimmerPhase"
    )
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.13f)
    val span = 900f
    val x = phase * (span * 2f) - span
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(x, 0f),
        end = Offset(x + span, span * 0.6f)
    )
}

/**
 * Bottom space a scrolling list needs so its last row can clear the floating nav pill.
 * The pill sits above the system navigation bar, and lists now run all the way to the
 * bottom edge of the screen — rows scroll *behind* the pill instead of stopping above it.
 */
@Composable
fun floatingNavBottomPadding(): PaddingValues {
    val navBars = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PaddingValues(bottom = navBars + 96.dp)
}

@Composable
fun LoadingState(modifier: Modifier = Modifier, label: String = "Loading...") {
    Column(modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
        repeat(7) { SkeletonRow() }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp)
        )
    }
}

/** One chat-list placeholder row: avatar, name line, preview line, timestamp. */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()
    Row(
        modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(brush))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.fillMaxWidth(0.45f).height(13.dp).clip(RoundedCornerShape(7.dp)).background(brush))
            Box(Modifier.fillMaxWidth(0.75f).height(11.dp).clip(RoundedCornerShape(6.dp)).background(brush))
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.width(34.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(brush))
    }
}

@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()
    Row(modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(brush))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.6f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(brush))
            Box(Modifier.fillMaxWidth(0.9f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(brush))
        }
    }
}

@Composable
fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (action != null) {
                Spacer(Modifier.height(18.dp))
                action()
            }
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(14.dp))
            Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            if (onRetry != null) {
                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.OutlinedButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Try again")
                }
            }
        }
    }
}

// ---------------------------------------------------------------- pull to refresh

private class PullState {
    var offset: Float by mutableFloatStateOf(0f)
    var dragging: Boolean by mutableStateOf(false)
}

/**
 * Dependency-free pull-to-refresh.
 *
 * Written against stable Compose foundation APIs rather than Material3's version-specific
 * PullToRefreshBox, so it keeps compiling across BOM upgrades.
 */
@Composable
fun PullToRefreshContainer(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { 72.dp.toPx() }
    val maxPx = with(density) { 150.dp.toPx() }

    val pullState = remember { PullState() }
    val scope = rememberCoroutineScope()
    val currentOnRefresh by rememberUpdatedState(onRefresh)
    val currentRefreshing by rememberUpdatedState(refreshing)

    fun settleBack() {
        val from = pullState.offset
        if (from <= 0f) {
            pullState.dragging = false
            return
        }
        scope.launch {
            animate(from, 0f, animationSpec = tween(200)) { value, _ -> pullState.offset = value }
            pullState.dragging = false
        }
    }

    LaunchedEffect(refreshing) {
        if (!refreshing) settleBack()
    }

    val connection = remember(maxPx, thresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f && pullState.offset > 0f) {
                    val consumed = minOf(available.y, pullState.offset)
                    pullState.offset -= consumed
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            // onPostScroll is a plain (non-suspend) pass; only the fling callbacks suspend.
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f && !currentRefreshing) {
                    pullState.offset = (pullState.offset + available.y * 0.55f).coerceIn(0f, maxPx)
                    pullState.dragging = true
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                val shouldTrigger = pullState.offset >= thresholdPx && !currentRefreshing
                if (shouldTrigger) {
                    pullState.offset = thresholdPx
                    currentOnRefresh()
                } else {
                    settleBack()
                }
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }

    Box(modifier.nestedScroll(connection)) {
        Box(Modifier.fillMaxSize().offset { IntOffset(0, pullState.offset.roundToInt()) }) {
            content()
        }
        RefreshIndicator(
            pullOffset = pullState.offset,
            thresholdPx = thresholdPx,
            refreshing = refreshing || pullState.dragging,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun RefreshIndicator(
    pullOffset: Float,
    thresholdPx: Float,
    refreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "refresh-spin")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "refresh-angle"
    )
    val shown = if (refreshing) thresholdPx else pullOffset
    val progress = (shown / thresholdPx).coerceIn(0f, 1f)
    val restPx = with(LocalDensity.current) { 40.dp.toPx() }
    val tint by animateColorAsState(
        if (progress >= 1f) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "refresh-tint"
    )

    Box(
        modifier = modifier
            .offset { IntOffset(0, (shown - restPx).roundToInt()) }
            .size(32.dp)
            .graphicsLayer {
                alpha = progress.coerceIn(0f, 1f)
                rotationZ = if (refreshing) spin else progress * 270f
            },
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(26.dp),
            strokeWidth = 2.5.dp,
            color = tint,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

// ---------------------------------------------------------------- settings rows

/** Compact grouped section, matching the row style used by mature messaging apps. */
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(14.dp))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) { content() }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 60.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/** A compact navigation row: icon, title, optional subtitle, chevron. */
@Composable
fun SettingsNavRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.45f
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha))
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.7f)
        )
    }
}

/** A compact row with a trailing switch. */
@Composable
fun SettingsSwitchRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    subtitle: String? = null,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val alpha = if (enabled) 1f else 0.45f
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha))
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** A row that shows a value on the right and navigates on tap. */
@Composable
fun SettingsValueRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(18.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Default.ChevronRight,
            null,
            Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/** Small header used inside long scrolling screens (Contacts, Chats, Settings hub). */
@Composable
fun SectionHeader(text: String, count: Int? = null, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                "$count",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/** Circular alphabet letter used by the fast-scroll index on Contacts. */
@Composable
fun AlphabetBubble(letter: Char, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 1.dp, vertical = 2.dp)
            .size(26.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            letter.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Small pill used for unread counts and status badges. */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(
        modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Wraps content in a horizontal row with even spacing, used for chip groups. */
@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

/** Simple refresh icon button used in top bars. */
@Composable
fun RefreshButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
    }
}

// ---------------------------------------------------------------- time helpers

/** Parses the ISO-8601 shapes PostgreSQL and Kotlin send, tolerating a few common variants. */
internal fun parseInstant(value: String?): java.time.Instant? {
    val raw = value?.trim().orEmpty()
    if (raw.isEmpty()) return null

    val parsers: List<(String) -> java.time.Instant> = listOf(
        { text -> java.time.OffsetDateTime.parse(text).toInstant() },
        { text -> java.time.ZonedDateTime.parse(text).toInstant() },
        { text -> java.time.Instant.parse(text) },
        // "2025-01-04 12:33:01.123456+00" — postgres.js hands this back for timestamptz.
        { text ->
            java.time.OffsetDateTime.parse(text.replace(' ', 'T').removeSuffix("+00") + "Z").toInstant()
        },
        // A naive timestamp with no offset: read it in the device's own zone.
        { text ->
            java.time.LocalDateTime.parse(text.replace(' ', 'T'))
                .atZone(ZoneId.systemDefault())
                .toInstant()
        }
    )
    for (parse in parsers) {
        val attempt = runCatching { parse(raw) }
        if (attempt.isSuccess) return attempt.getOrNull()
    }
    return null
}

/**
 * Compact relative timestamp: "now", "5m", "3h", "Yesterday", "Tue", "12 Mar".
 * Shared by the chat list, call history, status and settings so dates read the same everywhere.
 */
fun friendlyWhen(value: String?): String {
    val instant = parseInstant(value) ?: return ""
    val now = java.time.Instant.now()
    val seconds = (now.epochSecond - instant.epochSecond).coerceAtLeast(0L)

    return when {
        seconds < 45L -> "now"
        seconds < 3600L -> "${seconds / 60}m"
        seconds < 86_400L -> "${seconds / 3600}h"
        seconds < 172_800L -> "Yesterday"
        seconds < 7L * 86_400L ->
            DateTimeFormatter.ofPattern("EEE").withZone(ZoneId.systemDefault()).format(instant)
        instant.atZone(ZoneId.systemDefault()).year == now.atZone(ZoneId.systemDefault()).year ->
            DateTimeFormatter.ofPattern("d MMM").withZone(ZoneId.systemDefault()).format(instant)
        else ->
            DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault()).format(instant)
    }
}

/** Absolute local time, used where a relative label would be ambiguous. */
fun longWhen(value: String?): String {
    val instant = parseInstant(value) ?: return ""
    return DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(instant)
}

/** m:ss duration label for calls and voice notes. */
fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
