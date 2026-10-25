package dev.akkraizen.berill.api.ui.markdown

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import dev.akkraizen.berill.api.ui.selection.pastelCheckboxColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import dev.akkraizen.berill.api.ui.interaction.trackTextInput
import dev.akkraizen.berill.api.ui.navigation.rememberOpenLink

@Composable
fun MarkdownEditor(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  placeholder: String = "",
  textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
  onWikiLink: ((String) -> Unit)? = null
) {
  var field by remember { mutableStateOf(TextFieldValue(value)) }
  val current = if (field.text == value)
    field
  else
    TextFieldValue(
      value,
      TextRange(field.selection.start.coerceIn(0, value.length), field.selection.end.coerceIn(0, value.length))
    )
  var focused by remember { mutableStateOf(false) }
  val theme = rememberLiveTheme()
  val active = if (focused)
    activeLines(current.text, current.selection.min, current.selection.max)
  else
    emptySet()
  val holder = remember { LiveHolder() }
  val transformation = remember(active, theme) { LiveTransformation(active, theme, holder) }
  val layout = remember { mutableStateOf<TextLayoutResult?>(null) }
  val openLink = rememberOpenLink()
  val colors = MaterialTheme.colorScheme

  fun commit(next: TextFieldValue) {
    field = next
    if (next.text != value) onValueChange(next.text)
  }

  val onAction by rememberUpdatedState { action: LiveAction ->
    when (action) {
      is LiveAction.Url -> openLink(action.url)
      is LiveAction.Wiki -> onWikiLink?.invoke(action.page)
      is LiveAction.Task ->
        commit(current.copy(text = toggleMarkdownTask(current.text, action.line)))
    }
    Unit
  }

  BasicTextField(
    value = current,
    onValueChange = { next -> commit(MarkdownEditing.onInput(current, next)) },
    textStyle = textStyle.copy(color = colors.onSurface),
    cursorBrush = SolidColor(colors.primary),
    visualTransformation = transformation,
    onTextLayout = { layout.value = it },
    modifier = modifier
      .trackTextInput()
      .onFocusChanged { focused = it.isFocused || it.hasFocus }
      .onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val ctrl = e.isCtrlPressed || e.isMetaPressed
        when {
          e.key == Key.Tab && !ctrl -> {
            commit(MarkdownEditing.indent(current, outdent = e.isShiftPressed))
            true
          }

          ctrl && e.key == Key.B -> {
            commit(MarkdownEditing.wrap(current, "**"))
            true
          }

          ctrl && e.key == Key.I -> {
            commit(MarkdownEditing.wrap(current, "*"))
            true
          }

          else -> false
        }
      },
    decorationBox = { inner ->
      Box(
        Modifier.drawBehind {
          val r = holder.result
          val l = layout.value
          if (r != null && l != null) drawLivePreview(r, l, theme)
        }
          .pointerInput(Unit) {
            awaitPointerEventScope {
              while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Press) continue
                val change = event.changes.firstOrNull() ?: continue
                val r = holder.result ?: continue
                val l = layout.value ?: continue
                val action = hitLivePreview(r, l, change.position.x, change.position.y) ?: continue
                change.consume()
                onAction(action)
                do {
                  val rest = awaitPointerEvent(PointerEventPass.Initial)
                  rest.changes.forEach { it.consume() }
                } while (rest.changes.any { it.pressed })
              }
            }
          }
      ) {
        if (current.text.isEmpty()) {
          Text(placeholder, style = textStyle.copy(color = colors.onSurfaceVariant.copy(alpha = 0.6f)))
        }
        inner()
      }
    }
  )
}

@Composable
private fun rememberLiveTheme(): LiveTheme {
  val c = MaterialTheme.colorScheme
  val t = MaterialTheme.typography
  val md = rememberMarkdownStyle()
  val checkboxColors = pastelCheckboxColors()
  return remember(c, t, md, checkboxColors) {
    val headingStyles = listOf(
      t.headlineMedium,
      t.headlineSmall,
      t.titleLarge,
      t.titleMedium,
      t.titleSmall,
      t.titleSmall
    )
    LiveTheme(
      md = md,
      dim = SpanStyle(color = c.onSurfaceVariant.copy(alpha = 0.55f)),
      headings = headingStyles.map {
        SpanStyle(fontSize = it.fontSize, fontWeight = it.fontWeight) to
          ParagraphStyle(lineHeight = it.lineHeight)
      },
      codeText = SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
      fence =
        SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = c.onSurfaceVariant.copy(alpha = 0.7f)),
      frontmatter = SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = c.onSurfaceVariant),
      table = SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
      done = SpanStyle(color = c.onSurfaceVariant, textDecoration = TextDecoration.LineThrough),
      calloutTitle = SpanStyle(fontWeight = FontWeight.SemiBold),
      codeBackground = c.surfaceContainerHighest,
      frontBackground = c.surfaceContainer,
      quoteBar = c.primary.copy(alpha = 0.5f),
      rule = c.outlineVariant,
      checkBorder = c.onSurfaceVariant,
      checkFill = checkboxColors.checkedBoxColor,
      checkMark = checkboxColors.checkedCheckmarkColor,
      callouts = mapOf(
        CalloutTone.Info to (c.secondaryContainer.copy(alpha = 0.6f) to c.secondary),
        CalloutTone.Tip to (c.tertiaryContainer.copy(alpha = 0.6f) to c.tertiary),
        CalloutTone.Warning to (c.errorContainer.copy(alpha = 0.6f) to c.error),
        CalloutTone.Quote to (c.surfaceContainerHigh to c.outline)
      )
    )
  }
}

