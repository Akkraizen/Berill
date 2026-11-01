package dev.akkraizen.berill.core.ui.notes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.animation.AnimatedRoute
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.notes.*
import dev.akkraizen.berill.core.ui.CoreText

internal class NoteEditorSlot(val id: String?, val render: @Composable (Boolean) -> Unit)
internal val LocalNoteEditorSlot = staticCompositionLocalOf<NoteEditorSlot?> { null }

@Composable
private fun Editor(core: BerillCore, id: String, showBack: Boolean) {
  val slot = LocalNoteEditorSlot.current
  if (slot != null && slot.id == id) slot.render(showBack) else NoteEditor(core, id, showBack)
}

@Composable
internal fun NotesScreen(core: BerillCore, openedId: String?, twoPane: Boolean) {
  if (twoPane) {
    Row(Modifier.fillMaxSize()) {
      Box(Modifier.width(380.dp).fillMaxHeight()) { NotesList(core, openedId) }
      VerticalDivider()

      AnimatedRoute(openedId, Modifier.weight(1f).fillMaxHeight()) { id ->
        Box(Modifier.fillMaxSize()) {
          if (id == null) {
            Text(
              CoreText.selectOrCreateNote(),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.align(Alignment.Center)
            )
          } else {
            Editor(core, id, showBack = false)
          }
        }
      }
    }
  } else {

    AnimatedRoute(openedId) { id ->
      if (id == null) NotesList(core, null) else Editor(core, id, showBack = true)
    }
  }
}
