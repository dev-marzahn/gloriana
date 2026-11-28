package dev.marzahn.gloriana.logic.presets

import dev.marzahn.gloriana.logic.presets.file.keysNeverStored
import dev.marzahn.gloriana.logic.presets.storage.SHIPPED_PRESET_JSON
import dev.marzahn.gloriana.logic.settings.MakeDefaultResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class DefaultPresetTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun aFreshLibraryGetsTheShippedPresetAsItsDefaultOnce() = runTest {
    PresetLibrary(directory).use { library ->
      library.default.ensureShipped()
      library.default.ensureShipped()

      val presets = library.repository.presets().first()
      assertEquals(listOf("Default"), presets.map { it.name })
      assertEquals(presets.single().id, library.repository.defaultPreset().first())
    }
  }

  @Test
  fun theShippedPresetHoldsSillyTavernsStandardEntriesInOrder() = runTest {
    PresetLibrary(directory).use { library ->
      library.default.ensureShipped()
      val id = checkNotNull(library.repository.defaultPreset().first())

      val entries = library.stored(id).content.entries
      assertEquals(
        listOf(
          "main", "worldInfoBefore", "personaDescription", "charDescription", "charPersonality", "scenario",
          "enhanceDefinitions", "nsfw", "worldInfoAfter", "dialogueExamples", "chatHistory", "jailbreak",
        ),
        entries.map { it.identifier },
      )
      assertEquals(listOf("enhanceDefinitions"), entries.filterNot { it.enabled }.map { it.identifier })
      assertEquals(
        listOf(FilledWith.LorebookEntries, FilledWith.PersonaDescription, FilledWith.CharacterDescription),
        entries.slice(1..3).map { it.filledWith },
      )
    }
  }

  @Test
  fun theShippedPresetExportsAsTheShippedFile() = runTest {
    PresetLibrary(directory).use { library ->
      library.default.ensureShipped()
      val id = checkNotNull(library.repository.defaultPreset().first())

      val pinnedFile = Json.parseToJsonElement(SHIPPED_PRESET_JSON).jsonObject
      assertEquals(JsonObject(pinnedFile - keysNeverStored), library.exported(id))
    }
  }

  @Test
  fun theDefaultCantBeDeletedUntilAnotherIsTheDefault() = runTest {
    PresetLibrary(directory).use { library ->
      library.default.ensureShipped()
      val shipped = checkNotNull(library.repository.defaultPreset().first())
      val other = library.imported("minimal.json").id

      assertEquals(PresetDeleteResult.IsDefault, library.repository.delete(shipped))
      assertEquals("Default", library.stored(shipped).name)

      assertEquals(MakeDefaultResult.Made, library.repository.makeDefault(other))
      assertEquals(PresetDeleteResult.Deleted, library.repository.delete(shipped))
      assertEquals(PresetDeleteResult.IsDefault, library.repository.delete(other))
    }
  }

  @Test
  fun makingAPresetNoLongerStoredTheDefaultIsGoneAndChangesNothing() = runTest {
    PresetLibrary(directory).use { library ->
      library.default.ensureShipped()
      val shipped = library.repository.defaultPreset().first()

      assertEquals(MakeDefaultResult.Gone, library.repository.makeDefault(Uuid.random()))

      assertEquals(shipped, library.repository.defaultPreset().first())
    }
  }
}
