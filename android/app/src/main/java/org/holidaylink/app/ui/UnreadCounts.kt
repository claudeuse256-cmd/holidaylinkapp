package org.holidaylink.app.ui

import androidx.compose.runtime.mutableStateMapOf

/**
 * Unread messages per person (direct chats), filled by the app-wide chat listener, so the
 * Users directory can show the same green counter the Chats list shows.
 */
object UnreadCounts {
    val byPeer = mutableStateMapOf<String, Int>()
}
