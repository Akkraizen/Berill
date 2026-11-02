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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.ui.UiText
import dev.akkraizen.berill.api.ui.interaction.dragAnywhere
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
internal fun HomeScreen(core: BerillCore, editing: Boolean = false) {
  val entries = remember(core) { dev.akkraizen.berill.api.ui.state.QuickEntries(core.store::meta, core.store::setMeta, performUndo = { undo ->
    val previous = core.store.pendingDeletions.value.toSet()
    try { undo() } finally {
      core.store.dismissDeletions(core.store.pendingDeletions.value.filterNot { it in previous })
    }
  }) {
    core.connections.errors.report(FailureOperation.COMMAND, it)
  } }
  androidx.compose.runtime.CompositionLocalProvider(dev.akkraizen.berill.api.ui.state.LocalQuickEntries provides entries) {
    HomeContent(core, editing)
  }
}

@OptIn(ExperimentalTime::class, ExperimentalLayoutApi::class)
@Composable
private fun HomeContent(core: BerillCore, editing: Boolean) {
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

  val cardReorder = remember { Reorder() }
  val actionReorder = remember { Reorder() }
  fun prefs() = core.homePreferences.state.value
  val actionKeys = dev.akkraizen.berill.core.preferences.orderedKeys(listOf("core/new-note") + actions.map { (p, a) -> "${p.id}/${a.id}" }, preferences.actionOrder).filterNot { it in preferences.hiddenActions }
  val keys = dev.akkraizen.berill.core.preferences.orderedKeys(listOf("core/notes") + cards.map { (p, card) -> "${p.id}/${card.id}" }, preferences.cardOrder).filterNot { it in preferences.hiddenCards }
  val latestActions by androidx.compose.runtime.rememberUpdatedState(actionKeys)
  val latestCards by androidx.compose.runtime.rememberUpdatedState(keys)

  Column(Modifier.fillMaxWidth()) {
  if (editing) HiddenBar(core, enabled)
  Box(Modifier.fillMaxWidth()) {
  LazyVerticalStaggeredGrid(
    columns = StaggeredGridCells.Adaptive(340.dp),
    contentPadding = PaddingValues(16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalItemSpacing = 12.dp
  ) {
    if (preferences.greeting) item(key = "core/greeting", span = StaggeredGridItemSpan.FullLine) {
      EditableFrame(editing, onHide = { core.homePreferences.set(prefs().copy(greeting = false)) }) {
        Column(Modifier.fillMaxWidth()) {
          Text(greeting, style = MaterialTheme.typography.displaySmall)
          Text(
            CoreText.homeDate(now.date.dayMonth(), DAYS_FULL[now.dayOfWeek.ordinal])(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
    if (update is UpdateCheck.Available && !editing)
      item(key = "core/update", span = StaggeredGridItemSpan.FullLine) {
        HomeUpdateBanner(core)
      }
    item(key = "core/actions", span = StaggeredGridItemSpan.FullLine) {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actionKeys.forEach { actionKey ->
          val action = actions.firstOrNull { (p, a) -> "${p.id}/${a.id}" == actionKey }
          if (editing) {
            val label = if (actionKey == "core/new-note") CoreText.newNote() else action?.second?.label?.invoke() ?: actionKey
            val icon = if (actionKey == "core/new-note") BerillIcons.Add else action?.second?.icon ?: BerillIcons.Add
            EditableChip(
              label, icon,
              Modifier.reorderable(actionReorder, actionKey, { latestActions }) { order -> core.homePreferences.set(prefs().let { it.copy(actionOrder = dev.akkraizen.berill.core.preferences.mergeOrder(it.actionOrder, order)) }) },
              onHide = { core.homePreferences.set(prefs().let { it.copy(hiddenActions = it.hiddenActions + actionKey) }) }
            )
          } else if (actionKey == "core/new-note") BerillChip(onClick = { notePopup = true }, label = { Text(CoreText.newNote()) }, leadingIcon = { Ic(BerillIcons.Add, size = 18.dp) })
          else action?.let { (p, a) ->
            BerillChip(onClick = { if (a.popup != null) quickPopup = p to a else core.connections.errors.run(FailureOperation.COMMAND, a.run) }, label = { Text(a.label(), maxLines = 1, overflow = TextOverflow.Ellipsis) }, leadingIcon = { Ic(a.icon, size = 18.dp) })
          }
        }
      }
    }
    items(keys, key = { it }) { cardKey ->
      val modifier = if (editing) Modifier.reorderable(cardReorder, cardKey, { latestCards }) { order -> core.homePreferences.set(prefs().let { it.copy(cardOrder = dev.akkraizen.berill.core.preferences.mergeOrder(it.cardOrder, order)) }) } else Modifier
      Box(if (cardReorder.dragging != null) modifier.animateItem(fadeInSpec = null, fadeOutSpec = null) else modifier) {
        EditableFrame(editing, onHide = { core.homePreferences.set(prefs().let { it.copy(hiddenCards = it.hiddenCards + cardKey) }) }) {
          if (cardKey == "core/notes") RecentNotesCard(core, editing)
          else cards.firstOrNull { (p, card) -> "${p.id}/${card.id}" == cardKey }?.let { (p, card) ->
            HomeCardFrame(icon = p.icon, title = card.title(), editing = editing, onOpen = { core.nav.openSection(p.id) }) {
              PluginScope(p) { card.content() }
            }
          }
        }
      }
    }
  }
  if (!notePopup && quickPopup == null && !editing) dev.akkraizen.berill.api.ui.layout.QuickEntryFeedback(Modifier.align(Alignment.BottomCenter))
  }
  }
  if (notePopup) QuickNoteDialog(core) { notePopup = false }
  quickPopup?.takeIf { (plugin, _) -> enabled.any { it.id == plugin.id } }?.let { (plugin, action) ->
    PluginScope(plugin) {
      action.popup?.invoke({ quickPopup = null }, { error -> core.connections.errors.report(FailureOperation.COMMAND, error) })
    }
  }
}

@Composable
private fun HomeCardFrame(icon: ImageVector, title: String, editing: Boolean = false, onOpen: () -> Unit, content: @Composable () -> Unit) {
  Surface(
    shape = MaterialTheme.shapes.extraLarge,
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier = if (editing) Modifier else Modifier.interactive(MaterialTheme.shapes.extraLarge, onClick = onOpen)
  ) {
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Ic(icon, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (!editing) Ic(BerillIcons.Next, tint = MaterialTheme.colorScheme.primary) else Spacer(Modifier.width(32.dp))
      }
      content()
    }
  }
}

@Composable
private fun RecentNotesCard(core: BerillCore, editing: Boolean = false) {
  val notes by core.notes.observe().collectAsState(emptyList())
      HomeCardFrame(icon = Tab.Notes.icon, title = CoreText.recentNotes(), editing = editing, onOpen = { core.nav.selectTab(Tab.Notes) }) {
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

private class Reorder {
  var dragging by mutableStateOf<String?>(null)
  var pointer by mutableStateOf(Offset.Zero)
  var grab by mutableStateOf(Offset.Zero)
  var settled: String? = null
  val bounds = mutableStateMapOf<String, Rect>()
}

@Composable
private fun Modifier.reorderable(state: Reorder, key: String, order: () -> List<String>, onOrder: (List<String>) -> Unit): Modifier {
  val dragged = state.dragging == key
  return this
    .onGloballyPositioned { state.bounds[key] = it.boundsInRoot() }
    .zIndex(if (dragged) 1f else 0f)
    .graphicsLayer {
      if (dragged) {
        val origin = state.bounds[key]?.topLeft ?: Offset.Zero
        val shift = state.pointer - state.grab - origin
        translationX = shift.x
        translationY = shift.y
        scaleX = 1.02f
        scaleY = 1.02f
        shadowElevation = 16f
      }
    }
    .dragAnywhere(
      key,
      onStart = { at ->
        state.dragging = key
        state.settled = null
        state.grab = at
        state.pointer = (state.bounds[key]?.topLeft ?: Offset.Zero) + at
      },
      onMove = { delta ->
        state.pointer += delta
        val current = order()
        state.settled?.let { last -> if (state.bounds[last]?.contains(state.pointer) != true) state.settled = null }
        val target = state.bounds.entries.firstOrNull { (k, rect) ->
          k != key && k != state.settled && k in current && rect.deflate(minOf(rect.width, rect.height) * 0.2f).contains(state.pointer)
        }?.key
        if (target != null) {
          state.settled = target
          val next = current.toMutableList().apply { remove(key); add(current.indexOf(target), key) }
          if (next != current) onOrder(next)
        }
      },
      onEnd = { state.dragging = null },
      onCancel = { state.dragging = null }
    )
}

@Composable
private fun EditableFrame(editing: Boolean, onHide: () -> Unit, content: @Composable () -> Unit) {
  if (!editing) return content()
  Box {
    content()
    Box(Modifier.matchParentSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } })
    FilledTonalIconButton(onClick = onHide, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(36.dp)) {
      Icon(Icons.Outlined.VisibilityOff, contentDescription = UiText.hide(), modifier = Modifier.size(18.dp))
    }
  }
}

@Composable
private fun EditableChip(label: String, icon: ImageVector, modifier: Modifier, onHide: () -> Unit) {
  Surface(
    shape = MaterialTheme.shapes.small,
    color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    modifier = modifier
  ) {
    Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Ic(icon, size = 18.dp)
      Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
      IconButton(onClick = onHide, modifier = Modifier.size(32.dp)) {
        Icon(BerillIcons.Close, contentDescription = UiText.hide(), modifier = Modifier.size(16.dp))
      }
    }
  }
}

@Composable
private fun HiddenBar(core: BerillCore, enabled: List<BerillPlugin>) {
  val prefs by core.homePreferences.state.collectAsState()
  val cardNames = listOf("core/notes" to CoreText.recentNotes()) + enabled.flatMap { p -> p.homeCards(core.plugins.context(p)).map { "${p.id}/${it.id}" to it.title() } }
  val actionNames = listOf("core/new-note" to CoreText.newNote()) + enabled.flatMap { p -> p.quickActions(core.plugins.context(p)).map { "${p.id}/${it.id}" to it.label() } }
  val hiddenCards = cardNames.filter { it.first in prefs.hiddenCards }
  val hiddenActions = actionNames.filter { it.first in prefs.hiddenActions }
  Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(I18n.text("workspace.dragHint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
      TextButton(onClick = { core.homePreferences.reset() }) { Text(I18n.text("workspace.reset")) }
    }
    if (!prefs.greeting || hiddenCards.isNotEmpty() || hiddenActions.isNotEmpty())
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        Text(I18n.text("workspace.hidden"), style = MaterialTheme.typography.labelLarge)
        if (!prefs.greeting) RestoreChip(I18n.text("workspace.greeting")) { core.homePreferences.set(core.homePreferences.state.value.copy(greeting = true)) }
        hiddenCards.forEach { (key, name) -> RestoreChip(name) { core.homePreferences.state.value.let { core.homePreferences.set(it.copy(hiddenCards = it.hiddenCards - key)) } } }
        hiddenActions.forEach { (key, name) -> RestoreChip(name) { core.homePreferences.state.value.let { core.homePreferences.set(it.copy(hiddenActions = it.hiddenActions - key)) } } }
      }
  }
}

@Composable
private fun RestoreChip(label: String, onRestore: () -> Unit) {
  BerillChip(onClick = onRestore, label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }, leadingIcon = { Ic(BerillIcons.Add, size = 18.dp) })
}
