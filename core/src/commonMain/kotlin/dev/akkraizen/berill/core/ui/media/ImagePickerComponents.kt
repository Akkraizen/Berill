package dev.akkraizen.berill.core.ui.media

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.ui.animation.BerillMotion
import dev.akkraizen.berill.api.ui.interaction.interactive

@Composable
internal fun ImagePickerHeader(title: String, onDismiss: () -> Unit) {
  Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
      Icon(
        Icons.Outlined.PhotoLibrary,
        null,
        Modifier.padding(14.dp).size(24.dp),
        tint = MaterialTheme.colorScheme.onPrimaryContainer
      )
    }
    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, I18n.text("ui.close")) }
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ImagePickerActions(
  enabled: Boolean,
  onChoose: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shape = MaterialTheme.shapes.extraLarge
  ) {
    Row(
      Modifier.padding(12.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Spacer(Modifier.weight(1f))
      TextButton(onClick = onDismiss) { Text(I18n.text("ui.cancel")) }
      Button(
        onClick = onChoose,
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.heightIn(min = 48.dp)
      ) {
        Icon(Icons.Outlined.Check, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(I18n.text("imagePicker.use"))
      }
    }
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ImagePickerPlaceholder(message: String, loading: Boolean = false, modifier: Modifier = Modifier) {
  Column(
    modifier.padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    if (loading) LoadingIndicator(Modifier.size(56.dp))
    else Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
    Text(
      message,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center
    )
  }
}

@Composable
internal fun ImagePickerTile(
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  val colors = MaterialTheme.colorScheme
  val container by animateColorAsState(
    if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
    BerillMotion.effects(),
    label = "photo-container"
  )
  val scale by animateFloatAsState(if (selected) 0.88f else 1f, BerillMotion.spatial(), label = "photo-selection")
  val shape = MaterialTheme.shapes.large
  Surface(
    modifier.semantics { this.selected = selected }.interactive(shape, onClick = onClick),
    shape = shape,
    color = container,
    border = if (selected) BorderStroke(2.dp, colors.primary) else null
  ) {
    Box {
      Box(Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale }.clip(shape)) { content() }
      if (selected) {
        Surface(
          Modifier.align(Alignment.TopEnd).padding(8.dp),
          shape = MaterialTheme.shapes.extraLarge,
          color = colors.primary,
          contentColor = colors.onPrimary
        ) {
          Icon(Icons.Outlined.Check, I18n.text("imagePicker.selected"), Modifier.padding(4.dp).size(18.dp))
        }
      }
    }
  }
}
