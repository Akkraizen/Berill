package dev.akkraizen.berill.plugins.recipes

import dev.akkraizen.berill.api.Item
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class RecipePhotosTest {
  private fun photo(id: String, created: Long = 0, position: Long? = null) =
    Item(id, RecipeImage(recipe = "recipe", createdAt = created, position = position))

  @Test
  fun readsOldImagesAndKeepsTheirChronologicalOrder() {
    val old = Json.decodeFromString<RecipeImage>("""{"recipe":"r","data":"photo","createdAt":12}""")
    assertEquals(null, old.position)
    assertEquals(listOf("a", "b"), orderedRecipeImages(listOf(photo("b", 20), photo("a", 10))).map { it.id })
  }

  @Test
  fun storedOrderDefinesCoverWithoutChangingCreationDates() {
    val images = listOf(photo("old", 10, 1), photo("cover", 20, 0), photo("new", 30, 2))
    assertEquals(listOf("cover", "old", "new"), orderedRecipeImages(images).map { it.id })
    assertEquals(20L, orderedRecipeImages(images).first().value.createdAt)
  }

  @Test
  fun movingFirstLastAndOutOfBoundsKeepsEveryPhotoExactlyOnce() {
    val images = listOf(photo("a"), photo("b"), photo("c"))
    assertEquals(listOf("c", "a", "b"), moveRecipeImage(images, "c", -10).map { it.id })
    assertEquals(listOf("b", "c", "a"), moveRecipeImage(images, "a", 50).map { it.id })
    assertEquals(images, moveRecipeImage(images, "missing", 0))
    assertEquals(listOf(photo("only")), moveRecipeImage(listOf(photo("only")), "only", 1))
  }

  @Test
  fun equalPositionsHaveStableOrderAcrossDevices() {
    val images = listOf(photo("z", 10, 0), photo("a", 10, 0))
    assertEquals(listOf("a", "z"), orderedRecipeImages(images).map { it.id })
  }
}
