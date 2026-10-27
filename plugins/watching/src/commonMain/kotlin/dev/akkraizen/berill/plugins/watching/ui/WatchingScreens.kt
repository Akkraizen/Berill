package dev.akkraizen.berill.plugins.watching.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.Item
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.display.Stars
import dev.akkraizen.berill.api.ui.feedback.ProgressLine
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.interaction.DialogKeys
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.layout.FieldsRow
import dev.akkraizen.berill.api.ui.layout.KanbanBoard
import dev.akkraizen.berill.api.ui.layout.KanbanColumn
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.api.ui.media.Cover
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.plugins.watching.WatchingPlugin
import dev.akkraizen.berill.plugins.watching.data.ANIME
import dev.akkraizen.berill.plugins.watching.data.MEDIA
import dev.akkraizen.berill.plugins.watching.data.MOVIE
import dev.akkraizen.berill.plugins.watching.data.PLANNED
import dev.akkraizen.berill.plugins.watching.data.SERIES
import dev.akkraizen.berill.plugins.watching.data.STATUSES
import dev.akkraizen.berill.plugins.watching.data.Title
import dev.akkraizen.berill.plugins.watching.data.WATCHED
import dev.akkraizen.berill.plugins.watching.data.WATCHING
import dev.akkraizen.berill.plugins.watching.data.titles
import dev.akkraizen.berill.plugins.watching.i18n.WatchingText

internal val ICONS: Map<String, ImageVector> =
  mapOf(
    PLANNED to Icons.Outlined.WatchLater,
    WATCHING to Icons.Outlined.PlayCircle,
    WATCHED to Icons.Outlined.TaskAlt
  )
private val FILTERS = listOf("все", ANIME, MOVIE, SERIES)

private fun filterLabel(f: String) =
  when (f) {
    ANIME -> WatchingText.filterAnime()
    MOVIE -> WatchingText.filterMovies()
    SERIES -> WatchingText.filterSeries()
    else -> WatchingText.filterAll()
  }

private fun filterIcon(f: String): ImageVector =
  if (f in MEDIA) mediaIcon(f) else Icons.Outlined.AutoAwesome

@Composable
internal fun WatchBoard(ctx: PluginContext) {
  val collection = remember(ctx) { titles(ctx) }
  val all by remember(collection) { collection.observe() }.collectAsState(emptyList())
  var filter by rememberSaveable { mutableStateOf("все") }
  var adding by remember { mutableStateOf(false) }
  var searching by rememberSaveable { mutableStateOf<String?>(null) }

  val shown = if (filter == "все") all else all.filter { it.value.media == filter }
  val byStatus = shown.groupBy { it.value.normalizedStatus }
  val planned = byStatus[PLANNED].orEmpty().sortedBy { it.value.title.lowercase() }
  val watching = byStatus[WATCHING].orEmpty().sortedByDescending { it.value.started ?: "" }
  val watched = byStatus[WATCHED].orEmpty().sortedByDescending { it.value.finished ?: "" }

  PluginList {
    item { ChoiceChips(FILTERS, filter, ::filterLabel, { filter = it }, icon = ::filterIcon) }
    item {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        FilledTonalButton(onClick = { searching = if (searching == "anime") null else "anime" }) {
          Ic(mediaIcon(ANIME), size = 18.dp)
          Spacer(Modifier.width(8.dp))
          Text(WatchingText.findAnime())
        }
        FilledTonalButton(onClick = { searching = if (searching == "kp") null else "kp" }) {
          Ic(mediaIcon(MOVIE), size = 18.dp)
          Spacer(Modifier.width(8.dp))
          Text(WatchingText.findMovieOrSeries())
        }
        OutlinedButton(onClick = { adding = true }) {
          Ic(BerillIcons.Add, size = 18.dp)
          Spacer(Modifier.width(8.dp))
          Text(WatchingText.manually())
        }
        IconButton(onClick = { ctx.nav.openSettings() }) {
          Icon(Icons.Outlined.Settings, contentDescription = WatchingText.sectionSettings())
        }
      }
    }
    when (searching) {
      "anime" ->
        item(key = "search-anime") { TitleSearch(ctx, kinopoisk = false) { searching = null } }

      "kp" -> item(key = "search-kp") { TitleSearch(ctx, kinopoisk = true) { searching = null } }
    }
    item {
      KanbanBoard(
        columns = listOf(
          KanbanColumn(
            PLANNED,
            WatchingText.statusLabel(PLANNED),
            planned,
            WatchingText.empty(),
            ICONS[PLANNED]
          ),
          KanbanColumn(
            WATCHING,
            WatchingText.statusLabel(WATCHING),
            watching,
            WatchingText.empty(),
            ICONS[WATCHING]
          ),
          KanbanColumn(
            WATCHED,
            WatchingText.statusLabel(WATCHED),
            watched,
            WatchingText.empty(),
            ICONS[WATCHED]
          )
        ),
        itemKey = { it.id },
        onMove = { item, status ->
          collection
            .get(item.id)
            ?.takeIf { it.normalizedStatus != status }
            ?.let { collection.save(item.id, it.moveTo(status)) }
        },
        card = { item ->
          TitleCard(
            item,
            onOpen = { ctx.nav.open(WatchingPlugin.TITLE_ROUTE + item.id) },
            onPlusOne = {
              collection.get(item.id)?.let { collection.save(item.id, it.plusEpisode()) }
            }
          )
        }
      )
    }
    item { WatchingStatistics(shown.map { it.value }) }
  }

  if (adding)
    AddTitleDialog(
      initialMedia = if (filter in MEDIA) filter else ANIME,
      onDismiss = { adding = false },
      onAdd = { t ->
        collection.save(ctx.data.newId(), t.moveTo(t.status))
        adding = false
      }
    )
}

