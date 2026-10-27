package dev.akkraizen.berill.core

import dev.akkraizen.berill.core.files.DesktopImageSource
import dev.akkraizen.berill.core.files.clipboardImageSources
import dev.akkraizen.berill.core.files.downscaleToJpeg
import dev.akkraizen.berill.core.files.isImagePasteKey
import java.awt.Canvas
import java.awt.Color
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImagePickerTest {
  private fun transferable(flavor: DataFlavor, value: Any) = object : Transferable {
    override fun getTransferDataFlavors() = arrayOf(flavor)
    override fun isDataFlavorSupported(candidate: DataFlavor) = candidate == flavor
    override fun getTransferData(candidate: DataFlavor) = value
  }

  @Test
  fun recognizesImagePasteOnKeyPressWithControlAndIgnoresOtherShortcuts() {
    val component = Canvas()
    fun event(id: Int, modifiers: Int, code: Int) =
      KeyEvent(component, id, 0, modifiers, code, KeyEvent.CHAR_UNDEFINED)
    val control = InputEvent.CTRL_DOWN_MASK
    val alt = InputEvent.ALT_DOWN_MASK
    val press = KeyEvent.KEY_PRESSED
    val release = KeyEvent.KEY_RELEASED
    val v = KeyEvent.VK_V
    assertTrue(isImagePasteKey(event(press, control, v), mac = false))
    assertTrue(!isImagePasteKey(event(release, control, v), mac = false))
    assertTrue(!isImagePasteKey(event(press, 0, v), mac = false))
    assertTrue(!isImagePasteKey(event(press, control or alt, v), mac = false))
    assertTrue(!isImagePasteKey(event(press, control, KeyEvent.VK_C), mac = false))
  }

  @Test
  fun recognizesCommandPasteOnMac() {
    val component = Canvas()
    fun event(id: Int, modifiers: Int, code: Int) =
      KeyEvent(component, id, 0, modifiers, code, KeyEvent.CHAR_UNDEFINED)
    val press = KeyEvent.KEY_PRESSED
    val v = KeyEvent.VK_V
    assertTrue(isImagePasteKey(event(press, InputEvent.META_DOWN_MASK, v), mac = true))
    assertTrue(!isImagePasteKey(event(press, InputEvent.CTRL_DOWN_MASK, v), mac = true))
    assertTrue(!isImagePasteKey(event(press, InputEvent.META_DOWN_MASK, v), mac = false))
  }

  @Test
  fun acceptsClipboardImagesAndPreservesTheirPixels() {
    val original = BufferedImage(12, 8, BufferedImage.TYPE_INT_ARGB)
    original.setRGB(2, 3, Color.RED.rgb)
    val result = assertIs<DesktopImageSource.FromClipboard>(
      clipboardImageSources(transferable(DataFlavor.imageFlavor, original)).single()
    )
    assertEquals(12, result.image.width)
    assertEquals(Color.RED.rgb, result.image.getRGB(2, 3))
  }

  @Test
  fun acceptsCopiedImageFilesAndIgnoresText() {
    val file = Files.createTempFile("berill-image-", ".PNG").toFile()
    try {
      val source = clipboardImageSources(transferable(DataFlavor.javaFileListFlavor, listOf(file)))
      assertEquals(file, assertIs<DesktopImageSource.FromFile>(source.single()).file)
    } finally {
      file.delete()
    }
    assertTrue(clipboardImageSources(StringSelection("text")).isEmpty())
    assertTrue(clipboardImageSources(null).isEmpty())
  }

  @Test
  fun acceptsAllCopiedImagesInOrderAndSkipsDuplicatesAndOtherFiles() {
    val directory = Files.createTempDirectory("berill-clipboard-").toFile()
    try {
      val first = directory.resolve("first.PNG").apply { writeBytes(byteArrayOf()) }
      val second = directory.resolve("second.jpg").apply { writeBytes(byteArrayOf()) }
      val text = directory.resolve("notes.txt").apply { writeText("notes") }
      val missing = directory.resolve("missing.png")
      val files = listOf(first, text, second, first, missing, directory)
      val sources = clipboardImageSources(transferable(DataFlavor.javaFileListFlavor, files))
      assertEquals(listOf(first, second), sources.map { assertIs<DesktopImageSource.FromFile>(it).file })
    } finally {
      directory.listFiles()?.forEach { it.delete() }
      directory.delete()
    }
  }

  @Test
  fun prefersCopiedFilesOverSingleImageFlavor() {
    val file = Files.createTempFile("berill-clipboard-", ".png").toFile()
    try {
      val contents = object : Transferable {
        override fun getTransferDataFlavors() = arrayOf(DataFlavor.javaFileListFlavor, DataFlavor.imageFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor) = flavor in transferDataFlavors
        override fun getTransferData(flavor: DataFlavor): Any =
          if (flavor == DataFlavor.javaFileListFlavor) listOf(file) else BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
      }
      assertEquals(file, assertIs<DesktopImageSource.FromFile>(clipboardImageSources(contents).single()).file)
    } finally {
      file.delete()
    }
  }

  @Test
  fun scalesImagesAndCompositesTransparencyOnWhite() {
    val original = BufferedImage(400, 200, BufferedImage.TYPE_INT_ARGB)
    val jpeg = ImageIO.read(ByteArrayInputStream(downscaleToJpeg(original, 100)))
    assertEquals(100, jpeg.width)
    assertEquals(50, jpeg.height)
    val pixel = Color(jpeg.getRGB(20, 20))
    assertTrue(pixel.red >= 250 && pixel.green >= 250 && pixel.blue >= 250)
    val small = ImageIO.read(ByteArrayInputStream(downscaleToJpeg(original, 800)))
    assertEquals(400, small.width)
  }

  @Test
  fun rejectsUnreadableImagesAndInvalidSize() {
    assertNull(downscaleToJpeg(byteArrayOf(1, 2, 3), 100))
    assertFailsWith<IllegalArgumentException> {
      downscaleToJpeg(BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), 0)
    }
  }
}
