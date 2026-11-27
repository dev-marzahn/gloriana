package dev.marzahn.gloriana.logic.characters

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

class CardExportDiffTest {
  @TempDir
  lateinit var directory: Path

  @TestFactory
  fun everySingleCardExportsWithEveryFieldOfItsSource() = ImporterCorpus.singleCards.flatMap { file ->
    Container.entries.map { container ->
      DynamicTest.dynamicTest("${file.name} as ${container.name}") {
        CharacterLibrary(Files.createTempDirectory(directory, "library")).use { library ->
          runTest {
            val character = library.importedCharacter(file)
            val format = if (container == Container.Png) CardFormat.Png else CardFormat.Json
            val (_, exported) = library.export(character.id, format)
            val written = when (container) {
              Container.Png -> CardSources.pngCard(exported)
              Container.Json -> Json.parseToJsonElement(exported.decodeToString()).jsonObject
            }

            val sourceHoldsFiles = !file.name.endsWith(".json")
            assertEquals(
              emptyList(),
              CardDiff.differences(CardSources.cardJson(file), written, sourceHoldsFiles, container),
            )
          }
        }
      }
    }
  }
}
