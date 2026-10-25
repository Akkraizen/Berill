package dev.akkraizen.berill.api.ui.markdown

import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping

internal fun buildLivePreview(text: String, active: Set<Int>, theme: LiveTheme): LiveResult =
  LiveBuilder(text, active, theme).build()

private val HEADING_L = Regex("""^(#{1,6})[ \t]+""")
private val LIST_L = Regex("""^([ \t]*)([-*+]|\d{1,9}[.)])([ \t]+)(\[([ xX])\](?:[ \t]+|$))?""")
private val QUOTE_L = Regex("""^([ \t]*)>[ \t]?""")
private val CALLOUT_L = Regex("""^\[!([A-Za-z-]+)][+-]?""")
private val TABLE_L = Regex("""^\s*\|.*\|\s*$""")
private val TABLE_SEP_L = Regex("""^[\s|:\-]+$""")
private val RULE_L = Regex("""^(?:(?:\*[ \t]*){3,}|(?:-[ \t]*){3,}|(?:_[ \t]*){3,})$""")
private const val ESCAPABLE_L = "\\`*_{}[]()#+-.!|~=<>"

private class LiveBuilder(private val text: String, private val active: Set<Int>, private val theme: LiveTheme) {
  private val n = text.length
  private val hidden = BooleanArray(n)
  private val replace = HashMap<Int, Char>()
  private val spans = ArrayList<Triple<SpanStyle, Int, Int>>()
  private val paragraphs = ArrayList<Triple<ParagraphStyle, Int, Int>>()
  private val decorations = ArrayList<LiveDecoration>()
  private val clicks = ArrayList<LiveClick>()
  private val starts: IntArray = buildList {
    add(0)
    text.forEachIndexed { i, c -> if (c == '\n') add(i + 1) }
  }
    .toIntArray()

  private fun lineEnd(li: Int) = if (li + 1 < starts.size) starts[li + 1] - 1 else n

  private fun line(li: Int) = text.substring(starts[li], lineEnd(li))

  private fun hide(from: Int, to: Int) {
    for (k in from until to.coerceAtMost(n))
      if (text[k] != '\n') hidden[k] = true
  }

  private fun span(style: SpanStyle, from: Int, to: Int) {
    if (to > from)
      spans += Triple(style, from, to)
  }

  private fun markup(from: Int, to: Int, isActive: Boolean) {
    if (isActive)
      span(theme.dim, from, to)
    else
      hide(from, to)
  }

  fun build(): LiveResult {
    var li = 0
    if (line(0).trim() == "---") {
      val end = (1 until starts.size).firstOrNull {
        line(it).trim() == "---"
      }
      if (end != null) {
        span(theme.frontmatter, 0, lineEnd(end))
        decorations += LiveDecoration.Block(0, lineEnd(end), theme.frontBackground)
        li = end + 1
      }
    }
    while (li < starts.size) {
      val l = line(li)
      val t = l.trim()
      li = when {
        t.startsWith("```") || t.startsWith("~~~") -> codeBlock(li)
        QUOTE_L.containsMatchIn(l) -> quoteBlock(li)
        else -> {
          lineContent(li, starts[li], l)
          li + 1
        }
      }
    }
    return result()
  }

  private fun codeBlock(first: Int): Int {
    val fence = line(first).trim().take(3)
    var last = first + 1

    while (last < starts.size && !line(last).trim().startsWith(fence))
      last++

    val closed = last < starts.size
    val end = if (closed) last else starts.size - 1
    val blockActive = (first..end).any { it in active }

    for (k in first..end) {
      val isFence = k == first || (closed && k == end)
      span(
        if (isFence)
          (if (blockActive)
            theme.dim.merge(theme.codeText)
          else
            theme.fence)
        else
          theme.codeText,
        starts[k],
        lineEnd(k)
      )
    }
    decorations += LiveDecoration.Block(starts[first], lineEnd(end), theme.codeBackground)
    return end + 1
  }

