package dev.akkraizen.berill.api.ui.fields

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.akkraizen.berill.api.ui.*

@Composable
fun MonthSwitcher(month: YearMonth, onChange: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    IconButton(onClick = { onChange(month + -1) }) {
      Icon(BerillIcons.Prev, contentDescription = UiText.previousMonth())
    }
    TextButton(onClick = { onChange(YearMonth.current()) }) {
      Text(
        if (month == YearMonth.current()) UiText.thisMonth() else month.title,
        style = MaterialTheme.typography.titleLarge
      )
    }
    IconButton(onClick = { onChange(month + 1) }) {
      Icon(BerillIcons.Next, contentDescription = UiText.nextMonth())
    }
  }
}
