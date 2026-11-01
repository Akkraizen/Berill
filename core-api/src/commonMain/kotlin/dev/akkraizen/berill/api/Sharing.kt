package dev.akkraizen.berill.api

import kotlinx.serialization.json.JsonPrimitive

data class SharedText(val title: String, val text: String)
interface ShareSource : Capability {
  val label: String
  fun accept(content: SharedText)
}

class CollectionShareSource(
  private val ctx: PluginContext,
  private val collection: String,
  override val label: String,
  private val routePrefix: String
) : ShareSource {
  override fun accept(content: SharedText) {
    val id = ctx.data.newId()
    val url = Regex("https?://[^\\s]+").find(content.text)?.value
    ctx.data.raw(collection).patch(id, mapOf(
      "title" to JsonPrimitive(content.title.ifBlank { content.text.lineSequence().first().take(160) }),
      "body" to JsonPrimitive(content.text),
      "url" to JsonPrimitive(url)
    ))
    ctx.nav.openPlugin(ctx.pluginId, routePrefix + id)
  }
}
