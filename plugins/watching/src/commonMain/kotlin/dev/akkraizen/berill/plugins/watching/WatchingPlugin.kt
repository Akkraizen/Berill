package dev.akkraizen.berill.plugins.watching

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.api.HomeCard
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.PluginPermissions
import dev.akkraizen.berill.api.ui.animation.AnimatedRoute
import dev.akkraizen.berill.plugins.watching.i18n.WatchingText
import dev.akkraizen.berill.plugins.watching.ui.TitleScreen
import dev.akkraizen.berill.plugins.watching.ui.WatchBoard
import dev.akkraizen.berill.plugins.watching.ui.WatchingNow
import dev.akkraizen.berill.plugins.watching.ui.WatchingSettings

object WatchingPlugin : BerillPlugin {
  const val TITLES = "titles"

  override val id = "watching"
  override val title
    get() = WatchingText.title()

  override val icon = Icons.Outlined.Movie
  override val order = 30
  override val description
    get() = WatchingText.description()

  override val installedByDefault = true
  override val hasSettings = true

  @Composable
  override fun Settings(ctx: PluginContext) = WatchingSettings(ctx)

  override val collections = listOf(TITLES)

  override val permissions = PluginPermissions(
    network = listOf("shikimori.one", "shiki.one", "shikimori.me", "kinopoiskapiunofficial.tech"),
    anyImageHost = true
  )

  internal const val TITLE_ROUTE = "title:"

  @Composable
  override fun Screen(ctx: PluginContext) {
    val route by ctx.nav.route.collectAsState()
    val r = route

    AnimatedRoute(r) { r ->
      when {
        r != null && r.startsWith(TITLE_ROUTE) -> TitleScreen(ctx, r.removePrefix(TITLE_ROUTE))
        else -> WatchBoard(ctx)
      }
    }
  }

  override fun homeCards(ctx: PluginContext) =
    listOf(HomeCard("watching", WatchingText.watchingNowCard, order = 31) { WatchingNow(ctx) })
}
