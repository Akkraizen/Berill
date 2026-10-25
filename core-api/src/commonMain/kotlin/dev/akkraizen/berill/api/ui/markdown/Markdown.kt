package dev.akkraizen.berill.api.ui.markdown

enum class MdAlign {
  Start,
  Center,
  End
}

sealed interface MdBlock {
  data class Heading(val level: Int, val text: String) : MdBlock

  data class Paragraph(val text: String) : MdBlock

  data class ListBlock(val items: List<MdListItem>) : MdBlock

  data class Quote(val blocks: List<MdBlock>) : MdBlock

  data class Callout(val kind: String, val title: String, val blocks: List<MdBlock>) : MdBlock

  data class Code(val language: String, val code: String) : MdBlock

  data class Table(val header: List<String>, val align: List<MdAlign>, val rows: List<List<String>>) : MdBlock

  data class Properties(val entries: List<Pair<String, String>>) : MdBlock

  data class Image(val alt: String, val url: String) : MdBlock

  data object Rule : MdBlock
}

data class MdListItem(
  val level: Int,
  val number: String?,
  val checked: Boolean?,
  val text: String,
  val line: Int
)

fun parseMarkdown(text: String): List<MdBlock> =
  MdParser(
    text
      .split('\n')
      .map { it.removeSuffix("\r") },
    0,
    true
  ).parse()

fun toggleMarkdownTask(text: String, line: Int): String {
  val lines = text.split('\n').toMutableList()
  if (line !in lines.indices) return text
  val match = TASK_BOX.find(lines[line]) ?: return text
  val pos = match.groupValues[1].length
  val next = if (match.groupValues[2].isBlank()) "x" else " "
  lines[line] = lines[line].replaceRange(pos, pos + 1, next)
  return lines.joinToString("\n")
}

fun markdownPlainText(text: String): String {
  val inline = MdInline(MarkdownStyle.Plain, onWikiLink = null)
  val out = mutableListOf<String>()
  fun walk(blocks: List<MdBlock>) {
    for (b in blocks) when (b) {
      is MdBlock.Heading -> out += inline.build(b.text).text
      is MdBlock.Paragraph -> out += inline.build(b.text).text
      is MdBlock.ListBlock -> b.items.forEach { out += inline.build(it.text).text }
      is MdBlock.Quote -> walk(b.blocks)
      is MdBlock.Callout -> {
        if (b.title.isNotBlank()) out += inline.build(b.title).text
        walk(b.blocks)
      }

      is MdBlock.Code -> out += b.code
      is MdBlock.Table ->
        (listOf(b.header) + b.rows).forEach { row ->
          out += row.joinToString(" · ") { inline.build(it).text }
        }

      is MdBlock.Image -> if (b.alt.isNotBlank()) out += b.alt
      is MdBlock.Properties,
      MdBlock.Rule -> Unit
    }
  }
  walk(parseMarkdown(text))
  return out.joinToString("\n")
}

private val HEADING = Regex("""^(#{1,6})\s+(.*?)\s*#*\s*$""")
private val RULE = Regex("""^(?:(?:\*\s*){3,}|(?:-\s*){3,}|(?:_\s*){3,})$""")
private val LIST_ITEM = Regex("""^([ \t]*)([-*+]|\d{1,9}[.)])(?:[ \t]+(.*))?$""")
private val TASK = Regex("""^\[([ xX])\](?:[ \t]+(.*))?$""")
private val TASK_BOX = Regex("""^([ \t]*(?:[-*+]|\d{1,9}[.)])[ \t]+\[)([ xX])\]""")
private val TABLE_SEPARATOR = Regex("""^\|?\s*:?-+:?\s*(\|\s*:?-+:?\s*)*\|?$""")
private val IMAGE_LINE = Regex("""^!\[([^\]]*)]\(([^)\s]+)(?:\s+"[^"]*")?\)$""")
private val CALLOUT = Regex("""^\[!([A-Za-z-]+)][+-]?\s*(.*)$""")
private val CELL_SPLIT = Regex("""(?<!\\)\|""")

private class MdParser(private val lines: List<String>, private val base: Int, private val document: Boolean) {
  fun parse(): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    var i = 0
    if (document && lines.firstOrNull()?.trim() == "---") {
      val end = (1 until lines.size).firstOrNull { lines[it].trim() == "---" }
      if (end != null) {
        val entries = lines.subList(1, end).mapNotNull { line ->
          val key = line.substringBefore(':', "").trim()
          if (key.isEmpty() || line.startsWith(" ") || line.startsWith("\t"))
            null
          else
            key to line.substringAfter(':').trim().removeSurrounding("\"")
        }
        if (entries.isNotEmpty()) out += MdBlock.Properties(entries)
        i = end + 1
      }
    }
    while (i < lines.size) {
      val t = lines[i].trim()
      when {
        t.isEmpty() -> i++
        t.startsWith("```") || t.startsWith("~~~") -> i = code(i, out)
        HEADING.matches(t) -> {
          val m = HEADING.find(t)!!
          out += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2])
          i++
        }

        RULE.matches(t) -> {
          out += MdBlock.Rule
          i++
        }

