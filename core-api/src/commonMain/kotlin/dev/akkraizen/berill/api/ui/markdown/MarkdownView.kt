package dev.akkraizen.berill.api.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.akkraizen.berill.api.ui.LocalImagePolicy
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.interaction.interactive

@Composable
fun MarkdownText(
  markdown: String,
  modifier: Modifier = Modifier,
  textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
  onWikiLink: ((String) -> Unit)? = null,
  onToggleTask: ((line: Int) -> Unit)? = null
) {
  val blocks = remember(markdown) { parseMarkdown(markdown) }
  val wiki by rememberUpdatedState(onWikiLink)
  val toggle by rememberUpdatedState(onToggleTask)
  val style = rememberMarkdownStyle()
  val hasWiki = onWikiLink != null
  val inline = remember(style, hasWiki) {
    MdInline(style, if (hasWiki) { page -> wiki?.invoke(page) } else null)
  }
  val scope = MdScope(inline, textStyle, if (onToggleTask != null) { line -> toggle?.invoke(line) } else null)

  SelectionContainer(modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { blocks.forEach { scope.Block(it) } }
  }
}

@Composable
fun rememberMarkdownStyle(): MarkdownStyle {
  val c = MaterialTheme.colorScheme
  return remember(c.primary, c.surfaceContainerHighest, c.tertiaryContainer) {
    MarkdownStyle(
      code = SpanStyle(fontFamily = FontFamily.Monospace, background = c.surfaceContainerHighest),
      link = SpanStyle(color = c.primary, textDecoration = TextDecoration.Underline),
      wikiLink = SpanStyle(color = c.primary, fontWeight = FontWeight.SemiBold),
      highlight = SpanStyle(background = c.tertiaryContainer, color = c.onTertiaryContainer),
      tag = SpanStyle(color = c.primary, background = c.primary.copy(alpha = 0.12f))
    )
  }
}

private class MdScope(val inline: MdInline, val textStyle: TextStyle, val onToggleTask: ((Int) -> Unit)?) {
  @Composable
  fun Block(block: MdBlock) {
    when (block) {
      is MdBlock.Heading -> Heading(block)
      is MdBlock.Paragraph -> Inline(block.text, textStyle)
      is MdBlock.ListBlock -> ListView(block)
      is MdBlock.Quote -> QuoteView(block.blocks)
      is MdBlock.Callout -> CalloutView(block)
      is MdBlock.Code -> CodeView(block)
      is MdBlock.Table -> TableView(block)
      is MdBlock.Properties -> PropertiesView(block)
      is MdBlock.Image ->
        if (LocalImagePolicy.current(block.url)) {
          AsyncImage(
            model = block.url,
            contentDescription = block.alt.ifBlank { null },
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth()
              .heightIn(max = 420.dp)
              .clip(MaterialTheme.shapes.medium)
          )
        } else {
          Text(block.alt.ifBlank { block.url }, style = textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

      MdBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = 6.dp))
    }
  }

  @Composable
  fun Inline(text: String, style: TextStyle, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    val annotated = remember(text, inline) { inline.build(text) }
    Text(annotated, style = style, modifier = modifier, textAlign = textAlign)
  }

  @Composable
  private fun Heading(h: MdBlock.Heading) {
    val t = MaterialTheme.typography
    val style = when (h.level) {
      1 -> t.headlineMedium
      2 -> t.headlineSmall
      3 -> t.titleLarge
      4 -> t.titleMedium
      else -> t.titleSmall
    }
    Inline(h.text, style, Modifier.padding(top = if (h.level <= 2) 10.dp else 4.dp))
  }

