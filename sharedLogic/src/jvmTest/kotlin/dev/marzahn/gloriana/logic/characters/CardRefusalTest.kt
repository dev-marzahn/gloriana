package dev.marzahn.gloriana.logic.characters

import dev.marzahn.gloriana.logic.characters.CardFixtures.base64
import dev.marzahn.gloriana.logic.characters.CardFixtures.image
import dev.marzahn.gloriana.logic.characters.CardFixtures.png
import dev.marzahn.gloriana.logic.characters.CardFixtures.zip
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CardRefusalTest {
  @TempDir
  lateinit var directory: Path

  private lateinit var library: CharacterLibrary

  @BeforeEach
  fun open() {
    library = CharacterLibrary(directory.resolve("library"))
  }

  @AfterEach
  fun close() = library.close()

  @TestFactory
  fun eachRefusedFileIsNamedWithItsReasonAndAddsNothing() = refusals().map { (file, reason) ->
    DynamicTest.dynamicTest(file.name) {
      runTest {
        val result = library.importOne(file)

        assertEquals(FileImportResult.Failed(reason), result)
        assertEquals(emptyList(), library.list())
        assertEquals(emptyList(), stagedOrStoredFiles())
      }
    }
  }

  @Test
  fun anUnsafeEntryIsNeverWrittenAnywhere() = runTest {
    listOf(ImporterCorpus.zipEscape, ImporterCorpus.charxEscape, ImporterCorpus.charxAbsolute)
      .forEach { library.importOne(it) }

    val written = directory.toFile().walkTopDown().filter { it.name.startsWith("MARKER-") }.toList()
    assertEquals(emptyList(), written)
  }

  @Test
  fun aFileThatCannotBeOpenedIsNamedSo() = runTest {
    val gone = object : PickedFile {
      override val name = "gone.png"

      override fun open(): OpenedFile = OpenedFile.Unavailable
    }

    assertEquals(FileImportResult.Failed(ImportFailure.CantBeOpened), library.importOne(gone))
  }

  @Test
  fun aFileThatFailsWhileBeingReadIsNamedAsUnopenable() = runTest {
    val failing = object : PickedFile {
      override val name = "failing.png"

      override fun open(): OpenedFile = OpenedFile.Opened(
        object : ByteSource {
          override fun read(into: ByteArray, offset: Int, length: Int): Int = throw java.io.IOException("unplugged")

          override fun close() = Unit
        },
      )
    }

    assertEquals(FileImportResult.Failed(ImportFailure.CantBeOpened), library.importOne(failing))
    assertEquals(emptyList(), stagedOrStoredFiles())
  }

  private fun stagedOrStoredFiles() =
    library.directory.resolve("assets").toFile().walkTopDown().filter { it.isFile }.toList()

  private fun refusals(): List<Pair<CorpusFile, ImportFailure>> = listOf(
    ImporterCorpus.brokenPng to ImportFailure.Damaged,
    ImporterCorpus.pngWithBadCrc to ImportFailure.Damaged,
    charxWithAnEntryLargerThanDeclared() to ImportFailure.Unsafe,
    CorpusFile("large.bin", ByteArray(17 * 1024 * 1024) { 'x'.code.toByte() }) to ImportFailure.NotACharacterCard,
    pngWithCorruptAssetChunk() to ImportFailure.Damaged,
    charxClaimingAHugeDirectory() to ImportFailure.Damaged,
    charxWithTruncatedDeflate() to ImportFailure.Damaged,
    ImporterCorpus.unparsableJson to ImportFailure.Damaged,
    ImporterCorpus.unpackableCharx to ImportFailure.Damaged,
    ImporterCorpus.charxWithoutCard to ImportFailure.Damaged,
    ImporterCorpus.pngWithoutCard to ImportFailure.NotACharacterCard,
    ImporterCorpus.lorebook to ImportFailure.NotACharacterCard,
    ImporterCorpus.preset to ImportFailure.NotACharacterCard,
    ImporterCorpus.zipWithoutCard to ImportFailure.NotACharacterCard,
    ImporterCorpus.textFile to ImportFailure.NotACharacterCard,
    oversizedJson() to ImportFailure.TooLarge(SizeLimit.CardJson),
    oversizedPngCard() to ImportFailure.TooLarge(SizeLimit.CardJson),
    charxWithTooManyEntries() to ImportFailure.TooLarge(SizeLimit.ZipEntryCount),
    charxWithAnOversizedEntry() to ImportFailure.TooLarge(SizeLimit.ZipEntrySize),
    ImporterCorpus.zipEscape to ImportFailure.Unsafe,
    ImporterCorpus.charxEscape to ImportFailure.Unsafe,
    ImporterCorpus.charxAbsolute to ImportFailure.Unsafe,
    ImporterCorpus.zipBomb to ImportFailure.Unsafe,
    ImporterCorpus.charxBomb to ImportFailure.Unsafe,
    unsafeAndTooLarge() to ImportFailure.Unsafe,
  )

  private fun oversizedJson(): CorpusFile {
    val padding = "x".repeat(16 * 1024 * 1024)
    return CorpusFile("huge.json", """{"name": "Huge", "description": "$padding"}""".toByteArray())
  }

  private fun oversizedPngCard(): CorpusFile {
    val padding = "x".repeat(16 * 1024 * 1024)
    val card = """{"spec": "chara_card_v3", "data": {"name": "Huge", "description": "$padding"}}""".toByteArray()
    return CorpusFile("huge.png", png(image(1), "ccv3" to base64(card)))
  }

  private fun charxWithTooManyEntries(): CorpusFile {
    val entries = (0..10_000).map { "e$it.txt" to ByteArray(0) }.toTypedArray()
    return CorpusFile("many.charx", zip("card.json" to cardJson(), *entries, method = ZipEntry.STORED))
  }

  private fun charxWithAnOversizedEntry(): CorpusFile {
    val zip = zip("card.json" to cardJson(), "big.bin" to ByteArray(16), method = ZipEntry.STORED)
    val declared = declareSizes(zip, entryName = "big.bin", size = 600L * MEGABYTE, compressedSize = 10L * MEGABYTE)
    return CorpusFile("big.charx", declared)
  }

  private fun unsafeAndTooLarge(): CorpusFile {
    val entries = (0..10_000).map { "e$it.txt" to ByteArray(0) }.toTypedArray()
    val escape = "../MARKER-ESCAPE.txt" to ByteArray(1)
    return CorpusFile("both.charx", zip("card.json" to cardJson(), *entries, escape, method = ZipEntry.STORED))
  }

  private fun charxClaimingAHugeDirectory(): CorpusFile {
    val endRecord = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN)
      .putInt(0x06054b50).putShort(0).putShort(0).putShort(1).putShort(1)
      .putInt(0x7FFFFFF0).putInt(0).putShort(0)
    return CorpusFile("huge-directory.charx", endRecord.array())
  }

  private fun charxWithTruncatedDeflate(): CorpusFile {
    val random = java.util.Random(1)
    val noise = String(CharArray(5_000) { 'a' + random.nextInt(26) })
    val card = """{"name": "Wren", "description": "$noise"}""".toByteArray()
    val zip = zip("card.json" to card)
    val compressed = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN).let { bytes ->
      (0 until zip.size - 4).first { bytes.getInt(it) == CENTRAL_HEADER }.let { bytes.getInt(it + 20) }
    }
    return CorpusFile("truncated.charx", declareSizes(zip, "card.json", card.size.toLong(), compressed / 2L))
  }

  private fun charxWithAnEntryLargerThanDeclared(): CorpusFile {
    val zip = zip("card.json" to cardJson(), "big.bin" to ByteArray(4_000))
    return CorpusFile("liar.charx", declareSizes(zip, entryName = "card.json", size = 10))
  }

  private fun pngWithCorruptAssetChunk(): CorpusFile {
    val card = """{"spec": "chara_card_v3", "data": {"name": "Bad asset"}}""".toByteArray()
    val png = png(image(2), "ccv3" to base64(card), "chara-ext-asset_:0" to "@@@=".toByteArray())
    return CorpusFile("bad-asset.png", png)
  }

  private fun cardJson() ="""{"name": "Wren", "description": "x"}""".toByteArray()

  private fun declareSizes(zip: ByteArray, entryName: String, size: Long, compressedSize: Long? = null): ByteArray {
    val file = Files.createTempFile(directory, "declared", ".zip")
    Files.write(file, zip)
    RandomAccessFile(file.toFile(), "rw").use { raf ->
      val bytes = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN)
      var position = 0
      while (position < zip.size - 4) {
        if (bytes.getInt(position) == CENTRAL_HEADER) {
          val nameLength = bytes.getShort(position + 28).toInt()
          val name = String(zip, position + 46, nameLength)
          if (name == entryName) {
            raf.seek(position + 20L)
            val sizes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            val compressed = compressedSize?.toInt() ?: bytes.getInt(position + 20)
            raf.write(sizes.putInt(compressed).putInt(size.toInt()).array())
          }
        }
        position++
      }
    }
    return Files.readAllBytes(file)
  }

  private companion object {
    const val CENTRAL_HEADER = 0x02014b50
    const val MEGABYTE = 1024L * 1024
  }
}
