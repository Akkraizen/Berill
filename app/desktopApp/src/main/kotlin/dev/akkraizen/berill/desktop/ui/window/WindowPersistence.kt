package dev.akkraizen.berill.desktop.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Toolkit
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext

internal data class SavedWindow(val bounds: Rectangle, val maximized: Boolean)

internal fun fitWindow(bounds: Rectangle, screens: List<Rectangle>): Rectangle {
  val screen = screens.maxByOrNull { area ->
    val intersection = area.intersection(bounds)
    if (intersection.isEmpty) 0L else intersection.width.toLong() * intersection.height
  } ?: return bounds
  val width = bounds.width.coerceAtLeast(480).coerceAtMost(screen.width)
  val height = bounds.height.coerceAtLeast(360).coerceAtMost(screen.height)
  return Rectangle(
    bounds.x.coerceIn(screen.x, screen.x + screen.width - width),
    bounds.y.coerceIn(screen.y, screen.y + screen.height - height), width, height
  )
}

internal class WindowPersistence(
  private val file: File = File(System.getProperty("user.home"), ".berill/window.properties")
) {
  fun read(): SavedWindow? = runCatching {
    val properties = Properties().apply { file.inputStream().use { load(it) } }
    val bounds = Rectangle(
      properties.getProperty("x").toInt(), properties.getProperty("y").toInt(),
      properties.getProperty("width").toInt(), properties.getProperty("height").toInt()
    )
    require(bounds.width in 1..100000 && bounds.height in 1..100000)

    SavedWindow(fitWindow(bounds, screenWorkAreas()), properties.getProperty("maximized").toBoolean())
  }.getOrNull()

  @Synchronized
  fun write(value: SavedWindow) {
    runCatching {
      val target = file.toPath()
      Files.createDirectories(target.parent)
      val temporary = Files.createTempFile(target.parent, "window-", ".tmp")
      try {
        Files.writeString(temporary, value.encode())
        try {
          Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
          Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        }
      } finally {
        Files.deleteIfExists(temporary)
      }
    }
  }
}

internal class PersistentWindowState(
  val state: WindowState,
  private val storage: WindowPersistence,
  initial: SavedWindow?
) {
  private var saved = initial

  fun capture(): SavedWindow? {
    if (state.placement == WindowPlacement.Floating && !state.isMinimized && state.position.isSpecified) {
      state.floatingBounds()?.let { saved = SavedWindow(it, maximized = false) }
    } else if (state.placement == WindowPlacement.Maximized) {
      saved = saved?.copy(maximized = true)
    }
    return saved
  }

  fun save() {
    capture()?.let(storage::write)
  }
}

@OptIn(FlowPreview::class)
@Composable
internal fun rememberPersistentWindowState(): PersistentWindowState {
  val storage = remember { WindowPersistence() }
  val initial = remember { storage.read() }
  val state = rememberWindowState(
    placement = if (initial?.maximized == true) WindowPlacement.Maximized else WindowPlacement.Floating,
    position = initial?.bounds?.let { WindowPosition(it.x.dp, it.y.dp) } ?: WindowPosition.PlatformDefault,
    width = (initial?.bounds?.width ?: 1100).dp,
    height = (initial?.bounds?.height ?: 800).dp
  )
  val persistent = remember(state) { PersistentWindowState(state, storage, initial) }
  LaunchedEffect(persistent) {
    snapshotFlow { persistent.capture() }.debounce(400).collect { value ->
      if (value != null) withContext(Dispatchers.IO) { storage.write(value) }
    }
  }
  DisposableEffect(persistent) {
    onDispose { persistent.save() }
  }
  return persistent
}

private fun screenWorkAreas(): List<Rectangle> =
  GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { device ->
    val config = device.defaultConfiguration
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
    Rectangle(config.bounds).apply {
      x += insets.left
      y += insets.top
      width -= insets.left + insets.right
      height -= insets.top + insets.bottom
    }
  }.filter { it.width > 0 && it.height > 0 }

private fun WindowState.floatingBounds(): Rectangle? {
  val width = size.width.value
  val height = size.height.value
  if (!width.isFinite() || !height.isFinite() || width < 480 || height < 360) return null
  return Rectangle(position.x.value.roundToInt(), position.y.value.roundToInt(), width.roundToInt(), height.roundToInt())
}

private fun SavedWindow.encode(): String = listOf(
  "x=${bounds.x}",
  "y=${bounds.y}",
  "width=${bounds.width}",
  "height=${bounds.height}",
  "maximized=$maximized"
).joinToString(separator = "\n", postfix = "\n")
