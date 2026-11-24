@file:OptIn(ExperimentalMaterial3Api::class)

package org.holidaylink.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.tasks.await
import org.holidaylink.app.data.AppSettings
import org.holidaylink.app.net.safeTimestamp
import org.holidaylink.app.net.FirebaseBackend

/** One starred message recovered from Firestore. */
private data class StarredRow(
    val chatId: String,
    val messageId: String,
    val text: String,
    val hasImage: Boolean,
    val atSeconds: Long
)

/**
 * "Starred messages" — a plain local list of chat/message ids is kept on the device, and this
 * screen rehydrates each one from Firestore. Tapping a row opens its conversation. This mirrors
 * WhatsApp's Starred messages screen without needing a server-side collection.
 */
@Composable
fun StarredMessagesScreen(onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    val starred = AppSettings.state.starredMessages
    val signature = starred.keys.sorted().joinToString(",")
    var rows by remember { mutableStateOf<List<StarredRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(signature) {
        loading = true
        val db = FirebaseBackend.db
        val out = mutableListOf<StarredRow>()
        starred.keys.forEach { k ->
            val chatId = k.substringBefore("/")
            val msgId = k.substringAfter("/", "")
            if (chatId.isBlank() || msgId.isBlank()) return@forEach
            val snap = runCatching {
                db.collection("chats").document(chatId).collection("messages").document(msgId).get().await()
            }.getOrNull() ?: return@forEach
            if (!snap.exists()) return@forEach
            out.add(
                StarredRow(
                    chatId = chatId,
                    messageId = msgId,
                    text = snap.getString("text").orEmpty(),
                    hasImage = !snap.getString("imageUrl").isNullOrBlank(),
                    atSeconds = snap.safeTimestamp("timestamp")?.seconds ?: 0L
                )
            )
        }
        rows = out.sortedByDescending { it.atSeconds }
        loading = false
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(tr("Starred messages")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { inner ->
        when {
            loading && rows.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(inner),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {}

            rows.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(inner).padding(horizontal = 32.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.StarBorder,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "No starred messages yet",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Long-press a message and tap Star to keep it here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(inner),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(rows, key = { it.chatId + "/" + it.messageId }) { row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenChat(row.chatId) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            when {
                                row.text.isNotBlank() -> row.text
                                row.hasImage -> "Photo"
                                else -> "Message"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
