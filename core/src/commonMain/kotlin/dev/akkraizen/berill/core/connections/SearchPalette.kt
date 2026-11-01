package dev.akkraizen.berill.core.connections

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.akkraizen.berill.api.SearchResult
import dev.akkraizen.berill.api.ui.animation.BerillMotion
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.interaction.LocalShortcuts
import dev.akkraizen.berill.api.ui.interaction.ShortcutAction
import dev.akkraizen.berill.core.BerillCore
import kotlinx.coroutines.CancellationException
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.core.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Shared header actions; the desktop caption uses the compact pointer targets. */
@Composable
fun ConnectionTools(core: BerillCore, compact: Boolean = false, showSearch: Boolean = true) {
  val notices by core.notifications.state.collectAsState()
  val notificationPreferences by core.notificationPreferences.state.collectAsState()
  val activePlugins by core.plugins.enabled.collectAsState()
  val activeIds = activePlugins.map { it.id }.toSet()
  val now by produceState(kotlin.time.Clock.System.now().toEpochMilliseconds()) {
    while (true) { value = kotlin.time.Clock.System.now().toEpochMilliseconds(); delay(30_000) }
  }
  val unread = notices.count {
    notificationPreferences.allows(it.kind, it.pluginId ?: (it.open as? dev.akkraizen.berill.api.widgets.WidgetAction.Open)?.pluginId, it.reminderEvent) &&
      core.notifications.isVisible(it, activeIds) && !it.read && it.status != dev.akkraizen.berill.core.notifications.NoticeStatus.RUNNING && it.at <= now
  }
  if (!compact) {
    MobileConnectionTools(core, showSearch, unread)
    return
  }
  Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
    BadgedBox(badge = { if (unread > 0) Badge { Text(unread.coerceAtMost(99).toString()) } }) {
      HeaderAction(Icons.Outlined.Notifications, I18n.text("center.title"), compact) { core.notifications.open.value = true }
    }
    if (showSearch) HeaderAction(Icons.Outlined.Search, listOfNotNull(ConnectionsText.searchTitle(), LocalShortcuts.current.label(ShortcutAction.SEARCH)).joinToString(" · "), compact, emphasized = true) { core.connections.searchOpen.value = true }
    HeaderAction(Icons.Outlined.Today, ConnectionsText.day(), compact) { core.connections.dayOpen.value = true }
    HeaderAction(Icons.Outlined.DeleteOutline, ConnectionsText.trash(), compact) { core.connections.trashOpen.value = true }
  }
}

/** Search entry point in the desktop caption; the command palette owns the actual input. */
@Composable
fun ConnectionSearch(core: BerillCore, compact: Boolean) {
  HeaderSearchButton(compact = compact, shortcut = LocalShortcuts.current.label(ShortcutAction.SEARCH), onClick = { core.connections.searchOpen.value = true })
}

