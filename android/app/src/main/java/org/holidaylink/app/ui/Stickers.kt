package org.holidaylink.app.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

data class StickerPackDef(
    val id: String,
    val title: String,
    val stickers: List<StickerDef>
)

data class StickerDef(
    val ref: String,
    val title: String,
    val assetPath: String? = null,
    val resId: Int? = null,
)

/** Pack-aware catalog for the bundled animated Telegram-style sticker packs. */
object StickerCatalog {
    @Volatile private var cachedPacks: List<StickerPackDef>? = null

    fun packs(context: Context): List<StickerPackDef> {
        cachedPacks?.let { return it }
        synchronized(this) {
            cachedPacks?.let { return it }
            val built = assetPacks(context)
            cachedPacks = built
            return built
        }
    }

    fun all(context: Context): List<StickerDef> = packs(context).flatMap { it.stickers }

    fun find(context: Context, ref: String?): StickerDef? {
        if (ref.isNullOrBlank()) return null
        val key = normalizeRef(ref)
        return all(context).firstOrNull { it.ref == key }
    }

    private fun assetPacks(context: Context): List<StickerPackDef> {
        val am = context.assets
        val packNames = (am.list("stickers") ?: emptyArray())
            .filter { name ->
                // keep only directories that contain sticker json files
                val kids = am.list("stickers/$name") ?: emptyArray()
                kids.any { it.endsWith(".json", true) }
            }
            .sorted()

        return packNames.map { pack ->
            val files = (am.list("stickers/$pack") ?: emptyArray())
                .filter { it.endsWith(".json", true) }
                .sorted()
            val stickers = files.map { file ->
                val path = "stickers/$pack/$file"
                val ref = "hlsticker://pack/$pack/$file"
                StickerDef(ref = ref, title = file.removeSuffix(".json").replace('_', ' '), assetPath = path)
            }
            StickerPackDef(id = pack.lowercase(), title = pack, stickers = stickers)
        }
    }
}

/** Normalizes a sticker reference to the canonical form used by the catalog. */
fun normalizeStickerRef(ref: String?): String? = ref?.let { normalizeRef(it) }

private fun normalizeRef(raw: String): String {
    if (raw.startsWith("hlsticker://pack/")) return raw
    if (raw.startsWith("hlsticker://")) return raw
    if (raw.startsWith("pack:")) {
        // legacy-ish internal form: pack:<Pack>:<file>
        val rest = raw.removePrefix("pack:")
        val parts = rest.split(":", limit = 2)
        if (parts.size == 2) return "hlsticker://pack/${parts[0]}/${parts[1]}"
    }
    // bare legacy id
    return "hlsticker://$raw"
}

private const val RAW_STICKER_PREFIX = "hlsticker://raw/"

/** Reference for a bundled starter sticker (a Lottie file in res/raw). */
fun rawStickerRef(resName: String): String = RAW_STICKER_PREFIX + resName

/** The res/raw name inside a starter-sticker reference, or null for any other reference. */
fun stickerRawName(ref: String?): String? =
    ref?.takeIf { it.startsWith(RAW_STICKER_PREFIX) }?.removePrefix(RAW_STICKER_PREFIX)?.takeIf { it.isNotBlank() }

/** Resolve asset path for pack stickers. */
fun stickerAssetPath(ref: String?): String? {
    val r = normalizeStickerRef(ref) ?: return null
    if (!r.startsWith("hlsticker://pack/")) return null
    val rest = r.removePrefix("hlsticker://pack/")
    val slash = rest.indexOf('/')
    if (slash <= 0 || slash >= rest.lastIndex) return null
    val pack = rest.substring(0, slash)
    val file = rest.substring(slash + 1)
    return "stickers/$pack/$file"
}

/**
 * An animated sticker rendered with Lottie.
 * Supports raw resources, remote URLs, and bundled asset pack files.
 */
@Composable
fun AnimatedSticker(
    url: String? = null,
    resId: Int? = null,
    assetPath: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    size: Dp = 120.dp,
) {
    val resolvedAsset = assetPath ?: stickerAssetPath(url)
    // Bundled starter stickers are raw Lottie resources, referenced as hlsticker://raw/<name>.
    val appContext = LocalContext.current
    val rawRes = remember(url) {
        stickerRawName(url)?.let { name ->
            appContext.resources.getIdentifier(name, "raw", appContext.packageName).takeIf { it != 0 }
        }
    }
    val resolvedRes = resId ?: rawRes
    val resolvedUrl = if (resolvedAsset == null && resolvedRes == null) url?.takeUnless { it.startsWith("hlsticker://") } else null

    if (resolvedAsset == null && resolvedRes == null && resolvedUrl.isNullOrBlank()) return

    val spec = remember(resolvedAsset, resolvedRes, resolvedUrl) {
        when {
            resolvedAsset != null -> LottieCompositionSpec.Asset(resolvedAsset)
            resolvedRes != null -> LottieCompositionSpec.RawRes(resolvedRes)
            !resolvedUrl.isNullOrBlank() -> LottieCompositionSpec.Url(resolvedUrl)
            else -> null
        }
    } ?: return

    val composition by rememberLottieComposition(spec)
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )
    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier
            .size(size)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentScale = ContentScale.Fit
    )
}

/** Render any sticker ref (legacy id, pack asset ref, or URL). */
@Composable
fun StickerRef(
    ref: String?,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    onClick: (() -> Unit)? = null
) {
    AnimatedSticker(
        url = ref,
        assetPath = stickerAssetPath(ref),
        modifier = modifier,
        size = size,
        onClick = onClick
    )
}

/** A small sticker in a grid that the user can tap to send. */
@Composable
fun StickerItem(
    title: String,
    stickerId: String? = null,
    ref: String? = null,
    url: String? = null,
    resId: Int? = null,
    assetPath: String? = null,
    onPick: (String) -> Unit
) {
    val sendRef = ref ?: url ?: stickerId?.let { normalizeStickerRef(it) } ?: return
    Column(
        Modifier
            .size(92.dp)
            .padding(3.dp)
            .clickable { onPick(sendRef) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedSticker(
            url = if (assetPath == null) (url ?: sendRef) else null,
            assetPath = assetPath ?: stickerAssetPath(sendRef),
            resId = resId,
            size = 68.dp
        )
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
