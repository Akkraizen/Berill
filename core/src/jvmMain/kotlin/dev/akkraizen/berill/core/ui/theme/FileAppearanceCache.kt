package dev.akkraizen.berill.core.ui.theme

import dev.akkraizen.berill.core.desktopDataDirectory
import java.io.File

class FileAppearanceCache(private val file: File) : AppearanceCache {
  override fun read(): Appearance? = runCatching { decodeAppearance(file.takeIf { it.isFile }?.readText()) }.getOrNull()

  override fun write(appearance: Appearance) {
    val text = appearance.encode()
    if (runCatching { file.readText() }.getOrNull() == text) return
    file.parentFile?.mkdirs()
    file.writeText(text)
  }
}

fun desktopAppearanceCache(): FileAppearanceCache =
  FileAppearanceCache(File(desktopDataDirectory(File(System.getProperty("user.home"))), "appearance"))

fun cachedDesktopAppearance(): Appearance = desktopAppearanceCache().read() ?: Appearance()
