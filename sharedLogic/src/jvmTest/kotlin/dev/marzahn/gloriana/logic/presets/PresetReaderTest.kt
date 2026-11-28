package dev.marzahn.gloriana.logic.presets

import dev.marzahn.gloriana.logic.characters.CorpusFile
import dev.marzahn.gloriana.logic.connection.Samplers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PresetReaderTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun entriesFollowTheGlobalOrderWithTheirSettings() = runTest {
    PresetLibrary(directory).use { library ->
      val preset = library.stored(library.imported("sillytavern-full.json").id)

      assertEquals("sillytavern-full", preset.name)
      assertEquals(
        listOf("main", "personaDescription", "charDescription", "chatHistory", "3f0c9a2e-tone", "jailbreak"),
        preset.content.entries.map { it.identifier },
      )
      val tone = preset.content.entries.single { it.identifier == "3f0c9a2e-tone" }
      assertEquals(EntryPlacement.InChat(depth = 2), tone.placement)
      assertEquals(EntryRole.User, tone.role)
      assertEquals("Tone reminder", tone.name)
      assertEquals("Keep the tone quiet and warm.", tone.text)
      val description = preset.content.entries.single { it.identifier == "charDescription" }
      assertFalse(description.enabled)
      assertTrue(description.filledByApp)
      assertEquals("[Carry on from where you stopped.]", preset.content.wrappers[WrapperPrompt.ContinueNudge])
      assertEquals("{{persona}}", preset.content.wrappers[WrapperPrompt.PersonaDescription])
    }
  }

  @Test
  fun withoutAnOrderListThePromptsAreTheEntriesAllOn() = runTest {
    PresetLibrary(directory).use { library ->
      val entries = library.stored(library.imported("only-prompts.json").id).content.entries

      assertEquals(listOf("main", "aside", "deep", "nodepth"), entries.map { it.identifier })
      assertTrue(entries.all { it.enabled })
      assertEquals("First main.", entries[0].text)
      val aside = entries[1]
      assertEquals("aside", aside.name)
      assertEquals(EntryRole.System, aside.role)
      assertEquals(EntryPlacement.Relative, aside.placement)
      assertEquals(EntryPlacement.InChat(0), entries[2].placement)
      assertEquals(EntryPlacement.InChat(4), entries[3].placement)
      assertEquals(EntryRole.System, entries[3].role)
    }
  }

  @Test
  fun theFirstOrderListServesWithoutAGlobalOneAndSkipsDanglingAndRepeatedItems() = runTest {
    PresetLibrary(directory).use { library ->
      val content = library.stored(library.imported("odd-order.json").id).content

      assertEquals(
        listOf("main" to true, "chatHistory" to false, "tail" to true),
        content.entries.map { it.identifier to it.enabled },
      )
      assertEquals(listOf("Early", "main", "chatHistory", "Late", "tail"), content.rows.map(::label))
      assertEquals(WrapperPrompt.Impersonation.pinnedDefault, content.wrappers[WrapperPrompt.Impersonation])
      assertEquals("[Begin]", content.wrappers[WrapperPrompt.NewChat])
    }
  }

  @Test
  fun groupsAndThePersonaWrapperComeBackFromGlorianasOwnKey() = runTest {
    PresetLibrary(directory).use { library ->
      val preset = library.stored(library.imported("gloriana-groups.json").id)

      assertEquals(
        listOf("Setup", "main", "personaDescription", "Story", "chatHistory", "aside", "Empty"),
        preset.content.rows.map(::label),
      )
      assertEquals("[You are: {{persona}}]", preset.content.wrappers[WrapperPrompt.PersonaDescription])
    }
  }

  @Test
  fun aByteOrderMarkBeforeTheJsonIsRead() = runTest {
    val withMark = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + PresetCorpus.bytes("minimal.json")

    PresetLibrary(directory).use { library ->
      assertIs<PresetImportResult.Imported>(library.import.import(CorpusFile("marked.json", withMark)))
    }
  }

  @Test
  fun samplersThatArentFiniteNumbersAreNotRead() = runTest {
    val file = """{"prompts": [], "temperature": 1e999, "top_p": "0.5", "seed": 3}"""

    PresetLibrary(directory).use { library ->
      val result = library.import.import(CorpusFile("odd.json", file.encodeToByteArray()))
      val imported = assertIs<PresetImportResult.Imported>(result)
      assertEquals(PresetSamplers.of(Samplers(seed = 3)), imported.samplers)
    }
  }

  @Test
  fun aSeedBeyondTheDoublesWholeNumbersKeepsItsExactValue() = runTest {
    val file = """{"prompts": [], "seed": 9007199254740993, "top_k": 4.0}"""

    PresetLibrary(directory).use { library ->
      val result = library.import.import(CorpusFile("seed.json", file.encodeToByteArray()))
      assertEquals(
        PresetSamplers.of(Samplers(topK = 4, seed = 9_007_199_254_740_993)),
        assertIs<PresetImportResult.Imported>(result).samplers,
      )
    }
  }

  @Test
  fun topKAtOrBelowZeroANegativeSeedAndWholeNumbersBeyondALongSetNothing() = runTest {
    val files = listOf(
      """{"prompts": [], "top_k": -3, "seed": -1}""",
      """{"prompts": [], "top_k": 0, "seed": 1e20}""",
      """{"prompts": [], "top_k": 1e20, "seed": -9223372036854775809}""",
      """{"prompts": [], "top_k": 4.5, "seed": 1.5}""",
      """{"prompts": [], "seed": 9223372036854775808}""",
    )

    PresetLibrary(directory).use { library ->
      files.forEachIndexed { index, file ->
        val result = library.import.import(CorpusFile("limits-$index.json", file.encodeToByteArray()))
        assertTrue(assertIs<PresetImportResult.Imported>(result).samplers.isEmpty, file)
      }
    }
  }

  @Test
  fun aNegativeDepthInAFileReadsAsZeroAndSavesButANegativeEditIsRefused() = runTest {
    PresetLibrary(directory).use { library ->
      val preset = library.stored(library.imported("only-prompts.json").id)
      val deep = preset.content.entries[2]

      assertEquals(EntryPlacement.InChat(0), deep.placement)
      assertEquals(PresetSaveResult.Saved, library.repository.save(preset))
      assertEquals(preset, library.stored(preset.id))
      val negative = deep.copy(placement = EntryPlacement.InChat(-3))
      val rows = preset.content.rows.map { if (it == deep) negative else it }
      assertEquals(
        PresetSaveResult.Refused(setOf(PresetProblem.NegativeDepth(deep.id))),
        library.repository.save(preset.copy(content = preset.content.copy(rows = rows))),
      )
    }
  }

  private fun label(row: PresetRow) = when (row) {
    is PresetEntry -> row.identifier
    is PresetGroup -> row.name
  }
}