object MarkdownEditing {
  private val LIST_PREFIX = Regex("""^([ \t]*)(?:([-*+])|(\d{1,9})([.)]))([ \t]+)(\[[ xX]][ \t]+)?""")
  private val QUOTE_PREFIX = Regex("""^[ \t]*>[ \t]?""")

  fun onInput(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
    if (new.composition != null || !old.selection.collapsed || !new.selection.collapsed) return new
    val o = old.text
    val n = new.text
    val p = old.selection.start
    val inserted = n.length == o.length + 1 &&
      new.selection.start == p + 1 &&
      n.regionMatches(0, o, 0, p) &&
      n.regionMatches(p + 1, o, p, o.length - p)
    if (inserted) {
      return when (val ch = n[p]) {
        '\n' -> newline(o, p) ?: new
        '[',
        '(' -> {
          val next = o.getOrNull(p)
          if (next == null || next.isWhitespace() || next == ']' || next == ')') {
            val close = if (ch == '[') ']' else ')'
            TextFieldValue(o.substring(0, p) + ch + close + o.substring(p), TextRange(p + 1))
          } else
            new
        }

        ']',
        ')' -> if (o.getOrNull(p) == ch) TextFieldValue(o, TextRange(p + 1)) else new

        else -> new
      }
    }
    val deleted = p > 0 &&
      n.length == o.length - 1 &&
      new.selection.start == p - 1 &&
      n.regionMatches(0, o, 0, p - 1) &&
      n.regionMatches(p - 1, o, p, o.length - p)
    if (deleted) {
      val pair = o[p - 1] to o.getOrNull(p)
      if (pair == ('[' to ']') || pair == ('(' to ')'))
        return TextFieldValue(o.removeRange(p - 1, p + 1), TextRange(p - 1))
    }
    return new
  }

  private fun newline(o: String, p: Int): TextFieldValue? {
    val ls = o.lastIndexOf('\n', p - 1) + 1
    val le = o.indexOf('\n', p).let { if (it < 0) o.length else it }
    val line = o.substring(ls, le)
    val list = LIST_PREFIX.find(line)
    val quote = if (list == null) QUOTE_PREFIX.find(line) else null
    val m = list ?: quote ?: return null
    val prefix = m.value.length
    if (p < ls + prefix) return null
    if (line.substring(prefix).isBlank()) {
      return TextFieldValue(o.substring(0, ls) + o.substring(le), TextRange(ls))
    }
    val next = if (list != null) {
      val g = list.groupValues
      val marker = g[2].ifEmpty { "${(g[3].toLongOrNull() ?: 0) + 1}${g[4]}" }
      g[1] + marker + g[5] + (if (g[6].isNotEmpty()) "[ ] " else "")
    } else {
      m.value
    }
    val insert = "\n" + next
    return TextFieldValue(o.substring(0, p) + insert + o.substring(p), TextRange(p + insert.length))
  }

  fun indent(value: TextFieldValue, outdent: Boolean): TextFieldValue {
    val t = value.text
    val a = value.selection.min
    val z = value.selection.max
    val starts = mutableListOf(t.lastIndexOf('\n', a - 1) + 1)
    while (true) {
      val next = t.indexOf('\n', starts.last())
      if (next < 0 || next + 1 > z || (next + 1 == z && z > a)) break
      starts += next + 1
    }
    val single = value.selection.collapsed && starts.size == 1
    if (!outdent && single && LIST_PREFIX.find(t.substring(starts[0])) == null) {
      return TextFieldValue(t.substring(0, a) + "\t" + t.substring(a), TextRange(a + 1))
    }
    val removed = starts.associateWith { s ->
      if (!outdent)
        0
      else
        when {
          t.startsWith("\t", s) -> 1
          else -> t.substring(s).takeWhile { it == ' ' }.length.coerceAtMost(4)
        }
    }
    val out = StringBuilder()
    var from = 0
    for (s in starts) {
      out.appendRange(t, from, s)
      if (outdent)
        from = s + removed.getValue(s)
      else {
        out.append('\t')
        from = s
      }
    }
    out.appendRange(t, from, t.length)
    fun map(x: Int): Int {
      var r = x
      for (s in starts) {
        if (outdent) {
          val len = removed.getValue(s)
          if (x >= s + len)
            r -= len
          else if (x > s)
            r -= x - s
        } else if (x >= s) {
          r += 1
        }
      }
      return r
    }

    val sel = value.selection
    return TextFieldValue(out.toString(), TextRange(map(sel.start), map(sel.end)))
  }

  fun wrap(value: TextFieldValue, mark: String): TextFieldValue {
    val t = value.text
    val a = value.selection.min
    val z = value.selection.max
    val len = mark.length
    if (a >= len &&
      z + len <= t.length &&
      t.regionMatches(a - len, mark, 0, len) &&
      t.regionMatches(z, mark, 0, len)
    ) {
      val text = t.substring(0, a - len) + t.substring(a, z) + t.substring(z + len)
      return TextFieldValue(text, TextRange(a - len, z - len))
    }
    val text = t.substring(0, a) + mark + t.substring(a, z) + mark + t.substring(z)
    return TextFieldValue(text, TextRange(a + len, z + len))
  }
}
