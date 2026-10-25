package dev.akkraizen.berill.core

import androidx.compose.ui.text.LinkAnnotation
import dev.akkraizen.berill.api.ui.markdown.MarkdownStyle
import dev.akkraizen.berill.api.ui.markdown.MdAlign
import dev.akkraizen.berill.api.ui.markdown.MdBlock
import dev.akkraizen.berill.api.ui.markdown.MdInline
import dev.akkraizen.berill.api.ui.markdown.markdownPlainText
import dev.akkraizen.berill.api.ui.markdown.parseMarkdown
import dev.akkraizen.berill.api.ui.markdown.toggleMarkdownTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MarkdownTest {
  @Test
  fun `блоки разбираются`() {
    val md = """
            ---
            tags: книги
            ---
            # Заголовок
            Абзац
            вторая строка

            - пункт
              - вложенный
            1. первый
            - [ ] задача
            - [x] сделано

            > цитата

            > [!tip] Совет
            > текст

            ```kotlin
            val x = 1
            ```

            | A | B |
            |:-:|--:|
            | 1 | 2 |

            ---
            """
      .trimIndent()
    val blocks = parseMarkdown(md)
    assertEquals(MdBlock.Properties(listOf("tags" to "книги")), blocks[0])
    assertEquals(MdBlock.Heading(1, "Заголовок"), blocks[1])
    assertEquals(MdBlock.Paragraph("Абзац\nвторая строка"), blocks[2])
    val list = assertIs<MdBlock.ListBlock>(blocks[3])
    assertEquals(listOf(0, 1, 0, 0, 0), list.items.map { it.level })
    assertEquals("1.", list.items[2].number)
    assertEquals(listOf(null, null, null, false, true), list.items.map { it.checked })
    assertEquals(MdBlock.Quote(listOf(MdBlock.Paragraph("цитата"))), blocks[4])
    val callout = assertIs<MdBlock.Callout>(blocks[5])
    assertEquals("tip", callout.kind)
    assertEquals("Совет", callout.title)
    assertEquals(MdBlock.Code("kotlin", "val x = 1"), blocks[6])
    val table = assertIs<MdBlock.Table>(blocks[7])
    assertEquals(listOf("A", "B"), table.header)
    assertEquals(listOf(MdAlign.Center, MdAlign.End), table.align)
    assertEquals(listOf(listOf("1", "2")), table.rows)
    assertEquals(MdBlock.Rule, blocks[8])
  }

  @Test
  fun `задача переключается в исходном тексте`() {
    val md = "Список\n- [ ] купить\n  - [x] молоко"
    val line = assertIs<MdBlock.ListBlock>(parseMarkdown(md)[1]).items[0].line
    assertEquals(1, line)
    assertEquals("Список\n- [x] купить\n  - [x] молоко", toggleMarkdownTask(md, 1))
    assertEquals("Список\n- [ ] купить\n  - [ ] молоко", toggleMarkdownTask(md, 2))
    assertEquals(md, toggleMarkdownTask(md, 0))
  }

  @Test
  fun `строчная разметка`() {
    val inline = MdInline(MarkdownStyle.Plain) {}
    val text =
      inline.build("**жир** и *курсив*, `код`, [сайт](https://a.b), [[Заметка|подпись]], snake_case_name, 2 * 3 * 4")
    assertEquals("жир и курсив, код, сайт, подпись, snake_case_name, 2 * 3 * 4", text.text)
    val links = text.getLinkAnnotations(0, text.length).map { it.item }
    assertTrue(links.any { it is LinkAnnotation.Url && it.url == "https://a.b" })
    assertTrue(links.any { it is LinkAnnotation.Clickable && it.tag == "wiki:Заметка" })
  }

  @Test
  fun `текст без разметки`() {
    assertEquals("Заголовок\nжирный текст\nпункт", markdownPlainText("# Заголовок\n**жирный** текст\n\n- пункт"))
  }
}
