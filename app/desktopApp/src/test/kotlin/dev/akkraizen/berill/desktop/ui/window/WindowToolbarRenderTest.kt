package dev.akkraizen.berill.desktop.ui.window

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.core.connections.HeaderSearchButton
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class WindowToolbarRenderTest {
  private fun render(width: Int, light: Boolean = false) {
    var search: Rect? = null
    var actions: Rect? = null
    var caption: Rect? = null
    var compact: Boolean? = null
    var syncStatus: Rect? = null
    var title by mutableStateOf("Home")
    val scene = ImageComposeScene(width, 64) {
      MaterialTheme(colorScheme = if (light) lightColorScheme() else darkColorScheme()) {
        WindowToolbar(title, search = { small ->
          compact = small
          Box(Modifier.onGloballyPositioned { search = it.boundsInRoot() }) { HeaderSearchButton(small, "Ctrl+K") {} }
        }, actions = {
          Row(Modifier.onGloballyPositioned { actions = it.boundsInRoot() }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(Icons.Outlined.Notifications, Icons.Outlined.Today, Icons.Outlined.DeleteOutline).forEach { icon ->
              CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 36.dp) {
                IconButton({}, Modifier.size(36.dp)) { Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
              }
            }
          }
        }, status = {
          CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 36.dp) {
            IconButton({}, Modifier.size(36.dp).onGloballyPositioned { syncStatus = it.boundsInRoot() }) {
              Icon(Icons.Outlined.CloudDone, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }, captionButtons = {
          Row(Modifier.onGloballyPositioned { caption = it.boundsInRoot() }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(Icons.Outlined.Remove, Icons.Outlined.CropSquare, Icons.Outlined.Close).forEach { icon ->
              Box(Modifier.size(44.dp, 32.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
          }
        })
      }
    }
    try {
      scene.render(0).close(); scene.render(16_000_000).close()
      val image = scene.render(32_000_000)
      try {
        val data = assertNotNull(image.encodeToData())
        try { File(System.getProperty("berill.uiEvidenceDir"), "toolbar-$width${if (light) "-light" else ""}.png").apply { parentFile.mkdirs(); writeBytes(data.bytes) } } finally { data.close() }
      } finally { image.close() }
      assertEquals(width < 840, compact)
      val s = assertNotNull(search); val a = assertNotNull(actions); val c = assertNotNull(caption)
      assertTrue(assertNotNull(syncStatus).right <= s.left, "Sync status must stay clear of the centered search")
      assertTrue(s.right <= a.left, "Search must not overlap quick actions")
      assertTrue(a.right < c.left, "Quick actions must remain separate from window controls")
      assertTrue(c.right <= width)
      if (width >= 840) assertEquals(width / 2f, s.center.x, 1f)
      val statusX = assertNotNull(syncStatus).left
      title = "A very long page title that should never move the sync indicator"
      scene.render(48_000_000).close(); scene.render(80_000_000).close()
      assertEquals(statusX, assertNotNull(syncStatus).left, 0.5f)
    } finally { scene.close() }
  }
  @Test fun centeredToolbar() = render(1010)
  @Test fun centeredToolbarAtBreakpoint() = render(840)
  @Test fun compactToolbar() = render(640)
  @Test fun minimumWindowToolbar() = render(480)
  @Test fun lightToolbar() = render(1010, true)
}
