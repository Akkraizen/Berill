package dev.akkraizen.berill.mcp

import dev.akkraizen.berill.core.secure.MemoryVault
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class BerillToolsTest {
  private val core = openCore(Files.createTempDirectory("berill-mcp").toFile(), MemoryVault())
  private val tools = BerillTools(core)

  private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content

  @Test
  fun createdNoteCanBeFoundReadAndEdited(): Unit = runBlocking {
    val id = (tools.createNote("Groceries", "milk\nbread") as JsonObject).text("id")

    val found = tools.search("BREAD", 10) as JsonArray
    assertEquals(1, found.size)
    assertEquals("notes", found[0].jsonObject.text("collection"))
    assertEquals(id, found[0].jsonObject.text("id"))

    tools.appendToNote(id, "eggs")
    assertEquals("milk\nbread\n\neggs", (tools.readNote(id) as JsonObject).text("body"))

    tools.updateNote(id, "Shopping", null)
    val note = tools.readNote(id) as JsonObject
    assertEquals("Shopping", note.text("title"))
    assertEquals("milk\nbread\n\neggs", note.text("body"))
    assertEquals(id, (tools.listNotes(10) as JsonArray).single().jsonObject.text("id"))
  }

  @Test
  fun deletingNeedsConfirmation() {
    val id = (tools.createNote("Temp", "") as JsonObject).text("id")

    assertFailsWith<ToolException> { tools.deleteNote(id, confirm = false) }
    tools.deleteNote(id, confirm = true)
    assertFailsWith<ToolException> { tools.readNote(id) }
  }

  @Test
  fun collectionsCoverSectionsButNotSettings(): Unit = runBlocking {
    tools.createNote("A", "b")
    val collections = (tools.listCollections() as JsonArray).map { it.jsonObject }
    val names = collections.map { it.text("collection") }

    assertTrue("notes" in names)
    assertTrue("settings" !in names)
    assertTrue(names.size > 10, names.toString())
    assertEquals(1, collections.first { it.text("collection") == "notes" }.getValue("records").jsonPrimitive.int)
    assertFailsWith<ToolException> { tools.readRecords("settings", 10, 0) }
    assertFailsWith<ToolException> { tools.getRecord("nope", "1") }
  }

  @Test
  fun recordsArePaged(): Unit = runBlocking {
    repeat(3) { tools.createNote("Note $it", "text") }

    val page = tools.readRecords("notes", 2, 1) as JsonObject
    assertEquals(3, page.getValue("total").jsonPrimitive.int)
    assertEquals(2, page.getValue("records").jsonArray.size)
  }

  @Test
  fun syncNeedsLogin(): Unit = runBlocking {
    assertEquals("false", (tools.syncStatus() as JsonObject).text("configured"))
    assertFailsWith<ToolException> { tools.syncNow() }
  }
}