@Composable
fun HeaderSearchButton(compact: Boolean, shortcut: String?, onClick: () -> Unit) {
  val label = I18n.text("connections.headerSearch")
  if (compact) HeaderAction(Icons.Outlined.Search, listOfNotNull(label, shortcut).joinToString(" · "), true, emphasized = true, onClick = onClick)
  else Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().height(40.dp), shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Icon(Icons.Outlined.Search, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
      Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (shortcut != null) Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(shortcut, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HeaderAction(icon: ImageVector, description: String, compact: Boolean, emphasized: Boolean = false, busy: Boolean = false, tint: Color? = null, onClick: () -> Unit) {
  val focusManager = LocalFocusManager.current
  val activate = { focusManager.clearFocus(force = true); onClick() }
  TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
    tooltip = { PlainTooltip { Text(description) } },
    state = rememberTooltipState()
  ) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides if (compact) 36.dp else 48.dp) {
      val interaction = remember { MutableInteractionSource() }
      val hovered by interaction.collectIsHoveredAsState()
      val pressed by interaction.collectIsPressedAsState()
      val colors = MaterialTheme.colorScheme
      val background by animateColorAsState(
        when {
          pressed -> colors.primaryContainer
          hovered || emphasized -> colors.secondaryContainer
          else -> Color.Transparent
        }, BerillMotion.effects(), label = "header-action-background"
      )
      val foreground by animateColorAsState(
        when {
          pressed -> colors.onPrimaryContainer
          hovered || emphasized -> colors.onSecondaryContainer
          else -> tint ?: colors.onSurfaceVariant
        }, BerillMotion.effects(), label = "header-action-foreground"
      )
      val modifier = Modifier.size(if (compact) 36.dp else 48.dp).pointerHoverIcon(PointerIcon.Hand)
        .then(if (busy) Modifier.semantics { contentDescription = description } else Modifier)
      if (emphasized) FilledTonalIconButton(onClick = activate, modifier = modifier, shape = CircleShape,
        interactionSource = interaction,
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = background, contentColor = foreground)) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = foreground, strokeWidth = 2.dp)
        else Icon(icon, description, Modifier.size(20.dp))
      } else IconButton(onClick = activate, modifier = modifier, interactionSource = interaction,
        colors = IconButtonDefaults.iconButtonColors(containerColor = background, contentColor = foreground)) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = foreground, strokeWidth = 2.dp)
        else Icon(icon, description, Modifier.size(20.dp))
      }
    }
  }
}

