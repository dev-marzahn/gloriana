package dev.marzahn.gloriana.logic.characters

import dev.marzahn.gloriana.logic.characters.CardFixtures.sha256
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import dev.marzahn.gloriana.logic.storage.RowKey
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CharacterImportTest {
  @TempDir
  lateinit var directory: Path

  private lateinit var library: CharacterLibrary

  @BeforeEach
  fun open() {
    library = CharacterLibrary(directory)
  }

  @AfterEach
  fun close() = library.close()

  @Test
  fun aMixedPickImportsInTurnAndReportsEachFile() = runTest {
    library.importOne(ImporterCorpus.ccv1Json)
    val questions = mutableListOf<AlreadyInLibrary>()
    val statuses = mutableListOf<Pair<Int, FileImportStatus>>()

    val results = library.repository.import(
      listOf(ImporterCorpus.ccv2Json, ImporterCorpus.brokenPng, ImporterCorpus.ccv1Png, ImporterCorpus.textFile),
      { question ->
        questions += question
        AlreadyInLibraryAnswer(AlreadyInLibraryChoice.Copy)
      },
      { index, status -> statuses += index to status },
    )

    assertIs<FileImportResult.Imported>(results[0])
    assertEquals(FileImportResult.Failed(ImportFailure.Damaged(DamagedAs.Png)), results[1])
    assertIs<FileImportResult.Imported>(results[2])
    assertEquals(FileImportResult.Failed(ImportFailure.NotACharacterCard), results[3])
    assertEquals(listOf("wren.png"), questions.map { it.fileName })
    assertEquals(1, questions.single().filesLeftAfterThis)
    assertEquals(listOf("Wren", "Brannoc", "Wren"), library.list().map { it.name })
    assertEquals(
      (0..3).map { it to FileImportStatus.Waiting } +
        results.flatMapIndexed { index, result ->
          listOf(index to FileImportStatus.Importing, index to FileImportStatus.Done(result))
        },
      statuses,
    )
  }

  @Test
  fun aCharacterImportedEarlierInTheSamePickCounts() = runTest {
    val asked = mutableListOf<String>()

    library.repository.import(listOf(ImporterCorpus.charx, ImporterCorpus.charxUpdated), { question ->
      asked += question.fileName
      AlreadyInLibraryAnswer(AlreadyInLibraryChoice.Cancel)
    })

    assertEquals(listOf("tamsin-1.1.charx"), asked)
    assertEquals(1, library.list().size)
  }

  @Test
  fun theAnswerCanHoldForTheRestOfThePick() = runTest {
    library.importOne(ImporterCorpus.ccv3PngFile)
    library.importOne(ImporterCorpus.charx)
    var asked = 0

    val results = library.repository.import(
      listOf(ImporterCorpus.ccv3PngFile, ImporterCorpus.charx, ImporterCorpus.ccv1Json),
      {
        asked++
        AlreadyInLibraryAnswer(AlreadyInLibraryChoice.Copy, forTheRestOfThePick = true)
      },
    )

    assertEquals(1, asked)
    assertTrue(results.all { it is FileImportResult.Imported })
    assertEquals(5, library.list().size)
  }

  @Test
  fun cancelLeavesTheFileOutAndWritesNothing() = runTest {
    library.importOne(ImporterCorpus.ccv3PngFile)

    val result = library.importOne(ImporterCorpus.ccv3PngFile, AlreadyInLibraryChoice.Cancel)

    assertEquals(FileImportResult.LeftOut, result)
    assertEquals(1, library.list().size)
  }

  @Test
  fun theSameNameWithAnotherCreatorIsNotAlreadyThere() = runTest {
    library.importOne(ImporterCorpus.ccv3PngFile)

    val result = library.repository.import(listOf(ImporterCorpus.sameNameOtherCreator), { error("asked") }).single()

    assertIs<FileImportResult.Imported>(result)
    assertEquals(2, library.list().size)
  }

  @Test
  fun aCopyIsListedFirstAndKeepsTheFilesName() = runTest {
    library.importOne(ImporterCorpus.ccv3PngFile)

    val copy = library.importOne(ImporterCorpus.ccv3PngFile, AlreadyInLibraryChoice.Copy)

    assertIs<FileImportResult.Imported>(copy)
    assertEquals(listOf(copy.id), library.list().take(1).map { it.id })
    assertEquals(listOf("Mara", "Mara"), library.list().map { it.name })
  }

  @Test
  fun updateReplacesTheLastImportedMatchAndKeepsItsIdAndPlace() = runTest {
    library.importOne(ImporterCorpus.charx)
    val copy = library.importedCharacter(ImporterCorpus.charx)
    library.clock.epochMillis = 9_000

    val result = library.importOne(ImporterCorpus.charxUpdated, AlreadyInLibraryChoice.Update)
    val updated = library.character(copy.id)

    assertEquals(FileImportResult.Updated(copy.id), result)
    assertEquals(copy.importedAt, updated.importedAt)
    assertEquals(listOf(copy.id), library.list().take(1).map { it.id })
    assertEquals("Retired.", updated.fields.description)
    assertEquals("1.1", updated.fields.characterVersion)
    assertEquals(listOf("Evening."), updated.fields.alternateGreetings)
    assertEquals(sha256(CardFixtures.image(43)), updated.avatar)
    assertEquals(emptyList(), updated.assets)
    assertEquals(listOf("river"), updated.keptFields.tags)
    val (_, json) = library.export(copy.id, CardFormat.Json)
    assertNull(Json.parseToJsonElement(json.decodeToString()).jsonObject.getValue("data").jsonObject["character_book"])
  }

  @Test
  fun updateWritesATombstoneForEachChildRowItReplaces() = runTest {
    val original = library.importedCharacter(ImporterCorpus.ccv2Json)

    library.importOne(ImporterCorpus.ccv2Json, AlreadyInLibraryChoice.Update)

    val tombstones = library.database.tombstoneDao().all().map { it.entityType }
    assertEquals(listOf("character_greetings", "character_greetings"), tombstones)
    assertEquals(original.fields, library.character(original.id).fields)
  }

  @Test
  fun updateKeepsPronounsEvenWhenTheFileHasItsOwn() = runTest {
    val ash = library.importedCharacter(ImporterCorpus.withPronouns)
    val otherPronouns = CorpusFile(
      "ash-other.json",
      """{"spec": "chara_card_v3", "data": {"name": "Ash", "creator": "glade", "description": "Third.",
        "extensions": {"gloriana": {"pronouns": {"sub": "she"}}}}}""".toByteArray(),
    )

    library.importOne(ImporterCorpus.withoutPronounsUpdate, AlreadyInLibraryChoice.Update)
    library.importOne(otherPronouns, AlreadyInLibraryChoice.Update)
    val updated = library.character(ash.id)
    val (_, json) = library.export(ash.id, CardFormat.Json)
    val again = library.importedCharacter(CorpusFile("again.json", json))

    assertEquals(Pronouns(subject = "they", objective = "them", possessive = "their"), updated.pronouns)
    assertEquals("Third.", updated.fields.description)
    assertEquals(updated.pronouns, again.pronouns)
  }

  @Test
  fun anUpdateFromJsonKeepsTheStoredAvatarAndFilesAfterTheJsonsAssets() = runTest {
    val tamsin = library.importedCharacter(ImporterCorpus.charx)
    val json = CorpusFile(
      "tamsin.json",
      """{"spec": "chara_card_v3", "data": {"name": "Tamsin", "creator": "oarlock", "description": "From JSON.",
        "assets": [
          {"type": "background", "uri": "https://example.com/river.png", "name": "river", "ext": "png"},
          {"type": "emotion", "uri": "embeded://smile.png", "name": "smile", "ext": "png"}
        ]}}""".toByteArray(),
    )

    library.importOne(json, AlreadyInLibraryChoice.Update)
    val updated = library.character(tamsin.id)

    assertEquals("From JSON.", updated.fields.description)
    assertEquals(tamsin.avatar, updated.avatar)
    assertEquals(
      listOf(CharacterAsset("background", "river", "png", AssetSource.Uri("https://example.com/river.png"))) +
        tamsin.assets,
      updated.assets,
    )
  }

  @Test
  fun anUpdateWhoseTargetWasDeletedMeanwhileImportsAsNew() = runTest {
    val tamsin = library.importedCharacter(ImporterCorpus.charx)

    val result = library.repository.import(listOf(ImporterCorpus.charxUpdated), { question ->
      library.repository.delete(question.existing.id)
      AlreadyInLibraryAnswer(AlreadyInLibraryChoice.Update)
    }).single()

    assertIs<FileImportResult.Imported>(result)
    assertEquals(listOf("Tamsin"), library.list().map { it.name })
    assertEquals("Retired.", library.character(result.id).fields.description)
    assertTrue(result.id != tamsin.id)
  }

  @Test
  fun theUpdateQuestionSaysWhenTheStoredEmbeddedLorebookWouldGo() = runTest {
    library.importedCharacter(ImporterCorpus.charx)
    val questions = mutableListOf<AlreadyInLibrary>()

    library.repository.import(listOf(ImporterCorpus.charxUpdated, ImporterCorpus.charx), { question ->
      questions += question
      AlreadyInLibraryAnswer(AlreadyInLibraryChoice.Cancel)
    })

    assertEquals(listOf(true, false), questions.map { it.losesEmbeddedLorebook })
  }

  @Test
  fun theUpdateQuestionSaysWhenTheFileHoldsNoFiles() = runTest {
    library.importedCharacter(ImporterCorpus.ccv2Json)
    val questions = mutableListOf<AlreadyInLibrary>()

    library.repository.import(listOf(ImporterCorpus.ccv2Json, ImporterCorpus.ccv2Png), { question ->
      questions += question
      AlreadyInLibraryAnswer(AlreadyInLibraryChoice.Cancel)
    })

    assertEquals(listOf(true, false), questions.map { it.fileHoldsNoFiles })
  }

  @Test
  fun aFailedWriteFailsOnlyItsFileAndThePickGoesOn() = runTest {
    var failNext = true
    library.close()
    library = CharacterLibrary(directory, faults = { point ->
      if (point == CharacterWritePoint.InsertRowWritten && failNext) {
        failNext = false
        error("the write failed")
      }
    })

    val results = library.repository.import(listOf(ImporterCorpus.charx, ImporterCorpus.ccv1Json), { error("asked") })

    assertEquals(FileImportResult.Failed(ImportFailure.CouldNotSave), results[0])
    assertIs<FileImportResult.Imported>(results[1])
    assertEquals(listOf("Wren"), library.list().map { it.name })
  }

  @Test
  fun aWriteThatRunsOutOfSpaceSaysStorageFull() = runTest {
    library.close()
    library = CharacterLibrary(directory, faults = { throw java.io.IOException("No space left on device") })

    assertEquals(FileImportResult.Failed(ImportFailure.StorageFull), library.importOne(ImporterCorpus.charx))
    assertEquals(emptyList(), library.stagingFiles())
  }

  @Test
  fun leavingWhileTheQuestionIsOpenDiscardsTheStagedFiles() = runTest {
    library.importedCharacter(ImporterCorpus.charx)
    val filesBefore = library.storedFiles()

    val asked = CompletableDeferred<Unit>()
    val pick = async {
      library.repository.import(listOf(ImporterCorpus.charxUpdated), {
        asked.complete(Unit)
        awaitCancellation()
      })
    }
    asked.await()
    pick.cancelAndJoin()

    assertEquals(emptyList(), library.stagingFiles())
    assertEquals(filesBefore, library.storedFiles())
  }

  @Test
  fun pronounsExportUnderGlorianasExtension() = runTest {
    val ash = library.importedCharacter(ImporterCorpus.withPronouns)

    val (_, json) = library.export(ash.id, CardFormat.Json)
    val again = library.importedCharacter(CorpusFile("again.json", json))

    assertEquals(ash.pronouns, again.pronouns)
  }

  @Test
  fun deleteRemovesTheCharacterAndItsRowsWithTombstones() = runTest {
    val tamsin = library.importedCharacter(ImporterCorpus.charx)

    val deleted = library.repository.delete(tamsin.id)

    assertEquals(DeleteResult.Deleted, deleted)
    assertEquals(emptyList(), library.list())
    val tombstones = library.database.tombstoneDao().all()
    assertEquals(
      listOf("character_assets", "character_assets", "characters"),
      tombstones.map { it.entityType }.sorted(),
    )
    assertTrue(tombstones.any { it.rowKey == RowKey.Row(tamsin.id) })
    assertEquals(ExportResult.CharacterGone, library.export(tamsin.id, CardFormat.Png).first)
  }

  @Test
  fun deletingACharacterThatIsGoneChangesNothing() = runTest {
    val tamsin = library.importedCharacter(ImporterCorpus.charx)
    library.repository.delete(tamsin.id)

    assertEquals(DeleteResult.Gone, library.repository.delete(tamsin.id))
    assertEquals(3, library.database.tombstoneDao().all().size)
  }

  @Test
  fun aNewerSpecVersionAndAnUnknownSpecImportWhatTheAppReads() = runTest {
    val newer = library.importedCharacter(ImporterCorpus.newerSpecVersion)
    val unknown = library.importedCharacter(ImporterCorpus.unknownSpec)

    assertEquals("Next", newer.fields.name)
    assertEquals("Odd", unknown.fields.name)
    assertEquals("", unknown.fields.description)
    val (_, json) = library.export(unknown.id, CardFormat.Json)
    val data = Json.parseToJsonElement(json.decodeToString()).jsonObject.getValue("data").jsonObject
    assertTrue("MARKER-UNKNOWN-SPEC" in data)
  }

  @Test
  fun aPngWithBothChunksReadsCcv3() = runTest {
    assertEquals("From ccv3", library.importedCharacter(ImporterCorpus.bothChunksDiffer).fields.name)
  }

  @Test
  fun aPngWithADamagedCcv3ChunkFallsBackToChara() = runTest {
    val png = CardFixtures.png(
      CardFixtures.image(1),
      "ccv3" to "not base64 json".toByteArray(),
      "chara" to CardFixtures.base64("""{"name": "Fallback", "description": "x"}""".toByteArray()),
    )

    assertEquals("Fallback", library.importedCharacter(CorpusFile("fallback.png", png)).fields.name)
  }

  @Test
  fun aCharxWithoutAMainIconUsesItsFirstIcon() = runTest {
    val card = """{"spec": "chara_card_v3", "data": {"name": "Icons", "assets": [
      {"type": "icon", "uri": "embeded://b.png", "name": "second", "ext": "png"},
      {"type": "icon", "uri": "embeded://a.png", "name": "first", "ext": "png"}]}}"""
    val charx = CardFixtures.zip(
      "card.json" to card.toByteArray(),
      "a.png" to CardFixtures.image(1),
      "b.png" to CardFixtures.image(2),
    )

    val icons = library.importedCharacter(CorpusFile("icons.charx", charx))

    assertEquals(sha256(CardFixtures.image(2)), icons.avatar)
  }
}
