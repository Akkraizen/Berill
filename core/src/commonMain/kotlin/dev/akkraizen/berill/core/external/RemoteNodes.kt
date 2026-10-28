package dev.akkraizen.berill.core.external

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.BerillIcons
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.cards.Stat
import dev.akkraizen.berill.api.ui.cards.StatTiles
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.EmptyHint
import dev.akkraizen.berill.api.ui.feedback.ProgressLine
import dev.akkraizen.berill.api.ui.feedback.Tone
import dev.akkraizen.berill.api.ui.feedback.color
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.layout.DetailHeader
import dev.akkraizen.berill.api.ui.markdown.MarkdownText
import dev.akkraizen.berill.api.ui.media.Cover
import dev.akkraizen.berill.api.ui.selection.ChoiceChips
import dev.akkraizen.berill.api.ui.selection.pastelCheckboxColors
import dev.akkraizen.berill.sdk.ButtonStyle
import dev.akkraizen.berill.sdk.Keyboard
import dev.akkraizen.berill.sdk.TextStyle
import dev.akkraizen.berill.sdk.Tone as RemoteTone
import dev.akkraizen.berill.sdk.UiNode
import kotlinx.coroutines.delay

internal fun remoteIcon(name: String?): ImageVector? =
  when (name) {
    null,
    "" -> null

    "add" -> BerillIcons.Add
    "delete" -> BerillIcons.Delete
    "edit" -> BerillIcons.Edit
    "check" -> BerillIcons.Check
    "close" -> BerillIcons.Close
    "star" -> BerillIcons.Star
    "star_outline" -> BerillIcons.StarOutline
    "back" -> BerillIcons.Back
    "next" -> BerillIcons.Next
    "prev" -> BerillIcons.Prev
    "search" -> Icons.Outlined.Search
    "settings" -> Icons.Outlined.Settings
    "info" -> Icons.Outlined.Info
    else -> BerillIcons.catalog[name]
  }

private fun RemoteTone.toTone(): Tone =
  when (this) {
    RemoteTone.Normal -> Tone.Normal
    RemoteTone.Muted -> Tone.Muted
    RemoteTone.Good -> Tone.Good
    RemoteTone.Bad -> Tone.Bad
    RemoteTone.Warn -> Tone.Warn
    RemoteTone.Info -> Tone.Info
  }

@Composable
internal fun RemoteNodes(nodes: List<UiNode>, spacing: Int = 10, onAction: (String, String?) -> Unit) {
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.coerceIn(0, 48).dp)) {
    nodes.forEach { RemoteNode(it, onAction) }
  }
}

@Composable
private fun RowScope.RowChild(node: UiNode, onAction: (String, String?) -> Unit) {
  if (node is UiNode.Weighted) {
    Box(Modifier.weight(node.weight.coerceIn(0.05f, 20f))) { RemoteNode(node.child, onAction) }
  } else {
    RemoteNode(node, onAction)
  }
}

