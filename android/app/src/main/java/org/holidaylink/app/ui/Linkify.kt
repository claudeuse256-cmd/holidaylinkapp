package org.holidaylink.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

private val URL_REGEX = Regex("""(https?://[^\s<>"]+|www\.[^\s<>"]+)""", RegexOption.IGNORE_CASE)

/**
 * Turns web addresses inside a message into tappable links (underlined, opened in the
 * browser). Punctuation that ends a sentence ("see https://x.com.") is left out of the link.
 */
fun linkify(text: String, linkColor: Color): AnnotatedString {
    if (!text.contains("http", ignoreCase = true) && !text.contains("www.", ignoreCase = true)) {
        return AnnotatedString(text)
    }
    return buildAnnotatedString {
        var last = 0
        for (match in URL_REGEX.findAll(text)) {
            val link = match.value.trimEnd('.', ',', ')', '!', '?', ';', ':', ']', '}', '"', '\'')
            if (link.length < 5) continue
            append(text.substring(last, match.range.first))
            val target = if (link.startsWith("www.", ignoreCase = true)) "https://$link" else link
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
