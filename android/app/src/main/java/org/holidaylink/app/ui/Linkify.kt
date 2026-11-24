package org.holidaylink.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

// Endings accepted for an address typed without "https://" (example.com, holidaylink.app/x).
// A list, not "anything", so "file.txt" or "3.5" are not turned into links.
private const val TLDS = "com|net|org|io|co|app|dev|me|ug|info|biz|edu|gov|tv|ly|link|site|online|xyz|ai|uk|us|ke|tz|rw|za|ng|gh|in|de|fr|be|ca|au|ru|cn|jp|cc|ws|to|gl|gg|page|store|tech|shop|live|news|blog|top|club"

private val URL_REGEX = Regex(
    """(https?://[^\s<>"]+|www\.[^\s<>"]+|(?<![\w@./-])(?:[a-z0-9][a-z0-9-]*\.)+(?:$TLDS)(?![\w@-])(?::\d+)?(?:/[^\s<>"]*)?)""",
    RegexOption.IGNORE_CASE
)

/**
 * Turns web addresses inside a message into tappable links (underlined, opened in the
 * browser). Punctuation that ends a sentence ("see https://x.com.") is left out of the link.
 */
fun linkify(text: String, linkColor: Color): AnnotatedString {
    if (!text.contains('.')) return AnnotatedString(text)
    return buildAnnotatedString {
        var last = 0
        for (match in URL_REGEX.findAll(text)) {
            val link = match.value.trimEnd('.', ',', ')', '!', '?', ';', ':', ']', '}', '"', '\'')
            if (link.length < 5) continue
            append(text.substring(last, match.range.first))
            val target = if (link.startsWith("http://", ignoreCase = true) || link.startsWith("https://", ignoreCase = true)) link else "https://$link"
            withLink(
                LinkAnnotation.Url(
                    target,
                    TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                )
            ) { append(link) }
            last = match.range.first + link.length
        }
        append(text.substring(last))
    }
}
