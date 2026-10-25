package dev.akkraizen.berill.api.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.HttpException
import dev.akkraizen.berill.api.NetworkNotAllowedException
import dev.akkraizen.berill.api.ui.*
import dev.akkraizen.berill.api.ui.animation.AppearAnimated
import dev.akkraizen.berill.api.ui.cards.SectionCard
import dev.akkraizen.berill.api.ui.display.Ic
import dev.akkraizen.berill.api.ui.feedback.color
import dev.akkraizen.berill.api.ui.interaction.BerillTextField
import dev.akkraizen.berill.api.ui.interaction.interactive
import dev.akkraizen.berill.api.ui.media.Cover
import dev.akkraizen.berill.api.ui.state.rememberDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

fun networkErrorText(e: Throwable): String =
  when (e) {
    is NetworkNotAllowedException -> UiText.networkNotAllowed(e.host)()
    is HttpException ->
      when (e.status) {
        401,
        403 -> UiText.accessDenied(e.status)()

        429 -> UiText.tooManyRequests()
        else -> UiText.serverResponded(e.status)()
      }

    else -> UiText.noConnection(e.message ?: e::class.simpleName)()
  }

@Composable
fun OnlineSearchPanel(
  title: String,
  icon: ImageVector,
  placeholder: String,
  fallbackIcon: ImageVector,
  onClose: () -> Unit,
  search: suspend (String) -> List<SearchHit>,
  onPick: suspend (SearchHit) -> String?,
  errorText: (Throwable) -> String = ::networkErrorText,
  extra: (@Composable () -> Unit)? = null,
  draftKey: String? = null
) {
  val scope = rememberCoroutineScope()
  var query by (if (draftKey != null) rememberDraft(draftKey) else remember { mutableStateOf("") })
  var loading by remember { mutableStateOf(false) }
  var results by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
  var message by remember { mutableStateOf<String?>(null) }
  var picking by remember { mutableStateOf<String?>(null) }

  fun runSearch() {
    val q = query.trim()
    if (q.isEmpty() || loading) return
    loading = true
    message = null
    scope.launch {
      try {
        results = search(q)
        if (results.isEmpty()) message = UiText.nothingFound()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        results = emptyList()
        message = errorText(e)
      } finally {
        loading = false
      }
    }
  }

  AppearAnimated {
    SectionCard(
      title,
      icon,
      trailing = {
        IconButton(onClick = onClose) {
          Icon(BerillIcons.Close, contentDescription = UiText.closeSearch())
        }
      }
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BerillTextField(
          query,
          { query = it },
          placeholder = { Text(placeholder) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          modifier = Modifier.weight(1f),
          onSubmit = ::runSearch
        )
        Button(onClick = ::runSearch, enabled = !loading && query.isNotBlank()) {
          if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
          } else {
            Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
          }
          Text(UiText.find(), modifier = Modifier.padding(start = 8.dp))
        }
      }
      extra?.invoke()
      message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
      BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = (maxWidth / 300.dp).toInt().coerceIn(1, 3)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          results.chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              row.forEach { hit ->
                key(hit.ref) {
                  AppearAnimated(Modifier.weight(1f)) {
                    SearchHitCard(
                      hit = hit,
                      fallbackIcon = fallbackIcon,
                      busy = picking == hit.ref,
                      modifier = Modifier.fillMaxWidth(),
                      onClick = {
                        if (picking == null) {
                          picking = hit.ref
                          scope.launch {
                            try {
                              message = onPick(hit)
                            } catch (e: CancellationException) {
                              throw e
                            } catch (e: Throwable) {
                              message = errorText(e)
                            } finally {
                              picking = null
                            }
                          }
                        }
                      }
                    )
                  }
                }
              }
              repeat(cols - row.size) { Box(Modifier.weight(1f)) }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SearchHitCard(
  hit: SearchHit,
  fallbackIcon: ImageVector,
  busy: Boolean,
  modifier: Modifier,
  onClick: () -> Unit
) {
  Surface(
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
    modifier = modifier.interactive(MaterialTheme.shapes.large, onClick = onClick)
  ) {
    Row(
      Modifier.padding(10.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Cover(hit.cover, fallbackIcon, Modifier.size(48.dp, 72.dp))
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(hit.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        hit.subtitle?.let {
          Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        hit.meta?.let {
          Text(
            it,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
          )
        }
      }
      if (busy)
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
      else
        Ic(BerillIcons.Add, tint = MaterialTheme.colorScheme.primary)
    }
  }
}
