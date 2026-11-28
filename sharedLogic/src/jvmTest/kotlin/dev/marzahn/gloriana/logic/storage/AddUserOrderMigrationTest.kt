package dev.marzahn.gloriana.logic.storage

import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class AddUserOrderMigrationTest {
  @TempDir
  lateinit var dataDirectory: Path

  @Test
  fun eachListKeepsTheOrderItShowedBeforeTheMigrationAsItsStoredOrder() = runTest {
    val harness = MigrationHarness(dataDirectory)
    harness.createAt(version = 6) { connection ->
      listOf("03" to "Ada", "01" to "Cleo", "02" to "Bram").forEach { (id, name) ->
        connection.execSQL(
          "INSERT INTO personas (id, name, nameKey, description, personaLabel, personaNote, updatedAt) " +
            "VALUES (x'${rowId(id)}', '$name', '${name.lowercase()}', '', '', '', 10)",
        )
      }
      listOf("01" to "bravo", "02" to "Alpha", "03" to "alpha").forEach { (id, name) ->
        connection.execSQL(
          "INSERT INTO presets (id, name, nameKey, wrappers, sourceJson, updatedAt) " +
            "VALUES (x'${rowId(id)}', '$name', '${name.lowercase()}', '{}', '{}', 10)",
        )
      }
      listOf("01" to "Zeta", "02" to "alpha").forEach { (id, name) ->
        connection.execSQL(
          "INSERT INTO connections (id, name, nameKey, format, baseUrl, model, contextSize, maxReplyTokens, " +
            "streaming, includeUsage, timeoutSeconds, samplers, customBody, headers, apiKey, updatedAt) " +
            "VALUES (x'${rowId(id)}', '$name', '${name.lowercase()}', 'OpenAiChatCompletions', " +
            "'https://api.example.com/v1', 'model-1', 8192, 1024, 1, 1, 60, '{}', '{}', '[]', NULL, 10)",
        )
      }
    }

    val migrated = harness.migrateToLatest { database ->
      Migrated(
        personas = database.personaDao().placedRows().map { it.position to it.id.short() },
        presets = database.presetDao().summaries().first().map { it.name },
        connections = database.connectionDao().observeAll().first().map { it.position to it.name },
      )
    }

    assertEquals(listOf(0L to "01", 1L to "02", 2L to "03"), migrated.personas)
    assertEquals(listOf("Alpha", "alpha", "bravo"), migrated.presets)
    assertEquals(listOf(0L to "alpha", 1L to "Zeta"), migrated.connections)
  }

  private data class Migrated(
    val personas: List<Pair<Long, String>>,
    val presets: List<String>,
    val connections: List<Pair<Long, String>>,
  )

  private fun rowId(last: String) = "0192f6a0d4c37a1b8c2d3e4f5a6b7c$last"

  private fun kotlin.uuid.Uuid.short() = toHexString().takeLast(2)
}
