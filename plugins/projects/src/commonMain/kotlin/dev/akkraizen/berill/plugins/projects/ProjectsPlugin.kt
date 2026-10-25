package dev.akkraizen.berill.plugins.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.FlexibleStringList
import dev.akkraizen.berill.api.HomeCard
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.animation.AnimatedRoute
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.interaction.BerillChip
import dev.akkraizen.berill.api.ui.today
import kotlinx.serialization.Serializable

object ProjectsPlugin : BerillPlugin {
  const val PROJECTS = "projects"

  override val id = "projects"
  override val title
    get() = ProjectsText.title()

  override val icon = Icons.Outlined.Construction
  override val order = 70
  override val description
    get() = ProjectsText.description()

  override val installedByDefault = true
  override val collections = listOf(PROJECTS)

  internal const val PROJECT_ROUTE = "project:"

  @Composable
  override fun Screen(ctx: PluginContext) {
    val route by ctx.nav.route.collectAsState()
    val r = route

    AnimatedRoute(r) { r ->
      if (r != null && r.startsWith(PROJECT_ROUTE))
        ProjectScreen(ctx, r.removePrefix(PROJECT_ROUTE))
      else
        ProjectsBoard(ctx)
    }
  }

  override fun homeCards(ctx: PluginContext) =
    listOf(HomeCard("active", ProjectsText.activeProjects, order = 60) { ActiveProjects(ctx) })
}

const val IDEA = "идея"
const val IN_PROGRESS = "в работе"
const val PAUSED = "на паузе"
const val READY = "готово"
val STATUSES = listOf(IDEA, IN_PROGRESS, PAUSED, READY)

val STATUS_LABELS: Map<String, String>
  get() =
    mapOf(
      IDEA to ProjectsText.statusIdeas(),
      IN_PROGRESS to ProjectsText.statusInProgress(),
      PAUSED to ProjectsText.statusPaused(),
      READY to ProjectsText.statusReady()
    )
val STATUS_ICONS: Map<String, ImageVector> = mapOf(
  IDEA to Icons.Outlined.Lightbulb,
  IN_PROGRESS to Icons.Outlined.Construction,
  PAUSED to Icons.Outlined.PauseCircle,
  READY to Icons.Outlined.Verified
)

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

@Composable
private fun ActiveProjects(ctx: PluginContext) {
  val all by remember(ctx) { projects(ctx).observe() }.collectAsState(emptyList())
  val active = all.filter { it.value.normalizedStatus == IN_PROGRESS }
    .sortedBy { it.value.title.lowercase() }
  if (active.isEmpty()) EmptyHint(ProjectsText.noActiveProjects())
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    active.forEach { item ->
      BerillChip(
        onClick = {
          ctx.nav.openPlugin(ProjectsPlugin.id, ProjectsPlugin.PROJECT_ROUTE + item.id)
        },
        label = { Text(item.value.title, style = MaterialTheme.typography.labelLarge) }
      )
    }
  }
}
