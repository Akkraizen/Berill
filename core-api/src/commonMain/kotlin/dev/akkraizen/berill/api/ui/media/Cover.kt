package dev.akkraizen.berill.api.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.akkraizen.berill.api.ui.*
import dev.akkraizen.berill.api.ui.display.Ic

@Composable
fun Cover(
  url: String?,
  fallback: ImageVector,
  modifier: Modifier = Modifier,
  shape: Shape = MaterialTheme.shapes.medium,
  bytes: ByteArray? = null,
  contentScale: ContentScale = ContentScale.Crop
) {
  Box(
    modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
    contentAlignment = Alignment.Center
  ) {
    Ic(fallback, size = 32.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    if (bytes != null) {
      AsyncImage(
        model = bytes,
        contentDescription = null,
        contentScale = contentScale,
        modifier = Modifier.matchParentSize()
      )
    } else if (url != null && LocalImagePolicy.current(url)) {
      val context = LocalPlatformContext.current
      val request =
        remember(url) { ImageRequest.Builder(context).data(url).crossfade(true).build() }
      AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = contentScale,
        modifier = Modifier.matchParentSize()
      )
    }
  }
}
