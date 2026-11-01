package dev.akkraizen.berill.plugins.watching.data

import dev.akkraizen.berill.api.DataCollection
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.watching.WatchingPlugin
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

val PLANNED = WatchStatus.PLANNED
val WATCHING = WatchStatus.WATCHING
val WATCHED = WatchStatus.WATCHED
val STATUSES = listOf(PLANNED, WATCHING, WATCHED)

val ANIME = MediaKind.ANIME
val MOVIE = MediaKind.MOVIE
val SERIES = MediaKind.SERIES
val MEDIA = listOf(ANIME, MOVIE, SERIES)

@Serializable
data class Title(
  val title: String = "",
  val media: MediaKind = ANIME,
  val status: WatchStatus = PLANNED,
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
  val normalizedStatus: WatchStatus
    get() = if (status in STATUSES) status else PLANNED

  val isSeries: Boolean
    get() = media != MOVIE && (episodes ?: 0) > 0

  fun moveTo(target: WatchStatus): Title {
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
