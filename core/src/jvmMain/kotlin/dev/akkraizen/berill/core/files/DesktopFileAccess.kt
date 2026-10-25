package dev.akkraizen.berill.core.files

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.unit.dp
import dev.akkraizen.berill.api.FileAccess
import dev.akkraizen.berill.api.PickedFile
import dev.akkraizen.berill.api.i18n.Tr
import java.awt.Color
import java.awt.RenderingHints
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageOutputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp")

internal class DesktopFileAccess : FileAccess {
  override val supportsImageDrop: Boolean get() = true

  @OptIn(ExperimentalComposeUiApi::class)
  @Composable
  override fun imageDropTarget(enabled: Boolean, onDrop: (List<PickedFile>) -> Unit): Modifier {
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onDrop)
    val accepting by rememberUpdatedState(enabled)
    var hovering by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    val target = remember {
      object : DragAndDropTarget {
        override fun onEntered(event: DragAndDropEvent) { hovering = true }
        override fun onExited(event: DragAndDropEvent) { hovering = false }
        override fun onEnded(event: DragAndDropEvent) { hovering = false }
        override fun onDrop(event: DragAndDropEvent): Boolean {
          hovering = false
          if (!accepting || importing) return false
          val sources = runCatching { clipboardImageSources(event.awtTransferable) }.getOrDefault(emptyList())
          if (sources.isEmpty()) return false
          importing = true
          scope.launch {
            try {
              val files = sources.mapNotNull { convertImage(it, 1600) }
              if (files.isNotEmpty()) callback(files)
            } finally { importing = false }
          }
          return true
        }
      }
    }
    val border = if (hovering) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), MaterialTheme.shapes.medium)
    else Modifier
    return Modifier.then(border).dragAndDropTarget(
      shouldStartDragAndDrop = { accepting && !importing && it.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) },
      target = target
    )
  }

  override suspend fun pickImage(maxSide: Int): PickedFile? {
    require(maxSide > 0)
    return convertImage(DesktopImagePicker.pick(false).firstOrNull() ?: return null, maxSide)
  }

  override suspend fun pickImages(maxSide: Int): List<PickedFile> {
    require(maxSide > 0)
    return DesktopImagePicker.pick(true).mapNotNull { convertImage(it, maxSide) }
  }

  private suspend fun convertImage(source: DesktopImageSource, maxSide: Int): PickedFile? =
    withContext(Dispatchers.IO) {
      val jpeg = runCatching {
        when (source) {
          is DesktopImageSource.FromFile -> downscaleToJpeg(source.file.readBytes(), maxSide)
          is DesktopImageSource.FromClipboard -> downscaleToJpeg(source.image, maxSide)
        }
      }.getOrNull() ?: return@withContext null
      val name = when (source) {
        is DesktopImageSource.FromFile -> source.file.nameWithoutExtension
        is DesktopImageSource.FromClipboard -> "clipboard"
      }
      PickedFile("$name.jpg", jpeg, "image/jpeg")
    }

  override suspend fun pickFiles(extensions: List<String>, maxBytes: Int): List<PickedFile> =
    withContext(Dispatchers.IO) {
      val allowed = extensions.map { it.lowercase() }.toSet()
      openDesktopFiles(Tr("desktopFileAccess.dialog.2")(), allowed.toList()).mapNotNull { file ->
        if (!file.isFile || file.length() > maxBytes ||
          (allowed.isNotEmpty() && file.extension.lowercase() !in allowed)
        ) null else PickedFile(file.name, file.readBytes(), "application/octet-stream")
      }
    }

  override suspend fun pickFile(extensions: List<String>, maxBytes: Int): PickedFile? =
    withContext(Dispatchers.IO) {
      val allowed = extensions.map { it.lowercase() }.toSet()
      val file = openDesktopFile(Tr("desktopFileAccess.dialog.2")(), allowed.toList()) ?: return@withContext null
      if (allowed.isNotEmpty() && file.extension.lowercase() !in allowed) return@withContext null
      if (!file.isFile || file.length() > maxBytes) return@withContext null
      PickedFile(file.name, file.readBytes(), "application/octet-stream")
    }
}

internal fun downscaleToJpeg(bytes: ByteArray, maxSide: Int, quality: Float = 0.85f): ByteArray? {
  val source = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
  return downscaleToJpeg(source, maxSide, quality)
}

internal fun downscaleToJpeg(source: BufferedImage, maxSide: Int, quality: Float = 0.85f): ByteArray {
  require(maxSide > 0)
  val scale = minOf(1.0, maxSide.toDouble() / maxOf(source.width, source.height))
  val width = (source.width * scale).roundToInt().coerceAtLeast(1)
  val height = (source.height * scale).roundToInt().coerceAtLeast(1)
  val target = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
  val g = target.createGraphics()
  g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
  g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
  g.color = Color.WHITE
  g.fillRect(0, 0, width, height)
  g.drawImage(source, 0, 0, width, height, null)
  g.dispose()

  val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
  val out = ByteArrayOutputStream()
  MemoryCacheImageOutputStream(out).use { stream ->
    writer.output = stream
    val params = writer.defaultWriteParam.apply {
      compressionMode = ImageWriteParam.MODE_EXPLICIT
      compressionQuality = quality
    }
    writer.write(null, IIOImage(target, null, null), params)
    writer.dispose()
  }
  return out.toByteArray()
}
