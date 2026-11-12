package org.holidaylink.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

/**
 * An animated sticker rendered with Lottie.
 *
 * Pass a [url] (a Lottie JSON hosted on your server or CDN) or a raw [resId] resource from the app.
 * The composition is downloaded/cached once and then plays on loop.
 */
@Composable
fun AnimatedSticker(
    url: String? = null,
    resId: Int? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    size: Dp = 120.dp,
    emoji: String? = null
) {
    if (!emoji.isNullOrBlank()) {
        val transition = rememberInfiniteTransition(label = "holidaylink-sticker")
        val scale by transition.animateFloat(0.88f, 1.08f, infiniteRepeatable(tween(620, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sticker-scale")
        val tilt by transition.animateFloat(-7f, 7f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sticker-tilt")
        androidx.compose.material3.Text(
            emoji,
            fontSize = (size.value * 0.72f).sp,
            textAlign = TextAlign.Center,
            modifier = modifier.size(size).graphicsLayer(scaleX = scale, scaleY = scale, rotationZ = tilt)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        )
        return
    }
    val spec = remember(url, resId) {
        when {
            resId != null -> LottieCompositionSpec.RawRes(resId)
            !url.isNullOrBlank() -> LottieCompositionSpec.Url(url)
            else -> null
        }
    }
    if (spec == null) return
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

/** A small sticker in a grid that the user can tap to send. */
@Composable
fun StickerItem(
    title: String,
    stickerId: String,
    url: String? = null,
    resId: Int? = null,
    onPick: (String) -> Unit
) {
    Column(
        Modifier
            .size(88.dp)
            .padding(3.dp)
            .clickable { onPick(if (!url.isNullOrBlank()) url else "hlsticker://$stickerId") },
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        AnimatedSticker(url = url, resId = resId, size = 70.dp, emoji = stickerEmoji("hlsticker://$stickerId"))
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}


/** Emoji artwork is bundled with the app and animated locally, so stickers do not depend on third-party URLs. */
fun stickerEmoji(idOrUrl: String?): String? {
    val id = idOrUrl?.removePrefix("hlsticker://") ?: return null
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

/** Resolve the stable sticker ID embedded in a message to this app's bundled animation. */
fun bundledStickerResource(idOrUrl: String?): Int? {
    val id = idOrUrl?.removePrefix("hlsticker://") ?: return null
    return when (id) {
        "happy" -> org.holidaylink.app.R.raw.hl_sticker_happy
        "laugh" -> org.holidaylink.app.R.raw.hl_sticker_laugh
        "love" -> org.holidaylink.app.R.raw.hl_sticker_love
        "heart" -> org.holidaylink.app.R.raw.hl_sticker_heart
        "kiss" -> org.holidaylink.app.R.raw.hl_sticker_kiss
        "wink" -> org.holidaylink.app.R.raw.hl_sticker_wink
        "cool" -> org.holidaylink.app.R.raw.hl_sticker_cool
        "fire" -> org.holidaylink.app.R.raw.hl_sticker_fire
        "star" -> org.holidaylink.app.R.raw.hl_sticker_star
        "clap" -> org.holidaylink.app.R.raw.hl_sticker_clap
        "wave" -> org.holidaylink.app.R.raw.hl_sticker_wave
        "party" -> org.holidaylink.app.R.raw.hl_sticker_party
        "sad" -> org.holidaylink.app.R.raw.hl_sticker_sad
        "cry" -> org.holidaylink.app.R.raw.hl_sticker_cry
        "angry" -> org.holidaylink.app.R.raw.hl_sticker_angry
        "wow" -> org.holidaylink.app.R.raw.hl_sticker_wow
        "sleep" -> org.holidaylink.app.R.raw.hl_sticker_sleep
        "coffee" -> org.holidaylink.app.R.raw.hl_sticker_coffee
        "music" -> org.holidaylink.app.R.raw.hl_sticker_music
        "dance" -> org.holidaylink.app.R.raw.hl_sticker_dance
        "rocket" -> org.holidaylink.app.R.raw.hl_sticker_rocket
        "trophy" -> org.holidaylink.app.R.raw.hl_sticker_trophy
        "flower" -> org.holidaylink.app.R.raw.hl_sticker_flower
        "sun" -> org.holidaylink.app.R.raw.hl_sticker_sun
        "moon" -> org.holidaylink.app.R.raw.hl_sticker_moon
        "rainbow" -> org.holidaylink.app.R.raw.hl_sticker_rainbow
        "cat" -> org.holidaylink.app.R.raw.hl_sticker_cat
        "dog" -> org.holidaylink.app.R.raw.hl_sticker_dog
        "bear" -> org.holidaylink.app.R.raw.hl_sticker_bear
        "bunny" -> org.holidaylink.app.R.raw.hl_sticker_bunny
        "ghost" -> org.holidaylink.app.R.raw.hl_sticker_ghost
        "robot" -> org.holidaylink.app.R.raw.hl_sticker_robot
        "alien" -> org.holidaylink.app.R.raw.hl_sticker_alien
        "crown" -> org.holidaylink.app.R.raw.hl_sticker_crown
        "diamond" -> org.holidaylink.app.R.raw.hl_sticker_diamond
        "gift" -> org.holidaylink.app.R.raw.hl_sticker_gift
        "balloon" -> org.holidaylink.app.R.raw.hl_sticker_balloon
        "pizza" -> org.holidaylink.app.R.raw.hl_sticker_pizza
        "icecream" -> org.holidaylink.app.R.raw.hl_sticker_icecream
        "football" -> org.holidaylink.app.R.raw.hl_sticker_football
        "thumbsup" -> org.holidaylink.app.R.raw.hl_sticker_thumbsup
        "ok" -> org.holidaylink.app.R.raw.hl_sticker_ok
        "pray" -> org.holidaylink.app.R.raw.hl_sticker_pray
        "strong" -> org.holidaylink.app.R.raw.hl_sticker_strong
        "thinking" -> org.holidaylink.app.R.raw.hl_sticker_thinking
        "shy" -> org.holidaylink.app.R.raw.hl_sticker_shy
        "sparkle" -> org.holidaylink.app.R.raw.hl_sticker_sparkle
        "butterfly" -> org.holidaylink.app.R.raw.hl_sticker_butterfly
        "lightning" -> org.holidaylink.app.R.raw.hl_sticker_lightning
        "hug" -> org.holidaylink.app.R.raw.hl_sticker_hug
        else -> null
    }
}
