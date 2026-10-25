package dev.akkraizen.berill.api.ui.layout

import androidx.compose.ui.graphics.vector.ImageVector
import dev.akkraizen.berill.api.ui.UiText

class KanbanColumn<T>(
  val key: String,
  val title: String,
  val items: List<T>,
  val emptyText: String = UiText.empty(),
  val icon: ImageVector? = null
)
