package dev.marzahn.gloriana.logic.personas

import androidx.sqlite.execSQL
import dev.marzahn.gloriana.logic.characters.Pronouns
import dev.marzahn.gloriana.logic.presets.PresetLibrary
import dev.marzahn.gloriana.logic.storage.MigrationHarness
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class AddPersonasMigrationTest {
  @TempDir
  lateinit var dataDirectory: Path

  @Test
  fun aVersionFourLibraryKeepsItsPresetsAndSettingsAndStoresPersonasAfterTheMigration() = runTest {
    val harness = MigrationHarness(dataDirectory)
    harness.createAt(version = 4) { connection ->
      connection.execSQL(
        "INSERT INTO presets (id, name, nameKey, wrappers, sourceJson, updatedAt) VALUES " +
          "(x'0192f6a0d4c37a1b8c2d3e4f5a6b7c8d', 'Night shift', 'night shift', '{}', '{\"prompts\":[]}', 10)",
      )
      connection.execSQL(
        "INSERT INTO library_settings (`key`, value, updatedAt) VALUES ('globalSamplers', '{\"topP\":0.9}', 11)",
      )
    }

    val migrated = harness.migrateToLatest { database ->
      val presets = PresetLibrary(dataDirectory, database)
      val personas = PersonaLibrary(dataDirectory, database)
      val created = personas.created(wren().copy(pronouns = Pronouns(subject = "she")))
      Migrated(
        presets = presets.repository.presets().first().map { it.name },
        globalTopP = presets.global.samplers().first().topP,
        persona = personas.stored(created.id).fields,
      )
    }

    assertEquals(listOf("Night shift"), migrated.presets)
    assertEquals(0.9, migrated.globalTopP)
    assertEquals(wren().copy(pronouns = Pronouns(subject = "she")), migrated.persona)
  }

  private class Migrated(val presets: List<String>, val globalTopP: Double?, val persona: PersonaFields)
}
