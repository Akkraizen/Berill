package dev.akkraizen.berill.widgets

import android.content.Context
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalGlanceId
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.components.FilledButton
import androidx.glance.appwidget.components.OutlineButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.akkraizen.berill.R
import dev.akkraizen.berill.api.widgets.WidgetAction
import dev.akkraizen.berill.api.widgets.WidgetBlock
import dev.akkraizen.berill.api.widgets.WidgetButton
import dev.akkraizen.berill.api.widgets.WidgetCheck
import dev.akkraizen.berill.api.widgets.WidgetContent
import dev.akkraizen.berill.api.widgets.WidgetDay
import dev.akkraizen.berill.api.widgets.WidgetIcon
import dev.akkraizen.berill.api.widgets.WidgetMetric
import dev.akkraizen.berill.api.widgets.WidgetRow
import dev.akkraizen.berill.api.widgets.WidgetTone
import dev.akkraizen.berill.core.widgets.WidgetHub
import dev.akkraizen.berill.core.widgets.WidgetText
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json

private val actionJson = Json { encodeDefaults = false }

private val GoodColor = ColorProvider(day = Color(0xFF2E7D32), night = Color(0xFF81C784))
private val WarnColor = ColorProvider(day = Color(0xFFB26A00), night = Color(0xFFFFB74D))

private class WidgetEnv(val context: Context, val appWidgetId: Int, val width: Dp, val height: Dp) {
  val narrow: Boolean
    get() = width < 220.dp
}

@Composable
private fun toneColor(tone: WidgetTone): ColorProvider =
  when (tone) {
    WidgetTone.Normal -> GlanceTheme.colors.onSurface
    WidgetTone.Accent -> GlanceTheme.colors.primary
    WidgetTone.Good -> GoodColor
    WidgetTone.Warn -> WarnColor
    WidgetTone.Bad -> GlanceTheme.colors.error
    WidgetTone.Muted -> GlanceTheme.colors.onSurfaceVariant
  }

private fun iconRes(icon: WidgetIcon): Int =
  when (icon) {
    WidgetIcon.Add -> R.drawable.ic_widget_add
    WidgetIcon.Remove -> R.drawable.ic_widget_remove
    WidgetIcon.Check -> R.drawable.ic_widget_check
    WidgetIcon.Play -> R.drawable.ic_widget_play
    WidgetIcon.Stop -> R.drawable.ic_widget_stop
    WidgetIcon.Next -> R.drawable.ic_widget_next
    WidgetIcon.Previous -> R.drawable.ic_widget_previous
    WidgetIcon.Refresh -> R.drawable.ic_widget_refresh
    WidgetIcon.Open -> R.drawable.ic_widget_open
    WidgetIcon.Note -> R.drawable.ic_widget_note
    WidgetIcon.Water -> R.drawable.ic_widget_water
    WidgetIcon.Timer -> R.drawable.ic_widget_timer
    WidgetIcon.Food -> R.drawable.ic_widget_food
    WidgetIcon.Book -> R.drawable.ic_widget_book
    WidgetIcon.Movie -> R.drawable.ic_widget_movie
    WidgetIcon.Fitness -> R.drawable.ic_widget_fitness
    WidgetIcon.Calendar -> R.drawable.ic_widget_calendar
    WidgetIcon.Money -> R.drawable.ic_widget_money
    WidgetIcon.Recipe -> R.drawable.ic_widget_recipe
    WidgetIcon.School -> R.drawable.ic_widget_school
    WidgetIcon.Translate -> R.drawable.ic_widget_translate
    WidgetIcon.Habit -> R.drawable.ic_widget_habit
    WidgetIcon.Mood -> R.drawable.ic_widget_mood
  }

private fun WidgetEnv.action(action: WidgetAction): Action {
  val encoded = actionJson.encodeToString(WidgetAction.serializer(), action)
  return when {
    action is WidgetAction.Run && !action.opensApp ->
      actionRunCallback<WidgetActionCallback>(actionParametersOf(WidgetActionCallback.ACTION to encoded))
    else -> actionStartActivity(WidgetHost.openIntent(context, appWidgetId, action, encoded))
  }
}

@Composable
private fun WidgetLabel(text: String, size: TextUnit = 13.sp, color: ColorProvider = GlanceTheme.colors.onSurface, bold: Boolean = false, maxLines: Int = 1, align: TextAlign = TextAlign.Start, modifier: GlanceModifier = GlanceModifier) {
  Text(
    text = text,
    modifier = modifier,
    style = TextStyle(color = color, fontSize = size, fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal, textAlign = align),
    maxLines = maxLines
  )
}

