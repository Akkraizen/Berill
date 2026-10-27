package dev.akkraizen.berill.plugins.projects

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.HomeCard
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.animation.AnimatedRoute
import dev.akkraizen.berill.plugins.projects.data.projects
import dev.akkraizen.berill.plugins.projects.i18n.ProjectsText
import dev.akkraizen.berill.plugins.projects.ui.ActiveProjects
import dev.akkraizen.berill.plugins.projects.ui.ProjectScreen
import dev.akkraizen.berill.plugins.projects.ui.ProjectsBoard

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
