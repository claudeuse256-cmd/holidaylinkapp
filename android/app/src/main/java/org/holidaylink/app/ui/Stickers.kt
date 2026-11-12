package org.holidaylink.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
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
    size: Dp = 120.dp
) {
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
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Fit
    )
}

/** A small sticker in a grid that the user can tap to send. */
@Composable
fun StickerItem(
    title: String,
    url: String? = null,
    resId: Int? = null,
    onPick: (String) -> Unit
) {
    Column(
        Modifier
            .size(110.dp)
            .padding(6.dp)
            .clickable { onPick(url.orEmpty()) }
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
    ) {
        AnimatedSticker(url = url, resId = resId, size = 72.dp)
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
