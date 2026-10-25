package dev.akkraizen.berill.core.external

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.feedback.color
import dev.akkraizen.berill.api.ui.layout.PluginList
import dev.akkraizen.berill.sdk.protocol.Target
import dev.akkraizen.berill.sdk.protocol.TargetKind
import kotlinx.coroutines.delay

@Composable
internal fun RemotePage(client: ExternalPluginClient, ctx: PluginContext, pageId: String) {
  val route by ctx.nav.route.collectAsState()
  val target = Target(TargetKind.Page, pageId, route)
  Subscribe(client, target)
  val state by remember(client, target) { client.state(target) }.collectAsState(RemoteState.Loading)
  PluginList {
    item { ToastBanner(client) }
    when (val s = state) {
      RemoteState.Loading -> item { LoadingHint() }
      is RemoteState.Failed -> item { FailureCard(s.message) { client.retry() } }
      is RemoteState.Ready -> {
        s.error?.let { e -> item { ErrorNote(e) } }
        s.nodes.forEachIndexed { i, node ->
          item(key = "n$i") { RemoteNode(node) { a, v -> client.event(target, a, v) } }
        }
      }
    }
  }
}

@Composable
internal fun RemoteContent(client: ExternalPluginClient, target: Target) {
  Subscribe(client, target)
  val state by remember(client, target) { client.state(target) }.collectAsState(RemoteState.Loading)
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    when (val s = state) {
      RemoteState.Loading -> LoadingHint()
      is RemoteState.Failed -> FailureCard(s.message) { client.retry() }
      is RemoteState.Ready -> {
        s.error?.let { ErrorNote(it) }
        RemoteNodes(s.nodes) { a, v -> client.event(target, a, v) }
      }
    }
  }
}

@Composable
private fun Subscribe(client: ExternalPluginClient, target: Target) {
  DisposableEffect(client, target) {
    client.mount(target)
    onDispose { client.unmount(target) }
  }
}

@Composable
private fun ToastBanner(client: ExternalPluginClient) {
  val toast by client.toast.collectAsState()
  var visible by remember { mutableStateOf<Pair<Long, String>?>(null) }
  LaunchedEffect(toast) {
    val t = toast ?: return@LaunchedEffect
    visible = t
    delay(3000)
    if (visible == t) visible = null
  }
  visible?.let { (_, text) ->
    Surface(
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.inverseSurface,
      contentColor = MaterialTheme.colorScheme.inverseOnSurface
    ) {
      Text(text, modifier = Modifier.fillMaxWidth().padding(12.dp))
    }
  }
}

@Composable
private fun LoadingHint() {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    Text(ExternalText.loading(), color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun FailureCard(message: String, onRetry: () -> Unit) {
  SectionCard(message, Icons.Outlined.ErrorOutline) {
    TextButton(onClick = onRetry) { Text(ExternalText.retry()) }
  }
}

@Composable
private fun ErrorNote(message: String) {
  Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}
