package dev.akkraizen.berill

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import dev.akkraizen.berill.api.ui.media.Cover
import dev.akkraizen.berill.api.ui.media.LocalImageFailure
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlinx.coroutines.*
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class RecipeImageRenderTest {
  @Test fun decodeFailureIsReportedInsteadOfSilentlyShowingPlaceholder() = runBlocking {
    var failed = false
    val scene = ImageComposeScene(128, 128) {
      MaterialTheme {
        CompositionLocalProvider(LocalImageFailure provides { failed = true }) {
          Cover(null, Icons.Outlined.RestaurantMenu, Modifier.fillMaxSize(), bytes = "invalid image".encodeToByteArray())
        }
      }
    }
    try {
      repeat(40) { delay(50); scene.render(System.nanoTime()).close() }
      assertTrue(failed, "Image decoding errors must reach the photo panel")
    } finally { scene.close() }
  }

  @Test fun recipeGalleryReadsItsStoredPhoto() = runBlocking {
    val driver = app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver(app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver.IN_MEMORY)
    dev.akkraizen.berill.core.db.BerillDatabase.Schema.create(driver)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val core = dev.akkraizen.berill.core.BerillCore(driver, listOf(dev.akkraizen.berill.plugins.recipes.RecipesPlugin), scope)
    val fixture = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB)
    for (x in 0..15) for (y in 0..15) fixture.setRGB(x, y, 0xffe02020.toInt())
    val bytes = ByteArrayOutputStream().also { ImageIO.write(fixture, "png", it) }.toByteArray()
    val plugin = dev.akkraizen.berill.plugins.recipes.RecipesPlugin
    val ctx = core.plugins.context(plugin)
    val hash = ctx.files.storeAttachment(bytes)!!
    ctx.data.collection(plugin.RECIPES, dev.akkraizen.berill.plugins.recipes.data.Recipe.serializer()).save("recipe", dev.akkraizen.berill.plugins.recipes.data.Recipe(title = "Photo recipe"))
    ctx.data.collection(plugin.IMAGES, dev.akkraizen.berill.plugins.recipes.data.RecipeImage.serializer()).save("photo", dev.akkraizen.berill.plugins.recipes.data.RecipeImage(recipe = "recipe", data = "blob:$hash"))
    val scene = ImageComposeScene(400, 600) { MaterialTheme { plugin.Screen(ctx) } }
    try {
      var rendered = false
      repeat(40) {
        delay(50)
        val image = scene.render(System.nanoTime())
        try {
          val encoded = image.encodeToData()!!
          try {
            val result = ImageIO.read(ByteArrayInputStream(encoded.bytes))
            var red = 0
            for (x in 0 until result.width step 4) for (y in 0 until result.height step 4) {
              if ((result.getRGB(x, y) and 0xffffff) == 0xe02020) red++
            }
            if (red > 100) rendered = true
          } finally { encoded.close() }
        } finally { image.close() }
      }
      assertTrue(rendered, "Stored recipe photo stayed on the placeholder")
    } finally { scene.close(); scope.cancel(); driver.close() }
  }

  @Test fun cachedPhotoBytesRenderAsAnImage() = runBlocking {
    val fixture = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB)
    for (x in 0..15) for (y in 0..15) fixture.setRGB(x, y, 0xffe02020.toInt())
    val bytes = ByteArrayOutputStream().also { ImageIO.write(fixture, "png", it) }.toByteArray()
    var failed = false
    val scene = ImageComposeScene(128, 128) {
      MaterialTheme {
        CompositionLocalProvider(LocalImageFailure provides { failed = true }) {
          Cover(null, Icons.Outlined.RestaurantMenu, Modifier.fillMaxSize(), bytes = bytes)
        }
      }
    }
    try {
      var rendered = false
      repeat(40) {
        delay(50)
        val image = scene.render(System.nanoTime())
        try {
          val encoded = image.encodeToData()!!
          try {
            val color = ImageIO.read(ByteArrayInputStream(encoded.bytes)).getRGB(64, 64)
            if ((color and 0xffffff) == 0xe02020) rendered = true
          } finally { encoded.close() }
        } finally { image.close() }
        if (rendered) return@repeat
      }
      assertFalse(failed, "Image decoding failed")
      assertTrue(rendered, "Photo stayed on the placeholder")
    } finally { scene.close() }
  }
}
