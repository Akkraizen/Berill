package dev.akkraizen.berill.plugins.watching

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.fields.CommitTextField

@Composable
internal fun WatchingSettings(ctx: PluginContext) {
  val key by
  remember(ctx) {
    kinopoiskKey(ctx)
    ctx.secrets.observe(KINOPOISK_KEY)
  }
    .collectAsState(ctx.secrets.get(KINOPOISK_KEY))
  SectionCard(WatchingText.kinopoisk(), Icons.Outlined.Key) {
    Text(
      WatchingText.kinopoiskHint(),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    CommitTextField(key ?: "", { ctx.secrets.set(KINOPOISK_KEY, it.trim()) }, WatchingText.apiKey())
    Text(
      WatchingText.keyLocalHint(),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
  SectionCard("Shikimori", mediaIcon(ANIME)) {
    Text(
      WatchingText.shikimoriHint(),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
  Text(
    WatchingText.keysStorageHint(),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
  )
}
