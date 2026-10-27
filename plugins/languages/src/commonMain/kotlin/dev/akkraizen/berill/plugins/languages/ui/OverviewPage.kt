package dev.akkraizen.berill.plugins.languages.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.animation.AnimatedRoute
import dev.akkraizen.berill.plugins.languages.LanguagesPlugin

@Composable
internal fun OverviewPage(ctx: PluginContext) {
  val route by ctx.nav.route.collectAsState()
  AnimatedRoute(route) { r ->
    if (r != null && r.startsWith(LanguagesPlugin.LESSON_ROUTE))
      LessonScreen(ctx, r.removePrefix(LanguagesPlugin.LESSON_ROUTE))
    else
      LanguagesHome(ctx)
  }
}
