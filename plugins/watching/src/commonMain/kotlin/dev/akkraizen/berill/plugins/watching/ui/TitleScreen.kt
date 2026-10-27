package dev.akkraizen.berill.plugins.watching.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.dialogs.DeleteButton
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.feedback.ProgressLine
import dev.akkraizen.berill.api.ui.fields.BodyEditor
import dev.akkraizen.berill.api.ui.fields.CommitTextField
import dev.akkraizen.berill.api.ui.fields.DateField
import dev.akkraizen.berill.api.ui.fields.IntField
import dev.akkraizen.berill.api.ui.layout.DetailHeader
import dev.akkraizen.berill.api.ui.layout.FieldsRow
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.api.ui.layout.TwoPane
import dev.akkraizen.berill.api.ui.media.Cover
import dev.akkraizen.berill.api.ui.search.OnlineSearchPanel
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.plugins.watching.WatchingPlugin
import dev.akkraizen.berill.plugins.watching.data.ANIME
import dev.akkraizen.berill.plugins.watching.data.Kinopoisk
import dev.akkraizen.berill.plugins.watching.data.MEDIA
import dev.akkraizen.berill.plugins.watching.data.MOVIE
import dev.akkraizen.berill.plugins.watching.data.PLANNED
import dev.akkraizen.berill.plugins.watching.data.STATUSES
import dev.akkraizen.berill.plugins.watching.data.Shikimori
import dev.akkraizen.berill.plugins.watching.data.Title
import dev.akkraizen.berill.plugins.watching.data.titles
import dev.akkraizen.berill.plugins.watching.i18n.WatchingText

@Composable
internal fun TitleScreen(ctx: PluginContext, id: String) {
  val collection = remember(ctx) { titles(ctx) }
  val title by
  remember(collection, id) { collection.observe(id) }.collectAsState(collection.get(id))
  val t = title
    ?: run {
      EmptyHint(WatchingText.titleDeleted())
      return
    }

  fun update(transform: (Title) -> Title) {
    val current = collection.get(id) ?: return
    collection.save(id, transform(current))
  }

  PluginList {
    item {
      DetailHeader(t.title.ifBlank { WatchingText.untitled() }, mediaIcon(t.media)) {
        DeleteButton(WatchingText.deleteWhat()) {
          collection.delete(id)
          ctx.nav.back()
        }
      }
    }
    item {
      TwoPane(
        left = {
          SectionCard {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
              Cover(t.cover, mediaIcon(t.media), Modifier.size(110.dp, 165.dp))
              Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CommitTextField(
                  t.title,
                  { v -> update { it.copy(title = v.trim()) } },
                  WatchingText.name()
                )
                CommitTextField(
                  t.originalName ?: "",
                  { v -> update { it.copy(originalName = v.trim().ifEmpty { null }) } },
                  WatchingText.originalName()
                )
                val scores = listOfNotNull(
                  t.shikimoriScore?.let { "Shikimori $it" },
                  t.kpRating?.let { "${WatchingText.kpShort()} $it" },
                  t.imdbRating?.let { "IMDb $it" }
                )
                if (scores.isNotEmpty())
                  Text(
                    scores.joinToString(" · "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
              }
            }
            ChoiceChips(
              MEDIA,
              t.media,
              { WatchingText.mediaLabel(it) },
              { m -> update { it.copy(media = m) } },
              icon = ::mediaIcon
            )
            ChoiceChips(
              STATUSES,
              t.normalizedStatus,
              { WatchingText.statusLabel(it) },
              { s -> update { it.moveTo(s) } },
              icon = { ICONS[it] }
            )
            if (t.isSeries) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                Text(
                  WatchingText.episodesOf(t.watched ?: 0, t.episodes ?: 0)(),
                  modifier = Modifier.weight(1f)
                )
                FilledTonalButton(onClick = { update { it.plusEpisode() } }) { Text("+1") }
              }
              ProgressLine((t.watched ?: 0) / t.episodes!!.toFloat())
            }
          }
        },
        right = {
          SectionCard(WatchingText.properties()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              IntField(
                t.year,
                { v -> update { it.copy(year = v) } },
                WatchingText.year(),
                Modifier.weight(1f)
              )
              IntField(
                t.rating,
                { v -> update { it.copy(rating = v?.coerceIn(0, 10)) } },
                WatchingText.rating(),
                Modifier.weight(1f)
              )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              IntField(
                t.episodes,
                { v -> update { it.copy(episodes = v) } },
                WatchingText.episodes(),
                Modifier.weight(1f)
              )
              IntField(
                t.watched,
                { v -> update { it.copy(watched = v) } },
                WatchingText.watched(),
                Modifier.weight(1f)
              )
            }
            CommitTextField(
              t.genre ?: "",
              { v -> update { it.copy(genre = v.trim().ifEmpty { null }) } },
              WatchingText.genres()
            )
            FieldsRow {
              DateField(
                t.started,
                { v -> update { it.copy(started = v) } },
                WatchingText.started()
              )
              DateField(
                t.finished,
                { v -> update { it.copy(finished = v) } },
                WatchingText.finished()
              )
            }
            CommitTextField(
              t.cover ?: "",
              { v -> update { it.copy(cover = v.trim().ifEmpty { null }) } },
              WatchingText.posterLink()
            )
            CommitTextField(
              t.url ?: "",
              { v -> update { it.copy(url = v.trim().ifEmpty { null }) } },
              WatchingText.pageLink()
            )
          }
        }
      )
    }
    item {
      SectionCard(WatchingText.notesSection()) {
        BodyEditor(
          t.body ?: "",
          { v -> update { it.copy(body = v.ifBlank { null }) } },
          WatchingText.notesPlaceholder()
        )
      }
    }
  }
}

