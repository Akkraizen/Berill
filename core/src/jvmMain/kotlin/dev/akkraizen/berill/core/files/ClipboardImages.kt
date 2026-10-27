package dev.akkraizen.berill.core.files

import androidx.compose.foundation.Image
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import java.io.File
import javax.swing.ImageIcon

internal sealed interface DesktopImageSource {
  data class FromFile(val file: File) : DesktopImageSource
  data class FromClipboard(val image: BufferedImage) : DesktopImageSource
}

internal fun clipboardImageSources(contents: Transferable?): List<DesktopImageSource> {
  if (contents == null) return emptyList()
  if (contents.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
    val files = contents.getTransferData(DataFlavor.javaFileListFlavor) as? List<*> ?: emptyList<Any>()
    val images = files.filterIsInstance<File>()
      .filter { it.isFile && it.extension.lowercase() in IMAGE_EXTENSIONS }.distinct()
      .map { DesktopImageSource.FromFile(it) }
    if (images.isNotEmpty()) return images
  }
  if (contents.isDataFlavorSupported(DataFlavor.imageFlavor)) {
    val image = contents.getTransferData(DataFlavor.imageFlavor) as? Image ?: return emptyList()
    val icon = ImageIcon(image)
    if (icon.iconWidth <= 0 || icon.iconHeight <= 0) return emptyList()
    val bitmap = BufferedImage(icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB)
    bitmap.createGraphics().let { graphics ->
      graphics.drawImage(image, 0, 0, null)
      graphics.dispose()
    }
    return listOf(DesktopImageSource.FromClipboard(bitmap))
  }
  return emptyList()
}

internal data class DesktopImagePreview(val source: DesktopImageSource, val bitmap: ImageBitmap)

internal val isMacOs: Boolean = System.getProperty("os.name").orEmpty().startsWith("Mac")

internal fun isImagePasteKey(event: KeyEvent, mac: Boolean = isMacOs): Boolean {
  if (event.id != KeyEvent.KEY_PRESSED || event.keyCode != KeyEvent.VK_V || event.isAltDown) return false
  return if (mac) event.isMetaDown && !event.isControlDown else event.isControlDown && !event.isMetaDown
}

internal val pasteShortcutLabel: String
  get() = if (isMacOs) "⌘V" else "Ctrl+V"
