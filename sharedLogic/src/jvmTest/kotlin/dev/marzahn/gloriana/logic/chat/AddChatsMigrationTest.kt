package dev.marzahn.gloriana.logic.chat

import androidx.sqlite.execSQL
import dev.marzahn.gloriana.logic.storage.MigrationHarness
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class AddChatsMigrationTest {
  @TempDir
  lateinit var dataDirectory: Path

  @Test
  fun aVersionFiveLibraryKeepsItsPersonasAndStoresChatsAfterTheMigration() = runTest {
    val harness = MigrationHarness(dataDirectory)
    harness.createAt(version = 5) { connection ->
      connection.execSQL(
        "INSERT INTO personas (id, name, nameKey, description, personaLabel, personaNote, updatedAt) VALUES " +
          "(x'0192f6a0d4c37a1b8c2d3e4f5a6b7c8d', 'Wren', 'wren', '', '', '', 10)",
      )
    }

    val migrated = harness.migrateToLatest { database ->
      val library = ChatLibrary(dataDirectory, database)
      val chat = library.started(library.newChat(library.character()))
      library.personas.repository.personas().first().map { it.fields.name } to library.messages(chat.id).size
    }

    assertEquals(listOf("User", "Wren") to 1, migrated.first.sorted() to migrated.second)
  }
}
