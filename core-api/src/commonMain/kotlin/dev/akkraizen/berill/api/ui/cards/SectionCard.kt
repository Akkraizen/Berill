package dev.akkraizen.berill.api.ui.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.rotate
import dev.akkraizen.berill.api.ui.UiText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.animation.BerillMotion
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.color
import dev.akkraizen.berill.api.ui.interaction.interactive

@Composable
fun SectionCard(
  title: String? = null,
  icon: ImageVector? = null,
  modifier: Modifier = Modifier,
  onTitleClick: (() -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
  collapseKey: String? = null,
  content: @Composable ColumnScope.() -> Unit
) {
  var expanded by rememberExpanded(collapseKey)
  val compact = dev.akkraizen.berill.api.ui.layout.LocalCompactLayout.current
  Surface(
    modifier.fillMaxWidth().let {
      if (onTitleClick != null)
        it.interactive(MaterialTheme.shapes.extraLarge, onClick = onTitleClick)
      else
        it
    },
    shape = MaterialTheme.shapes.extraLarge,
    color = MaterialTheme.colorScheme.surfaceContainerHigh
  ) {
    Column(
      Modifier.animateContentSize(BerillMotion.spatial()).padding(if (compact) 12.dp else 18.dp),
      verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)
    ) {
      if (title != null || trailing != null) {
        Row(
          Modifier.let { if (collapseKey != null && onTitleClick == null) it.interactive(bleed = 6.dp) { expanded = !expanded } else it },
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          if (icon != null) Ic(icon, tint = MaterialTheme.colorScheme.primary)
          Text(
            title ?: "",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
          )
          trailing?.invoke()
          if (collapseKey != null) CollapseToggle(expanded) { expanded = !expanded }
        }
      }
      if (collapseKey == null) content()
      else AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
      }
    }
  }
}

private object Collapsed {
  val state = mutableStateMapOf<String, Boolean>()
}

@Composable
fun rememberExpanded(key: String?, default: Boolean = true): MutableState<Boolean> {
  val local = rememberSaveable(key) { mutableStateOf(default) }
  if (key == null) return local
  return remember(key) {
    object : MutableState<Boolean> {
      override var value: Boolean
        get() = Collapsed.state[key] ?: default
        set(v) { Collapsed.state[key] = v }
      override fun component1() = value
      override fun component2(): (Boolean) -> Unit = { value = it }
    }
  }
}

@Composable
fun CollapseToggle(expanded: Boolean, onToggle: () -> Unit) {
  val rotation by animateFloatAsState(if (expanded) 0f else -90f, label = "collapse")
  IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
    Icon(
      Icons.Outlined.ExpandMore,
      contentDescription = if (expanded) UiText.collapse() else UiText.expand(),
      modifier = Modifier.rotate(rotation),
      tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}
