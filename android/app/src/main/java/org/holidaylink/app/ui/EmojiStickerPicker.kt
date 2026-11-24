package org.holidaylink.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.holidaylink.app.net.ApiException
import org.holidaylink.app.net.Imgbb
import java.io.File

/**
 * The user's own stickers, kept on the phone.
 *
 * A sticker is a picture the user picked, shrunk to 512 px and stored as a transparent PNG.
 * The first time one is sent it is uploaded once and the address is remembered next to the
 * file, so every later send is instant.
 */
object CustomStickers {

    private fun dir(context: Context): File =
        File(context.filesDir, "custom_stickers").apply { mkdirs() }

    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.isFile && f.extension == "png" }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    /** Copies a picked picture into the sticker folder. Null when it cannot be read. */
    fun add(context: Context, uri: Uri): File? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalStateException("unreadable image")
        val longest = maxOf(decoded.width, decoded.height).coerceAtLeast(1)
        val scale = minOf(1f, 512f / longest)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else decoded
        val target = File(dir(context), "sticker-${System.currentTimeMillis()}.png")
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        target
    }.getOrNull()

    fun delete(file: File) {
        runCatching { file.delete() }
        runCatching { File(file.path + ".url").delete() }
    }

    /** Address of the uploaded copy, uploading it the first time. */
    suspend fun urlFor(file: File): String = withContext(Dispatchers.IO) {
        val memo = File(file.path + ".url")
        memo.takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.startsWith("http") }?.let { return@withContext it }
        if (!file.isFile) throw ApiException("That sticker is no longer on this phone.")
        val url = Imgbb.uploadJpeg(file.readBytes())
        runCatching { memo.writeText(url) }
        url
    }

    /** Names of the bundled starter stickers (Lottie files in res/raw). */
    fun starterNames(context: Context): List<String> {
        val fields = runCatching { Class.forName(context.packageName + ".R\$raw").fields }.getOrNull().orEmpty()
        return fields.map { it.name }.filter { it.startsWith("hl_sticker_") }.sorted()
    }
}

private val EMOJI = (
    "😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 😚 😙 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔 " +
    "🤐 🤨 😐 😑 😶 😏 😒 🙄 😬 🤥 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🤧 🥵 🥶 🥴 😵 🤯 🤠 🥳 😎 🤓 🧐 " +
    "😕 😟 🙁 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 " +
    "👍 👎 👊 ✊ 🤛 🤜 👏 🙌 👐 🤲 🤝 🙏 ✌️ 🤞 🤟 🤘 👌 🤏 👈 👉 👆 👇 ☝️ ✋ 🤚 🖖 👋 🤙 💪 " +
    "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💯 💢 💥 💫 💦 💨 " +
    "🔥 ✨ ⭐ 🌟 🎉 🎊 🎈 🎁 🏆 🥇 ⚽ 🏀 🎯 🎮 🎵 🎶 🎤 🎧 📚 📝 ✅ ❌ ⚠️ 💡 📷 📱 💻 ⏰ 🚀 ✈️ 🚗 🏠 🌍 🌞 🌙 🌈 ☕ 🍕 🍔 🍟 🎂 " +
    "🐶 🐱 🐭 🐰 🦊 🐻 🐼 🐨 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🦄 🐝 🦋 🍎 🍌 🍉 🍇 🍓 🍍 🥑 🍞 🍗 🍦 🍫"
    ).split(" ").filter { it.isNotBlank() }

/**
 * One picker for everything you put in a message, in three sections:
 *  1. Emoji (about 200),
 *  2. the built-in animated sticker packs,
 *  3. "My stickers": a starter set plus any picture the user adds.
 *
 * [onEmoji] adds to the message box and the sheet stays open, so several can be picked in a
 * row. A sticker is sent at once and the sheet closes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EmojiStickerSheet(
    onDismiss: () -> Unit,
    onEmoji: (String) -> Unit,
    onSticker: (String) -> Unit,
    onCustomSticker: (File) -> Unit
) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(min = 420.dp, max = 560.dp)) {
            TabRow(selectedTabIndex = tab) {
                // Stickers come first, then emoji, then the user's own.
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(tr("Stickers")) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(tr("Emoji")) })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(tr("My stickers")) })
            }
            when (tab) {
                1 -> LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 6.dp)
                ) {
                    gridItems(EMOJI) { emoji ->
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp)).clickable { onEmoji(emoji) }.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) { Text(emoji, fontSize = 24.sp) }
                    }
                }

                0 -> {
                    val packs = remember(context) { StickerCatalog.packs(context) }
                    var selectedPack by remember { mutableStateOf(packs.firstOrNull()?.id.orEmpty()) }
                    val current = packs.firstOrNull { it.id == selectedPack } ?: packs.firstOrNull()
                    if (packs.size > 1) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            items(packs, key = { it.id }) { pack ->
                                val active = pack.id == current?.id
                                Column(
                                    Modifier.clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (active) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                        )
                                        .clickable { selectedPack = pack.id }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    pack.stickers.firstOrNull()?.let { preview ->
                                        AnimatedSticker(url = preview.ref, assetPath = preview.assetPath, resId = preview.resId, size = 30.dp)
                                    }
                                    Text(pack.title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 6.dp)
                    ) {
                        gridItems(current?.stickers.orEmpty(), key = { it.ref }) { sticker ->
                            StickerItem(
                                title = sticker.title,
                                ref = sticker.ref,
                                resId = sticker.resId,
                                assetPath = sticker.assetPath,
                                onPick = onSticker
                            )
                        }
                    }
                }

                else -> {
                    var customVersion by remember { mutableIntStateOf(0) }
                    val custom = remember(customVersion) { CustomStickers.list(context) }
                    var deleting by remember { mutableStateOf<File?>(null) }
                    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                        if (uri != null && CustomStickers.add(context, uri) != null) customVersion++
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 6.dp)
                    ) {
                        item(key = "add-sticker") {
                            Column(
                                Modifier.size(92.dp).padding(3.dp).clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable {
                                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Add, "Add sticker", tint = MaterialTheme.colorScheme.primary)
                                Text(tr("Add"), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        gridItems(custom, key = { it.name }) { file ->
                            val bitmap by produceState<Bitmap?>(initialValue = null, file.path) {
                                value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.absolutePath) }
                            }
                            Box(
                                Modifier.size(92.dp).padding(6.dp).clip(RoundedCornerShape(10.dp))
                                    .combinedClickable(onClick = { onCustomSticker(file) }, onLongClick = { deleting = file }),
                                contentAlignment = Alignment.Center
                            ) {
                                bitmap?.let {
                                    Image(it.asImageBitmap(), "Sticker", Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
                                }
                            }
                        }
                    }
                    deleting?.let { file ->
                        AlertDialog(
                            onDismissRequest = { deleting = null },
                            title = { Text("Remove this sticker?") },
                            text = { Text("It is removed from My stickers on this phone. Messages you already sent are not affected.") },
                            confirmButton = {
                                TextButton(onClick = { CustomStickers.delete(file); deleting = null; customVersion++ }) { Text(tr("Remove")) }
                            },
                            dismissButton = { TextButton(onClick = { deleting = null }) { Text(tr("Keep")) } }
                        )
                    }
                }
            }
        }
    }
}
