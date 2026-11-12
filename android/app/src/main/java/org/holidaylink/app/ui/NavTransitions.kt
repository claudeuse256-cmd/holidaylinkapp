package org.holidaylink.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Screen transitions the user can pick in Settings.
 *
 * Every style is a single progress value (0 → 1) mapped onto a graphics layer, so adding one
 * costs nothing at runtime and the settings preview plays the exact same code the app uses.
 * [NONE] is the default: navigating then swaps screens with no animation at all.
 */
enum class NavTransition(val id: String, val label: String, val group: String) {
    NONE("none", "None (instant)", "Plain"),
    FADE("fade", "Fade", "Plain"),
    SLIDE_LEFT("slide_left", "Slide in from right", "Slide"),
    SLIDE_RIGHT("slide_right", "Slide in from left", "Slide"),
    SLIDE_UP("slide_up", "Slide up", "Slide"),
    SLIDE_DOWN("slide_down", "Slide down", "Slide"),
    FADE_SLIDE_LEFT("fade_slide_left", "Fade + slide from right", "Slide"),
    FADE_SLIDE_RIGHT("fade_slide_right", "Fade + slide from left", "Slide"),
    FADE_SLIDE_UP("fade_slide_up", "Fade + slide up", "Slide"),
    PUSH_LEFT("push_left", "Push left", "Slide"),
    PUSH_RIGHT("push_right", "Push right", "Slide"),
    SCALE_IN("scale_in", "Zoom in", "Scale"),
    SCALE_OUT("scale_out", "Zoom out", "Scale"),
    SCALE_FADE("scale_fade", "Zoom + fade", "Scale"),
    GROW("grow", "Grow from corner", "Scale"),
    BOUNCE("bounce", "Bounce", "Playful"),
    SPRING_SLIDE("spring_slide", "Springy slide", "Playful"),
    ROTATE("rotate", "Rotate in", "Playful"),
    FLIP_X("flip_x", "Flip horizontally", "3D"),
    FLIP_Y("flip_y", "Flip vertically", "3D");

    companion object {
        fun fromId(id: String?): NavTransition =
            entries.firstOrNull { it.id == id } ?: NONE
    }
}

private const val TRAVEL_DP = 34f

/** How long each style takes; the bouncy ones use a spring instead of a fixed duration. */
private fun specFor(t: NavTransition): AnimationSpec<Float> = when (t) {
    NavTransition.BOUNCE -> spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    NavTransition.SPRING_SLIDE -> spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
    NavTransition.FADE, NavTransition.FADE_SLIDE_UP -> tween(200, easing = LinearOutSlowInEasing)
    else -> tween(260, easing = FastOutSlowInEasing)
}

/**
 * Plays [transition] whenever [key] changes and hosts the screen underneath it.
 *
 * With [enabled] false (or the style set to None) the value snaps straight to 1, so a screen
 * change is a hard cut — which is what the "no animations" setting has to mean.
 */
@Composable
fun NavTransitionHost(
    transition: NavTransition,
    key: Any?,
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(key, transition, enabled) {
        if (!enabled || transition == NavTransition.NONE) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, specFor(transition))
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                applyTransition(transition, progress.value, TRAVEL_DP * density)
            }
    ) { content() }
}

/** Maps a 0→1 progress onto alpha/translation/scale/rotation for one style. */
private fun androidx.compose.ui.graphics.GraphicsLayerScope.applyTransition(
    t: NavTransition,
    p: Float,
    travelPx: Float
) {
    val q = 1f - p
    when (t) {
        NavTransition.NONE -> Unit
        NavTransition.FADE -> alpha = p
        NavTransition.SLIDE_LEFT -> translationX = q * travelPx
        NavTransition.SLIDE_RIGHT -> translationX = -q * travelPx
        NavTransition.SLIDE_UP -> translationY = q * travelPx
        NavTransition.SLIDE_DOWN -> translationY = -q * travelPx
        NavTransition.FADE_SLIDE_LEFT -> { alpha = p; translationX = q * travelPx }
        NavTransition.FADE_SLIDE_RIGHT -> { alpha = p; translationX = -q * travelPx }
        NavTransition.FADE_SLIDE_UP -> { alpha = p; translationY = q * travelPx }
        NavTransition.PUSH_LEFT -> { translationX = q * travelPx * 1.6f; scaleX = 1f - 0.04f * q; scaleY = scaleX }
        NavTransition.PUSH_RIGHT -> { translationX = -q * travelPx * 1.6f; scaleX = 1f - 0.04f * q; scaleY = scaleX }
        NavTransition.SCALE_IN -> { scaleX = 0.86f + 0.14f * p; scaleY = scaleX }
        NavTransition.SCALE_OUT -> { scaleX = 1.12f - 0.12f * p; scaleY = scaleX; alpha = 0.4f + 0.6f * p }
        NavTransition.SCALE_FADE -> { alpha = p; scaleX = 0.9f + 0.1f * p; scaleY = scaleX }
        NavTransition.GROW -> {
            scaleX = 0.92f + 0.08f * p
            scaleY = scaleX
            alpha = p
            translationY = q * travelPx * 0.4f
        }
        NavTransition.BOUNCE -> { scaleY = 0.9f + 0.1f * p; scaleX = scaleY; alpha = p.coerceAtMost(1f) }
        NavTransition.SPRING_SLIDE -> translationX = q * travelPx * 1.4f
        NavTransition.ROTATE -> {
            rotationZ = q * 8f
            alpha = p
            scaleX = 0.94f + 0.06f * p
            scaleY = scaleX
        }
        NavTransition.FLIP_X -> {
            cameraDistance = 14f * density
            rotationY = q * 90f
            alpha = if (p < 0.15f) 0f else 1f
        }
        NavTransition.FLIP_Y -> {
            cameraDistance = 14f * density
            rotationX = -q * 90f
            alpha = if (p < 0.15f) 0f else 1f
        }
    }
}

/**
 * The looping preview used by the Animations settings page: a small card plays the chosen
 * transition over and over so the user sees exactly what they are about to pick.
 */
@Composable
fun NavTransitionPreview(transition: NavTransition, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val progress = remember { Animatable(1f) }
    LaunchedEffect(transition) {
        while (true) {
            delay(420)
            progress.snapTo(0f)
            progress.animateTo(1f, specFor(transition))
        }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(scheme.surfaceContainerLow)
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    applyTransition(transition, progress.value, 30f * density)
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.8f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(scheme.primary.copy(alpha = 0.55f))
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.55f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(scheme.outlineVariant)
            )
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(scheme.primaryContainer)
            )
        }
    }
}

/** Short caption shown under a preview so the list reads clearly. */
fun NavTransition.description(): String = when (group) {
    "Plain" -> "No movement"
    "Slide" -> "Screen glides in"
    "Scale" -> "Screen grows into place"
    "Playful" -> "Springy and bouncy"
    else -> "Turns in 3D"
}

/** Guards against a saved id that no longer exists after an update. */
fun navTransitionOrNone(id: String?, enabled: Boolean): NavTransition =
    if (!enabled) NavTransition.NONE else NavTransition.fromId(id)
