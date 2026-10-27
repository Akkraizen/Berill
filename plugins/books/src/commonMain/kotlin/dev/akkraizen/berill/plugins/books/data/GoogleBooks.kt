package dev.akkraizen.berill.plugins.books.data

import dev.akkraizen.berill.api.HttpException
import dev.akkraizen.berill.api.PluginContext
import dev.akkraizen.berill.api.arr
import dev.akkraizen.berill.api.int
import dev.akkraizen.berill.api.migrateSecretFromSettings
import dev.akkraizen.berill.api.obj
import dev.akkraizen.berill.api.str
import dev.akkraizen.berill.api.strings
import dev.akkraizen.berill.api.stripHtml
import dev.akkraizen.berill.api.ui.search.SearchHit
import dev.akkraizen.berill.api.ui.search.networkErrorText
import dev.akkraizen.berill.api.urlEncode
import dev.akkraizen.berill.plugins.books.i18n.BooksText
import kotlinx.serialization.json.JsonElement

internal const val GOOGLE_BOOKS_KEY = "googleBooksKey"

internal class GoogleBooks(private val ctx: PluginContext) {
  private val base = "https://www.googleapis.com/books/v1/volumes"

  private fun keyParam(): String {
    ctx.migrateSecretFromSettings(GOOGLE_BOOKS_KEY)
    return ctx.secrets
      .get(GOOGLE_BOOKS_KEY)
      ?.trim()
      ?.takeIf { it.isNotEmpty() }
      ?.let { "&key=${it.urlEncode()}" } ?: ""
  }

  suspend fun search(query: String): List<SearchHit> {
    val json = ctx.http.getJson("$base?q=${query.urlEncode()}&maxResults=20&printType=books${keyParam()}")
    return json.arr("items").mapNotNull { item ->
      val id = item.str("id") ?: return@mapNotNull null
      val info = item.obj("volumeInfo")
      SearchHit(
        ref = id,
        title = fullTitle(info) ?: return@mapNotNull null,
        subtitle = info.strings("authors").joinToString(", ").ifEmpty { null },
        meta =
          listOfNotNull(info.str("publishedDate")?.take(4), info.int("pageCount")?.let { BooksText.pagesShort(it)() })
            .joinToString(" · ")
            .ifEmpty { null },
        cover = cover(info)
      )
    }
  }

  suspend fun details(id: String): Book {
    val info = ctx.http
      .getJson("$base/${id.urlEncode()}?${keyParam().removePrefix("&")}")
      .obj("volumeInfo")
    val description = info.str("description")?.stripHtml()
    return Book(
      title = fullTitle(info) ?: BooksText.untitled(),
      author = info.strings("authors").joinToString(", ").ifEmpty { null },
      genre = info.strings("categories").firstOrNull()?.substringBefore(" / ")?.trim(),
      pages = info.int("pageCount")?.takeIf { it > 0 },
      cover = cover(info),
      body = description?.let { BooksText.bodyTemplate(it) }
    )
  }

  private fun fullTitle(info: JsonElement?): String? {
    val title = info.str("title") ?: return null
    val subtitle = info.str("subtitle")
    return if (subtitle != null) "$title. $subtitle" else title
  }

  private fun cover(info: JsonElement?): String? {
    val links = info.obj("imageLinks")
    val url = links.str("thumbnail") ?: links.str("smallThumbnail") ?: return null
    return url.replace("http://", "https://").replace("&edge=curl", "")
  }

  companion object {
    fun errorText(e: Throwable): String =
      if (e is HttpException && e.status == 429) {
        BooksText.rateLimited()
      } else {
        "Google Books: ${networkErrorText(e)}"
      }
  }
}