@Composable
internal fun WidgetSurface(prefs: Preferences) {
  val content = prefs[WidgetHost.CONTENT]?.let(WidgetHub::decodeContent)
    ?: WidgetHub.notice(WidgetText.appName(), WidgetText.loading())
  WidgetFrame(content)
}

@Composable
internal fun WidgetFrame(content: WidgetContent) {
  val context = LocalContext.current
  val glanceId = LocalGlanceId.current
  val size = LocalSize.current
  val appWidgetId = runCatching { WidgetHost.appWidgetId(context, glanceId) }.getOrDefault(0)
  val env = WidgetEnv(context, appWidgetId, size.width, size.height)
  Column(
    modifier = GlanceModifier
      .fillMaxSize()
      .appWidgetBackground()
      .cornerRadius(20.dp)
      .background(GlanceTheme.colors.widgetBackground)
      .padding(horizontal = 12.dp, vertical = 10.dp)
  ) {
    env.Header(content)
    Spacer(GlanceModifier.height(6.dp))
    if (content.blocks.isEmpty()) {
      WidgetLabel(content.empty ?: "", color = GlanceTheme.colors.onSurfaceVariant, maxLines = 4)
    } else {
      LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
        items(content.blocks.flatMap(::split)) { block ->
          Column(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)) { env.Block(block) }
        }
      }
    }
  }
}

private fun split(block: WidgetBlock): List<WidgetBlock> =
  when (block) {
    is WidgetBlock.Rows -> block.items.map { WidgetBlock.Rows(listOf(it)) }
    is WidgetBlock.Checks -> block.items.map { WidgetBlock.Checks(listOf(it)) }
    is WidgetBlock.Grid -> listOf(WidgetBlock.Grid(block.header, emptyList())) + block.rows.map { WidgetBlock.Grid(emptyList(), listOf(it)) }
    else -> listOf(block)
  }

@Composable
private fun WidgetEnv.Header(content: WidgetContent) {
  Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    val open = content.open?.let { action(it) }
    Row(
      modifier = (if (open != null) GlanceModifier.clickable(open) else GlanceModifier).defaultWeight(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      content.icon?.let {
        Image(
          provider = ImageProvider(iconRes(it)),
          contentDescription = null,
          modifier = GlanceModifier.size(20.dp),
          colorFilter = ColorFilter.tint(GlanceTheme.colors.primary)
        )
        Spacer(GlanceModifier.width(8.dp))
      }
      Column {
        WidgetLabel(content.title, size = 15.sp, bold = true)
        content.subtitle?.let { WidgetLabel(it, size = 12.sp, color = GlanceTheme.colors.onSurfaceVariant) }
      }
    }
    content.buttons.take(2).forEach { button ->
      Spacer(GlanceModifier.width(4.dp))
      CircleIconButton(
        imageProvider = ImageProvider(iconRes(button.icon ?: WidgetIcon.Open)),
        contentDescription = button.label,
        onClick = action(button.action),
        modifier = GlanceModifier.size(36.dp),
        backgroundColor = GlanceTheme.colors.secondaryContainer,
        contentColor = GlanceTheme.colors.onSecondaryContainer
      )
    }
  }
}

@Composable
private fun WidgetEnv.Block(block: WidgetBlock) {
  when (block) {
    is WidgetBlock.Text -> WidgetLabel(block.text, size = if (block.large) 18.sp else 13.sp, color = toneColor(block.tone), bold = block.large, maxLines = if (block.large) 2 else 6)
    is WidgetBlock.Rows -> block.items.forEach { RowItem(it) }
    is WidgetBlock.Checks -> block.items.forEach { CheckItem(it) }
    is WidgetBlock.Buttons -> Buttons(block.items)
    is WidgetBlock.Bar -> BarItem(block)
    is WidgetBlock.Metrics -> MetricsRow(block.items)
    is WidgetBlock.Ring -> RingItem(block.metric)
    is WidgetBlock.Sparkline -> SparklineItem(block)
    is WidgetBlock.Grid -> GridItem(block)
    is WidgetBlock.Month -> MonthItem(block)
    is WidgetBlock.Countdown -> CountdownItem(block)
    is WidgetBlock.Card -> CardItem(block)
  }
}

