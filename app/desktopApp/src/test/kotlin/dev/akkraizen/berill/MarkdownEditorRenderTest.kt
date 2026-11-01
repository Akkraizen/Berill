package dev.akkraizen.berill

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.akkraizen.berill.api.ui.markdown.MarkdownEditor
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class MarkdownEditorRenderTest {
  private val sample = "# Heading\nA long source line wraps onto multiple visual rows while keeping a single source-line number.\n\n- [x] Completed task\n- [ ] Next task\n\n```kotlin\nval answer = 42\nprintln(answer)\n```\n[[Wiki link]]\n"
  private fun render(numbers: Boolean, width: Int, fontSize: Int, file: String) {
    var value = sample
    val scene = ImageComposeScene(width, 720) {
      MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
          Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            MarkdownEditor(value, { value = it }, Modifier.fillMaxWidth(), lineNumbers = numbers,
              textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSize.sp, fontFamily = FontFamily.Monospace))
          }
        }
      }
    }
    try {
      scene.render(0).close()
      scene.render(16_000_000).close()
      val image = scene.render(32_000_000)
      try {
        assertEquals(width, image.width)
        val data = assertNotNull(image.encodeToData())
        try {
          val dir = File(System.getProperty("berill.uiEvidenceDir"))
          dir.mkdirs(); File(dir, file).writeBytes(data.bytes)
        } finally { data.close() }
      } finally { image.close() }
      assertEquals(sample, value)
    } finally { scene.close() }
  }
  @Test fun numberedLargeFontEditorRendersOnNarrowScreensWithoutChangingMarkdown() = render(true, 360, 24, "editor-numbered.png")
  @Test fun editorWithoutNumbersStillRenders() = render(false, 720, 16, "editor-plain.png")
}
