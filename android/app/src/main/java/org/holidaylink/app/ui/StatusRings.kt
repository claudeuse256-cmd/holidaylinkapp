package org.holidaylink.app.ui

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.holidaylink.app.data.StatusSeen

/**
 * Which people currently have a status, and whether you have already watched all of it —
 * the green / grey ring WhatsApp draws around a profile picture.
 *
 *  - green ring: they have a status you have not seen yet
 *  - grey ring: you have seen everything they posted
 *  - no ring: no active status
 *
 * Fed by the status strip and the status screen, read by every [Avatar] that is given a user id.
 */
object StatusRings {

    enum class Ring { NONE, NEW, SEEN }

    private val activeIds = mutableMapOf<String, List<String>>()

    /** Observable: avatars recompose when a ring changes. */
    val states = mutableStateMapOf<String, Ring>()

    /** Per person: one flag per status, true = already seen. Drives the segmented ring on the chats header. */
    val segments = mutableStateMapOf<String, List<Boolean>>()

    fun segmentsOf(userId: String?): List<Boolean> =
        if (userId.isNullOrBlank()) emptyList() else segments[userId].orEmpty()

    /** Replaces what we know about active statuses (userId -> status ids). */
    fun update(context: Context, active: Map<String, List<String>>) {
        activeIds.clear()
        activeIds.putAll(active)
        recompute(context)
    }

    /** Call after the user has watched something so the ring turns grey at once. */
    fun recompute(context: Context) {
        val seen = StatusSeen.ids(context)
        val next = activeIds.filterValues { it.isNotEmpty() }.mapValues { (_, ids) ->
            if (ids.all { it in seen }) Ring.SEEN else Ring.NEW
        }
        val flags = activeIds.filterValues { it.isNotEmpty() }
            .mapValues { (_, ids) -> ids.map { it in seen }.sortedByDescending { it } }
        segments.keys.toList().filter { it !in flags }.forEach { segments.remove(it) }
        flags.forEach { (user, list) -> if (segments[user] != list) segments[user] = list }
        states.keys.toList().filter { it !in next }.forEach { states.remove(it) }
        next.forEach { (user, ring) -> if (states[user] != ring) states[user] = ring }
    }

    fun of(userId: String?): Ring =
        if (userId.isNullOrBlank()) Ring.NONE else states[userId] ?: Ring.NONE
}

/**
 * Ring around a status avatar in the chats header, split into one arc per status:
 * green arcs for the ones you have not seen, grey for the ones you have. A single status is
 * a full circle. (Avatars elsewhere keep one plain ring: see [Avatar].)
 */
fun Modifier.segmentedStatusRing(
    userId: String?,
    strokeWidth: Dp = 3.dp,
    newColor: Color = Color(0xFF25D366),
    seenColor: Color = Color(0xFF9AA0A6)
): Modifier = drawBehind {
    val flags = StatusRings.segmentsOf(userId)
    if (flags.isEmpty()) return@drawBehind
    val stroke = strokeWidth.toPx()
    val topLeft = Offset(stroke / 2f, stroke / 2f)
    val arc = Size(size.width - stroke, size.height - stroke)
    val count = flags.size
    if (count == 1) {
        drawArc(
            color = if (flags[0]) seenColor else newColor,
            startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = topLeft, size = arc, style = Stroke(width = stroke)
        )
    } else {
        val gap = if (count > 20) 2f else 6f
        val sweep = 360f / count - gap
        flags.forEachIndexed { index, seen ->
            drawArc(
                color = if (seen) seenColor else newColor,
                startAngle = -90f + index * 360f / count + gap / 2f,
                sweepAngle = sweep, useCenter = false,
                topLeft = topLeft, size = arc,
                style = Stroke(width = stroke, cap = StrokeCap.Butt)
            )
        }
    }
}
