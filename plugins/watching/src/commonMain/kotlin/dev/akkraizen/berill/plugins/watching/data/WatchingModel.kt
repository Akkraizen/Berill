package dev.akkraizen.berill.plugins.watching.data

import dev.akkraizen.berill.api.DataCollection
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.watching.WatchingPlugin
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val PLANNED = "в планах"
const val WATCHING = "смотрю"
const val WATCHED = "просмотрено"
val STATUSES = listOf(PLANNED, WATCHING, WATCHED)

const val ANIME = "аниме"
const val MOVIE = "фильм"
const val SERIES = "сериал"
val MEDIA = listOf(ANIME, MOVIE, SERIES)

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
