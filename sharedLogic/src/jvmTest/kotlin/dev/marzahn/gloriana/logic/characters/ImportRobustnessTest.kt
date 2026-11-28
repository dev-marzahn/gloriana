package dev.marzahn.gloriana.logic.characters

import dev.marzahn.gloriana.logic.brand.avatarGradientColors
import dev.marzahn.gloriana.logic.characters.CardFixtures.sha256
import dev.marzahn.gloriana.logic.characters.files.PlaceholderAvatar
import dev.marzahn.gloriana.logic.files.CorpusFile
import dev.marzahn.gloriana.logic.files.ExportFailure
import dev.marzahn.gloriana.logic.files.ExportProblem
import dev.marzahn.gloriana.logic.files.OpenedFile
import dev.marzahn.gloriana.logic.files.PickedFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.zip.ZipEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ImportRobustnessTest {
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
  fun aBrokenCharxThenAGoodPngInOnePickImportsThePng() = runTest {
    val results = library.repository.import(listOf(ImporterCorpus.unpackableCharx, ImporterCorpus.ccv1Png), {
      error("asked")
    })

    assertEquals(FileImportResult.Failed(ImportFailure.Damaged(DamagedAs.Zip)), results[0])
    assertIs<FileImportResult.Imported>(results[1])
    assertEquals(emptyList(), library.stagingFiles())
  }

  @Test
  fun aPickCancelledDuringTheReadLeavesNoStagedFiles() = runTest {
    val reading = CompletableDeferred<Unit>()
    val release = CountDownLatch(1)
    val slow = object : PickedFile {
      override val name = "slow.charx"

      override fun open(): OpenedFile {
        reading.complete(Unit)
        release.await()
        return ImporterCorpus.charx.open()
      }
    }

    val pick = async { library.repository.import(listOf(slow), { error("asked") }) }
    withContext(Dispatchers.Default) { reading.await() }
    pick.cancel()
    release.countDown()
    pick.cancelAndJoin()

    assertEquals(emptyList(), library.stagingFiles())
    assertEquals(emptyList(), library.list())
  }

  @Test
  fun anUpdateTakesTheMatchAgainWhenItsTargetGoesMeanwhile() = runTest {
    val older = library.importedCharacter(ImporterCorpus.charx)
    library.importedCharacter(ImporterCorpus.charx)

    val result = library.repository.import(listOf(ImporterCorpus.charxUpdated), { question ->
      library.repository.delete(question.existing.id)
      AlreadyInLibraryAnswer(AlreadyInLibraryChoice.Update)
    }).single()

    assertEquals(FileImportResult.Updated(older.id), result)
    assertEquals(listOf(older.id), library.list().map { it.id })
    assertEquals("Retired.", library.character(older.id).fields.description)
  }

  @Test
  fun anAvatarTheTranscoderFailsOnExportsThePlaceholder() = runTest {
    library.close()
    library = CharacterLibrary(directory, transcoder = { _, _ -> error("not a JPEG after all") })
    val jpegHeader = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(32) { 7 }
    val card = """{"spec": "chara_card_v3", "data": {"name": "Jpeg",
      "assets": [{"type": "icon", "uri": "embeded://a.jpg", "name": "main", "ext": "jpg"}]}}""".toByteArray()
    val charx = CardFixtures.zip("card.json" to card, "a.jpg" to jpegHeader, method = ZipEntry.STORED)
    val character = library.importedCharacter(CorpusFile("jpeg.charx", charx))

    val (result, png) = library.export(character.id, CardFormat.Png)
    val again = library.importedCharacter(CorpusFile("again.png", png))

    assertEquals(ExportResult.Saved(AvatarChange.Unreadable), result)
    assertEquals(sha256(PlaceholderAvatar.png(avatarGradientColors(character.id))), again.avatar)
  }

  @Test
  fun aStoredAssetMissingFromTheStoreStopsAPngExport() = runTest {
    val tamsin = library.importedCharacter(ImporterCorpus.charx)
    val asset = tamsin.assets.first().source as AssetSource.Stored
    java.nio.file.Files.delete(Path.of(library.assets.pathOf(asset.hash)))

    val (result, _) = library.export(tamsin.id, CardFormat.Png)

    assertEquals(ExportResult.NotSaved(ExportProblem(ExportFailure.StoredFileUnreadable, false)), result)
  }
}
