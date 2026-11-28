package dev.marzahn.gloriana.logic.characters

import dev.marzahn.gloriana.logic.brand.avatarGradientColors
import dev.marzahn.gloriana.logic.characters.CardFixtures.sha256
import dev.marzahn.gloriana.logic.characters.files.PlaceholderAvatar
import dev.marzahn.gloriana.logic.files.CorpusFile
import dev.marzahn.gloriana.logic.files.ExportFailure
import dev.marzahn.gloriana.logic.files.ExportProblem
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.zip.ZipEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class CardRoundTripTest {
  @TempDir
  lateinit var directory: Path

  private lateinit var library: CharacterLibrary

  @BeforeEach
  fun open() {
    library = CharacterLibrary(directory)
  }

  @AfterEach
  fun close() = library.close()

  @TestFactory
  fun aPngExportImportsAgainAsTheSameCharacter() = ImporterCorpus.singleCards.map { file ->
    DynamicTest.dynamicTest(file.name) {
      runTest {
        val original = library.importedCharacter(file)

        val (result, png) = library.export(original.id, CardFormat.Png)
        val again = library.importedCharacter(CorpusFile("again.png", png))

        assertIs<ExportResult.Saved>(result)
        val expectedAvatar = original.avatar ?: sha256(PlaceholderAvatar.png(avatarGradientColors(original.id)))
        assertEquals(original.comparable().copy(avatar = expectedAvatar), again.comparable())
      }
    }
  }

  @TestFactory
  fun aJsonExportImportsAgainAsTheSameCharacterWithoutStoredFiles() = ImporterCorpus.singleCards.map { file ->
    DynamicTest.dynamicTest(file.name) {
      runTest {
        val original = library.importedCharacter(file)

        val (result, json) = library.export(original.id, CardFormat.Json)
        val again = library.importedCharacter(CorpusFile("again.json", json))

        assertIs<ExportResult.Saved>(result)
        val withoutFiles = original.assets.filterNot { it.source is AssetSource.Stored }
        assertEquals(original.comparable().copy(avatar = null, assets = withoutFiles), again.comparable())
      }
    }
  }

  @Test
  fun aCcv2CardReadsItsFieldsFromData() = runTest {
    val brannoc = library.importedCharacter(ImporterCorpus.ccv2Json)

    assertEquals(
      CharacterFields(
        name = "Brannoc",
        description = "A smith of the hill country.",
        personality = "Gruff",
        scenario = "The forge at dusk.",
        firstMessage = "Mind the sparks.",
        alternateGreetings = listOf("Back again?", "Quiet day."),
        exampleMessages = "{{char}}: Hm.",
        creator = "ashfall",
        characterVersion = "1.2",
        creatorNotes = "Tested with long chats.",
        systemPrompt = "Stay in role. {{original}}",
        postHistoryInstructions = "{{original}} Keep it short.",
        characterNote = CharacterNote("Brannoc limps.", depth = 4, role = "system"),
      ),
      brannoc.fields,
    )
    assertEquals(listOf("smith", "fantasy"), brannoc.keptFields.tags)
  }

  @Test
  fun aCcv1CardHasNoCreatorEvenWhenItsTopLevelNamesOne() = runTest {
    val card = CorpusFile("v1.json", """{"name": "Ren", "description": "d", "creator": "someone"}""".toByteArray())

    assertEquals("", library.importedCharacter(card).fields.creator)
  }

  @Test
  fun aCcv3CardKeepsItsGroupGreetingsLanguagesSourceAndDates() = runTest {
    val isolde = library.importedCharacter(ImporterCorpus.ccv3Json)

    assertEquals("Isolde", isolde.fields.nickname)
    assertEquals(listOf("Everyone, gather round."), isolde.fields.groupOnlyGreetings)
    assertEquals(
      KeptCardFields(
        tags = listOf("maps"),
        source = listOf("https://example.com/isolde"),
        creationDate = 1_700_000_000,
        modificationDate = 1_700_100_000,
        creatorNotesInOtherLanguages = mapOf("en" to "English notes.", "de" to "Deutsche Hinweise."),
      ),
      isolde.keptFields,
    )
  }

  @Test
  fun aCharxAvatarIsItsMainIconAndItsOtherFilesAreStoredAssets() = runTest {
    val tamsin = library.importedCharacter(ImporterCorpus.charx)

    assertEquals(sha256(CardFixtures.image(40)), tamsin.avatar)
    assertEquals(
      listOf(
        CharacterAsset("icon", "side", "png", AssetSource.Stored(sha256(CardFixtures.image(41)))),
        CharacterAsset("emotion", "smile", "png", AssetSource.Stored(sha256(CardFixtures.image(42)))),
      ),
      tamsin.assets,
    )
    assertTrue(tamsin.keptFields.hasEmbeddedLorebook)
  }

  @Test
  fun aCharxEmbeddedLorebookExportsAgain() = runTest {
    val tamsin = library.importedCharacter(ImporterCorpus.charx)

    val (_, json) = library.export(tamsin.id, CardFormat.Json)
    val data = Json.parseToJsonElement(json.decodeToString()).jsonObject.getValue("data").jsonObject

    assertEquals("River lore", data.getValue("character_book").jsonObject.getValue("name").jsonPrimitive.content)
  }

  @Test
  fun aPngExportCarriesACharaAndACcv3ChunkAndNoStaleCard() = runTest {
    val both = library.importedCharacter(ImporterCorpus.bothChunksDiffer)

    val (_, png) = library.export(both.id, CardFormat.Png)

    assertEquals("From ccv3", both.fields.name)
    assertEquals(listOf("chara", "ccv3"), CardSources.pngTexts(png).keys.toList())
  }

  @Test
  fun aDanglingAssetUriNeverCollidesWithAnEmbeddedOne() = runTest {
    val card = """{"spec": "chara_card_v3", "data": {"name": "Dangling", "assets": [
      {"type": "background", "uri": "__asset:0", "name": "gone", "ext": "png"},
      {"type": "background", "uri": "__asset:1", "name": "kept", "ext": "png"}]}}"""
    val png = CardFixtures.png(
      CardFixtures.image(5),
      "ccv3" to CardFixtures.base64(card.toByteArray()),
      "chara-ext-asset_:1" to CardFixtures.base64(CardFixtures.image(6)),
    )
    val dangling = library.importedCharacter(CorpusFile("dangling.png", png))

    val (_, exported) = library.export(dangling.id, CardFormat.Png)
    val again = library.importedCharacter(CorpusFile("again.png", exported))

    assertEquals(dangling.assets, again.assets)
  }

  @Test
  fun aNonPngAvatarIsConvertedForAPngExport() = runTest {
    val converted = CardFixtures.image(70)
    library.close()
    library = CharacterLibrary(directory, transcoder = { _, _ -> TranscodedAvatar(converted, downsampled = true) })
    val character = library.importedCharacter(gifCharx())

    val (result, png) = library.export(character.id, CardFormat.Png)
    val again = library.importedCharacter(CorpusFile("again.png", png))

    assertEquals(ExportResult.Saved(AvatarChange.Converted(firstFrameOnly = false, downsampled = true)), result)
    assertEquals(sha256(converted), again.avatar)
  }

  @Test
  fun anAvatarTheCodecCantReadExportsThePlaceholderAndSaysSo() = runTest {
    val character = library.importedCharacter(gifCharx())

    val (result, png) = library.export(character.id, CardFormat.Png)
    val again = library.importedCharacter(CorpusFile("again.png", png))

    assertEquals(ExportResult.Saved(AvatarChange.Unreadable), result)
    assertEquals(sha256(PlaceholderAvatar.png(avatarGradientColors(character.id))), again.avatar)
  }

  @Test
  fun aJsonExportNamesTheStoredFilesItLeavesOut() = runTest {
    val tamsin = library.importedCharacter(ImporterCorpus.charx)

    assertEquals(listOf(LeftOutOfJson.Avatar) + tamsin.assets.map(LeftOutOfJson::Asset), tamsin.leftOutOfJson)
  }

  @Test
  fun aReimportAddsNoAssetFiles() = runTest {
    library.importedCharacter(ImporterCorpus.charx)
    val before = library.storedFiles()

    library.importedCharacter(ImporterCorpus.charx)

    assertEquals(before, library.storedFiles())
    assertTrue(before.isNotEmpty())
  }

  @Test
  fun aMissingStoredFileStopsTheExportBeforeWriting() = runTest {
    val tamsin = library.importedCharacter(ImporterCorpus.charx)
    java.nio.file.Files.delete(Path.of(library.assets.pathOf(checkNotNull(tamsin.avatar))))

    val (result, bytes) = library.export(tamsin.id, CardFormat.Png)

    val missing = ExportProblem(ExportFailure.StoredFileUnreadable, incompleteFileLeft = false)
    assertEquals(ExportResult.NotSaved(missing), result)
    assertEquals(0, bytes.size)
  }

  private fun gifCharx(): CorpusFile {
    val card = """
      {"spec": "chara_card_v3", "data": {"name": "Gif",
        "assets": [{"type": "icon", "uri": "embeded://a.gif", "name": "main", "ext": "gif"}]}}
    """.toByteArray()
    val gif = "GIF89a".toByteArray() + ByteArray(20)
    return CorpusFile("gif.charx", CardFixtures.zip("card.json" to card, "a.gif" to gif, method = ZipEntry.STORED))
  }

  private fun Character.comparable() = copy(id = Uuid.NIL, importedAt = 0)
}
