package dev.akkraizen.berill.plugins.watching.widgets

import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.i18n.Tr
import dev.akkraizen.berill.api.widgets.WidgetAction
import dev.akkraizen.berill.api.widgets.WidgetBlock
import dev.akkraizen.berill.api.widgets.WidgetButton
import dev.akkraizen.berill.api.widgets.WidgetContent
import dev.akkraizen.berill.api.widgets.WidgetEffect
import dev.akkraizen.berill.api.widgets.WidgetIcon
import dev.akkraizen.berill.api.widgets.WidgetProvider
import dev.akkraizen.berill.api.widgets.WidgetRequest
import dev.akkraizen.berill.api.widgets.WidgetRow
import dev.akkraizen.berill.api.widgets.WidgetSize
import dev.akkraizen.berill.plugins.watching.WatchingPlugin
import dev.akkraizen.berill.plugins.watching.data.WATCHING
import dev.akkraizen.berill.plugins.watching.data.titles
import dev.akkraizen.berill.plugins.watching.i18n.WatchingText

internal fun watchingWidgets(ctx: PluginContext): List<WidgetProvider> = listOf(WatchingNowWidget(ctx))

private class WatchingNowWidget(private val ctx: PluginContext) : WidgetProvider {
  override val id = "now"
  override val title: Tr = WatchingText.widgetNowTitle
  override val description: Tr = WatchingText.widgetNowDescription
  override val size = WidgetSize.Medium

  private fun watching() = titles(ctx).all().filter { it.value.normalizedStatus == WATCHING }.sortedByDescending { it.value.started ?: "" }

  override suspend fun content(request: WidgetRequest): WidgetContent {
    val list = watching()
    return WidgetContent(
      title = WatchingText.watchingNowCard(),
      icon = WidgetIcon.Movie,
      open = WidgetAction.Open(),
      blocks = if (list.isEmpty())
        emptyList()
      else
        listOf(
          WidgetBlock.Rows(
            list.map { item ->
              val t = item.value
              WidgetRow(
                title = t.title,
                subtitle = if (t.isSeries) WatchingText.episodesProgress(t.watched ?: 0, t.episodes ?: 0)() else WatchingText.mediaLabel(t.media),
                action = WidgetAction.Open(route = WatchingPlugin.TITLE_ROUTE + item.id),
                icon = WidgetIcon.Movie,
                button = if (t.isSeries) WidgetButton("+1", WidgetAction.Run(EPISODE, mapOf(TITLE to item.id)), WidgetIcon.Add) else null
              )
            }
          )
        ),
      empty = WatchingText.nothingWatching()
    )
  }

  override suspend fun perform(request: WidgetRequest, action: WidgetAction.Run): WidgetEffect {
    if (action.id != EPISODE) return WidgetEffect.Refresh()
    val id = action.args[TITLE] ?: return WidgetEffect.Refresh()
    val collection = titles(ctx)
    collection.get(id)?.let { collection.save(id, it.plusEpisode()) }
    return WidgetEffect.Refresh()
  }

  override suspend fun summary(request: WidgetRequest): WidgetRow? {
    val list = watching()
    val first = list.firstOrNull() ?: return null
    val t = first.value
    return WidgetRow(
      title = WatchingText.watchingNowCard(),
      subtitle = t.title,
      trailing = if (t.isSeries) "${t.watched ?: 0}/${t.episodes}" else null,
      action = WidgetAction.Open(pluginId = WatchingPlugin.id, route = WatchingPlugin.TITLE_ROUTE + first.id),
      icon = WidgetIcon.Movie
    )
  }

  private companion object {
    const val EPISODE = "episode"
    const val TITLE = "title"
  }
}