        t.startsWith(">") -> i = quote(i, out)
        isTableStart(i) -> i = table(i, out)
        LIST_ITEM.matches(lines[i]) -> i = list(i, out)
        IMAGE_LINE.matches(t) -> {
          val m = IMAGE_LINE.find(t)!!
          out += MdBlock.Image(m.groupValues[1], m.groupValues[2])
          i++
        }

        else -> i = paragraph(i, out)
      }
    }
    return out
  }

  private fun startsBlock(i: Int): Boolean {
    val t = lines[i].trim()
    return t.isEmpty() ||
      t.startsWith("```") ||
      t.startsWith("~~~") ||
      HEADING.matches(t) ||
      RULE.matches(t) ||
      t.startsWith(">") ||
      LIST_ITEM.matches(lines[i]) ||
      isTableStart(i)
  }

  private fun paragraph(start: Int, out: MutableList<MdBlock>): Int {
    var i = start + 1
    while (i < lines.size && !startsBlock(i)) i++
    out += MdBlock.Paragraph(lines.subList(start, i).joinToString("\n") { it.trim() })
    return i
  }

  private fun code(start: Int, out: MutableList<MdBlock>): Int {
    val open = lines[start].trim()
    val fence = open.take(3)
    val language = open.drop(3).trim()
    var i = start + 1
    val body = mutableListOf<String>()
    while (i < lines.size && !lines[i].trim().startsWith(fence)) body += lines[i++]
    out += MdBlock.Code(language, body.joinToString("\n"))
    return if (i < lines.size) i + 1 else i
  }

  private fun quote(start: Int, out: MutableList<MdBlock>): Int {
    var i = start
    val inner = mutableListOf<String>()
    while (i < lines.size && lines[i].trimStart().startsWith(">")) {
      inner +=
        lines[i].trimStart().removePrefix(">").let { if (it.startsWith(" ")) it.drop(1) else it }
      i++
    }
    val callout = CALLOUT.find(inner.first().trim())
    out +=
      if (callout != null) {
        val kind = callout.groupValues[1].lowercase()
        val blocks = MdParser(inner.drop(1), base + start + 1, false).parse()
        MdBlock.Callout(kind, callout.groupValues[2], blocks)
      } else {
        MdBlock.Quote(MdParser(inner, base + start, false).parse())
      }
    return i
  }

  private fun isTableStart(i: Int): Boolean =
    i + 1 < lines.size &&
      lines[i].contains('|') &&
      lines[i + 1].contains('-') &&
      TABLE_SEPARATOR.matches(lines[i + 1].trim())

  private fun cells(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split(CELL_SPLIT).map {
      it.trim().replace("\\|", "|")
    }

  private fun table(start: Int, out: MutableList<MdBlock>): Int {
    val header = cells(lines[start])
    val align = cells(lines[start + 1]).map {
      when {
        it.startsWith(":") && it.endsWith(":") -> MdAlign.Center
        it.endsWith(":") -> MdAlign.End
        else -> MdAlign.Start
      }
    }
    var i = start + 2
    val rows = mutableListOf<List<String>>()
    while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) {
      val row = cells(lines[i])
      rows += List(header.size) { row.getOrElse(it) { "" } }
      i++
    }
    out += MdBlock.Table(header, List(header.size) { align.getOrElse(it) { MdAlign.Start } }, rows)
    return i
  }

  private fun list(start: Int, out: MutableList<MdBlock>): Int {
    data class Raw(
      val indent: Int,
      val number: String?,
      val checked: Boolean?,
      var text: String,
      val line: Int
    )

    val raw = mutableListOf<Raw>()
    var i = start
    while (i < lines.size) {
      val line = lines[i]
      val m = LIST_ITEM.find(line)
      when {
        m != null -> {
          val indent = m.groupValues[1].fold(0) { w, c -> w + if (c == '\t') 4 else 1 }
          val marker = m.groupValues[2]
          var text = m.groupValues[3]
          var checked: Boolean? = null
          TASK.find(text)?.let { task ->
            checked = task.groupValues[1].isNotBlank()
            text = task.groupValues[2]
          }
          val number = marker.takeIf { it.first().isDigit() }?.let { it.dropLast(1) + "." }
          raw += Raw(indent, number, checked, text, base + i)
          i++
        }

        line.isNotBlank() &&
          (line.startsWith(" ") || line.startsWith("\t")) &&
          raw.isNotEmpty() &&
          !startsBlock(i) -> {
          raw.last().text += "\n" + line.trim()
          i++
        }

        else -> break
      }
    }
    val levels = raw.map { it.indent }.distinct().sorted()
    out +=
      MdBlock.ListBlock(
        raw.map {
          MdListItem(levels.indexOf(it.indent), it.number, it.checked, it.text, it.line)
        }
      )
    return i
  }
}

internal enum class CalloutTone {
  Info,
  Tip,
  Warning,
  Quote
}

internal fun calloutTone(kind: String): CalloutTone =
  when (kind) {
    "tip",
    "hint",
    "important",
    "success",
    "check",
    "done" -> CalloutTone.Tip

    "warning",
    "caution",
    "attention",
    "danger",
    "error",
    "bug",
    "failure",
    "fail",
    "missing" -> CalloutTone.Warning

    "quote",
    "cite" -> CalloutTone.Quote

    else -> CalloutTone.Info
  }
