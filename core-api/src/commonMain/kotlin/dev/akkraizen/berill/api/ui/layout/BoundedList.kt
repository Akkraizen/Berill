package dev.akkraizen.berill.api.ui.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A nested list grows to its content, then scrolls without pushing adjacent controls away. */
@Composable
fun BoundedList(modifier: Modifier = Modifier, maxHeight: Dp = 280.dp, spacing: Dp = 10.dp, content: @Composable ColumnScope.() -> Unit) {
  Column(
    modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(spacing),
    content = content
  )
}
