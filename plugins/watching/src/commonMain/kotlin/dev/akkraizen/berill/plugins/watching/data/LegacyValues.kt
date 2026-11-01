package dev.akkraizen.berill.plugins.watching.data

/**
 * Values stored in synced records by older versions (and imported from Obsidian). They are data,
 * not interface text: never translate or edit them, or existing records stop matching.
 * This file is exempt from the hard-coded Cyrillic check (verifySourceLocalization).
 */
internal object LegacyValues {
  val watchStatus: Map<WatchStatus, String> = mapOf(
    WatchStatus.PLANNED to "в планах",
    WatchStatus.WATCHING to "смотрю",
    WatchStatus.WATCHED to "просмотрено",
  )
  val mediaKind: Map<MediaKind, String> = mapOf(
    MediaKind.ANIME to "аниме",
    MediaKind.MOVIE to "фильм",
    MediaKind.SERIES to "сериал",
  )
  val mediaFormat: Map<MediaFormat, String> = mapOf(
    MediaFormat.MOVIE to "фильм",
    MediaFormat.SPECIAL to "спешл",
    MediaFormat.TV_SPECIAL to "TV-спешл",
    MediaFormat.MUSIC to "клип",
    MediaFormat.VIDEO to "видео",
    MediaFormat.SERIES to "сериал",
    MediaFormat.MINI_SERIES to "мини-сериал",
    MediaFormat.TV_SHOW to "ТВ-шоу",
  )
}
