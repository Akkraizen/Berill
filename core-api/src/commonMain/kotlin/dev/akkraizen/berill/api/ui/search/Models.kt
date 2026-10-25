package dev.akkraizen.berill.api.ui.search

data class SearchHit(
  val ref: String,
  val title: String,
  val subtitle: String? = null,
  val meta: String? = null,
  val cover: String? = null
)
