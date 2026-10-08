package org.holidaylink.app.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
        value = photo?.takeIf { it.isNotBlank() }?.let { decodeAvatar(context, it) }
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
                modifier = Modifier.fillMaxSize().clip(shape)
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

// ---------------------------------------------------------------- states

@Composable
fun LoadingState(modifier: Modifier = Modifier, label: String = "Loading...") {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(strokeWidth = 3.dp)
            Spacer(Modifier.height(12.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
