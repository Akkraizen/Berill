package dev.akkraizen.berill.plugins.study.data

/**
 * Values stored in synced records by older versions (and imported from Obsidian). They are data,
 * not interface text: never translate or edit them, or existing records stop matching.
 * This file is exempt from the hard-coded Cyrillic check (verifySourceLocalization).
 */
internal object LegacyValues {
  val taskStatus: Map<TaskStatus, String> = mapOf(
    TaskStatus.TODO to "не начато",
    TaskStatus.DOING to "в работе",
    TaskStatus.SUBMITTED to "сдано",
  )
  val subjectControl: Map<SubjectControl, List<String>> = mapOf(
    SubjectControl.EXAM to listOf("экзамен"),
    SubjectControl.CREDIT to listOf("зачёт", "зачет"),
  )
}
