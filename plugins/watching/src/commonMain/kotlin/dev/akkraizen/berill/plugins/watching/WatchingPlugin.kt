package dev.akkraizen.berill.plugins.watching

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
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.DataCollection
import dev.akkraizen.berill.api.HomeCard
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.PluginPermissions
import dev.akkraizen.berill.api.ui.animation.AnimatedRoute
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.feedback.ProgressLine
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.media.Cover
import dev.akkraizen.berill.api.ui.today
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object WatchingPlugin : BerillPlugin {
  const val TITLES = "titles"

  override val id = "watching"
  override val title
    get() = WatchingText.title()

  override val icon = Icons.Outlined.Movie
  override val order = 30
  override val description
    get() = WatchingText.description()

  override val installedByDefault = true
  override val hasSettings = true

  @Composable
  override fun Settings(ctx: PluginContext) = WatchingSettings(ctx)

  override val collections = listOf(TITLES)

  override val permissions = PluginPermissions(
    network = listOf("shikimori.one", "shiki.one", "shikimori.me", "kinopoiskapiunofficial.tech"),
    anyImageHost = true
  )

  internal const val TITLE_ROUTE = "title:"

  @Composable
  override fun Screen(ctx: PluginContext) {
    val route by ctx.nav.route.collectAsState()
    val r = route

    AnimatedRoute(r) { r ->
      when {
        r != null && r.startsWith(TITLE_ROUTE) -> TitleScreen(ctx, r.removePrefix(TITLE_ROUTE))
        else -> WatchBoard(ctx)
      }
    }
  }

  override fun homeCards(ctx: PluginContext) =
    listOf(HomeCard("watching", WatchingText.watchingNowCard, order = 31) { WatchingNow(ctx) })
}

const val PLANNED = "в планах"
const val WATCHING = "смотрю"
const val WATCHED = "просмотрено"
val STATUSES = listOf(PLANNED, WATCHING, WATCHED)

const val ANIME = "аниме"
const val MOVIE = "фильм"
const val SERIES = "сериал"
val MEDIA = listOf(ANIME, MOVIE, SERIES)

fun mediaIcon(media: String): ImageVector =
  when (media) {
    ANIME -> Icons.Outlined.Animation
    MOVIE -> Icons.Outlined.Movie
    SERIES -> Icons.Outlined.Tv
    else -> Icons.Outlined.Theaters
  }

@Serializable
data class Title(
  val title: String = "",
  val media: String = ANIME,
  val status: String = PLANNED,
  val year: Int? = null,
  val genre: String? = null,
  val format: String? = null,
  val country: String? = null,
  val episodes: Int? = null,
  val watched: Int? = null,
  val rating: Int? = null,
  @SerialName("shikimori_score") val shikimoriScore: Double? = null,
  @SerialName("kp_rating") val kpRating: Double? = null,
  @SerialName("imdb_rating") val imdbRating: Double? = null,
  val started: String? = null,
  val finished: String? = null,
  val cover: String? = null,
  val url: String? = null,
  @SerialName("original_name") val originalName: String? = null,
  val body: String? = null
) {
  val normalizedStatus: String
    get() = if (status in STATUSES) status else PLANNED

  val isSeries: Boolean
    get() = media != MOVIE && (episodes ?: 0) > 0

  fun moveTo(target: String): Title {
    val now = today().toString()
    return when (target) {
      WATCHING -> copy(status = target, started = started ?: now, finished = null)
      WATCHED ->
        copy(status = target, started = started ?: now, finished = finished ?: now, watched = episodes ?: watched)

      else -> copy(status = target, started = null, finished = null)
    }
  }

  fun plusEpisode(): Title {
    val total = episodes ?: return this
    val next = ((watched ?: 0) + 1).coerceAtMost(total)
    return when {
      next >= total -> copy(watched = total).moveTo(WATCHED)
      normalizedStatus != WATCHING -> copy(watched = next).moveTo(WATCHING)
      else -> copy(watched = next)
    }
  }
}

internal fun titles(ctx: PluginContext): DataCollection<Title> = ctx.data.collection(WatchingPlugin.TITLES, Title.serializer())

@Composable
private fun WatchingNow(ctx: PluginContext) {
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
