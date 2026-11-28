package dev.marzahn.gloriana.logic.connection

import androidx.sqlite.execSQL
import dev.marzahn.gloriana.logic.storage.MigrationHarness
import dev.marzahn.gloriana.logic.storage.RowKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class AddConnectionsMigrationTest {
  @TempDir
  lateinit var dataDirectory: Path

  @Test
  fun aVersionTwoLibraryKeepsItsCharactersAndStoresConnectionsAfterTheMigration() = runTest {
    val harness = MigrationHarness(dataDirectory)
    harness.createAt(version = 2) { connection ->
      connection.execSQL(
        "INSERT INTO characters (id, name, nickname, description, personality, scenario, firstMessage, " +
          "exampleMessages, creator, characterVersion, creatorNotes, systemPrompt, postHistoryInstructions, " +
          "keptFields, cardJson, nameKey, creatorKey, importedAt, updatedAt) VALUES " +
          "(x'0192f6a0d4c37a1b8c2d3e4f5a6b7c8d', 'Wren', '', '', '', '', '', '', '', '', '', '', '', " +
          "'{}', '{}', 'wren', '', 10, 11)",
      )
      connection.execSQL(
        "INSERT INTO tombstones (entityType, rowKey, deletedAt, updatedAt) " +
          "VALUES ('characters', x'0192f6a0d4c37a1b8c2d3e4f5a6b7c8e', 100, 101)",
      )
    }

    val migrated = harness.migrateToLatest { database ->
      val library = ConnectionLibrary(dataDirectory, database = database)
      val saved = library.created(draft(key = KeyInput.Entered("sk-1")))
      Migrated(
        characters = database.characterDao().summaries().first().map { it.name },
        tombstones = database.tombstoneDao().all().map { it.rowKey },
        saved = saved.id,
        default = library.default(),
      )
    }

    assertEquals(listOf("Wren"), migrated.characters)
    assertEquals(listOf(RowKey.Row(Uuid.parse("0192f6a0-d4c3-7a1b-8c2d-3e4f5a6b7c8e"))), migrated.tombstones)
    assertEquals(migrated.saved, migrated.default)
  }

  private class Migrated(
    val characters: List<String>,
    val tombstones: List<RowKey>,
    val saved: Uuid,
    val default: Uuid?,
  )
}
