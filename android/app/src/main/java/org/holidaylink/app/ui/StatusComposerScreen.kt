
package org.holidaylink.app.ui

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Send

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect

import org.holidaylink.app.media.MediaPicker
import org.holidaylink.app.net.Api
import org.holidaylink.app.net.FirestoreChat
import org.holidaylink.app.net.FirebaseBackend
import org.holidaylink.app.net.User
import org.holidaylink.app.voice.VoiceRecorder

import java.io.File

data class GalleryItem(
    val uri: Uri,
    val isVideo: Boolean,
    val dateAddedSec: Long
)

private fun queryRecentMedia(context: Context, limit: Int = 120): List<GalleryItem> {
    val resolver = context.contentResolver
    val items = mutableListOf<GalleryItem>()

    val imageProjection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DATE_ADDED
    )

    runCatching {
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            imageProjection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

            while (cursor.moveToNext() && items.size < limit) {
                val id = cursor.getLong(idCol)
                val date = cursor.getLong(dateCol)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                items.add(GalleryItem(uri, false, date))
            }
        }
    }

    val videoProjection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DATE_ADDED
    )

    runCatching {
        resolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            videoProjection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

            while (cursor.moveToNext() && items.size < limit * 2) {
                val id = cursor.getLong(idCol)
                val date = cursor.getLong(dateCol)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                items.add(GalleryItem(uri, true, date))
            }
        }
    }

    return items.sortedByDescending { it.dateAddedSec }.take(limit)
}

private fun loadThumbnail(context: Context, item: GalleryItem): Bitmap? = runCatching {
    val resolver = context.contentResolver

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        resolver.loadThumbnail(
            item.uri,
            android.util.Size(240, 240),
            null
        )
    } else {
        val id = ContentUris.parseId(item.uri)

        if (item.isVideo) {
            @Suppress("DEPRECATION")
            MediaStore.Video.Thumbnails.getThumbnail(
                resolver,
                id,
                MediaStore.Video.Thumbnails.MINI_KIND,
                null
            )
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Thumbnails.getThumbnail(
                resolver,
                id,
                MediaStore.Images.Thumbnails.MINI_KIND,
                null
            )
        }
    }
}.getOrNull()

