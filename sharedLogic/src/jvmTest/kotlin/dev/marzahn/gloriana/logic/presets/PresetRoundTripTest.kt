package dev.marzahn.gloriana.logic.presets

import dev.marzahn.gloriana.logic.connection.Sampler
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PresetRoundTripTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun eachCorpusPresetExportsAsItsSourceLessTheKeysNeverStored() = runTest {
    PresetLibrary(directory).use { library ->
      PresetCorpus.presets.forEach { name ->
        val imported = library.imported(name)
        assertEquals(PresetCorpus.storedPart(name), library.exported(imported.id), name)
      }
    }
  }

  @Test
  fun keysAndPromptsTheAppDoesntUseExportAgain() = runTest {
    PresetLibrary(directory).use { library ->
      val exported = library.exported(library.imported("sillytavern-full.json").id)

      assertEquals(JsonPrimitive("a key Gloriana doesn't use"), exported["MARKER-KEPT"])
      assertTrue(
        exported.getValue("prompts").jsonArray.any {
          it.jsonObject.getValue("identifier").jsonPrimitive.content == "MARKER-UNORDERED"
        },
      )
      assertTrue(
        exported.getValue("prompt_order").jsonArray.any {
          it.jsonObject.getValue("character_id").jsonPrimitive.content == "100000"
        },
      )
      assertEquals(JsonPrimitive(100), exported.getValue("prompts").jsonArray[0].jsonObject["injection_order"])
    }
  }

  @Test
  fun credentialAndProxyKeysAreSkippedAndStoredNowhere() = runTest {
    PresetLibrary(directory).use { library ->
      listOf("sillytavern-1.19.1.json", "sillytavern-full.json").forEach { name ->
        val imported = library.imported(name)

        assertTrue(imported.credentialsSkipped, name)
        val exported = library.exported(imported.id).toString()
        assertFalse("MARKER-CREDENTIAL" in exported || "MARKER-PROXY" in exported, name)
      }
    }
    directory.toFile().walkTopDown().filter { it.isFile }.forEach { file ->
      val text = String(file.readBytes(), Charsets.ISO_8859_1)
      assertFalse("MARKER-CREDENTIAL" in text || "MARKER-PROXY" in text, "$file")
    }
  }

  @Test
  fun aFileWithEmptyCredentialKeysSaysNothingWasSkipped() = runTest {
    PresetLibrary(directory).use { library -> assertFalse(library.imported("minimal.json").credentialsSkipped) }
  }

  @Test
  fun theFilesSamplersAreReadForTheOffer() = runTest {
    PresetLibrary(directory).use { library ->
      assertEquals(
        mapOf<PresetSampler, Double>(
          PresetSampler.OnConnection(Sampler.Temperature) to 0.85,
          PresetSampler.OnConnection(Sampler.TopP) to 0.92,
          PresetSampler.OnConnection(Sampler.TopK) to 40.0,
          PresetSampler.OnConnection(Sampler.FrequencyPenalty) to 0.3,
          PresetSampler.OnConnection(Sampler.PresencePenalty) to 0.1,
          PresetSampler.OnConnection(Sampler.Seed) to 7.0,
          PresetSampler.WithoutConnectionField.TopA to 0.2,
          PresetSampler.WithoutConnectionField.MinP to 0.05,
          PresetSampler.WithoutConnectionField.RepetitionPenalty to 1.1,
        ),
        library.imported("sillytavern-full.json").samplers.values,
      )
      assertEquals(
        mapOf<PresetSampler, Double>(
          PresetSampler.OnConnection(Sampler.Temperature) to 0.9,
          PresetSampler.OnConnection(Sampler.FrequencyPenalty) to 0.2,
          PresetSampler.OnConnection(Sampler.PresencePenalty) to 0.0,
          PresetSampler.OnConnection(Sampler.TopP) to 0.95,
          PresetSampler.WithoutConnectionField.TopA to 0.0,
          PresetSampler.WithoutConnectionField.MinP to 0.0,
          PresetSampler.WithoutConnectionField.RepetitionPenalty to 1.0,
        ),
        library.imported("sillytavern-1.19.1.json").samplers.values,
      )
      assertTrue(library.imported("only-prompts.json").samplers.isEmpty)
    }
  }
}
