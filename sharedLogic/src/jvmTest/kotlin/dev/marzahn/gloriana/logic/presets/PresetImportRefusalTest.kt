package dev.marzahn.gloriana.logic.presets

import dev.marzahn.gloriana.logic.characters.CorpusFile
import dev.marzahn.gloriana.logic.files.OpenedFile
import dev.marzahn.gloriana.logic.files.PickedFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

private const val SPEC_LIMIT_BYTES = 16_777_216

class PresetImportRefusalTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun eachFailingFileNamesItsReasonAndAddsNoPreset() = runTest {
    val gone = object : PickedFile {
      override val name = "gone.json"

      override fun open() = OpenedFile.Unavailable
    }
    val failing = object : PickedFile {
      override val name = "failing.json"

      override fun open(): OpenedFile = error("revoked")
    }
    val cases = listOf(
      PresetCorpus.file("damaged.json") to PresetImportFailure.Damaged,
      CorpusFile("latin1.json", byteArrayOf(0x7B, 0xFF.toByte(), 0x7D)) to PresetImportFailure.Damaged,
      CorpusFile("prompts.json", """{"prompts": {}}""".encodeToByteArray()) to PresetImportFailure.Damaged,
      PresetCorpus.file("character-card.json") to PresetImportFailure.NotAPreset,
      PresetCorpus.file("lorebook.json") to PresetImportFailure.NotAPreset,
      PresetCorpus.file("text-completion.json") to PresetImportFailure.NotAPreset,
      CorpusFile("list.json", "[1, 2]".encodeToByteArray()) to PresetImportFailure.NotAPreset,
      CorpusFile("huge.json", spaces(SPEC_LIMIT_BYTES + 1)) to PresetImportFailure.TooLarge,
      gone to PresetImportFailure.CantBeOpened,
      failing to PresetImportFailure.CantBeOpened,
    )

    PresetLibrary(directory).use { library ->
      cases.forEach { (file, reason) ->
        assertEquals(PresetImportResult.Failed(reason), library.import.import(file), file.name)
      }
      assertEquals(emptyList(), library.repository.presets().first())
    }
  }

  @Test
  fun aFileAtTheLimitImports() = runTest {
    val minimal = PresetCorpus.bytes("minimal.json")
    val padded = minimal + spaces(SPEC_LIMIT_BYTES - minimal.size)

    PresetLibrary(directory).use { library ->
      library.import.import(CorpusFile("padded.json", padded)).let { result ->
        assertEquals(PresetImportResult.Imported::class, result::class)
      }
    }
  }

  @Test
  fun aPresetIsNamedFromItsFileWithoutJsonAndListsByName() = runTest {
    PresetLibrary(directory).use { library ->
      listOf("Zephyr.JSON", "alpha.json", " Mid .json", "notes.txt", ".json").forEach { name ->
        library.import.import(CorpusFile(name, PresetCorpus.bytes("minimal.json")))
      }
      library.import.import(CorpusFile("alpha.json", PresetCorpus.bytes("minimal.json")))

      assertEquals(
        listOf(".json", "alpha", "alpha", "Mid", "notes.txt", "Zephyr"),
        library.repository.presets().first().map { it.name },
      )
    }
  }

  private fun spaces(count: Int) = ByteArray(count) { ' '.code.toByte() }
}
