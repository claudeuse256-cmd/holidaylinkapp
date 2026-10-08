package org.holidaylink.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.holidaylink.app.net.Api
import org.holidaylink.app.net.ApiException
import org.holidaylink.app.net.CallRecord
import org.holidaylink.app.net.User

private val MissedRed = Color(0xFFDC2626)

/**
 * Call history.
 *
 * Every row is a real call record written to /calls. Audio between two HolidayLink users is not
 * carried by this app yet, so the header says so plainly instead of pretending a dialler exists.
 */
@Composable
fun CallsScreen(
    api: Api,
    onOpenChat: (User) -> Unit
) {
    var calls by remember { mutableStateOf<List<CallRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf("all") }

    LaunchedEffect(reload) {
        if (reload > 0) refreshing = true
        try {
            calls = api.callHistory()
            error = ""
        } catch (e: Exception) {
            error = (e as? ApiException)?.message ?: "We couldn't load your call history."
        } finally {
            loading = false
            refreshing = false
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            runCatching { calls = api.callHistory() }
        }
    }

    val visible = remember(calls, filter) {
        when (filter) {
            "missed" -> calls.filter { it.missed }
            "outgoing" -> calls.filter { !it.incoming }
            "incoming" -> calls.filter { it.incoming }
            else -> calls
        }
    }

    // Day separators are flattened into the list up front: item() cannot be called from inside
    // an itemsIndexed item body.
    val rows = remember(visible) {
        val out = mutableListOf<CallListRow>()
        visible.forEach { call ->
            val day = dayKey(call.createdAt)
            if (day != null && out.lastOrNull()?.day != day) {
                out += CallListRow(key = "day-$day", day = day, call = null)
            }
            out += CallListRow(key = call.id, day = day, call = call)
        }
        out
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 18.dp, end = 4.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Calls",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            RefreshButton { reload++ }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "all" to "All",
                "missed" to "Missed",
                "outgoing" to "Outgoing",
                "incoming" to "Incoming"
            ).forEach { (key, label) ->
                FilterPill(label = label, selected = filter == key) { filter = key }
            }
        }

        when {
            loading -> LoadingState(label = "Loading calls…")
            error.isNotBlank() && calls.isEmpty() -> ErrorState(error) { reload++ }
            calls.isEmpty() -> EmptyState(
                icon = Icons.Default.Call,
                title = "No calls yet",
                subtitle = "Calls you place from a chat or a contact will be listed here."
            )

            visible.isEmpty() -> EmptyState(
                icon = Icons.Default.Call,
                title = "Nothing in this filter",
                subtitle = "Try a different filter to see the rest of your history."
            )

            else -> PullToRefreshContainer(
                refreshing = refreshing,
                onRefresh = { reload++ },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(rows, key = { it.key }) { row ->
                        val call = row.call
                        if (call == null) {
                            SectionHeader(dayTitle(row.day, calls))
                        } else {
                            CallRow(call = call, onOpen = { call.peer?.let(onOpenChat) })
                        }
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "HolidayLink records who called, when, and for how long. Live audio between " +
                    "HolidayLink users is not carried by this app yet.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clickable { onClick() }
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                MaterialTheme.shapes.small
            )
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun CallRow(call: CallRecord, onOpen: () -> Unit) {
    val name = call.peer?.displayName ?: "Unknown number"
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Avatar(
                name = name,
                photo = call.peer?.avatarUrl,
                online = call.peer?.online,
                size = 50.dp
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(20.dp)
                    .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        call.missed -> Icons.Default.CallMissed
                        call.incoming -> Icons.Default.CallReceived
                        else -> Icons.Default.CallMade
                    },
                    contentDescription = null,
                    tint = if (call.missed) MissedRed else Color(0xFF22C55E),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (call.missed) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (call.kind == "video") Icons.Default.Videocam else Icons.Default.Call,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    buildString {
                        append(if (call.incoming) "Incoming" else "Outgoing")
                        append(if (call.durationSeconds > 0) {
                            " · ${formatDuration(call.durationSeconds * 1000L)}"
                        } else {
                            ""
                        })
                        append(" · ${friendlyWhen(call.createdAt)}")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (call.missed) {
                        MissedRed
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (call.status != "ringing" && call.status != "missed") {
                Text(
                    longWhen(call.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/** One rendered line: either a day separator ([call] is null) or a call record. */
private data class CallListRow(
    val key: String,
    val day: String?,
    val call: CallRecord?
)

/** Human title for a day bucket: Today, Yesterday, then a short date. */
private fun dayTitle(day: String?, calls: List<CallRecord>): String {
    if (day == null) return ""
    val today = java.time.LocalDate.now()
    return when (day) {
        today.toString() -> "Today"
        today.minusDays(1).toString() -> "Yesterday"
        else -> calls.firstOrNull { dayKey(it.createdAt) == day }
            ?.let { friendlyWhen(it.createdAt) }
            .orEmpty()
    }
}

private fun dayKey(value: String?): String? {
    val instant = parseInstant(value) ?: return null
    return instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
}