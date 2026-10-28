package dev.akkraizen.berill.plugins.projects.ui

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.interaction.BerillChip
import dev.akkraizen.berill.plugins.projects.ProjectsPlugin
import dev.akkraizen.berill.plugins.projects.data.IDEA
import dev.akkraizen.berill.plugins.projects.data.IN_PROGRESS
import dev.akkraizen.berill.plugins.projects.data.PAUSED
import dev.akkraizen.berill.plugins.projects.data.READY
import dev.akkraizen.berill.plugins.projects.data.projects
import dev.akkraizen.berill.plugins.projects.i18n.ProjectsText

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

@Composable
internal fun ActiveProjects(ctx: PluginContext) {
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
        label = { Text(item.value.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) }
      )
    }
  }
}
