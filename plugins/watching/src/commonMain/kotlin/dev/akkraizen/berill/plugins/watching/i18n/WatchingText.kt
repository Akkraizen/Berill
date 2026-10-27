package dev.akkraizen.berill.plugins.watching.i18n

import dev.akkraizen.berill.plugins.watching.data.ANIME
import dev.akkraizen.berill.plugins.watching.data.MOVIE
import dev.akkraizen.berill.plugins.watching.data.PLANNED
import dev.akkraizen.berill.plugins.watching.data.SERIES
import dev.akkraizen.berill.plugins.watching.data.WATCHED
import dev.akkraizen.berill.plugins.watching.data.WATCHING

internal object WatchingText {

  val title = pluginI18n.tr("watching.title")
  val description =
    pluginI18n.tr("watching.description")
  val watchingNowCard = pluginI18n.tr("watching.watchingNowCard")
  val nothingWatching = pluginI18n.tr("watching.nothingWatching")

  fun episodesProgress(
    watched: Int,
    total: Int
  ) = pluginI18n.tr("watching.episodesProgress", "watched" to watched, "total" to total)

  val statusPlanned = pluginI18n.tr("watching.statusPlanned")
  val statusWatching = pluginI18n.tr("watching.statusWatching")
  val statusWatched = pluginI18n.tr("watching.statusWatched")

  fun statusLabel(status: String): String =
    when (status) {
      PLANNED -> statusPlanned()
      WATCHING -> statusWatching()
      WATCHED -> statusWatched()
      else -> status
    }

  val mediaAnime = pluginI18n.tr("watching.mediaAnime")
  val mediaMovie = pluginI18n.tr("watching.mediaMovie")
  val mediaSeries = pluginI18n.tr("watching.mediaSeries")

  fun mediaLabel(media: String): String =
    when (media) {
      ANIME -> mediaAnime()
      MOVIE -> mediaMovie()
      SERIES -> mediaSeries()
      else -> media
    }

  val filterAll = pluginI18n.tr("watching.filterAll")
  val filterAnime = pluginI18n.tr("watching.filterAnime")
  val filterMovies = pluginI18n.tr("watching.filterMovies")
  val filterSeries = pluginI18n.tr("watching.filterSeries")

  fun formatLabel(format: String): String =
    when (format) {
      "фильм" -> pluginI18n.tr("watching.formatLabel")()
      "спешл" -> pluginI18n.tr("watching.formatLabel.2")()
      "TV-спешл" -> pluginI18n.tr("watching.formatLabel.3")()
      "клип" -> pluginI18n.tr("watching.formatLabel.4")()
      "видео" -> pluginI18n.tr("watching.formatLabel.5")()
      "сериал" -> pluginI18n.tr("watching.formatLabel.6")()
      "мини-сериал" -> pluginI18n.tr("watching.formatLabel.7")()
      "ТВ-шоу" -> pluginI18n.tr("watching.formatLabel.8")()
      else -> format
    }

  val findAnime = pluginI18n.tr("watching.findAnime")
  val findMovieOrSeries = pluginI18n.tr("watching.findMovieOrSeries")
  val manually = pluginI18n.tr("watching.manually")
  val sectionSettings = pluginI18n.tr("watching.sectionSettings")

  fun watchedIn(year: Int) = pluginI18n.tr("watching.watchedIn", "year" to year)

  val episodesWatched = pluginI18n.tr("watching.episodesWatched")
  val empty = pluginI18n.tr("watching.empty")

  val newTitle = pluginI18n.tr("watching.newTitle")
  val name = pluginI18n.tr("watching.name")
  val year = pluginI18n.tr("watching.year")
  val episodes = pluginI18n.tr("watching.episodes")
  val add = pluginI18n.tr("watching.add")
  val cancel = pluginI18n.tr("watching.cancel")

  val titleDeleted = pluginI18n.tr("watching.titleDeleted")
  val untitled = pluginI18n.tr("watching.untitled")
  val deleteWhat = pluginI18n.tr("watching.deleteWhat")
  val originalName = pluginI18n.tr("watching.originalName")
  val kpShort = pluginI18n.tr("watching.kpShort")

  fun episodesOf(watched: Int, total: Int) = pluginI18n.tr("watching.episodesOf", "watched" to watched, "total" to total)

  val properties = pluginI18n.tr("watching.properties")
  val rating = pluginI18n.tr("watching.rating")
  val watched = pluginI18n.tr("watching.watched")
  val genres = pluginI18n.tr("watching.genres")
  val started = pluginI18n.tr("watching.started")
  val finished = pluginI18n.tr("watching.finished")
  val posterLink = pluginI18n.tr("watching.posterLink")
  val pageLink = pluginI18n.tr("watching.pageLink")
  val notesSection = pluginI18n.tr("watching.notesSection")
  val notesPlaceholder = pluginI18n.tr("watching.notesPlaceholder")

  fun bodyTemplate(description: String): String =
    pluginI18n.text("watching.bodyTemplate", "description" to description)

  val searchKinopoisk = pluginI18n.tr("watching.searchKinopoisk")
  val searchShikimori = pluginI18n.tr("watching.searchShikimori")
  val movieOrSeriesName = pluginI18n.tr("watching.movieOrSeriesName")
  val animeName = pluginI18n.tr("watching.animeName")

  fun alreadyInJournal(title: String) = pluginI18n.tr("watching.alreadyInJournal", "title" to title)

  fun added(title: String, status: String) = pluginI18n.tr("watching.added", "title" to title, "status" to status)

  val addAs = pluginI18n.tr("watching.addAs")

  fun episodesShort(n: Int) = pluginI18n.tr("watching.episodesShort", "n" to n)

  val shikimoriUnavailable = pluginI18n.tr("watching.shikimoriUnavailable")
  val kinopoisk = pluginI18n.tr("watching.kinopoisk")
  val kpMissingKey = pluginI18n.tr("watching.kpMissingKey")
  val kpInvalidKey = pluginI18n.tr("watching.kpInvalidKey")
  val kpDailyLimit = pluginI18n.tr("watching.kpDailyLimit")
  val kpTooMany = pluginI18n.tr("watching.kpTooMany")

  fun kpError(details: String) = pluginI18n.tr("watching.kpError", "details" to details)

  val kinopoiskHint = pluginI18n.tr("watching.kinopoiskHint")
  val apiKey = pluginI18n.tr("watching.apiKey")
  val keyLocalHint = pluginI18n.tr("watching.keyLocalHint")
  val shikimoriHint = pluginI18n.tr("watching.shikimoriHint")
  val keysStorageHint = pluginI18n.tr("watching.keysStorageHint")
}
