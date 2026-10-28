package dev.akkraizen.berill.core.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.DAYS_FULL
import dev.akkraizen.berill.api.ui.dayMonth
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.interaction.BerillChip
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.nav.Tab
import dev.akkraizen.berill.core.notes.displayTitle
import dev.akkraizen.berill.core.plugin.PluginScope
import dev.akkraizen.berill.core.update.UpdateCheck
import dev.akkraizen.berill.core.ui.CoreText
import dev.akkraizen.berill.core.ui.settings.HomeUpdateBanner
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalTime::class, ExperimentalLayoutApi::class)
@Composable
internal fun HomeScreen(core: BerillCore) {
  val enabled by core.plugins.enabled.collectAsState()
  val notes by core.notes.observe().collectAsState(emptyList())
  val update by core.updates.state.collectAsState()
  val cards = remember(enabled) {
    enabled
      .flatMap { p -> p.homeCards(core.plugins.context(p)).map { p to it } }
      .sortedBy { it.second.order }
  }
  val actions = remember(enabled) { enabled.flatMap { p -> p.quickActions(core.plugins.context(p)) } }

  val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
  val greeting = when (now.hour) {
    in 5..11 -> CoreText.goodMorning()
    in 12..17 -> CoreText.goodAfternoon()
    in 18..22 -> CoreText.goodEvening()
    else -> CoreText.goodNight()
  }

  LazyVerticalStaggeredGrid(
    columns = StaggeredGridCells.Adaptive(340.dp),
    contentPadding = PaddingValues(16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalItemSpacing = 12.dp
  ) {
    item(span = StaggeredGridItemSpan.FullLine) {
      Column {
        Text(greeting, style = MaterialTheme.typography.displaySmall)
        Text(
          CoreText.homeDate(now.date.dayMonth(), DAYS_FULL[now.dayOfWeek.ordinal])(),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
    if (update is UpdateCheck.Available)
      item(key = "core/update", span = StaggeredGridItemSpan.FullLine) {
        HomeUpdateBanner(core)
      }
    item(span = StaggeredGridItemSpan.FullLine) {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BerillChip(
          onClick = { core.nav.openNote(null) },
          label = { Text(CoreText.newNote(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
          leadingIcon = { Ic(BerillIcons.Add, size = 18.dp) }
        )
        actions.forEach { a ->
          BerillChip(onClick = a.run, label = { Text(a.label(), maxLines = 1, overflow = TextOverflow.Ellipsis) }, leadingIcon = { Ic(a.icon, size = 18.dp) })
        }
      }
    }
    item(key = "core/notes") {
      HomeCardFrame(icon = Tab.Notes.icon, title = CoreText.recentNotes(), onOpen = { core.nav.selectTab(Tab.Notes) }) {
        if (notes.isEmpty()) {
          Text(CoreText.emptyYet(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
          notes.take(5).forEach { item ->
            Text(
              item.value.displayTitle(),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.fillMaxWidth()
                .interactive(bleed = 6.dp) { core.nav.openNote(item.id) }
                .padding(vertical = 4.dp)
            )
          }
        }
      }
    }
    items(cards, key = { (p, card) -> "${p.id}/${card.id}" }) { (p, card) ->
      Box(Modifier.animateItem()) {
        HomeCardFrame(icon = p.icon, title = card.title(), onOpen = { core.nav.openSection(p.id) }) {
          PluginScope(p) { card.content() }
        }
      }
    }
  }
}

@Composable
private fun HomeCardFrame(icon: ImageVector, title: String, onOpen: () -> Unit, content: @Composable () -> Unit) {
  Surface(
    shape = MaterialTheme.shapes.extraLarge,
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier = Modifier.interactive(MaterialTheme.shapes.extraLarge, onClick = onOpen)
  ) {
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Ic(icon, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Ic(BerillIcons.Next, tint = MaterialTheme.colorScheme.primary)
      }
      content()
    }
  }
}
