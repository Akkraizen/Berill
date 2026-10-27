package dev.akkraizen.berill.plugins.nutrition.data

import dev.akkraizen.berill.api.HttpException
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.arr
import dev.akkraizen.berill.api.double
import dev.akkraizen.berill.api.i18n.I18n
import dev.akkraizen.berill.api.int
import dev.akkraizen.berill.api.obj
import dev.akkraizen.berill.api.str
import dev.akkraizen.berill.api.ui.search.networkErrorText
import dev.akkraizen.berill.api.urlEncode
import kotlinx.serialization.json.JsonElement

internal class OpenFoodFacts(private val ctx: PluginContext) {

  suspend fun search(query: String): List<Product> {
    val lang = I18n.language.code.substringBefore('-')
    val url = "$BASE/cgi/search.pl?search_terms=${query.urlEncode()}&search_simple=1&action=process&json=1" +
      "&page_size=30&lc=$lang&fields=$fields"
    return ctx.http
      .getJson(url)
      .arr("products")
      .mapNotNull { it.toProduct() }
      .distinctBy { it.code ?: it.name }
  }

  suspend fun byBarcode(code: String): Product? {
    val json = try {
      ctx.http.getJson("$BASE/api/v2/product/${code.urlEncode()}.json?fields=$fields")
    } catch (e: HttpException) {
      if (e.status == 404) return null else throw e
    }
    if (json.int("status") == 0) return null
    return json.obj("product")?.toProduct()
  }

  private fun JsonElement.toProduct(): Product? {
    val localized = str("product_name_${I18n.language.code.substringBefore('-')}") ?: str("product_name_en")
    val name = localized ?: str("product_name") ?: str("generic_name") ?: return null
    val n = obj("nutriments")
    val kcal = n.double("energy-kcal_100g") ?: n.double("energy_100g")?.div(4.184) ?: return null
    return Product(
      name = name,
      brand = str("brands")?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() },
      kcal100 = kcal,
      protein100 = n.double("proteins_100g") ?: 0.0,
      fat100 = n.double("fat_100g") ?: 0.0,
      carbs100 = n.double("carbohydrates_100g") ?: 0.0,
      code = str("code"),
      image = str("image_small_url") ?: str("image_front_small_url"),
      servingGrams = double("serving_quantity")?.takeIf { it > 0 && it < 5000 }
    )
  }

  companion object {
    private const val BASE = "https://world.openfoodfacts.org"
    private val fields: String
      get() = "code,product_name,product_name_${I18n.language.code.substringBefore('-')},product_name_en," +
        "generic_name,brands,nutriments,image_small_url,image_front_small_url,serving_quantity"

    fun errorText(e: Throwable) = "Open Food Facts: ${networkErrorText(e)}"
  }
}
