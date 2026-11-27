package dev.marzahn.gloriana.logic.presets

import androidx.sqlite.execSQL
import dev.marzahn.gloriana.logic.storage.MigrationHarness
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class AddPresetsMigrationTest {
  @TempDir
  lateinit var dataDirectory: Path

  @Test
  fun aVersionThreeLibraryKeepsItsConnectionsAndSettingsAndStoresPresetsAfterTheMigration() = runTest {
    val harness = MigrationHarness(dataDirectory)
    harness.createAt(version = 3) { connection ->
      connection.execSQL(
        "INSERT INTO connections (id, name, nameKey, format, baseUrl, model, contextSize, maxReplyTokens, streaming, " +
          "includeUsage, timeoutSeconds, samplers, customBody, headers, apiKey, updatedAt) VALUES " +
          "(x'0192f6a0d4c37a1b8c2d3e4f5a6b7c8d', 'Home', 'home', 'OpenAiChatCompletions', " +
          "'http://192.168.1.5:8080/v1', 'local-model', 8192, 512, 1, 1, 60, '{\"temperature\":0.6}', " +
          "'{}', '[]', NULL, 10)",
      )
      connection.execSQL(
        "INSERT INTO library_settings (`key`, value, updatedAt) VALUES " +
          "('globalSamplers', '{\"topP\":0.9}', 11)",
      )
    }

    val migrated = harness.migrateToLatest { database ->
      PresetLibrary(dataDirectory, database).let { library ->
        val imported = library.imported("minimal.json")
        Migrated(
          connections = library.connections.list().map { it.fields.name to it.fields.samplers.temperature },
          globalTopP = library.global.samplers().first().topP,
          presets = library.repository.presets().first().map { it.name },
          entries = library.stored(imported.id).content.entries.map { it.identifier },
        )
      }
    }

    assertEquals(listOf("Home" to 0.6), migrated.connections)
    assertEquals(0.9, migrated.globalTopP)
    assertEquals(listOf("minimal"), migrated.presets)
    assertEquals(listOf("main", "chatHistory"), migrated.entries)
  }

  private class Migrated(
    val connections: List<Pair<String, Double?>>,
    val globalTopP: Double?,
    val presets: List<String>,
    val entries: List<String>,
  )
}
