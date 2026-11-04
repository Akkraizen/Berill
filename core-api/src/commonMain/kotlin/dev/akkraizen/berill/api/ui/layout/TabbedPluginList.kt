package dev.akkraizen.berill.api.ui.layout

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.UiText
import dev.akkraizen.berill.api.ui.animation.BerillMotion
import dev.akkraizen.berill.api.ui.interaction.SwipeDirection
import dev.akkraizen.berill.api.ui.interaction.swipeArea
import dev.akkraizen.berill.api.ui.selection.BerillTabs

const val TAB_RECORDS = 0
const val TAB_STATISTICS = 1

@Composable
fun TabbedPluginList(
  tabs: List<String> = listOf(UiText.tabRecords(), UiText.tabStatistics()),
  modifier: Modifier = Modifier,
  actions: (@Composable RowScope.() -> Unit)? = null,
  content: LazyListScope.(tab: Int) -> Unit
) {
  var selected by rememberSaveable { mutableIntStateOf(0) }
  val saved = rememberSaveableStateHolder()
  val current = selected.coerceIn(0, tabs.lastIndex)
  Column(modifier.fillMaxSize()) {
    TabsToolbar(tabs, current, { selected = it }, actions)
    AnimatedContent(
      targetState = current,
      modifier = Modifier.weight(1f).swipeArea(
        canSwipe = { direction -> if (direction == SwipeDirection.NEXT) current < tabs.lastIndex else current > 0 },
        feedback = false
      ) { direction -> selected = (current + if (direction == SwipeDirection.NEXT) 1 else -1).coerceIn(0, tabs.lastIndex) },
      transitionSpec = { BerillMotion.slide(forward = targetState > initialState) },
      label = "tabs"
    ) { tab ->
      saved.SaveableStateProvider(tab) {
        PluginList { content(tab) }
      }
    }
  }
}

@Composable
fun TabsToolbar(
  tabs: List<String>,
  selected: Int,
  onSelect: (Int) -> Unit,
  actions: (@Composable RowScope.() -> Unit)? = null
) {
  if (actions == null) {
    BerillTabs(tabs, selected, onSelect)
    return
  }
  BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
    if (maxWidth >= 560.dp)
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BerillTabs(tabs, selected, onSelect, Modifier.weight(1f), PaddingValues(0.dp))
        actions()
      }
    else
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BerillTabs(tabs, selected, onSelect, contentPadding = PaddingValues(0.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
          actions()
        }
      }
  }
}

@Composable
fun PageActions(content: @Composable RowScope.() -> Unit) {
  Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    verticalAlignment = Alignment.CenterVertically,
    content = content
  )
}
