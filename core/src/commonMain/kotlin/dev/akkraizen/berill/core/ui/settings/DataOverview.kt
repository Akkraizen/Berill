package dev.akkraizen.berill.core.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.core.BerillCore
import dev.akkraizen.berill.core.nav.Tab
import dev.akkraizen.berill.core.ui.CoreText
import dev.akkraizen.berill.protocol.Collections

@Composable
internal fun DataOverview(core: BerillCore) {
  val counts by core.store.observeCounts().collectAsState(emptyMap())

  fun owner(name: String) =
    when (name) {
      Collections.NOTES -> Tab.Notes.title to Tab.Notes.icon
      Collections.SETTINGS -> Tab.Settings.title to Tab.Settings.icon
      else -> core.collections.owner(name)?.let { it.title to it.icon }
    }
  if (counts.isEmpty())
    Text(CoreText.databaseEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
  counts
    .toList()
    .sortedBy { it.first }
    .forEach { (name, total) ->
      val owner = owner(name)
      Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Ic(
          owner?.second ?: Icons.Outlined.HelpOutline,
          size = 18.dp,
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(owner?.first ?: CoreText.noPlugin(), modifier = Modifier.weight(1f))
        Text(
          name,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 12.dp)
        )
        Text("$total", color = MaterialTheme.colorScheme.primary)
      }
    }
}
