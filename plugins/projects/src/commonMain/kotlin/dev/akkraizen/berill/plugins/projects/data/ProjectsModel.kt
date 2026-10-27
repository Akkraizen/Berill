package dev.akkraizen.berill.plugins.projects.data

import dev.akkraizen.berill.api.FlexibleStringList
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.today
import dev.akkraizen.berill.plugins.projects.ProjectsPlugin
import kotlinx.serialization.Serializable

const val IDEA = "идея"
const val IN_PROGRESS = "в работе"
const val PAUSED = "на паузе"
const val READY = "готово"
val STATUSES = listOf(IDEA, IN_PROGRESS, PAUSED, READY)

@Serializable
data class Project(
  val title: String = "",
  val status: String = IDEA,
  val description: String? = null,
  @Serializable(with = FlexibleStringList::class) val stack: List<String> = emptyList(),
  val repo: String? = null,
  val site: String? = null,
  val created: String? = null,
  val started: String? = null,
  val finished: String? = null,
  val body: String? = null
) {
  val normalizedStatus: String
    get() = if (status in STATUSES) status else IDEA

  fun moveTo(target: String): Project {
    val now = today().toString()
    return when (target) {
      IN_PROGRESS -> copy(status = target, started = started ?: now, finished = null)
      READY -> copy(status = target, started = started ?: now, finished = finished ?: now)
      else -> copy(status = target, finished = null)
    }
  }
}

data class CheckItem(val line: Int, val text: String, val done: Boolean)

private val CHECK = Regex("""^\s*[-*] \[([ xX])\] (.*)$""")

fun checklist(body: String?): List<CheckItem> =
  body.orEmpty().lines().mapIndexedNotNull { i, line ->
    CHECK.matchEntire(line)?.let { m -> CheckItem(i, m.groupValues[2], m.groupValues[1] != " ") }
  }

fun toggleCheck(body: String, line: Int, expectedText: String): String {
  val lines = body.lines().toMutableList()

  val index = line.takeIf {
    CHECK.matchEntire(lines.getOrNull(it) ?: "")?.groupValues?.get(2) == expectedText
  }
    ?: lines
      .indexOfFirst { CHECK.matchEntire(it)?.groupValues?.get(2) == expectedText }
      .takeIf { it >= 0 }
    ?: return body
  val m = CHECK.matchEntire(lines[index]) ?: return body
  val mark = if (m.groupValues[1] == " ") "x" else " "
  lines[index] = lines[index].replaceFirst("[${m.groupValues[1]}]", "[$mark]")
  return lines.joinToString("\n")
}

fun addCheck(body: String?, text: String): String {
  val lines = body.orEmpty().lines().toMutableList()
  val header = lines.indexOfFirst { it.trim().startsWith("## Задачи") }
  if (header < 0) {
    if (lines.isNotEmpty() && lines.last().isNotBlank()) lines += ""
    lines += listOf("## Задачи", "- [ ] $text")
    return lines.joinToString("\n")
  }
  var insertAt = header + 1
  var i = header + 1
  while (i < lines.size && !lines[i].startsWith("## ")) {
    if (CHECK.matches(lines[i])) insertAt = i + 1
    i++
  }
  lines.add(insertAt, "- [ ] $text")
  return lines.joinToString("\n")
}

const val TEMPLATE = "## Идея\n\n\n## Задачи\n\n\n## Заметки\n"

internal fun projects(ctx: PluginContext) =
  ctx.data.collection(ProjectsPlugin.PROJECTS, Project.serializer())
