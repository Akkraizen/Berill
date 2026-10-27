package dev.akkraizen.berill.plugins.watching.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Theaters
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.feedback.ProgressLine
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.media.Cover
import dev.akkraizen.berill.plugins.watching.WatchingPlugin
import dev.akkraizen.berill.plugins.watching.data.ANIME
import dev.akkraizen.berill.plugins.watching.data.MOVIE
import dev.akkraizen.berill.plugins.watching.data.SERIES
import dev.akkraizen.berill.plugins.watching.data.WATCHING
import dev.akkraizen.berill.plugins.watching.data.titles
import dev.akkraizen.berill.plugins.watching.i18n.WatchingText

fun mediaIcon(media: String): ImageVector =
  when (media) {
    ANIME -> Icons.Outlined.Animation
    MOVIE -> Icons.Outlined.Movie
    SERIES -> Icons.Outlined.Tv
    else -> Icons.Outlined.Theaters
  }

@Composable
internal fun WatchingNow(ctx: PluginContext) {
  val collection = remember(ctx) { titles(ctx) }
  val all by remember(collection) { collection.observe() }.collectAsState(emptyList())
  val now = all.filter { it.value.normalizedStatus == WATCHING }
    .sortedByDescending { it.value.started ?: "" }
  if (now.isEmpty()) EmptyHint(WatchingText.nothingWatching())
  now.forEach { item ->
    val t = item.value
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
      Cover(t.cover, mediaIcon(t.media), Modifier.size(40.dp, 60.dp))
      Column(
        Modifier.weight(1f).interactive(bleed = 6.dp) {
          ctx.nav.openPlugin(WatchingPlugin.id, WatchingPlugin.TITLE_ROUTE + item.id)
        },
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Text(t.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (t.isSeries) {
          Text(
            WatchingText.episodesProgress(t.watched ?: 0, t.episodes ?: 0)(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          ProgressLine((t.watched ?: 0) / t.episodes!!.toFloat())
        }
      }
      if (t.isSeries)
        FilledTonalButton(
          onClick = {
            collection.get(item.id)?.let { collection.save(item.id, it.plusEpisode()) }
          }
        ) {
          Text("+1")
        }
    }
  }
}
