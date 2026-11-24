package org.holidaylink.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One quiet banner shown inside the app instead of a heads-up alert in the system shade. */
data class InAppAlert(val title: String, val body: String, val targetId: String)

/**
 * Alerts raised while the app is already on screen.
 *
 * A notification in the shade is the right surface when the phone is in a pocket, and the
 * wrong one when it is in the user's hand: it covers what they are reading, plays a sound
 * they did not ask for and then has to be dismissed. [Notifier] hands those to this bus
 * instead, and [InAppAlertHost] draws them.
 */
object InAppAlerts {

    private val current = MutableStateFlow<InAppAlert?>(null)
    val alerts: StateFlow<InAppAlert?> = current

    fun show(alert: InAppAlert) {
        current.value = alert
    }

    fun dismiss(alert: InAppAlert?) {
        // Only clear the one on display: a newer alert may already have replaced it.
        if (alert != null && current.value !== alert) return
        current.value = null
    }
}

/**
 * The banner itself. Sits at the top of the signed-in app, above every screen, and leaves on
 * its own after a few seconds so it never needs a swipe to get rid of.
 */
@Composable
fun InAppAlertHost(onOpen: (String) -> Unit) {
    val alert by InAppAlerts.alerts.collectAsState()

    LaunchedEffect(alert) {
        val shown = alert ?: return@LaunchedEffect
        delay(4_500)
        InAppAlerts.dismiss(shown)
    }

    Box(Modifier.fillMaxWidth().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = alert != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut()
        ) {
            val shown = alert ?: return@AnimatedVisibility
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .shadow(6.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        shown.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        shown.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = {
                    InAppAlerts.dismiss(shown)
                    onOpen(shown.targetId)
                }) {
                    Text(
                        "Open",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(onClick = { InAppAlerts.dismiss(shown) }) {
                    Icon(
                        Icons.Default.Close,
                        "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