@Composable
fun StatusGalleryPicker(
    onBack: () -> Unit,
    onPickCamera: () -> Unit,
    onPickMedia: (uri: Uri, isVideo: Boolean) -> Unit,
    onWriteTextStatus: () -> Unit
) {
    val context = LocalContext.current

    var granted by remember {
        mutableStateOf(hasMediaGalleryPermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        granted = result.values.all { it } || hasMediaGalleryPermission(context)
    }

    LaunchedEffect(Unit) {
        if (!granted) {
            permissionLauncher.launch(mediaGalleryPermissions())
        }
    }

    val items by produceState(
        initialValue = emptyList<GalleryItem>(),
        granted
    ) {
        value = if (granted) {
            withContext(Dispatchers.IO) {
                queryRecentMedia(context)
            }
        } else {
            emptyList()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF070B10))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "Back",
                    tint = Color.White
                )
            }

            Text(
                "My status",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )

            TextButton(onClick = onWriteTextStatus) {
                Text("Aa Text", color = Color.White)
            }
        }

        when {
            !granted -> {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(48.dp)
                        )

                        Spacer(Modifier.height(10.dp))

                        Text(
                            "Allow photo and video access to post from your gallery.",
                            color = Color.White.copy(alpha = 0.85f)
                        )

                        Spacer(Modifier.height(10.dp))

                        Button(
                            onClick = {
                                permissionLauncher.launch(mediaGalleryPermissions())
                            }
                        ) {
                            Text("Allow access")
                        }
                    }
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(2.dp)
                ) {
                    item {
                        Box(
                            Modifier
                                .padding(2.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { onPickCamera() },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    "Camera",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )

                                Spacer(Modifier.height(4.dp))

                                Text(
                                    "Camera",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    items(
                        items = items,
                        key = { it.uri.toString() }
                    ) { media ->
                        GalleryThumbnail(
                            media = media,
                            onClick = {
                                onPickMedia(media.uri, media.isVideo)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryThumbnail(
    media: GalleryItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        media.uri
    ) {
        value = withContext(Dispatchers.IO) {
            loadThumbnail(context, media)
        }
    }

    Box(
        Modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
    ) {
        val bmp = bitmap

        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (media.isVideo) {
            Icon(
                Icons.Default.PlayCircle,
                "Video",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(20.dp)
            )
        }
    }
}

@Composable
fun StatusTextComposerPage(
    api: Api,
    onDismiss: () -> Unit,
    onPosted: () -> Unit
){
    var text by remember { mutableStateOf("") }
    var posting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    var voiceRecording by remember { mutableStateOf(false) }
    var voiceRecorder by remember { mutableStateOf<VoiceRecorder?>(null) }
    var voiceMs by remember { mutableLongStateOf(0L) }
    var voiceBlob by remember { mutableStateOf<String?>(null) }

    val voiceHandler = remember { Handler(Looper.getMainLooper()) }

    var musicUri by remember { mutableStateOf<Uri?>(null) }
    var musicName by remember { mutableStateOf("") }

    var mentionPicker by remember { mutableStateOf(false) }
    var mentionSearch by remember { mutableStateOf("") }
    var mentionUsers by remember { mutableStateOf<List<User>>(emptyList()) }
    var mentionedUserIds by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val uid = FirebaseBackend.uid.orEmpty()

        if (uid.isNotBlank()) {
            runCatching {
                FirestoreChat.chatsFlow(uid).collect { rows ->
                    val ids = rows
                        .filter { !it.isGroup }
                        .mapNotNull { it.peerId }
                        .distinct()

                    mentionUsers = FirestoreChat
                        .resolvePeers(ids)
                        .values
                        .sortedBy { it.displayName.lowercase() }
                }
            }
        }
    }

    val musicPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            musicUri = uri
            musicName = MediaPicker.inspect(context, uri).name ?: "Audio track"
        }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { grantedMic ->
        if (grantedMic) {
            voiceRecording = true
        } else {
            error = "Microphone permission is needed for voice statuses."
        }
    }

    LaunchedEffect(voiceRecording) {
        if (voiceRecording) {
            voiceRecorder = VoiceRecorder(context)

            if (voiceRecorder?.start() == true) {
                voiceMs = 0L

                voiceHandler.post(object : Runnable {
                    override fun run() {
                        if (voiceRecording) {
                            voiceMs += 1_000
                            voiceHandler.postDelayed(this, 1_000)
                        }
                    }
                })
            } else {
                voiceRecording = false
                error = "Microphone unavailable."
            }
        } else {
            voiceHandler.removeCallbacksAndMessages(null)

            val clip = voiceRecorder?.stop()
            voiceRecorder = null

            clip?.let {
                if (it.durationMs > 60_000) {
                    error = "Voice status too long (max 60 seconds)."
                    runCatching { it.file.delete() }
                } else {
                    val bytes = it.file.readBytes()
                    voiceBlob = android.util.Base64.encodeToString(
                        bytes,
                        android.util.Base64.NO_WRAP
                    )
                    voiceMs = it.durationMs
                    error = ""
                    runCatching { it.file.delete() }
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, "Cancel")
            }

            Text(
                "Text status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                enabled = (text.isNotBlank() || voiceBlob != null) &&
                        !posting && !voiceRecording,
                onClick = {
                    if (posting) return@IconButton

                    posting = true
                    error = ""

                    scope.launch {
                        runCatching {
                            postFirestoreStatus(
                                context = context,
                                text = text.trim(),
                                mediaDataUrl = voiceBlob,
                                mediaUri = null,
                                mediaKind = if (voiceBlob != null) "voice" else "text",
                                fileName = "",
                                voiceDurationMs = voiceMs,
                                mentionedUserIds = mentionedUserIds,
                                musicUri = musicUri
                            )
                        }.onSuccess {
                            onPosted()
                        }.onFailure {
                            posting = false
                            error = "We couldn't post that status. Check your internet."
                        }
                    }
                }
            ) {
                if (posting) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Send, "Post")
                }
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(280) },
                label = { Text("What's happening?") },
                minLines = 4,
                supportingText = { Text("${text.length}/280") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        mentionPicker = true
                        mentionSearch = ""
                    }
                ) {
                    Text("@ Mention")
                }

                TextButton(
                    onClick = {
                        musicPicker.launch(arrayOf("audio/*"))
                    }
                ) {
                    Text(if (musicUri == null) "Add music" else "Change music")
                }

                TextButton(
                    onClick = {
                        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            voiceRecording = !voiceRecording
                        } else {
                            micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                ) {
                    Icon(Icons.Default.Mic, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))

                    Text(
                        if (voiceRecording) "Stop (${formatSeconds(voiceMs)})"
                        else if (voiceBlob != null) "Re-record voice"
                        else "Add voice"
                    )
                }
            }

            if (musicName.isNotBlank()) {
                Text(
                    musicName,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            if (error.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (mentionPicker) {
        AlertDialog(
            onDismissRequest = { mentionPicker = false },
            title = { Text("Mention a contact") },
            text = {
                Column {
                    OutlinedTextField(
                        value = mentionSearch,
                        onValueChange = { mentionSearch = it },
                        label = { Text("Search chats") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    val matches = mentionUsers.filter {
                        mentionSearch.isBlank() ||
                                it.displayName.contains(mentionSearch, true) ||
                                it.username.contains(
                                    mentionSearch.trimStart('@'),
                                    true
                                )
                    }

                    if (matches.isEmpty()) {
                        Text(
                            "No chat contacts found. Start a chat with someone first.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(
                            Modifier.height(260.dp)
                        ) {
                            items(
                                matches,
                                key = { it.id }
                            ) { person ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val handle =
                                                person.username.takeIf { it.isNotBlank() }
                                                    ?: person.id

                                            val token = "@$handle"

                                            text = (
                                                    text.trimEnd() +
                                                            if (text.isBlank()) token
                                                            else " $token"
                                                    ).take(280)

                                            mentionedUserIds =
                                                mentionedUserIds + (person.id to handle)

                                            mentionPicker = false
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Avatar(
                                        name = person.displayName,
                                        photo = person.avatarUrl,
                                        size = 38.dp
                                    )

                                    Spacer(Modifier.width(10.dp))

                                    Column {
                                        Text(
                                            person.displayName,
                                            fontWeight = FontWeight.SemiBold
                                        )

                                        Text(
                                            if (person.username.isBlank()) {
                                                "HolidayLink contact"
                                            } else {
                                                "@${person.username}"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { mentionPicker = false }
                ) {
                    Text("Done")
                }
            }
        )
    }
}

private fun formatSeconds(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
