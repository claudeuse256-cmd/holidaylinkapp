@file:OptIn(ExperimentalMaterial3Api::class)

package org.holidaylink.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.holidaylink.app.media.MediaDownload
import java.io.File

/**
 * Reads a past paper / guide inside the app instead of bouncing out to a browser.
 *
 * Images show directly; PDFs are rasterised with the platform [PdfRenderer]; anything else
 * (or a PDF that will not parse) falls back to an embedded Google Docs viewer in a WebView,
 * which is still in-app. The Download action saves the original file to Downloads.
 */
@Composable
fun PaperViewerScreen(
    url: String,
    title: String,
    fileType: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val lower = url.lowercase()
    val isImage = fileType.startsWith("image", true) ||
        lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
        lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".gif")

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title.ifBlank { "Paper" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val ok = MediaDownload.save(context, url, title)
                            scope.launch {
                                snackbar.showSnackbar(
                                    if (ok) "Downloading to Downloads…" else "Couldn't start the download."
                                )
                            }
                        }
                    ) { Icon(Icons.Default.Download, "Download") }
                }
            )
        },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when {
                url.isBlank() -> EmptyState(
                    icon = Icons.Default.Description,
                    title = "No file",
                    subtitle = "This entry has no file attached yet."
                )
                isImage -> RemoteImage(url = url, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else -> PaperBody(url = url)
            }
        }
    }
}

@Composable
private fun PaperBody(url: String) {
    val context = LocalContext.current
    var pages by remember(url) { mutableStateOf<List<Bitmap>?>(null) }
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        val result = withContext(Dispatchers.IO) { renderPdf(context, url) }
        if (result.isNullOrEmpty()) failed = true else pages = result
    }

    val loaded = pages
    when {
        failed -> WebFallback(url)
        loaded == null -> Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator()
        }
        else -> LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(loaded) { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
            }
        }
    }
}

/** Last resort: render the document with Google's embedded viewer inside a WebView. */
@Composable
private fun WebFallback(url: String) {
    AndroidView(
        factory = { ctx ->
            android.webkit.WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                webViewClient = android.webkit.WebViewClient()
                loadUrl("https://docs.google.com/gview?embedded=true&url=" + Uri.encode(url))
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

/** Downloads the file to a persistent copy and rasterises up to 60 pages. Null means "cannot render". */
private fun renderPdf(context: Context, url: String): List<Bitmap>? = runCatching {
    // Kept in the persistent media folder, not cacheDir: the system may wipe the cache, which
    // turned already-read past papers into "not available offline" after a few days.
    val file = kotlinx.coroutines.runBlocking {
        org.holidaylink.app.net.MediaCache.fetch(context, url, "pdf")
    } ?: return@runCatching null
    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer = PdfRenderer(pfd)
    val total = renderer.pageCount.coerceAtMost(60)
    val out = ArrayList<Bitmap>(total)
    for (i in 0 until total) {
        val page = renderer.openPage(i)
        val width = (page.width * 1.5f).toInt().coerceAtLeast(1)
        val height = (page.height * 1.5f).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(AndroidColor.WHITE)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        out.add(bmp)
    }
    renderer.close()
    pfd.close()
    out
}.getOrNull()
