package dev.akkraizen.berill.core

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.akkraizen.berill.api.ui.markdown.MarkdownEditing
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownEditingTest {
  private fun type(text: String, cursor: Int, ch: Char): TextFieldValue {
    val old = TextFieldValue(text, TextRange(cursor))
    val new = TextFieldValue(text.substring(0, cursor) + ch + text.substring(cursor), TextRange(cursor + 1))
    return MarkdownEditing.onInput(old, new)
  }

  @Test
  fun `Enter продолжает списки и цитаты`() {
    assertEquals(TextFieldValue("- один\n- ", TextRange(9)), type("- один", 6, '\n'))
    assertEquals(TextFieldValue("\t3. три\n\t4. ", TextRange(12)), type("\t3. три", 7, '\n'))
    assertEquals(TextFieldValue("- [x] готово\n- [ ] ", TextRange(19)), type("- [x] готово", 12, '\n'))
    assertEquals(TextFieldValue("> цитата\n> ", TextRange(11)), type("> цитата", 8, '\n'))
    assertEquals(TextFieldValue("просто\n", TextRange(7)), type("просто", 6, '\n'))
  }

  @Test
  fun `Enter на пустом пункте завершает список`() {
    assertEquals(TextFieldValue("- один\n", TextRange(7)), type("- один\n- ", 9, '\n'))
  }

  @Test
  fun `парные скобки`() {
    assertEquals(TextFieldValue("[]", TextRange(1)), type("", 0, '['))
    assertEquals(TextFieldValue("[[]]", TextRange(2)), type("[]", 1, '['))
    assertEquals(TextFieldValue("[a]", TextRange(3)), type("[a]", 2, ']'))
    val old = TextFieldValue("x[]", TextRange(2))
    assertEquals(TextFieldValue("x", TextRange(1)), MarkdownEditing.onInput(old, TextFieldValue("x]", TextRange(1))))
  }

  @Test
  fun `Tab вкладывает и выносит пункты`() {
    val v = TextFieldValue("- a\n- b", TextRange(5, 7))
    val indented = MarkdownEditing.indent(v, outdent = false)
    assertEquals("- a\n\t- b", indented.text)
    assertEquals(TextRange(6, 8), indented.selection)
    assertEquals(v.text, MarkdownEditing.indent(indented, outdent = true).text)
    assertEquals(
      TextFieldValue("ab\tc", TextRange(3)),
      MarkdownEditing.indent(TextFieldValue("abc", TextRange(2)), outdent = false)
    )
  }

  @Test
  fun `Ctrl+B оборачивает и снимает`() {
    val bold = MarkdownEditing.wrap(TextFieldValue("a слово b", TextRange(2, 7)), "**")
    assertEquals(TextFieldValue("a **слово** b", TextRange(4, 9)), bold)
    assertEquals(TextFieldValue("a слово b", TextRange(2, 7)), MarkdownEditing.wrap(bold, "**"))
  }
}
