package dev.akkraizen.berill.api.ui.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

internal sealed interface LiveAction {
  data class Url(val url: String) : LiveAction
  data class Wiki(val page: String) : LiveAction
  data class Task(val line: Int) : LiveAction
}

internal data class LiveClick(val start: Int, val end: Int, val action: LiveAction)

internal sealed interface LiveDecoration {
  data class Block(val start: Int, val end: Int, val color: Color) : LiveDecoration

  data class Bar(val start: Int, val end: Int, val color: Color) : LiveDecoration

  data class Rule(val start: Int) : LiveDecoration

  data class Checkbox(val start: Int, val end: Int, val checked: Boolean) : LiveDecoration
}

internal data class LiveTheme(
  val md: MarkdownStyle,
  val dim: SpanStyle,
  val headings: List<Pair<SpanStyle, ParagraphStyle>>,
  val codeText: SpanStyle,
  val fence: SpanStyle,
  val frontmatter: SpanStyle,
  val table: SpanStyle,
  val done: SpanStyle,
  val calloutTitle: SpanStyle,
  val codeBackground: Color,
  val frontBackground: Color,
  val quoteBar: Color,
  val rule: Color,
  val checkBorder: Color,
  val checkFill: Color,
  val checkMark: Color,
  val callouts: Map<CalloutTone, Pair<Color, Color>>
)

internal class LiveResult(
  val text: AnnotatedString,
  val mapping: OffsetMapping,
  val decorations: List<LiveDecoration>,
  val clicks: List<LiveClick>,
  private val visible: IntArray
) {
  fun toShown(original: Int): Int = visible[original.coerceIn(0, visible.size - 1)]
}

internal class LiveHolder {
  var result: LiveResult? = null
}

internal class LiveTransformation(
  private val active: Set<Int>,
  private val theme: LiveTheme,
  private val holder: LiveHolder
) : VisualTransformation {
  override fun filter(text: AnnotatedString): TransformedText {
    val result = buildLivePreview(text.text, active, theme)
    holder.result = result
    return TransformedText(result.text, result.mapping)
  }

  override fun equals(other: Any?): Boolean =
    other is LiveTransformation &&
      other.active == active &&
      other.theme == theme &&
      other.holder === holder

  override fun hashCode(): Int = active.hashCode() * 31 + theme.hashCode()
}

internal fun activeLines(text: String, selectionMin: Int, selectionMax: Int): Set<Int> {
  fun lineAt(offset: Int): Int {
    var count = 0
    for (k in 0 until offset.coerceIn(0, text.length))
      if (text[k] == '\n') count++
    return count
  }
  return (lineAt(selectionMin)..lineAt(selectionMax)).toSet()
}
