package dev.akkraizen.berill.core

import dev.akkraizen.berill.core.ui.theme.Appearance
import dev.akkraizen.berill.core.ui.theme.FileAppearanceCache
import dev.akkraizen.berill.core.ui.theme.ThemeMode
import dev.akkraizen.berill.core.ui.theme.decodeAppearance
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppearanceCacheTest {
  @Test
  fun fileCacheRoundTripsAndIgnoresGarbage() {
    val dir = Files.createTempDirectory("appearance").toFile()
    val cache = FileAppearanceCache(dir.resolve("nested/appearance"))
    assertNull(cache.read())
    val appearance = Appearance(ThemeMode.Light, "#3366FF", "en")
    cache.write(appearance)
    assertEquals(appearance, cache.read())
    assertNull(decodeAppearance("Purple\nx\ny"))
    assertNull(decodeAppearance("Dark"))
    dir.deleteRecursively()
  }
}
