package org.holidaylink.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.holidaylink.app.net.User

/**
 * Shown the instant a call is tapped, while the connection is still being set up.
 * Before this, nothing at all appeared until the call was ready, so it looked as if the tap
 * had done nothing.
 */
@Composable
fun DialingScreen(user: User, kind: String, onCancel: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B1B3A), Color(0xFF050B18))))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(name = user.displayName, photo = user.avatarUrl, size = 128.dp)
            Spacer(Modifier.height(24.dp))
            Text(
                user.displayName,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (kind == "video") "Video call · Calling…" else "Calling…",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        FilledIconButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp).size(68.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFE53935))
        ) {
            Icon(Icons.Default.CallEnd, "Cancel call", tint = Color.White)
        }
    }
}
