package org.holidaylink.app.ui

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
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
        states.keys.toList().filter { it !in next }.forEach { states.remove(it) }
        next.forEach { (user, ring) -> if (states[user] != ring) states[user] = ring }
    }

    fun of(userId: String?): Ring =
        if (userId.isNullOrBlank()) Ring.NONE else states[userId] ?: Ring.NONE
}