  @Composable
  private fun ListView(list: MdBlock.ListBlock) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      list.items.forEach { item ->
        Row(Modifier.padding(start = (item.level * 22).dp), verticalAlignment = Alignment.Top) {
          Box(Modifier.widthIn(min = 26.dp).height(24.dp), contentAlignment = Alignment.CenterStart) {
            when {
              item.checked != null -> {
                val toggle = onToggleTask
                Ic(
                  if (item.checked)
                    Icons.Outlined.CheckBox
                  else
                    Icons.Outlined.CheckBoxOutlineBlank,
                  size = 20.dp,
                  tint = if (item.checked) colors.primary else colors.onSurfaceVariant,
                  modifier = if (toggle != null)
                    Modifier.interactive(CircleShape, bleed = 2.dp) {
                      toggle(item.line)
                    }
                  else
                    Modifier
                )
              }

              item.number != null ->
                Text(item.number, style = textStyle, color = colors.onSurfaceVariant)

              else ->
                Box(
                  Modifier.padding(start = 6.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .then(
                      if (item.level == 0)
                        Modifier.background(colors.onSurfaceVariant)
                      else
                        Modifier.border(1.5.dp, colors.onSurfaceVariant, CircleShape)
                    )
                )
            }
          }
          val done = item.checked == true
          Inline(
            item.text,
            if (done)
              textStyle.copy(color = colors.onSurfaceVariant, textDecoration = TextDecoration.LineThrough)
            else
              textStyle,
            Modifier.weight(1f)
          )
        }
      }
    }
  }

  @Composable
  private fun QuoteView(blocks: List<MdBlock>) {
    Row(Modifier.height(IntrinsicSize.Min)) {
      Box(
        Modifier.width(3.dp)
          .fillMaxHeight()
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
      )
      CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
        Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          blocks.forEach { Block(it) }
        }
      }
    }
  }

  @Composable
  private fun CalloutView(callout: MdBlock.Callout) {
    val colors = MaterialTheme.colorScheme
    val tone = calloutTone(callout.kind)
    val (container, content) =
      when (tone) {
        CalloutTone.Info -> colors.secondaryContainer to colors.onSecondaryContainer
        CalloutTone.Tip -> colors.tertiaryContainer to colors.onTertiaryContainer
        CalloutTone.Warning -> colors.errorContainer to colors.onErrorContainer
        CalloutTone.Quote -> colors.surfaceContainerHigh to colors.onSurface
      }
    val icon = when (tone) {
      CalloutTone.Info -> Icons.Outlined.Info
      CalloutTone.Tip -> Icons.Outlined.Lightbulb
      CalloutTone.Warning -> Icons.Outlined.WarningAmber
      CalloutTone.Quote -> Icons.Outlined.FormatQuote
    }
    Surface(
      color = container,
      contentColor = content,
      shape = MaterialTheme.shapes.medium,
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Ic(icon, size = 18.dp)
          Inline(
            callout.title.ifBlank { callout.kind.replaceFirstChar { it.uppercase() } },
            MaterialTheme.typography.titleSmall,
            Modifier.padding(start = 8.dp)
          )
        }
        callout.blocks.forEach { Block(it) }
      }
    }
  }

  @Composable
  private fun CodeView(code: MdBlock.Code) {
    Surface(
      color = MaterialTheme.colorScheme.surfaceContainerHighest,
      shape = MaterialTheme.shapes.small,
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (code.language.isNotEmpty()) {
          Text(
            code.language,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Text(
          code.code,
          style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
          softWrap = false,
          modifier = Modifier.horizontalScroll(rememberScrollState())
        )
      }
    }
  }

  @Composable
  private fun TableView(table: MdBlock.Table) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    Column(Modifier.fillMaxWidth().clip(shape).border(1.dp, colors.outlineVariant, shape)) {
      TableRow(table.header, table.align, header = true, background = colors.surfaceContainerHigh)
      table.rows.forEach { row ->
        HorizontalDivider(color = colors.outlineVariant)
        TableRow(row, table.align, header = false, background = Color.Transparent)
      }
    }
  }

  @Composable
  private fun TableRow(cells: List<String>, align: List<MdAlign>, header: Boolean, background: Color) {
    val style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium
    Row(Modifier.fillMaxWidth().background(background).height(IntrinsicSize.Min)) {
      cells.forEachIndexed { index, cell ->
        if (index > 0)
          Box(
            Modifier.width(1.dp)
              .fillMaxHeight()
              .background(MaterialTheme.colorScheme.outlineVariant)
          )
        Inline(
          cell,
          style,
          Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp),
          textAlign = when (align.getOrElse(index) { MdAlign.Start }) {
            MdAlign.Start -> TextAlign.Start
            MdAlign.Center -> TextAlign.Center
            MdAlign.End -> TextAlign.End
          }
        )
      }
    }
  }

  @Composable
  private fun PropertiesView(props: MdBlock.Properties) {
    Surface(
      color = MaterialTheme.colorScheme.surfaceContainer,
      shape = MaterialTheme.shapes.small,
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        props.entries.forEach { (key, value) ->
          Row {
            Text(
              key,
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.width(140.dp)
            )
            Inline(value, MaterialTheme.typography.bodyMedium, Modifier.weight(1f))
          }
        }
      }
    }
  }
}
