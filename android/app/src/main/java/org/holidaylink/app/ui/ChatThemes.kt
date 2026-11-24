package org.holidaylink.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A colour scheme for the message bubbles. [mine] is the bubble for what you write; [other]
 * is the bubble for what others write in the LIGHT app theme (in the dark theme their bubble
 * keeps the normal dark surface so text stays readable). A null colour means "app default".
 */
data class ChatTheme(
    val id: String,
    val name: String,
    val mine: Color? = null,
    val mineText: Color = Color.White,
    val other: Color? = null,
    val otherText: Color = Color(0xFF1B1B1F)
)

object ChatThemes {

    val presets: List<ChatTheme> = listOf(
        ChatTheme("default", "Default"),
        ChatTheme("ocean", "Ocean", Color(0xFF1E88E5), Color.White, Color(0xFFE3F2FD), Color(0xFF0D2B45)),
        ChatTheme("forest", "Forest", Color(0xFF2E7D32), Color.White, Color(0xFFE8F5E9), Color(0xFF143D18)),
        ChatTheme("teal", "Teal", Color(0xFF00897B), Color.White, Color(0xFFE0F2F1), Color(0xFF00332E)),
        ChatTheme("sunset", "Sunset", Color(0xFFEF6C00), Color.White, Color(0xFFFFF3E0), Color(0xFF4A2300)),
        ChatTheme("rose", "Rose", Color(0xFFD81B60), Color.White, Color(0xFFFCE4EC), Color(0xFF4A0A1E)),
        ChatTheme("violet", "Violet", Color(0xFF6A1B9A), Color.White, Color(0xFFF3E5F5), Color(0xFF2D0A3D)),
        ChatTheme("gold", "Gold", Color(0xFFF9A825), Color(0xFF2B2000), Color(0xFFFFF8E1), Color(0xFF3E2F00)),
        ChatTheme("graphite", "Graphite", Color(0xFF455A64), Color.White, Color(0xFFECEFF1), Color(0xFF1C262B))
    )

    /** "custom:RRGGBB" is a colour the user picked with the slider. */
    fun byId(id: String): ChatTheme {
        if (id.startsWith("custom:")) {
            val rgb = id.removePrefix("custom:").toIntOrNull(16)
            if (rgb != null) {
                val colour = Color(0xFF000000.toInt() or rgb)
                return ChatTheme(
                    id = id,
                    name = "Custom",
                    mine = colour,
                    mineText = if (colour.luminance() > 0.55f) Color(0xFF1B1B1F) else Color.White,
                    other = lerp(colour, Color.White, 0.88f),
                    otherText = Color(0xFF1B1B1F)
                )
            }
        }
        return presets.firstOrNull { it.id == id } ?: presets.first()
    }

    /** (bubble colour, text colour) for one side of a conversation under [theme]. */
    @Composable
    fun bubbleColors(theme: ChatTheme, mine: Boolean): Pair<Color, Color> {
        val scheme = MaterialTheme.colorScheme
        val dark = scheme.background.luminance() < 0.5f
        val mineColour = theme.mine
        val otherColour = theme.other
        return when {
            mine && mineColour != null -> mineColour to theme.mineText
            mine -> scheme.primaryContainer to scheme.onPrimaryContainer
            !dark && otherColour != null -> otherColour to theme.otherText
            else -> scheme.surfaceContainerHigh to scheme.onSurface
        }
    }
}

/** Settings › Appearance › Chat theme: live preview, ready-made themes and a custom colour. */
@Composable
fun ChatThemePicker(selectedId: String, onSelect: (String) -> Unit) {
    val current = ChatThemes.byId(selectedId)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PreviewBubble("Hi! How was your day?", mine = false, theme = current)
            PreviewBubble("Great, thanks for asking!", mine = true, theme = current)
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(ChatThemes.presets, key = { it.id }) { theme ->
                val selected = theme.id == selectedId
                Column(
                    Modifier
                        .width(92.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelect(theme.id) }
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PreviewBubble("Hi", mine = false, theme = theme, compact = true)
                    PreviewBubble("Hello", mine = true, theme = theme, compact = true)
                    Text(
                        theme.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Custom colour", style = MaterialTheme.typography.labelLarge)
        var hue by remember { mutableFloatStateOf(210f) }
        Slider(
            value = hue,
            onValueChange = { hue = it },
            onValueChangeFinished = {
                val rgb = Color.hsv(hue, 0.62f, 0.78f).toArgb() and 0xFFFFFF
                onSelect("custom:" + "%06X".format(rgb))
            },
            valueRange = 0f..360f
        )
    }
}

@Composable
private fun PreviewBubble(text: String, mine: Boolean, theme: ChatTheme, compact: Boolean = false) {
    val (background, foreground) = ChatThemes.bubbleColors(theme, mine)
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            text,
            color = foreground,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .clip(RoundedCornerShape(if (compact) 10.dp else 18.dp))
                .background(background)
                .padding(horizontal = if (compact) 8.dp else 14.dp, vertical = if (compact) 3.dp else 8.dp)
        )
    }
}
