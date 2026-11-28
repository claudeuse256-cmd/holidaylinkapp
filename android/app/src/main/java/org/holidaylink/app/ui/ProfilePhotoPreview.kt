package org.holidaylink.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * A person's profile picture at full size (tap anywhere to close). Shown when you tap
 * a picture in the chat list and that person has no status to watch. Without a picture it
 * shows their initial on a large circle instead of nothing.
 */
@Composable
fun ProfilePhotoPreview(name: String, photoUrl: String?, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)).clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                name,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(20.dp)
            )
            if (!photoUrl.isNullOrBlank()) {
                RemoteImage(
                    url = photoUrl,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    contentScale = ContentScale.Fit
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(name = name, photo = null, size = 220.dp)
                    Spacer(Modifier.height(16.dp))
                    Text("No profile picture", color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}
