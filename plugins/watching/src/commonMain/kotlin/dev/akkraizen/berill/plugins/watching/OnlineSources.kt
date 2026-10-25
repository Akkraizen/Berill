package dev.akkraizen.berill.plugins.watching

import dev.akkraizen.berill.api.HttpException
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.arr
import dev.akkraizen.berill.api.double
import dev.akkraizen.berill.api.int
import dev.akkraizen.berill.api.list
import dev.akkraizen.berill.api.migrateSecretFromSettings
import dev.akkraizen.berill.api.obj
import dev.akkraizen.berill.api.str
import dev.akkraizen.berill.api.ui.search.SearchHit
import dev.akkraizen.berill.api.ui.search.networkErrorText
import dev.akkraizen.berill.api.urlEncode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement

internal fun titleBody(description: String?): String = WatchingText.bodyTemplate(description?.trim().orEmpty())

internal class Shikimori(private val ctx: PluginContext) {
  private val hosts = listOf("https://shikimori.one", "https://shiki.one", "https://shikimori.me")
  private var workingHost: String? = null

  private suspend fun get(path: String): Pair<String, JsonElement> {
    var last: Throwable? = null
    for (host in listOfNotNull(workingHost) + hosts.filter { it != workingHost }) {
      try {
        val json = ctx.http.getJson(host + path)
        workingHost = host
        return host to json
      } catch (e: CancellationException) {
        throw e
      } catch (e: HttpException) {
        if (e.status in 400..499 && e.status != 403 && e.status != 429) throw e
        last = e
      } catch (e: Exception) {
        last = e
      }
    }
    throw last ?: IllegalStateException(WatchingText.shikimoriUnavailable())
  }

  suspend fun search(query: String): List<SearchHit> {
    val (host, json) = get("/api/animes?search=${query.urlEncode()}&limit=12&order=popularity")
    return json.list().mapNotNull { a ->
      val id = a.int("id") ?: return@mapNotNull null
      val russian = a.str("russian")
      val name = a.str("name")
      val score = a.double("score")?.takeIf { it > 0 }
      SearchHit(
        ref = id.toString(),
        title = russian ?: name ?: return@mapNotNull null,
        subtitle = name?.takeIf { it != russian },
        meta = listOfNotNull(
          kindLabel(a.str("kind"))?.let(WatchingText::formatLabel),
          a.str("aired_on")?.take(4),
          a.int("episodes")
            ?.takeIf { it > 0 }
            ?.let { WatchingText.episodesShort(it)() },
          score?.let { "★ $it" }
        )
          .joinToString(" · ")
          .ifEmpty { null },
        cover = image(host, a)
      )
    }
  }

  suspend fun details(id: String): Title {
    val (host, a) = get("/api/animes/${id.urlEncode()}")
    val kind = a.str("kind")
    val episodes = a.int("episodes")?.takeIf { it > 0 }
      ?: a.int("episodes_aired")?.takeIf { it > 0 }
      ?: if (kind == "movie") 1 else null
    return Title(
      title = a.str("russian") ?: a.str("name") ?: WatchingText.untitled(),
      media = ANIME,
      year = a.str("aired_on")?.take(4)?.toIntOrNull(),
      genre = a.arr("genres")
        .mapNotNull { it.str("russian") ?: it.str("name") }
        .joinToString(", ")
        .ifEmpty { null },
      format = kindLabel(kind),
      episodes = episodes,
      watched = 0,
      shikimoriScore = a.double("score")?.takeIf { it > 0 },
      cover = image(host, a),
      url = a.str("url")?.let { if (it.startsWith("http")) it else host + it },
      originalName = a.str("name"),
      body = titleBody(cleanBbCode(a.str("description")))
    )
  }

  private fun image(host: String, a: JsonElement): String? {
    val img = a.obj("image")
    val path = img.str("original") ?: img.str("preview") ?: return null
    if (path.contains("missing_")) return null
    return if (path.startsWith("http")) path else host + path
  }

  companion object {
    private val KINDS = mapOf(
      "tv" to "TV",
      "movie" to "фильм",
      "ova" to "OVA",
      "ona" to "ONA",
      "special" to "спешл",
      "tv_special" to "TV-спешл",
      "music" to "клип"
    )

    fun kindLabel(kind: String?): String? = kind?.let { KINDS[it] ?: it }

    fun cleanBbCode(text: String?): String? =
      text
        ?.replace(Regex("""\[(\w+)(=[^\]]*)?]([^\[]*)\[/\1]"""), "$3")
        ?.replace(Regex("""\[[^\]]*]"""), "")
        ?.replace("\r", "")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    fun errorText(e: Throwable): String = "Shikimori: ${networkErrorText(e)}"
  }
}