@Composable
internal fun RemoteNode(node: UiNode, onAction: (String, String?) -> Unit) {
  val colors = MaterialTheme.colorScheme
  when (node) {
    is UiNode.Column -> RemoteNodes(node.children, node.spacing, onAction)
    is UiNode.Row ->
      Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(node.spacing.coerceIn(0, 48).dp)
      ) {
        node.children.forEach { RowChild(it, onAction) }
      }

    is UiNode.Weighted -> RemoteNode(node.child, onAction)
    is UiNode.Text ->
      Text(
        node.text,
        style = when (node.style) {
          TextStyle.Body -> MaterialTheme.typography.bodyMedium
          TextStyle.Title -> MaterialTheme.typography.titleMedium
          TextStyle.Headline -> MaterialTheme.typography.headlineSmall
          TextStyle.Label -> MaterialTheme.typography.labelLarge
          TextStyle.Caption -> MaterialTheme.typography.bodySmall
        },
        color = node.tone.toTone().color()
      )

    is UiNode.Header -> DetailHeader(node.title, remoteIcon(node.icon))
    is UiNode.Card ->
      SectionCard(
        title = node.title,
        icon = remoteIcon(node.icon),
        onTitleClick = node.action?.let { a -> { onAction(a, null) } }
      ) {
        node.children.forEach { RemoteNode(it, onAction) }
      }

    is UiNode.Button -> {
      val content: @Composable RowScope.() -> Unit = {
        remoteIcon(node.icon)?.let {
          Ic(it, size = 18.dp)
          Spacer(Modifier.width(8.dp))
        }
        Text(node.label)
      }
      val click = { onAction(node.action, null) }
      when (node.style) {
        ButtonStyle.Filled -> Button(onClick = click, enabled = node.enabled, content = content)
        ButtonStyle.Tonal ->
          FilledTonalButton(onClick = click, enabled = node.enabled, content = content)

        ButtonStyle.Outlined ->
          OutlinedButton(onClick = click, enabled = node.enabled, content = content)

        ButtonStyle.Text -> TextButton(onClick = click, enabled = node.enabled, content = content)
      }
    }

    is UiNode.IconButton ->
      IconButton(onClick = { onAction(node.action, null) }) {
        Icon(remoteIcon(node.icon) ?: Icons.Outlined.Extension, contentDescription = node.description)
      }

    is UiNode.TextField -> key(node.key) { RemoteTextField(node, onAction) }
    is UiNode.Checkbox ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(colors = pastelCheckboxColors(), checked = node.checked, onCheckedChange = { onAction(node.action, it.toString()) })
        Text(node.label)
      }

    is UiNode.Switch ->
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(node.label, modifier = Modifier.weight(1f))
        Switch(checked = node.checked, onCheckedChange = { onAction(node.action, it.toString()) })
      }

    is UiNode.Chips ->
      ChoiceChips(
        options = node.options.map { it.id },
        selected = node.selected ?: "",
        label = { id -> node.options.firstOrNull { it.id == id }?.label ?: id },
        onSelect = { onAction(node.action, it) }
      )

    is UiNode.Stats ->
      StatTiles(
        node.items.map {
          Stat(it.label, it.value, it.sub, it.tone.toTone(), remoteIcon(it.icon))
        }
      )

    is UiNode.Progress -> ProgressLine(node.fraction)
    is UiNode.ListItem ->
      Row(
        Modifier.fillMaxWidth()
          .then(
            node.action?.let { a ->
              Modifier.interactive(bleed = 6.dp) { onAction(a, null) }
            } ?: Modifier
          ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        remoteIcon(node.icon)?.let { Ic(it, tint = colors.primary) }
        Column(Modifier.weight(1f)) {
          Text(node.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
          node.subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2)
          }
        }
        node.trailing?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
      }

    UiNode.Divider -> HorizontalDivider(color = colors.outlineVariant)
    is UiNode.Spacer -> Spacer(Modifier.height(node.height.coerceIn(0, 96).dp))
    is UiNode.Empty -> EmptyHint(node.text)

    is UiNode.Image ->
      Cover(node.url, Icons.Outlined.Extension, Modifier.fillMaxWidth().height(node.height.coerceIn(24, 480).dp))

    is UiNode.Markdown -> MarkdownText(node.text)
  }
}

@Composable
private fun RemoteTextField(node: UiNode.TextField, onAction: (String, String?) -> Unit) {
  var text by remember { mutableStateOf(node.value) }
  var sent by remember { mutableStateOf(node.value) }
  LaunchedEffect(node.value) {
    if (node.value != sent) {
      sent = node.value
      text = node.value
    }
  }
  LaunchedEffect(text) {
    if (text != sent) {
      delay(300)
      sent = text
      onAction(node.action, text)
    }
  }
  BerillTextField(
    value = text,
    onValueChange = { text = it.take(5000) },
    label = { Text(node.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    placeholder = node.placeholder?.let { p -> { Text(p) } },
    singleLine = node.singleLine,
    keyboardOptions = KeyboardOptions(
      keyboardType = when (node.keyboard) {
        Keyboard.Text -> KeyboardType.Text
        Keyboard.Number -> KeyboardType.Number
        Keyboard.Decimal -> KeyboardType.Decimal
      }
    ),
    modifier = Modifier.fillMaxWidth(),
    onSubmit = node.submitAction?.let { submit ->
      {
        if (text != sent) {
          sent = text
          onAction(node.action, text)
        }
        onAction(submit, text)
      }
    }
  )
}
