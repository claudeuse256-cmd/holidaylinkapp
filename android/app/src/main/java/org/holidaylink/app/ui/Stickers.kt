package org.holidaylink.app.ui

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import org.holidaylink.app.R

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
    val emoji: String? = null
)

/** Pack-aware sticker catalog. New Telegram-style packs live in assets/stickers/<Pack>/*.json. */
object StickerCatalog {
    @Volatile private var cachedPacks: List<StickerPackDef>? = null

    fun packs(context: Context): List<StickerPackDef> {
        cachedPacks?.let { return it }
        synchronized(this) {
            cachedPacks?.let { return it }
            val built = buildList {
                add(classicPack())
                addAll(assetPacks(context))
            }
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

    private fun classicPack(): StickerPackDef {
        val defs = listOf(
            Triple("happy", "Happy", R.raw.hl_sticker_happy),
            Triple("laugh", "Laugh", R.raw.hl_sticker_laugh),
            Triple("love", "Love", R.raw.hl_sticker_love),
            Triple("heart", "Heart", R.raw.hl_sticker_heart),
            Triple("kiss", "Kiss", R.raw.hl_sticker_kiss),
            Triple("wink", "Wink", R.raw.hl_sticker_wink),
            Triple("cool", "Cool", R.raw.hl_sticker_cool),
            Triple("fire", "Fire", R.raw.hl_sticker_fire),
            Triple("star", "Star", R.raw.hl_sticker_star),
            Triple("clap", "Clap", R.raw.hl_sticker_clap),
            Triple("wave", "Wave", R.raw.hl_sticker_wave),
            Triple("party", "Party", R.raw.hl_sticker_party),
            Triple("sad", "Sad", R.raw.hl_sticker_sad),
            Triple("cry", "Cry", R.raw.hl_sticker_cry),
            Triple("angry", "Angry", R.raw.hl_sticker_angry),
            Triple("wow", "Wow", R.raw.hl_sticker_wow),
            Triple("sleep", "Sleep", R.raw.hl_sticker_sleep),
            Triple("coffee", "Coffee", R.raw.hl_sticker_coffee),
            Triple("music", "Music", R.raw.hl_sticker_music),
            Triple("dance", "Dance", R.raw.hl_sticker_dance),
            Triple("rocket", "Rocket", R.raw.hl_sticker_rocket),
            Triple("trophy", "Trophy", R.raw.hl_sticker_trophy),
            Triple("flower", "Flower", R.raw.hl_sticker_flower),
            Triple("sun", "Sun", R.raw.hl_sticker_sun),
            Triple("moon", "Moon", R.raw.hl_sticker_moon),
            Triple("rainbow", "Rainbow", R.raw.hl_sticker_rainbow),
            Triple("cat", "Cat", R.raw.hl_sticker_cat),
            Triple("dog", "Dog", R.raw.hl_sticker_dog),
            Triple("bear", "Bear", R.raw.hl_sticker_bear),
            Triple("bunny", "Bunny", R.raw.hl_sticker_bunny),
            Triple("ghost", "Ghost", R.raw.hl_sticker_ghost),
            Triple("robot", "Robot", R.raw.hl_sticker_robot),
            Triple("alien", "Alien", R.raw.hl_sticker_alien),
            Triple("crown", "Crown", R.raw.hl_sticker_crown),
            Triple("diamond", "Diamond", R.raw.hl_sticker_diamond),
            Triple("gift", "Gift", R.raw.hl_sticker_gift),
            Triple("balloon", "Balloon", R.raw.hl_sticker_balloon),
            Triple("pizza", "Pizza", R.raw.hl_sticker_pizza),
            Triple("icecream", "Ice cream", R.raw.hl_sticker_icecream),
            Triple("football", "Football", R.raw.hl_sticker_football),
            Triple("thumbsup", "Thumbs up", R.raw.hl_sticker_thumbsup),
            Triple("ok", "OK", R.raw.hl_sticker_ok),
            Triple("pray", "Pray", R.raw.hl_sticker_pray),
            Triple("strong", "Strong", R.raw.hl_sticker_strong),
            Triple("thinking", "Thinking", R.raw.hl_sticker_thinking),
            Triple("shy", "Shy", R.raw.hl_sticker_shy),
            Triple("sparkle", "Sparkle", R.raw.hl_sticker_sparkle),
            Triple("butterfly", "Butterfly", R.raw.hl_sticker_butterfly),
            Triple("lightning", "Lightning", R.raw.hl_sticker_lightning),
            Triple("hug", "Hug", R.raw.hl_sticker_hug)
        ).map { (id, title, res) ->
            StickerDef(ref = "hlsticker://$id", title = title, resId = res, emoji = stickerEmoji(id))
        }
        return StickerPackDef(id = "classic", title = "HolidayLink", stickers = defs)
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
            val stickers = files.mapIndexed { index, file ->
                val path = "stickers/$pack/$file"
                val ref = "hlsticker://pack/$pack/$file"
                StickerDef(ref = ref, title = "$pack ${index + 1}", assetPath = path)
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
    emoji: String? = null
) {
    val resolvedAsset = assetPath ?: stickerAssetPath(url)
    val resolvedRes = resId ?: bundledStickerResource(url)
    val resolvedUrl = if (resolvedAsset == null && resolvedRes == null) url?.takeUnless { it.startsWith("hlsticker://") } else null

    if (resolvedAsset == null && resolvedRes == null && resolvedUrl.isNullOrBlank()) {
        val emo = emoji ?: stickerEmoji(url)
        if (!emo.isNullOrBlank()) {
            val transition = rememberInfiniteTransition(label = "holidaylink-sticker")
            val scale by transition.animateFloat(
                0.88f, 1.08f,
                infiniteRepeatable(tween(620, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "sticker-scale"
            )
            val tilt by transition.animateFloat(
                -7f, 7f,
                infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "sticker-tilt"
            )
            Text(
                emo,
                fontSize = (size.value * 0.72f).sp,
                textAlign = TextAlign.Center,
                modifier = modifier
                    .size(size)
                    .graphicsLayer(scaleX = scale, scaleY = scale, rotationZ = tilt)
                    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            )
        }
        return
    }

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
        resId = bundledStickerResource(ref),
        emoji = stickerEmoji(ref),
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
            resId = resId ?: bundledStickerResource(sendRef),
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

/** Emoji artwork for classic bundled stickers, animated locally. */
fun stickerEmoji(idOrUrl: String?): String? {
    val r = normalizeStickerRef(idOrUrl) ?: return null
    val id = r.removePrefix("hlsticker://")
    return when (id) {
        "happy" -> "😄"
        "laugh" -> "😂"
        "love" -> "🥰"
        "heart" -> "❤️"
        "kiss" -> "😘"
        "wink" -> "😉"
        "cool" -> "😎"
        "fire" -> "🔥"
        "star" -> "⭐"
        "clap" -> "👏"
        "wave" -> "👋"
        "party" -> "🥳"
        "sad" -> "😢"
        "cry" -> "😭"
        "angry" -> "😡"
        "wow" -> "😮"
        "sleep" -> "😴"
        "coffee" -> "☕"
        "music" -> "🎵"
        "dance" -> "💃"
        "rocket" -> "🚀"
        "trophy" -> "🏆"
        "flower" -> "🌸"
        "sun" -> "☀️"
        "moon" -> "🌙"
        "rainbow" -> "🌈"
        "cat" -> "🐱"
        "dog" -> "🐶"
        "bear" -> "🐻"
        "bunny" -> "🐰"
        "ghost" -> "👻"
        "robot" -> "🤖"
        "alien" -> "👽"
        "crown" -> "👑"
        "diamond" -> "💎"
        "gift" -> "🎁"
        "balloon" -> "🎈"
        "pizza" -> "🍕"
        "icecream" -> "🍦"
        "football" -> "⚽"
        "thumbsup" -> "👍"
        "ok" -> "👌"
        "pray" -> "🙏"
        "strong" -> "💪"
        "thinking" -> "🤔"
        "shy" -> "😊"
        "sparkle" -> "✨"
        "butterfly" -> "🦋"
        "lightning" -> "⚡"
        "hug" -> "🤗"
        else -> null
    }
}

/** Resolve classic legacy sticker IDs to bundled raw animation resources. */
fun bundledStickerResource(idOrUrl: String?): Int? {
    val r = normalizeStickerRef(idOrUrl) ?: return null
    if (r.startsWith("hlsticker://pack/")) return null
    val id = r.removePrefix("hlsticker://")
    return when (id) {
        "happy" -> R.raw.hl_sticker_happy
        "laugh" -> R.raw.hl_sticker_laugh
        "love" -> R.raw.hl_sticker_love
        "heart" -> R.raw.hl_sticker_heart
        "kiss" -> R.raw.hl_sticker_kiss
        "wink" -> R.raw.hl_sticker_wink
        "cool" -> R.raw.hl_sticker_cool
        "fire" -> R.raw.hl_sticker_fire
        "star" -> R.raw.hl_sticker_star
        "clap" -> R.raw.hl_sticker_clap
        "wave" -> R.raw.hl_sticker_wave
        "party" -> R.raw.hl_sticker_party
        "sad" -> R.raw.hl_sticker_sad
        "cry" -> R.raw.hl_sticker_cry
        "angry" -> R.raw.hl_sticker_angry
        "wow" -> R.raw.hl_sticker_wow
        "sleep" -> R.raw.hl_sticker_sleep
        "coffee" -> R.raw.hl_sticker_coffee
        "music" -> R.raw.hl_sticker_music
        "dance" -> R.raw.hl_sticker_dance
        "rocket" -> R.raw.hl_sticker_rocket
        "trophy" -> R.raw.hl_sticker_trophy
        "flower" -> R.raw.hl_sticker_flower
        "sun" -> R.raw.hl_sticker_sun
        "moon" -> R.raw.hl_sticker_moon
        "rainbow" -> R.raw.hl_sticker_rainbow
        "cat" -> R.raw.hl_sticker_cat
        "dog" -> R.raw.hl_sticker_dog
        "bear" -> R.raw.hl_sticker_bear
        "bunny" -> R.raw.hl_sticker_bunny
        "ghost" -> R.raw.hl_sticker_ghost
        "robot" -> R.raw.hl_sticker_robot
        "alien" -> R.raw.hl_sticker_alien
        "crown" -> R.raw.hl_sticker_crown
        "diamond" -> R.raw.hl_sticker_diamond
        "gift" -> R.raw.hl_sticker_gift
        "balloon" -> R.raw.hl_sticker_balloon
        "pizza" -> R.raw.hl_sticker_pizza
        "icecream" -> R.raw.hl_sticker_icecream
        "football" -> R.raw.hl_sticker_football
        "thumbsup" -> R.raw.hl_sticker_thumbsup
        "ok" -> R.raw.hl_sticker_ok
        "pray" -> R.raw.hl_sticker_pray
        "strong" -> R.raw.hl_sticker_strong
        "thinking" -> R.raw.hl_sticker_thinking
        "shy" -> R.raw.hl_sticker_shy
        "sparkle" -> R.raw.hl_sticker_sparkle
        "butterfly" -> R.raw.hl_sticker_butterfly
        "lightning" -> R.raw.hl_sticker_lightning
        "hug" -> R.raw.hl_sticker_hug
        else -> null
    }
}
