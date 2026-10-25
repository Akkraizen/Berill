package dev.akkraizen.berill.api.ui.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.interaction.mouseAutoScroll

@Composable
fun AutoScrollLazyColumn(
  modifier: Modifier = Modifier,
  state: LazyListState = rememberLazyListState(),
  contentPadding: PaddingValues = PaddingValues(0.dp),
  verticalArrangement: Arrangement.Vertical = Arrangement.Top,
  content: LazyListScope.() -> Unit
) {
  LazyColumn(
    modifier = modifier.mouseAutoScroll(state),
    state = state,
    contentPadding = contentPadding,
    verticalArrangement = verticalArrangement,
    content = content
  )
}