internal const val KINOPOISK_KEY = "kinopoiskKey"

internal fun kinopoiskKey(ctx: PluginContext): String? {
  ctx.migrateSecretFromSettings(KINOPOISK_KEY)
  return ctx.secrets.get(KINOPOISK_KEY)?.trim()?.takeIf { it.isNotEmpty() }
}

internal class Kinopoisk(private val ctx: PluginContext) {
  private val host = "https://kinopoiskapiunofficial.tech"

  private suspend fun get(path: String): JsonElement {
    val key = kinopoiskKey(ctx) ?: throw MissingKeyException()
    return ctx.http.getJson(host + path, mapOf("X-API-KEY" to key))
  }

  suspend fun search(query: String): List<SearchHit> {
    val json = get("/api/v2.1/films/search-by-keyword?keyword=${query.urlEncode()}&page=1")
    return json.arr("films").mapNotNull { f ->
      val id = f.int("filmId") ?: f.int("kinopoiskId") ?: return@mapNotNull null
      val ru = f.str("nameRu")
      val en = f.str("nameEn")
      SearchHit(
        ref = id.toString(),
        title = ru ?: en ?: return@mapNotNull null,
        subtitle = en?.takeIf { it != ru },
        meta = listOfNotNull(
          TYPE_LABELS[f.str("type")]?.let(WatchingText::formatLabel),
          f.str("year")?.takeIf { it != "null" },
          f.str("rating")
            ?.takeIf { it != "null" && !it.endsWith("%") }
            ?.let { "★ $it" }
        )
          .joinToString(" · ")
          .ifEmpty { null },
        cover = f.str("posterUrlPreview") ?: f.str("posterUrl")
      )
    }
  }

  suspend fun details(id: String): Title {
    val f = get("/api/v2.2/films/${id.urlEncode()}")
    val type = f.str("type")
    val media = TYPES[type] ?: if (f.str("serial") == "true") SERIES else MOVIE
    val episodes = if (media == SERIES) {
      try {
        get("/api/v2.2/films/${id.urlEncode()}/seasons")
          .arr("items")
          .sumOf { it.arr("episodes").size }
          .takeIf { it > 0 }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        null
      }
    } else {
      1
    }
    return Title(
      title = f.str("nameRu") ?: f.str("nameEn") ?: f.str("nameOriginal") ?: WatchingText.untitled(),
      media = media,
      year = f.int("year"),
      genre = f.arr("genres").mapNotNull { it.str("genre") }.joinToString(", ").ifEmpty { null },
      format = TYPE_LABELS[type],
      country = f.arr("countries").mapNotNull { it.str("country") }.joinToString(", ").ifEmpty { null },
      episodes = episodes,
      watched = 0,
      kpRating = f.double("ratingKinopoisk"),
      imdbRating = f.double("ratingImdb"),
      cover = f.str("posterUrlPreview") ?: f.str("posterUrl"),
      url = f.str("webUrl") ?: "https://www.kinopoisk.ru/film/$id/",
      originalName = f.str("nameOriginal") ?: f.str("nameEn"),
      body = titleBody(f.str("description") ?: f.str("shortDescription"))
    )
  }

  class MissingKeyException : Exception("нет ключа API")

  companion object {
    private val TYPES = mapOf(
      "FILM" to MOVIE,
      "VIDEO" to MOVIE,
      "TV_SERIES" to SERIES,
      "MINI_SERIES" to SERIES,
      "TV_SHOW" to SERIES
    )
    private val TYPE_LABELS = mapOf(
      "FILM" to "фильм",
      "VIDEO" to "видео",
      "TV_SERIES" to "сериал",
      "MINI_SERIES" to "мини-сериал",
      "TV_SHOW" to "ТВ-шоу"
    )

    fun errorText(e: Throwable): String =
      when {
        e is MissingKeyException -> WatchingText.kpMissingKey()
        e is HttpException && e.status == 401 -> WatchingText.kpInvalidKey()
        e is HttpException && e.status == 402 -> WatchingText.kpDailyLimit()
        e is HttpException && e.status == 429 -> WatchingText.kpTooMany()
        else -> WatchingText.kpError(networkErrorText(e))()
      }
  }
}