@Composable
private fun WidgetEnv.RowItem(row: WidgetRow) {
  val click = row.action?.let { action(it) }
  Row(
    modifier = (if (click != null) GlanceModifier.clickable(click) else GlanceModifier)
      .fillMaxWidth()
      .cornerRadius(12.dp)
      .background(GlanceTheme.colors.surfaceVariant)
      .padding(horizontal = 10.dp, vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    row.icon?.let {
      Image(
        provider = ImageProvider(iconRes(it)),
        contentDescription = null,
        modifier = GlanceModifier.size(16.dp),
        colorFilter = ColorFilter.tint(toneColor(if (row.tone == WidgetTone.Normal) WidgetTone.Accent else row.tone))
      )
      Spacer(GlanceModifier.width(8.dp))
    }
    Column(GlanceModifier.defaultWeight()) {
      WidgetLabel(row.title, maxLines = 2)
      row.subtitle?.let { WidgetLabel(it, size = 11.sp, color = GlanceTheme.colors.onSurfaceVariant, maxLines = 2) }
    }
    row.trailing?.let {
      Spacer(GlanceModifier.width(6.dp))
      WidgetLabel(it, size = 12.sp, color = toneColor(if (row.tone == WidgetTone.Normal) WidgetTone.Muted else row.tone), bold = row.tone != WidgetTone.Normal)
    }
    row.button?.let { button ->
      Spacer(GlanceModifier.width(6.dp))
      FilledButton(
        text = button.label,
        onClick = action(button.action),
        modifier = GlanceModifier.height(32.dp)
      )
    }
  }
}

@Composable
private fun WidgetEnv.CheckItem(check: WidgetCheck) {
  Row(
    modifier = GlanceModifier.fillMaxWidth().cornerRadius(12.dp).background(GlanceTheme.colors.surfaceVariant).padding(horizontal = 4.dp, vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    CheckBox(
      checked = check.checked,
      onCheckedChange = action(check.action),
      modifier = GlanceModifier.defaultWeight(),
      text = check.label,
      style = TextStyle(
        color = if (check.checked) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface,
        fontSize = 13.sp
      ),
      maxLines = 2
    )
    val side = listOfNotNull(check.trailing, check.subtitle).joinToString(" · ")
    if (side.isNotEmpty()) {
      WidgetLabel(side, size = 11.sp, color = GlanceTheme.colors.onSurfaceVariant, modifier = GlanceModifier.padding(end = 6.dp))
    }
  }
}

@Composable
private fun WidgetEnv.Buttons(buttons: List<WidgetButton>) {
  val perRow = when {
    buttons.all { it.label.length <= 3 } -> if (narrow) 3 else 5
    narrow -> 2
    else -> 3
  }
  Column(GlanceModifier.fillMaxWidth()) {
    buttons.chunked(perRow).forEachIndexed { index, chunk ->
      if (index > 0) Spacer(GlanceModifier.height(4.dp))
      Row(GlanceModifier.fillMaxWidth()) {
        chunk.forEachIndexed { i, button ->
          if (i > 0) Spacer(GlanceModifier.width(4.dp))
          val modifier = GlanceModifier.defaultWeight().height(36.dp)
          val icon = button.icon?.takeIf { button.label.length > 3 }?.let { ImageProvider(iconRes(it)) }
          if (button.primary || button.selected)
            FilledButton(text = button.label, onClick = action(button.action), modifier = modifier, icon = icon, maxLines = 1)
          else
            OutlineButton(text = button.label, contentColor = GlanceTheme.colors.primary, onClick = action(button.action), modifier = modifier, icon = icon, maxLines = 1)
        }
      }
    }
  }
}

@Composable
private fun WidgetEnv.BarItem(bar: WidgetBlock.Bar) {
  Column(GlanceModifier.fillMaxWidth()) {
    Row(GlanceModifier.fillMaxWidth()) {
      WidgetLabel(bar.label, size = 12.sp, modifier = GlanceModifier.defaultWeight())
      WidgetLabel(bar.value, size = 12.sp, color = GlanceTheme.colors.onSurfaceVariant)
    }
    Spacer(GlanceModifier.height(3.dp))
    LinearProgressIndicator(
      progress = bar.fraction.coerceIn(0f, 1f),
      modifier = GlanceModifier.fillMaxWidth().height(6.dp),
      color = toneColor(bar.tone),
      backgroundColor = GlanceTheme.colors.surfaceVariant
    )
  }
}

@Composable
private fun WidgetEnv.MetricsRow(items: List<WidgetMetric>) {
  Row(GlanceModifier.fillMaxWidth()) {
    items.take(4).forEachIndexed { i, metric ->
      if (i > 0) Spacer(GlanceModifier.width(6.dp))
      Column(
        GlanceModifier.defaultWeight().cornerRadius(12.dp).background(GlanceTheme.colors.surfaceVariant).padding(8.dp)
      ) {
        WidgetLabel(metric.label, size = 11.sp, color = GlanceTheme.colors.onSurfaceVariant)
        WidgetLabel(metric.value, size = if (narrow) 13.sp else 15.sp, color = toneColor(metric.tone), bold = true)
        metric.subtitle?.let { WidgetLabel(it, size = 11.sp, color = GlanceTheme.colors.onSurfaceVariant) }
      }
    }
  }
}

@Composable
private fun WidgetEnv.RingItem(metric: WidgetMetric) {
  val ringColor = toneColor(metric.tone).getColor(context)
  val trackColor = GlanceTheme.colors.surfaceVariant.getColor(context)
  val density = context.resources.displayMetrics.density
  val bitmap = WidgetBitmaps.ring((72 * density).roundToInt(), metric.progress ?: 0f, ringColor.toArgb(), trackColor.toArgb(), 8 * density)
  Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Box(GlanceModifier.size(72.dp), contentAlignment = Alignment.Center) {
      Image(provider = ImageProvider(bitmap), contentDescription = null, modifier = GlanceModifier.size(72.dp))
      WidgetLabel("${((metric.progress ?: 0f) * 100).roundToInt()}%", size = 13.sp, bold = true, align = TextAlign.Center)
    }
    Spacer(GlanceModifier.width(12.dp))
    Column(GlanceModifier.defaultWeight()) {
      WidgetLabel(metric.label, size = 12.sp, color = GlanceTheme.colors.onSurfaceVariant)
      WidgetLabel(metric.value, size = 22.sp, bold = true, color = toneColor(metric.tone))
      metric.subtitle?.let { WidgetLabel(it, size = 12.sp, color = GlanceTheme.colors.onSurfaceVariant, maxLines = 2) }
    }
  }
}

@Composable
private fun WidgetEnv.SparklineItem(block: WidgetBlock.Sparkline) {
  val density = context.resources.displayMetrics.density
  val widthPx = ((width.value - 24) * density).roundToInt().coerceAtLeast(64)
  val heightPx = (64 * density).roundToInt()
  val bitmap = WidgetBitmaps.sparkline(
    widthPx,
    heightPx,
    block.values,
    GlanceTheme.colors.primary.getColor(context).toArgb(),
    3 * density
  )
  Column(GlanceModifier.fillMaxWidth()) {
    Image(provider = ImageProvider(bitmap), contentDescription = null, modifier = GlanceModifier.fillMaxWidth().height(64.dp))
    Row(GlanceModifier.fillMaxWidth()) {
      WidgetLabel(block.start ?: "", size = 11.sp, color = GlanceTheme.colors.onSurfaceVariant, modifier = GlanceModifier.defaultWeight())
      WidgetLabel(block.end ?: "", size = 11.sp, color = GlanceTheme.colors.onSurfaceVariant)
    }
  }
}

@Composable
private fun WidgetEnv.GridItem(block: WidgetBlock.Grid) {
  val cell = if (narrow) 14.dp else 18.dp
  val labelWidth = if (narrow) 70.dp else 110.dp
  if (block.header.isNotEmpty())
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Spacer(GlanceModifier.width(labelWidth))
      block.header.take(7).forEach { day ->
        WidgetLabel(day.take(2), size = 10.sp, color = GlanceTheme.colors.onSurfaceVariant, align = TextAlign.Center, modifier = GlanceModifier.width(cell + 4.dp))
      }
    }
  val primary = GlanceTheme.colors.primary.getColor(context)
  val empty = GlanceTheme.colors.surfaceVariant
  block.rows.forEach { row ->
    Row(GlanceModifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
      WidgetLabel(row.label, size = 12.sp, modifier = GlanceModifier.width(labelWidth))
      row.cells.take(7).forEach { value ->
        Box(GlanceModifier.width(cell + 4.dp), contentAlignment = Alignment.Center) {
          val fill = value ?: 0f
          Box(
            GlanceModifier
              .size(cell)
              .cornerRadius(5.dp)
              .background(if (fill <= 0f) empty else ColorProvider(primary.copy(alpha = 0.3f + 0.7f * fill)))
          ) {}
        }
      }
      row.trailing?.let {
        Spacer(GlanceModifier.defaultWeight())
        WidgetLabel(it, size = 11.sp, color = GlanceTheme.colors.onSurfaceVariant)
      }
    }
  }
}

