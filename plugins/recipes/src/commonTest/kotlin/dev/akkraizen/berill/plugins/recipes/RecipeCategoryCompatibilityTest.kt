package dev.akkraizen.berill.plugins.recipes
import dev.akkraizen.berill.plugins.recipes.data.*
import kotlin.test.*
class RecipeCategoryCompatibilityTest {
 @Test fun knownAndCustomCategoriesStayCompatible() {
  for (v in RecipeCategory.entries) assertEquals(v, RecipeCategory.parse(v.wire))
  assertNull(RecipeCategory.parse("My custom category"))
 }
}
