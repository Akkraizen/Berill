package dev.akkraizen.berill.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class NoteRef(val id: String, val title: String, val preview: String, val updatedAt: Long)

interface NoteDirectory {
  val available: Boolean

  fun observe(): Flow<List<NoteRef>>

  fun get(id: String): NoteRef?

  fun create(title: String, body: String): String?
}

object NoNotes : NoteDirectory {
  override val available = false

  override fun observe(): Flow<List<NoteRef>> = flowOf(emptyList())

  override fun get(id: String): NoteRef? = null

  override fun create(title: String, body: String): String? = null
}