@Composable
private fun WidgetEnv.MonthItem(block: WidgetBlock.Month) {
  val weeks = block.days.chunked(7).filter { week -> week.any { it.inMonth } }
  val cellHeight = ((height.value - 190f) / (weeks.size + 1)).coerceIn(28f, 46f).dp
  Column(GlanceModifier.fillMaxWidth()) {
    Row(GlanceModifier.fillMaxWidth()) {
      block.weekdays.take(7).forEach {
        WidgetLabel(it.take(2), size = 10.sp, color = GlanceTheme.colors.onSurfaceVariant, align = TextAlign.Center, modifier = GlanceModifier.defaultWeight())
      }
    }
    weeks.forEach { week ->
      Row(GlanceModifier.fillMaxWidth()) {
        week.forEach { day -> DayCell(day, GlanceModifier.defaultWeight().height(cellHeight)) }
      }
    }
  }
}

@Composable
private fun WidgetEnv.DayCell(day: WidgetDay, modifier: GlanceModifier) {
  val click = day.action?.let { action(it) }
  val text = when {
    day.today -> GlanceTheme.colors.onPrimary
    day.selected -> GlanceTheme.colors.onPrimaryContainer
    day.inMonth -> GlanceTheme.colors.onSurface
    else -> GlanceTheme.colors.outline
  }
  val base = GlanceModifier.fillMaxSize().cornerRadius(12.dp)
  val filled = when {
    day.today -> base.background(GlanceTheme.colors.primary)
    day.selected -> base.background(GlanceTheme.colors.primaryContainer)
    else -> base
  }
  Box(modifier = (if (click != null) modifier.clickable(click) else modifier).padding(1.dp), contentAlignment = Alignment.Center) {
    Column(modifier = filled, horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
      WidgetLabel(day.label, size = 12.sp, color = text, bold = day.today || day.selected, align = TextAlign.Center)
      if (day.events > 0)
        WidgetLabel(
          "•".repeat(day.events.coerceAtMost(3)),
          size = 9.sp,
          color = if (day.today) GlanceTheme.colors.onPrimary else GlanceTheme.colors.primary,
          align = TextAlign.Center
        )
    }
  }
}