@Composable
internal fun SearchDialog(core: BerillCore, close: () -> Unit) {
  var query by remember { mutableStateOf("") }
  var selected by remember { mutableIntStateOf(0) }
  var results by remember { mutableStateOf(emptyList<SearchResult>()) }
  var revision by remember { mutableIntStateOf(0) }
  var loading by remember { mutableStateOf(false) }
  var loadError by remember { mutableStateOf<String?>(null) }
  val partial by core.connections.searchPartial.collectAsState()
  val enabledPlugins by core.plugins.enabled.collectAsState()
  fun openResult(result: SearchResult) {
    if (core.connections.errors.run(FailureOperation.COMMAND) { result.open() }) close()
  }
  val focus = remember { FocusRequester() }
  val list = rememberLazyListState()
  val colors = MaterialTheme.colorScheme
  LaunchedEffect(Unit) { focus.requestFocus() }
  LaunchedEffect(selected, results) { if (selected in results.indices) list.animateScrollToItem(selected) }
  LaunchedEffect(query, revision, enabledPlugins) {
    results = emptyList(); selected = 0; loadError = null; loading = true
    try {
      delay(150)
      results = withContext(Dispatchers.Default) { core.connections.search(query) }
    } catch (e: CancellationException) { throw e } catch (e: Exception) {
      AppLog.error("search", "load", e); loadError = failureMessage(e)
    } finally { loading = false }
  }

  Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    BoxWithConstraints(Modifier.widthIn(max = 720.dp).fillMaxWidth().imePadding().padding(16.dp)) {
      val wide = maxWidth >= 480.dp
      Surface(
        modifier = Modifier.fillMaxWidth().heightIn(max = minOf(maxHeight, 720.dp)),
        shape = RoundedCornerShape(32.dp),
        color = colors.surfaceContainer,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp
      ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
              Text(ConnectionsText.searchTitle(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
              Text(ConnectionsText.searchSubtitle(), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (wide) LocalShortcuts.current.label(ShortcutAction.SEARCH)?.let { KeyBadge(it) }
            FilledTonalIconButton(onClick = close, modifier = Modifier.size(40.dp), shape = CircleShape) {
              Icon(Icons.Outlined.Close, ConnectionsText.close(), Modifier.size(20.dp))
            }
          }
          TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            shape = CircleShape,
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = { Text(ConnectionsText.searchHint(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Outlined.Search, null, tint = colors.primary) },
            trailingIcon = if (query.isBlank()) null else ({ IconButton(onClick = { query = ""; focus.requestFocus() }) {
              Icon(Icons.Outlined.Close, ConnectionsText.clearSearch())
            } }),
            colors = TextFieldDefaults.colors(
              focusedContainerColor = colors.surfaceContainerHighest,
              unfocusedContainerColor = colors.surfaceContainerHighest,
              focusedIndicatorColor = Color.Transparent,
              unfocusedIndicatorColor = Color.Transparent,
              disabledIndicatorColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).focusRequester(focus).onPreviewKeyEvent { e ->
              if (e.type != KeyEventType.KeyDown) false else when (e.key) {
                Key.DirectionDown -> { selected = (selected + 1).coerceAtMost((results.size - 1).coerceAtLeast(0)); true }
                Key.DirectionUp -> { selected = (selected - 1).coerceAtLeast(0); true }
                Key.Enter, Key.NumPadEnter -> { results.getOrNull(selected)?.let(::openResult); true }
                Key.Escape -> { close(); true }
                else -> false
              }
            }
          )
          Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (query.isBlank()) ConnectionsText.searchActions() else ConnectionsText.searchResults(),
              Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = colors.primary)
            Surface(shape = CircleShape, color = colors.surfaceContainerHighest) {
              Text("${results.size}", Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
            }
          }
          if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
          if (loadError != null || (!loading && partial)) FeatureErrorNotice(loadError ?: I18n.text("errors.partial")) { revision++ }
          LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            state = list,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
          ) {
            itemsIndexed(results, key = { _, result -> result.id }) { index, result ->
              SearchResultRow(core, result, selected = index == selected, first = index == 0, last = index == results.lastIndex) {
                openResult(result)
              }
            }
            if (results.isEmpty() && !loading && loadError == null && !partial) item {
              Column(Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = colors.surfaceContainerHighest) {
                  Icon(Icons.Outlined.SearchOff, null, Modifier.padding(16.dp).size(28.dp), tint = colors.onSurfaceVariant)
                }
                Text(ConnectionsText.searchEmpty(), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(ConnectionsText.searchEmptyHint(), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
              }
            }
          }
          if (wide) Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyBadge("↑ ↓")
            Text(ConnectionsText.searchNavigate(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            KeyBadge("Enter")
            Text(ConnectionsText.searchOpen(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            KeyBadge("Esc")
            Text(ConnectionsText.close(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
          }
        }
      }
    }
  }
}

@Composable
private fun SearchResultRow(core: BerillCore, result: SearchResult, selected: Boolean, first: Boolean, last: Boolean, onClick: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  val top by animateDpAsState(if (selected || first) 24.dp else 6.dp, BerillMotion.spatial(), label = "search-top")
  val bottom by animateDpAsState(if (selected || last) 24.dp else 6.dp, BerillMotion.spatial(), label = "search-bottom")
  val container by animateColorAsState(if (selected) colors.secondaryContainer else colors.surfaceContainerLow, BerillMotion.effects(), label = "search-result")
  val badgeRadius by animateDpAsState(if (selected) 24.dp else 14.dp, BerillMotion.spatial(), label = "search-icon")
  val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
  val owner = result.id.substringBefore('/').substringBefore(':').substringBefore('.')
  val icon = when (owner) {
    "note" -> Icons.Outlined.Description
    "day" -> Icons.Outlined.Today
    else -> core.plugins.byId(owner)?.icon ?: Icons.Outlined.Bolt
  }
  Surface(
    modifier = Modifier.fillMaxWidth().semantics { this.selected = selected; role = Role.Button }.interactive(shape, onClick = onClick),
    color = container,
    contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface,
    shape = shape
  ) {
    Row(Modifier.heightIn(min = 76.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
      Surface(shape = RoundedCornerShape(badgeRadius), color = if (selected) colors.secondary else colors.surfaceContainerHighest,
        contentColor = if (selected) colors.onSecondary else colors.primary) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(22.dp)) }
      }
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(result.title, style = MaterialTheme.typography.titleSmall, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(result.subtitle, style = MaterialTheme.typography.bodySmall, color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      if (selected) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = colors.secondary,
          contentColor = colors.onSecondary
        ) {
          Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.KeyboardReturn, ConnectionsText.searchOpen(), Modifier.size(24.dp))
          }
        }
      }
      else Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
    }
  }
}

@Composable
private fun KeyBadge(label: String) {
  Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(label, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
  }
}
