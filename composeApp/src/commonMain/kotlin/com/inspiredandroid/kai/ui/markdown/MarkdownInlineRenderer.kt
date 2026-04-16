package com.inspiredandroid.kai.ui.markdown

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

@Composable
internal fun List<InlineNode>.toAnnotatedString(): AnnotatedString {
    val colors = MaterialTheme.colorScheme
    return buildAnnotatedString { appendInlines(this@toAnnotatedString, colors) }
}

private fun AnnotatedString.Builder.appendInlines(nodes: List<InlineNode>, colors: ColorScheme) {
    for (n in nodes) appendInline(n, colors)
}

private fun AnnotatedString.Builder.appendInline(node: InlineNode, colors: ColorScheme) {
    when (node) {
        is Text -> append(node.value)

        is Strong -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            appendInlines(node.children, colors)
        }

        is Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
            appendInlines(node.children, colors)
        }

        is Strike -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
            appendInlines(node.children, colors)
        }

        is InlineCode -> withStyle(
            SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = colors.surfaceVariant,
            ),
        ) {
            append(node.code)
        }

        is Link -> withLink(
            LinkAnnotation.Url(
                url = node.href,
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color = colors.primary,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline,
                    ),
                ),
            ),
        ) {
            appendInlines(node.children, colors)
        }

        is Image -> append(node.alt)

        LineBreak -> append('\n')
    }
}
