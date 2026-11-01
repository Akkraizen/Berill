package dev.akkraizen.berill.plugins.projects.data

/**
 * Values stored in synced records by older versions (and imported from Obsidian). They are data,
 * not interface text: never translate or edit them, or existing records stop matching.
 * This file is exempt from the hard-coded Cyrillic check (verifySourceLocalization).
 */
internal object LegacyValues {
  val projectStatus: Map<ProjectStatus, String> = mapOf(
    ProjectStatus.IDEA to "идея",
    ProjectStatus.IN_PROGRESS to "в работе",
    ProjectStatus.PAUSED to "на паузе",
    ProjectStatus.READY to "готово",
  )
}