  private fun quoteBlock(first: Int): Int {
    var last = first

    while (last + 1 < starts.size && QUOTE_L.containsMatchIn(line(last + 1)))
      last++

    var tone: CalloutTone? = null

    for (k in first..last) {
      val s = starts[k]
      val l = line(k)
      val m = QUOTE_L.find(l)!!
      val isActive = k in active
      val marker = s + m.groupValues[1].length

      if (isActive)
        span(theme.dim, s, s + m.value.length)
      else
        replace[marker] = ' '

      var contentFrom = m.value.length

      if (k == first) {
        val callout = CALLOUT_L.find(l.substring(contentFrom))

        if (callout != null) {
          tone = calloutTone(callout.groupValues[1].lowercase())
          val cs = s + contentFrom
          val ce = cs + callout.value.length
          val hasTitle = l.substring(contentFrom + callout.value.length).isNotBlank()

          if (hasTitle || isActive) {
            markup(cs, ce, isActive)
          } else {
            val kindEnd = cs + 2 + callout.groupValues[1].length
            markup(cs, cs + 2, false)
            markup(kindEnd, ce, false)
            span(theme.calloutTitle, cs + 2, kindEnd)
          }

          span(theme.calloutTitle, ce, s + l.length)
          contentFrom += callout.value.length
        }
      }

      lineContent(k, s + contentFrom, l.substring(contentFrom), quoted = true)
    }

    val colors = tone?.let { theme.callouts.getValue(it) }
    if (colors != null)
      decorations += LiveDecoration.Block(starts[first], lineEnd(last), colors.first)
    decorations +=
      LiveDecoration.Bar(starts[first], lineEnd(last), colors?.second ?: theme.quoteBar)
    return last + 1
  }

  private fun lineContent(li: Int, base: Int, l: String, quoted: Boolean = false) {
    val isActive = li in active
    val trimmed = l.trim()
    HEADING_L.find(l)?.let { m ->
      val level = m.groupValues[1].length
      val (spanStyle, paragraph) = theme.headings[level - 1]
      markup(base, base + m.value.length, isActive)
      span(spanStyle, base, base + l.length)
      if (!quoted) paragraphs += Triple(paragraph, starts[li], lineEnd(li))
      inline(l, m.value.length, l.length, base, isActive)
      return
    }
    if (trimmed.isNotEmpty() && RULE_L.matches(trimmed)) {
      if (isActive) {
        span(theme.dim, base, base + l.length)
      } else {
        hide(base, base + l.length)
        decorations += LiveDecoration.Rule(starts[li])
      }
      return
    }
    if (TABLE_L.matches(l)) {
      span(theme.table, base, base + l.length)
      if (TABLE_SEP_L.matches(l)) {
        span(theme.dim, base, base + l.length)
      } else {
        l.forEachIndexed { k, c -> if (c == '|') span(theme.dim, base + k, base + k + 1) }
      }
      inline(l, 0, l.length, base, isActive)
      return
    }
    LIST_L.find(l)?.let { m ->
      val marker = base + m.groupValues[1].length
      val bullet = m.groupValues[2]
      if (bullet.length == 1 && !bullet[0].isDigit()) {
        if (!isActive) replace[marker] = '•'
      } else {
        span(theme.dim, marker, marker + bullet.length)
      }
      if (m.groups[4] != null) {
        val box = marker + bullet.length + m.groupValues[3].length
        val checked = m.groupValues[5].isNotBlank()
        if (!isActive) {
          for (k in box until box + 3) replace[k] = ' '
          decorations += LiveDecoration.Checkbox(box, box + 3, checked)
          clicks += LiveClick(box, box + 3, LiveAction.Task(li))
        } else {
          span(theme.dim, box, box + 3)
        }
        if (checked) span(theme.done, base + m.value.length, base + l.length)
      }
      inline(l, m.value.length, l.length, base, isActive)
      return
    }
    inline(l, 0, l.length, base, isActive)
  }

