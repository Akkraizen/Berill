package dev.akkraizen.berill.core.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.ui.dayMonth
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.notes.Note
import dev.akkraizen.berill.core.notes.displayTitle
import dev.akkraizen.berill.core.notes.tags
import dev.akkraizen.berill.core.ui.CoreText

internal class NoteTemplate(val key: String, val icon: ImageVector)

internal val BUILT_IN_TEMPLATES = listOf(
  NoteTemplate("lecture", Icons.Outlined.School),
  NoteTemplate("journal", Icons.Outlined.WbSunny),
  NoteTemplate("week", Icons.AutoMirrored.Outlined.EventNote),
  NoteTemplate("meeting", Icons.Outlined.Groups)
)

internal fun templateTags(): Set<String> = setOf("template", I18n.text("notes.templateTag").lowercase())

internal fun templateTitle(key: String): String = I18n.text("notes.template.$key")

internal fun templateBody(key: String): String = I18n.text("notes.template.$key.body", "date" to today().dayMonth())

internal fun userTemplateBody(note: Note): String =
  note.body.lines().map { line -> templateTags().fold(line) { acc, tag -> acc.replace(Regex("(?i)(^|\\s)#$tag(?=\\s|$)"), "$1") } }
    .joinToString("\n").trim()
    .replace("{date}", today().dayMonth())

internal fun createFromTemplate(core: BerillCore, title: String, body: String) {
  val id = core.data.newId()
  core.notes.save(id, title, body)
  core.nav.openNote(id)
}

@Composable
internal fun NoteTemplatesDialog(core: BerillCore, notes: List<Item<Note>>, onDismiss: () -> Unit) {
  val own = notes.filter { note -> note.value.tags().any { it in templateTags() } }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(CoreText.noteTemplates()) },
    text = {
      Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BUILT_IN_TEMPLATES.forEach { template ->
          TemplateRow(template.icon, templateTitle(template.key), templateBody(template.key)) {
            createFromTemplate(core, templateTitle(template.key) + " · " + today().dayMonth(), templateBody(template.key))
            onDismiss()
          }
        }
        if (own.isNotEmpty()) Text(CoreText.ownTemplates(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        own.forEach { note ->
          TemplateRow(Icons.Outlined.Description, note.value.displayTitle(), userTemplateBody(note.value)) {
            createFromTemplate(core, "", userTemplateBody(note.value))
            onDismiss()
          }
        }
        Text(CoreText.templatesHint(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text(CoreText.cancel()) } }
  )
}

@Composable
private fun TemplateRow(icon: ImageVector, title: String, body: String, onClick: () -> Unit) {
  Row(Modifier.fillMaxWidth().interactive(MaterialTheme.shapes.medium, bleed = 4.dp, onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Ic(icon, size = 22.dp, tint = MaterialTheme.colorScheme.primary)
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(body.lines().filter { it.isNotBlank() }.take(2).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}
