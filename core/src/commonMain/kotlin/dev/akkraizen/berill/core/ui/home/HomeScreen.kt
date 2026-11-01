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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.akkraizen.berill.api.QuickAction
import dev.akkraizen.berill.api.BerillPlugin
import dev.akkraizen.berill.core.connections.FailureOperation
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
  val entries = remember(core) { dev.akkraizen.berill.api.ui.state.QuickEntries(core.store::meta, core.store::setMeta, performUndo = { undo ->
    val previous = core.store.pendingDeletions.value.toSet()
    try { undo() } finally {
      core.store.dismissDeletions(core.store.pendingDeletions.value.filterNot { it in previous })
    }
  }) {
    core.connections.errors.report(FailureOperation.COMMAND, it)
  } }
  androidx.compose.runtime.CompositionLocalProvider(dev.akkraizen.berill.api.ui.state.LocalQuickEntries provides entries) {
    HomeContent(core)
  }
}

@OptIn(ExperimentalTime::class, ExperimentalLayoutApi::class)
@Composable
private fun HomeContent(core: BerillCore) {
  var quickPopup by remember { mutableStateOf<Pair<BerillPlugin, QuickAction>?>(null) }
  var notePopup by remember { mutableStateOf(false) }
  val enabled by core.plugins.enabled.collectAsState()
  androidx.compose.runtime.LaunchedEffect(enabled) {
    if (quickPopup?.first?.id?.let { id -> enabled.none { it.id == id } } == true) quickPopup = null
  }
  val preferences by core.homePreferences.state.collectAsState()
  val update by core.updates.state.collectAsState()
  val cards = remember(enabled, preferences) {
    enabled
      .flatMap { p -> p.homeCards(core.plugins.context(p)).map { p to it } }
      .sortedBy { it.second.order }
      .filter { (p, card) -> "${p.id}/${card.id}" !in preferences.hiddenCards }
      .sortedBy { (p, card) -> preferences.cardOrder.indexOf("${p.id}/${card.id}").let { if (it < 0) Int.MAX_VALUE else it } }
  }
  val actions = remember(enabled, preferences) { enabled.flatMap { p -> p.quickActions(core.plugins.context(p)).map { p to it } }
    .filter { (p, a) -> "${p.id}/${a.id}" !in preferences.hiddenActions }
    .sortedBy { (p, a) -> preferences.actionOrder.indexOf("${p.id}/${a.id}").let { if (it < 0) Int.MAX_VALUE else it } } }

  val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
  val greeting = when (now.hour) {
    in 5..11 -> CoreText.goodMorning()
    in 12..17 -> CoreText.goodAfternoon()
    in 18..22 -> CoreText.goodEvening()
    else -> CoreText.goodNight()
  }

  Box(Modifier.fillMaxWidth()) {
  LazyVerticalStaggeredGrid(
    columns = StaggeredGridCells.Adaptive(340.dp),
    contentPadding = PaddingValues(16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalItemSpacing = 12.dp
  ) {
    if (preferences.greeting) item(span = StaggeredGridItemSpan.FullLine) {
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
        val actionKeys = dev.akkraizen.berill.core.preferences.orderedKeys(listOf("core/new-note") + actions.map { (p, a) -> "${p.id}/${a.id}" }, preferences.actionOrder).filterNot { it in preferences.hiddenActions }
        actionKeys.forEach { actionKey ->
          if (actionKey == "core/new-note") BerillChip(onClick = { notePopup = true }, label = { Text(CoreText.newNote()) }, leadingIcon = { Ic(BerillIcons.Add, size = 18.dp) })
          else actions.firstOrNull { (p, a) -> "${p.id}/${a.id}" == actionKey }?.let { (p, a) ->
            BerillChip(onClick = { if (a.popup != null) quickPopup = p to a else core.connections.errors.run(FailureOperation.COMMAND, a.run) }, label = { Text(a.label(), maxLines = 1, overflow = TextOverflow.Ellipsis) }, leadingIcon = { Ic(a.icon, size = 18.dp) })
          }
        }
      }
    }
    val keys = dev.akkraizen.berill.core.preferences.orderedKeys(listOf("core/notes") + cards.map { (p, card) -> "${p.id}/${card.id}" }, preferences.cardOrder).filterNot { it in preferences.hiddenCards }
    items(keys, key = { it }) { cardKey ->
      if (cardKey == "core/notes") RecentNotesCard(core)
      else cards.firstOrNull { (p, card) -> "${p.id}/${card.id}" == cardKey }?.let { (p, card) ->
        HomeCardFrame(icon = p.icon, title = card.title(), onOpen = { core.nav.openSection(p.id) }) {
          PluginScope(p) { card.content() }
        }
      }
    }
  }
  if (!notePopup && quickPopup == null) dev.akkraizen.berill.api.ui.layout.QuickEntryFeedback(Modifier.align(Alignment.BottomCenter))
  }
  if (notePopup) QuickNoteDialog(core) { notePopup = false }
  quickPopup?.takeIf { (plugin, _) -> enabled.any { it.id == plugin.id } }?.let { (plugin, action) ->
    PluginScope(plugin) {
      action.popup?.invoke({ quickPopup = null }, { error -> core.connections.errors.report(FailureOperation.COMMAND, error) })
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

@Composable
private fun RecentNotesCard(core: BerillCore) {
  val notes by core.notes.observe().collectAsState(emptyList())
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