  private fun inline(l: String, lo: Int, hi: Int, base: Int, isActive: Boolean) {
    var i = lo
    while (i < hi) {
      val c = l[i]
      if (c == '\\' && i + 1 < hi && l[i + 1] in ESCAPABLE_L) {
        markup(base + i, base + i + 1, isActive)
        i += 2
        continue
      }
      if (c == '`') {
        val end = l.indexOf('`', i + 1)
        if (end in i + 2 until hi) {
          markup(base + i, base + i + 1, isActive)
          markup(base + end, base + end + 1, isActive)
          span(theme.md.code, base + i + 1, base + end)
          i = end + 1
          continue
        }
      }
      val embed = c == '!' && l.startsWith("[[", i + 1)
      if (embed || l.startsWith("[[", i)) {
        val open = if (embed) i + 1 else i
        val end = l.indexOf("]]", open + 2)
        if (end > open + 2 && end + 2 <= hi) {
          val inner = l.substring(open + 2, end)
          val pipe = inner.indexOf('|')
          val shownFrom = if (pipe >= 0) open + 3 + pipe else open + 2
          markup(base + i, base + shownFrom, isActive)
          markup(base + end, base + end + 2, isActive)
          span(theme.md.wikiLink, base + shownFrom, base + end)
          val page = inner.substringBefore('|').substringBefore('#').trim()
          if (!isActive && page.isNotEmpty())
            clicks += LiveClick(base + shownFrom, base + end, LiveAction.Wiki(page))
          i = end + 2
          continue
        }
      }
      if (c == '[' || (c == '!' && i + 1 < hi && l[i + 1] == '[')) {
        val open = if (c == '!') i + 1 else i
        val close = closing(l, open, hi)
        if (close > 0 && close + 1 < hi && l[close + 1] == '(') {
          val end = l.indexOf(')', close + 2)
          if (end in close + 2 until hi) {
            val url = l.substring(close + 2, end).trim().substringBefore(' ')
            markup(base + i, base + open + 1, isActive)
            markup(base + close, base + end + 1, isActive)
            span(theme.md.link, base + open + 1, base + close)
            if (!isActive && url.isNotEmpty())
              clicks += LiveClick(base + open + 1, base + close, LiveAction.Url(url))
            inline(l, open + 1, close, base, isActive)
            i = end + 1
            continue
          }
        }
      }
      if ((l.startsWith("https://", i) || l.startsWith("http://", i)) &&
        (i == 0 || l[i - 1].isWhitespace() || l[i - 1] in "(<")
      ) {
        var end = i
        while (end < hi && !l[end].isWhitespace() && l[end] != '>') end++
        while (end > i && l[end - 1] in ".,;:!?)\"'") end--
        span(theme.md.link, base + i, base + end)
        if (!isActive)
          clicks += LiveClick(base + i, base + end, LiveAction.Url(l.substring(i, end)))
        i = end
        continue
      }
      if (c == '#' &&
        (i == 0 || l[i - 1].isWhitespace()) &&
        i + 1 < hi &&
        (l[i + 1].isLetter() || l[i + 1] == '_')
      ) {
        var end = i + 1
        while (end < hi && (l[end].isLetterOrDigit() || l[end] in "_-/")) end++
        span(theme.md.tag, base + i, base + end)
        i = end
        continue
      }
      val emphasis = findEmphasis(l, i, hi)
      if (emphasis != null) {
        val (d, close) = emphasis
        markup(base + i, base + i + d.length, isActive)
        markup(base + close, base + close + d.length, isActive)
        span(emphasisStyle(d), base + i + d.length, base + close)
        inline(l, i + d.length, close, base, isActive)
        i = close + d.length
        continue
      }
      i++
    }
  }

  private fun emphasisStyle(d: String): SpanStyle = emphasisSpan(d, theme.md.highlight)

  private fun closing(s: String, open: Int, hi: Int): Int {
    var depth = 0
    for (k in open until hi) {
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

  private fun result(): LiveResult {
    val visible = IntArray(n + 1)
    val back = IntArray(n + 1)
    val shown = StringBuilder(n)
    for (o in 0 until n) {
      visible[o] = shown.length
      if (!hidden[o]) {
        back[shown.length] = o
        shown.append(replace[o] ?: text[o])
      }
    }
    val count = shown.length
    visible[n] = count
    back[count] = n
    val annotated = buildAnnotatedString {
      append(shown.toString())
      for ((style, a, z) in spans) {
        val ta = visible[a]
        val tz = visible[z.coerceAtMost(n)]
        if (tz > ta) addStyle(style, ta, tz)
      }
      for ((style, a, z) in paragraphs) {
        val ta = visible[a]
        val tz = visible[z.coerceAtMost(n)]
        if (tz > ta) addStyle(style, ta, tz)
      }
    }
    val mapping = object : OffsetMapping {
      override fun originalToTransformed(offset: Int): Int = visible[offset.coerceIn(0, n)]

      override fun transformedToOriginal(offset: Int): Int = back[offset.coerceIn(0, count)]
    }
    val ordered = decorations.sortedBy {
      when (it) {
        is LiveDecoration.Block -> 0
        is LiveDecoration.Bar -> 1
        else -> 2
      }
    }
    return LiveResult(annotated, mapping, ordered, clicks, visible)
  }
}

internal fun findEmphasis(s: String, i: Int, hi: Int = s.length): Pair<String, Int>? {
  for (d in EMPHASIS) {
    if (!s.startsWith(d, i)) continue
    val from = i + d.length
    if (from >= hi || s[from].isWhitespace()) continue
    if (d[0] == '_' && i > 0 && s[i - 1].isLetterOrDigit()) continue
    var close = s.indexOf(d, from)
    while (close in 0..(hi - d.length)) {
      val partOfDouble = d.length == 1 && s.startsWith(d + d, close)
      val ok = close > from &&
        !s[close - 1].isWhitespace() &&
        !partOfDouble &&
        (d.length == 2 || s[close - 1] != d[0]) &&
        (d[0] != '_' ||
          close + d.length >= s.length ||
          !s[close + d.length].isLetterOrDigit())
      if (ok) return d to close
      close = s.indexOf(d, close + if (partOfDouble) 2 else 1)
    }
  }
  return null
}

private val EMPHASIS = listOf("**", "__", "~~", "==", "*", "_")