@Composable
private fun TitleCard(item: Item<Title>, onOpen: () -> Unit, onPlusOne: () -> Unit) {
  val t = item.value
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Cover(t.cover, mediaIcon(t.media), Modifier.size(48.dp, 72.dp))
    Column(
      Modifier.weight(1f).interactive(bleed = 6.dp, onClick = onOpen),
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      Text(
        t.title,
        style = MaterialTheme.typography.titleSmall,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        listOfNotNull(WatchingText.mediaLabel(t.media), t.year?.toString())
          .joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Stars(t.rating)
      if (t.isSeries && t.normalizedStatus != PLANNED) {
        ProgressLine((t.watched ?: 0) / t.episodes!!.toFloat())
        Text("${t.watched ?: 0}/${t.episodes}", style = MaterialTheme.typography.labelSmall)
      }
    }
    if (t.isSeries && t.normalizedStatus == WATCHING)
      FilledTonalButton(onClick = onPlusOne) { Text("+1") }
  }
}

@Composable
private fun AddTitleDialog(initialMedia: String, onDismiss: () -> Unit, onAdd: (Title) -> Unit) {
  var name by remember { mutableStateOf("") }
  var media by remember { mutableStateOf(initialMedia) }
  var status by remember { mutableStateOf(PLANNED) }
  var episodes by remember { mutableStateOf("") }
  var year by remember { mutableStateOf("") }
  val confirm: () -> Unit = {
    val eps = if (media == MOVIE) 1 else episodes.toIntOrNull()
    onAdd(
      Title(
        title = name.trim(),
        media = media,
        status = status,
        year = year.toIntOrNull(),
        episodes = eps,
        watched = 0
      )
    )
  }
  DialogKeys(onConfirm = confirm, confirmEnabled = name.isNotBlank(), onDismiss = onDismiss)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(WatchingText.newTitle()) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BerillTextField(
          name,
          { name = it },
          label = { Text(WatchingText.name()) },
          singleLine = true
        )
        ChoiceChips(
          MEDIA,
          media,
          { WatchingText.mediaLabel(it) },
          { media = it },
          icon = ::mediaIcon
        )
        ChoiceChips(
          STATUSES,
          status,
          { WatchingText.statusLabel(it) },
          { status = it },
          icon = { ICONS[it] }
        )
        FieldsRow {
          BerillTextField(
            year,
            { year = it.filter(Char::isDigit).take(4) },
            label = { Text(WatchingText.year()) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
          )
          if (media != MOVIE)
            BerillTextField(
              episodes,
              { episodes = it.filter(Char::isDigit) },
              label = { Text(WatchingText.episodes()) },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier.fillMaxWidth()
            )
        }
      }
    },
    confirmButton = {
      TextButton(enabled = name.isNotBlank(), onClick = confirm) { Text(WatchingText.add()) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(WatchingText.cancel()) } }
  )
}
