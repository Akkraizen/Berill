package dev.akkraizen.berill.api.ui.markdown

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import dev.akkraizen.berill.api.ui.navigation.isSafeExternalLink

class MarkdownStyle(
  val code: SpanStyle,
  val link: SpanStyle,
  val wikiLink: SpanStyle,
  val highlight: SpanStyle,
  val tag: SpanStyle
) {
  companion object {
    val Plain = MarkdownStyle(SpanStyle(), SpanStyle(), SpanStyle(), SpanStyle(), SpanStyle())
  }
}

class MdInline(private val style: MarkdownStyle, private val onWikiLink: ((String) -> Unit)?) {
  fun build(text: String): AnnotatedString = buildAnnotatedString { render(text) }

  private val hover = SpanStyle(textDecoration = TextDecoration.Underline)

  private fun AnnotatedString.Builder.render(s: String) {
    val plain = StringBuilder()
    fun flush() {
      if (plain.isNotEmpty()) {
        append(plain.toString())
        plain.clear()
      }
    }

    var i = 0
    while (i < s.length) {
      val c = s[i]
      if (c == '\\' && i + 1 < s.length && s[i + 1] in ESCAPABLE) {
        plain.append(s[i + 1])
        i += 2
        continue
      }
      if (c == '`') {
        val end = s.indexOf('`', i + 1)
        if (end > i + 1) {
          flush()
          withStyle(style.code) { append(s.substring(i + 1, end)) }
          i = end + 1
          continue
        }
      }
      if (c == '!' && s.startsWith("[[", i + 1)) {
        i++
        continue
      }
      if (s.startsWith("[[", i)) {
        val end = s.indexOf("]]", i + 2)
        if (end > i + 2) {
          flush()
          val inner = s.substring(i + 2, end)
          val target = inner.substringBefore('|').trim()
          val page = target.substringBefore('#').trim()
          val label = if ('|' in inner) inner.substringAfter('|').trim() else target.replace("#", " › ")
          val handler = onWikiLink
          if (handler != null && page.isNotEmpty()) {
            withLink(
              LinkAnnotation.Clickable(
                "wiki:$page",
                TextLinkStyles(style.wikiLink, hoveredStyle = hover),
                LinkInteractionListener { handler(page) }
              )
            ) {
              append(label)
            }
          } else {
            withStyle(style.wikiLink) { append(label) }
          }
          i = end + 2
          continue
        }
      }
      if (c == '[' || (c == '!' && s.startsWith("[", i + 1))) {
        val open = if (c == '!') i + 1 else i
        val close = closingBracket(s, open)
        if (close > 0 && s.startsWith("(", close + 1)) {
          val end = s.indexOf(')', close + 2)
          if (end > 0) {
            flush()
            val url = s.substring(close + 2, end).trim().substringBefore(' ')
            val label = s.substring(open + 1, close).ifEmpty { url }
            if (isSafeExternalLink(url)) {
              withLink(LinkAnnotation.Url(url, TextLinkStyles(style.link, hoveredStyle = hover))) {
                if (c == '!')
                  withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(label) }
                else
                  render(label)
              }
            } else {
              if (c == '!')
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(label) }
              else
                render(label)
            }
            i = end + 1
            continue
          }
        }
      }
      if (c == '<') {
        val end = s.indexOf('>', i + 1)
        val inner = if (end > 0) s.substring(i + 1, end) else ""
        if (inner.startsWith("http://") || inner.startsWith("https://")) {
          flush()
          withLink(LinkAnnotation.Url(inner, TextLinkStyles(style.link, hoveredStyle = hover))) {
            append(inner)
          }
          i = end + 1
          continue
        }
      }
      if ((s.startsWith("https://", i) || s.startsWith("http://", i)) &&
        (i == 0 || s[i - 1].isWhitespace() || s[i - 1] == '(')
      ) {
        var end = i
        while (end < s.length && !s[end].isWhitespace()) end++
        while (end > i && s[end - 1] in ".,;:!?)\"'") end--
        flush()
        val url = s.substring(i, end)
        withLink(LinkAnnotation.Url(url, TextLinkStyles(style.link, hoveredStyle = hover))) {
          append(url)
        }
        i = end
        continue
      }
      if (c == '#' &&
        (i == 0 || s[i - 1].isWhitespace()) &&
        i + 1 < s.length &&
        (s[i + 1].isLetter() || s[i + 1] == '_')
      ) {
        var end = i + 1
        while (end < s.length && (s[end].isLetterOrDigit() || s[end] in "_-/")) end++
        flush()
        withStyle(style.tag) { append(s.substring(i, end)) }
        i = end
        continue
      }
      val emphasis = emphasisAt(s, i)
      if (emphasis != null) {
        val (delimiter, close) = emphasis
        flush()
        withStyle(spanFor(delimiter)) { render(s.substring(i + delimiter.length, close)) }
        i = close + delimiter.length
        continue
      }
      plain.append(c)
      i++
    }
    flush()
  }

  private fun emphasisAt(s: String, i: Int): Pair<String, Int>? = findEmphasis(s, i)

  private fun spanFor(d: String): SpanStyle = emphasisSpan(d, style.highlight)

  private fun closingBracket(s: String, open: Int): Int {
    var depth = 0
    for (k in open until s.length) {
      when (s[k]) {
        '[' -> depth++
        ']' -> {
          depth--
          if (depth == 0) return k
        }
      }
    }
    return -1
  }

  private companion object {
    const val ESCAPABLE = "\\`*_{}[]()#+-.!|~=<>"
  }
}

internal fun emphasisSpan(delimiter: String, highlight: SpanStyle): SpanStyle =
  when (delimiter) {
    "**", "__" -> SpanStyle(fontWeight = FontWeight.Bold)
    "*", "_" -> SpanStyle(fontStyle = FontStyle.Italic)
    "~~" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    else -> highlight
  }
