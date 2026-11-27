package dev.marzahn.gloriana.logic.characters.files

import dev.marzahn.gloriana.logic.assets.AssetStore
import dev.marzahn.gloriana.logic.assets.StorageFullException
import dev.marzahn.gloriana.logic.assets.StoreIoException
import dev.marzahn.gloriana.logic.assets.storeIo
import dev.marzahn.gloriana.logic.characters.CardFileRead
import dev.marzahn.gloriana.logic.characters.CardReader
import dev.marzahn.gloriana.logic.characters.ImportFailure
import dev.marzahn.gloriana.logic.characters.ImportedCard
import dev.marzahn.gloriana.logic.characters.OpenedFile
import dev.marzahn.gloriana.logic.characters.PickedFile
import dev.marzahn.gloriana.logic.characters.SizeLimit
import dev.marzahn.gloriana.logic.characters.card.CardJsonRead
import java.io.BufferedInputStream
import java.io.IOException
import java.nio.file.Files

private const val SNIFF_LENGTH = 64
private val ZIP_MAGIC = listOf(byteArrayOf(0x50, 0x4B, 0x03, 0x04), byteArrayOf(0x50, 0x4B, 0x05, 0x06))

internal class JvmCardReader(private val store: AssetStore) : CardReader {
  override fun read(file: PickedFile): CardFileRead = when (val opened = file.open()) {
    OpenedFile.Unavailable -> CardFileRead.Failed(ImportFailure.CantBeOpened)
    is OpenedFile.Opened -> {
      val staging = Staging(store)
      var result: CardFileRead? = null
      try {
        result = opened.source.asSourceStream().buffered().use { input -> readGuarded(file.name, input, staging) }
        result
      } finally {
        if (result !is CardFileRead.Card) staging.discardAll()
      }
    }
  }

  @Suppress("TooGenericExceptionCaught")
  private fun readGuarded(name: String, input: BufferedInputStream, staging: Staging): CardFileRead = try {
    readKnownKind(name, input, staging)
  } catch (_: SourceReadException) {
    CardFileRead.Failed(ImportFailure.CantBeOpened)
  } catch (_: StorageFullException) {
    CardFileRead.Failed(ImportFailure.StorageFull)
  } catch (_: StoreIoException) {
    CardFileRead.Failed(ImportFailure.CouldNotSave)
  } catch (_: EntryLargerThanDeclared) {
    CardFileRead.Failed(ImportFailure.Unsafe)
  } catch (_: IOException) {
    CardFileRead.Failed(ImportFailure.Damaged)
  } catch (_: RuntimeException) {
    CardFileRead.Failed(ImportFailure.Damaged)
  }

  private fun readKnownKind(name: String, input: BufferedInputStream, staging: Staging): CardFileRead {
    input.mark(SNIFF_LENGTH)
    val start = input.readUpTo(SNIFF_LENGTH)
    input.reset()
    return when {
      start.startsWith(PNG_SIGNATURE) -> PngCardReader(staging).read(input)
      ZIP_MAGIC.any { start.startsWith(it) } -> store.withTemporaryFile { zip ->
        storeIo { Files.newOutputStream(zip).use { input.copyTo(it) } }
        CharxReader(staging).read(name, zip)
      }
      CardJson.startsLikeJson(start) -> readJson(input)
      else -> CardFileRead.Failed(ImportFailure.NotACharacterCard)
    }
  }

  private fun readJson(input: BufferedInputStream): CardFileRead {
    val bytes = CardJson.readWithin(input, CardLimits.CARD_JSON_BYTES)
      ?: return CardFileRead.Failed(ImportFailure.TooLarge(SizeLimit.CardJson))
    return when (val card = CardJson.read(bytes)) {
      is CardJsonRead.Card -> CardFileRead.Card(
        ImportedCard.JsonOnly(card.card, card.card.assets.map { it.toAsset(stored = null) }),
      )
      CardJsonRead.Damaged -> CardFileRead.Failed(ImportFailure.Damaged)
      CardJsonRead.NotACard -> CardFileRead.Failed(ImportFailure.NotACharacterCard)
    }
  }
}

internal fun ByteArray.startsWith(prefix: ByteArray) =
  size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