@Composable
internal fun TitleSearch(ctx: PluginContext, kinopoisk: Boolean, onClose: () -> Unit) {
  val collection = remember(ctx) { titles(ctx) }
  val shikimori = remember(ctx) { Shikimori(ctx) }
  val kp = remember(ctx) { Kinopoisk(ctx) }
  var status by rememberSaveable { mutableStateOf(PLANNED) }
  OnlineSearchPanel(
    title = if (kinopoisk) WatchingText.searchKinopoisk() else WatchingText.searchShikimori(),
    icon = if (kinopoisk) mediaIcon(MOVIE) else mediaIcon(ANIME),
    placeholder = if (kinopoisk) WatchingText.movieOrSeriesName() else WatchingText.animeName(),
    fallbackIcon = if (kinopoisk) mediaIcon(MOVIE) else mediaIcon(ANIME),
    onClose = onClose,
    draftKey = if (kinopoisk) "watching/search/kinopoisk" else "watching/search/shikimori",
    search = { q -> if (kinopoisk) kp.search(q) else shikimori.search(q) },
    errorText = { e -> if (kinopoisk) Kinopoisk.errorText(e) else Shikimori.errorText(e) },
    onPick = { hit ->
      val sameKind: (String) -> Boolean = { media ->
        if (kinopoisk) media != ANIME else media == ANIME
      }
      val existing = collection.all().firstOrNull {
        it.value.title.equals(hit.title, ignoreCase = true) && sameKind(it.value.media)
      }
      if (existing != null) {
        ctx.nav.open(WatchingPlugin.TITLE_ROUTE + existing.id)
        WatchingText.alreadyInJournal(hit.title)()
      } else {
        val details = if (kinopoisk) kp.details(hit.ref) else shikimori.details(hit.ref)

        val title = details.copy(status = status).moveTo(status)
        collection.save(ctx.data.newId(), title)
        WatchingText.added(title.title, WatchingText.statusLabel(status).lowercase())()
      }
    },
    extra = {
      Text(
        WatchingText.addAs(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      ChoiceChips(
        STATUSES,
        status,
        { WatchingText.statusLabel(it) },
        { status = it },
        icon = { ICONS[it] }
      )
    }
  )
}
