package dev.akkraizen.berill.plugins.study.widgets

import dev.akkraizen.berill.api.Deadline
import dev.akkraizen.berill.api.DeadlineSource
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.all
import dev.akkraizen.berill.api.i18n.Tr
import dev.akkraizen.berill.api.ui.deadlineInfo
import dev.akkraizen.berill.api.ui.feedback.Tone
import dev.akkraizen.berill.api.widgets.WidgetAction
import dev.akkraizen.berill.api.widgets.WidgetBlock
import dev.akkraizen.berill.api.widgets.WidgetContent
import dev.akkraizen.berill.api.widgets.WidgetIcon
import dev.akkraizen.berill.api.widgets.WidgetProvider
import dev.akkraizen.berill.api.widgets.WidgetRequest
import dev.akkraizen.berill.api.widgets.WidgetRow
import dev.akkraizen.berill.api.widgets.WidgetSize
import dev.akkraizen.berill.api.widgets.WidgetTone
import dev.akkraizen.berill.plugins.study.i18n.StudyText
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

internal fun studyWidgets(ctx: PluginContext): List<WidgetProvider> = listOf(DeadlinesWidget(ctx))

internal fun Tone.widgetTone(): WidgetTone =
  when (this) {
    Tone.Bad -> WidgetTone.Bad
    Tone.Warn -> WidgetTone.Warn
    Tone.Good -> WidgetTone.Good
    Tone.Muted -> WidgetTone.Muted
    Tone.Info -> WidgetTone.Accent
    Tone.Normal -> WidgetTone.Normal
  }

private class DeadlinesWidget(private val ctx: PluginContext) : WidgetProvider {
  override val id = "deadlines"
  override val title: Tr = StudyText.widgetDeadlinesTitle
  override val description: Tr = StudyText.widgetDeadlinesDescription
  override val size = WidgetSize.Medium

  private suspend fun upcoming(): List<Deadline> {
    val sources = ctx.capabilities.all<DeadlineSource>()
    if (sources.isEmpty()) return emptyList()
    return combine(sources.map { it.deadlines() }) { lists -> lists.flatMap { it } }
      .first()
      .sortedBy { it.date }
  }

  private fun Deadline.row(): WidgetRow {
    val info = deadlineInfo(date)
    return WidgetRow(
      title = title,
      trailing = info.label,
      action = WidgetAction.Open(pluginId = pluginId, route = route),
      tone = info.tone.widgetTone(),
      icon = WidgetIcon.School
    )
  }

  override suspend fun content(request: WidgetRequest): WidgetContent {
    val list = upcoming().take(LIMIT)
    return WidgetContent(
      title = StudyText.deadlines(),
      icon = WidgetIcon.School,
      open = WidgetAction.Open(),
      blocks = if (list.isEmpty()) emptyList() else listOf(WidgetBlock.Rows(list.map { it.row() })),
      empty = StudyText.noDeadlines()
    )
  }

  override suspend fun summary(request: WidgetRequest): WidgetRow? {
    val next = upcoming().firstOrNull { deadlineInfo(it.date).days <= SOON } ?: return null
    return next.row().copy(subtitle = next.title, title = StudyText.deadlines())
  }

  private companion object {
    const val LIMIT = 8
    const val SOON = 7
  }
}
