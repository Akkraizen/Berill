package dev.akkraizen.berill.plugins.languages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.dialogs.DeleteButton
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.fields.BodyEditor
import dev.akkraizen.berill.api.ui.fields.CommitTextField
import dev.akkraizen.berill.api.ui.fields.DateField
import dev.akkraizen.berill.api.ui.layout.DetailHeader
import dev.akkraizen.berill.api.ui.layout.FieldsRow
import dev.akkraizen.berill.api.ui.layout.PluginList

@Composable
internal fun LessonScreen(ctx: PluginContext, id: String) {
  val collection = remember(ctx) { lessons(ctx) }
  val lesson by
  remember(collection, id) { collection.observe(id) }.collectAsState(collection.get(id))
  val l = lesson
    ?: run {
      EmptyHint(LanguagesText.lessonDeleted())
      return
    }

  fun update(transform: (Lesson) -> Lesson) {
    val current = collection.get(id) ?: return
    collection.save(id, transform(current))
  }
  PluginList {
    item {
      DetailHeader(
        l.title.ifBlank { LanguagesText.untitled() },
        if (l.kind == RULE) RULE_ICON else LESSON_ICON
      ) {
        DeleteButton(if (l.kind == RULE) LanguagesText.ruleNoun() else LanguagesText.lessonNoun()) {
          collection.delete(id)
          ctx.nav.back()
        }
      }
    }
    item {
      FieldsRow(weights = listOf(2f, 1f)) {
        CommitTextField(
          l.title,
          { v -> update { it.copy(title = v.trim()) } },
          LanguagesText.name()
        )
        DateField(
          l.date,
          { v -> update { it.copy(date = v) } },
          LanguagesText.date()
        )
      }
    }
    item {
      SectionCard {
        BodyEditor(
          l.body ?: "",
          { v -> update { it.copy(body = v.ifBlank { null }) } },
          LanguagesText.lessonNotes()
        )
      }
    }
  }
}
