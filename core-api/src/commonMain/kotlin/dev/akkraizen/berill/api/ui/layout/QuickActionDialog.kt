package dev.akkraizen.berill.api.ui.layout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.akkraizen.berill.api.Navigator
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.i18n.I18n

@Composable
fun QuickActionDialog(title: String, icon: ImageVector, onDismiss: () -> Unit, content: @Composable () -> Unit) {
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    BoxWithConstraints(Modifier.padding(16.dp).imePadding()) {
      Surface(Modifier.widthIn(max = 600.dp).fillMaxWidth().heightIn(max = minOf(maxHeight, 800.dp)),
        shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, I18n.text("connections.close"), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
          }
          Box(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            content()
            QuickEntryFeedback(Modifier.align(Alignment.BottomCenter))
          }
        }
      }
    }
  }
}

@Composable
fun rememberPopupContext(ctx: PluginContext, onDismiss: () -> Unit): PluginContext {
  val close by rememberUpdatedState(onDismiss)
  return remember(ctx) { popupContext(ctx) { close() } }
}

/** Keeps the current page underneath a quick-action dialog. */
fun popupContext(ctx: PluginContext, onDismiss: () -> Unit): PluginContext = object : PluginContext by ctx {
    override val nav = object : Navigator by ctx.nav {
      override fun back(): Boolean { onDismiss(); return true }
    }
  }
