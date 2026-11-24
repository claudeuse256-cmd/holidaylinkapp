package org.holidaylink.app.ui

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val YOUTUBE_ID = Regex("^[A-Za-z0-9_-]{6,15}$")

/** The video id of a YouTube link (watch, youtu.be, embed, shorts, live), or null for anything else. */
fun youtubeId(url: String): String? {
    val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return null
    val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return null
    val segments = uri.pathSegments
    val id = when {
        host == "youtu.be" -> segments.firstOrNull()
        host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com") -> {
            val first = segments.firstOrNull() ?: ""
            when {
                first == "watch" -> uri.getQueryParameter("v")
                first in setOf("embed", "shorts", "live", "v") -> segments.getOrNull(1)
                else -> null
            }
        }
        else -> null
    }
    return id?.takeIf { YOUTUBE_ID.matches(it) }
}

private fun isDirectVideo(url: String): Boolean {
    val path = url.substringBefore('?').lowercase()
    return path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".3gp") ||
        path.endsWith(".mkv") || path.endsWith(".m3u8")
}

/**
 * A feed video that plays INSIDE the app. A YouTube link shows its thumbnail with a play button
 * and opens an embedded player in a full-width pop-up; it no longer sends people to the YouTube
 * app or the browser. Direct video files play in a native player, other web video pages open
 * in an in-app web view.
 */
@Composable
fun InAppVideo(videoUrl: String) {
    var playing by remember { mutableStateOf(false) }
    val ytId = remember(videoUrl) { youtubeId(videoUrl) }
    if (ytId != null) {
        Box(
            Modifier.fillMaxWidth().height(190.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .clickable { playing = true }
        ) {
            RemoteImage(
                url = "https://img.youtube.com/vi/$ytId/hqdefault.jpg",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            Icon(
                Icons.Default.PlayCircle, "Play video", tint = Color.White,
                modifier = Modifier.size(60.dp).align(Alignment.Center)
            )
        }
    } else {
        Button(onClick = { playing = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Watch video")
        }
    }
    if (playing) {
        VideoPlayerDialog(videoUrl = videoUrl, youtubeId = ytId, onClose = { playing = false })
    }
}

@Composable
private fun VideoPlayerDialog(videoUrl: String, youtubeId: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            when {
                youtubeId != null -> EmbeddedWeb(
                    url = "https://www.youtube.com/embed/$youtubeId?autoplay=1&playsinline=1&rel=0&fs=0&modestbranding=1",
                    // YouTube asks embedded players to identify the app that hosts them.
                    referer = "https://${context.packageName}",
                    lockToYoutube = true,
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(16f / 9f)
                )

                isDirectVideo(videoUrl) -> AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            val controls = MediaController(ctx)
                            controls.setAnchorView(this)
                            setMediaController(controls)
                            setVideoURI(Uri.parse(videoUrl))
                            setOnPreparedListener { start() }
                        }
                    },
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(16f / 9f),
                    onRelease = { view -> runCatching { view.stopPlayback() } }
                )

                else -> EmbeddedWeb(
                    url = videoUrl,
                    referer = null,
                    lockToYoutube = false,
                    modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = 48.dp)
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)
            ) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
        }
    }
}

@Composable
private fun EmbeddedWeb(url: String, referer: String?, lockToYoutube: Boolean, modifier: Modifier) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                setBackgroundColor(android.graphics.Color.BLACK)
                webChromeClient = android.webkit.WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (!lockToYoutube) return false
                        // The embedded player must not be able to navigate away to other sites.
                        val host = request.url.host.orEmpty()
                        val allowed = host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com") ||
                            host.endsWith("google.com") || host.endsWith("gstatic.com")
                        return !allowed
                    }
                }
                if (referer != null) loadUrl(url, mapOf("Referer" to referer)) else loadUrl(url)
            }
        },
        modifier = modifier,
        onRelease = { web ->
            runCatching {
                web.stopLoading()
                web.loadUrl("about:blank")
                web.destroy()
            }
        }
    )
}