@Composable
private fun WidgetEnv.CountdownItem(block: WidgetBlock.Countdown) {
  val endsAt = block.endsAtMillis
  if (endsAt == null) {
    WidgetLabel(block.idle, size = 14.sp, color = GlanceTheme.colors.onSurfaceVariant, maxLines = 2)
    return
  }
  val color = GlanceTheme.colors.primary.getColor(context).toArgb()
  val views = RemoteViews(context.packageName, R.layout.widget_chronometer).apply {
    val base = SystemClock.elapsedRealtime() + (endsAt - System.currentTimeMillis())
    setChronometer(R.id.widget_chronometer, base, null, true)
    setChronometerCountDown(R.id.widget_chronometer, true)
    setTextColor(R.id.widget_chronometer, color)
  }
  Column(GlanceModifier.fillMaxWidth()) {
    WidgetLabel(block.label, size = 12.sp, color = GlanceTheme.colors.onSurfaceVariant)
    AndroidRemoteViews(views, GlanceModifier.fillMaxWidth())
  }
}

@Composable
private fun WidgetEnv.CardItem(block: WidgetBlock.Card) {
  Column(
    GlanceModifier.fillMaxWidth().cornerRadius(16.dp).background(GlanceTheme.colors.primaryContainer).padding(12.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    WidgetLabel(block.front, size = 20.sp, color = GlanceTheme.colors.onPrimaryContainer, bold = true, maxLines = 2, align = TextAlign.Center, modifier = GlanceModifier.fillMaxWidth())
    block.hint?.let { WidgetLabel(it, size = 12.sp, color = GlanceTheme.colors.onPrimaryContainer, align = TextAlign.Center, modifier = GlanceModifier.fillMaxWidth()) }
    Spacer(GlanceModifier.height(6.dp))
    WidgetLabel(block.back ?: "• • •", size = 15.sp, color = GlanceTheme.colors.onPrimaryContainer, maxLines = 3, align = TextAlign.Center, modifier = GlanceModifier.fillMaxWidth())
  }
}
