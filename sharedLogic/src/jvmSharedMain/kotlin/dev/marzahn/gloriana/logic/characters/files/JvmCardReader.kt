package dev.marzahn.gloriana.logic.characters.files

import dev.marzahn.gloriana.logic.assets.AssetStore
import dev.marzahn.gloriana.logic.assets.StorageFullException
import dev.marzahn.gloriana.logic.assets.StoreIoException
import dev.marzahn.gloriana.logic.assets.storeIo
import dev.marzahn.gloriana.logic.characters.CardFileRead
import dev.marzahn.gloriana.logic.characters.CardReader
import dev.marzahn.gloriana.logic.characters.DamagedAs
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

private enum class FileKind(val unreadable: ImportFailure) {
  Png(ImportFailure.Damaged(DamagedAs.Png)),
  Zip(ImportFailure.Damaged(DamagedAs.Zip)),
  Json(ImportFailure.Damaged(DamagedAs.Json)),
  Other(ImportFailure.NotACharacterCard),
}
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

  private fun readGuarded(name: String, input: BufferedInputStream, staging: Staging): CardFileRead =
    guarded(unreadable = ImportFailure.NotACharacterCard) {
      val kind = sniff(input)
      guarded(kind.unreadable) { readKind(kind, name, input, staging) }
    }

  @Suppress("TooGenericExceptionCaught")
  private fun guarded(unreadable: ImportFailure, read: () -> CardFileRead): CardFileRead = try {
    read()
  } catch (_: SourceReadException) {
    CardFileRead.Failed(ImportFailure.CantBeOpened)
  } catch (_: StorageFullException) {
    CardFileRead.Failed(ImportFailure.StorageFull)
  } catch (_: StoreIoException) {
    CardFileRead.Failed(ImportFailure.CouldNotSave)
  } catch (_: EntryLargerThanDeclared) {
    CardFileRead.Failed(ImportFailure.Unsafe)
  } catch (_: IOException) {
    CardFileRead.Failed(unreadable)
  } catch (_: RuntimeException) {
    CardFileRead.Failed(unreadable)
  }

  private fun sniff(input: BufferedInputStream): FileKind {
    input.mark(SNIFF_LENGTH)
    val start = input.readUpTo(SNIFF_LENGTH)
    input.reset()
    return when {
      start.startsWith(PNG_SIGNATURE) -> FileKind.Png
      ZIP_MAGIC.any { start.startsWith(it) } -> FileKind.Zip
      CardJson.startsLikeJson(start) -> FileKind.Json
      else -> FileKind.Other
    }
  }

  private fun readKind(kind: FileKind, name: String, input: BufferedInputStream, staging: Staging) = when (kind) {
    FileKind.Png -> PngCardReader(staging).read(input)
    FileKind.Zip -> store.withTemporaryFile { zip ->
      storeIo { Files.newOutputStream(zip).use { input.copyTo(it) } }
      CharxReader(staging).read(name, zip)
    }
    FileKind.Json -> readJson(input)
    FileKind.Other -> CardFileRead.Failed(ImportFailure.NotACharacterCard)
  }

  private fun readJson(input: BufferedInputStream): CardFileRead {
    val bytes = CardJson.readWithin(input, CardLimits.CARD_JSON_BYTES)
      ?: return CardFileRead.Failed(ImportFailure.TooLarge(SizeLimit.CardJson))
    return when (val card = CardJson.read(bytes)) {
      is CardJsonRead.Card -> CardFileRead.Card(
        ImportedCard.JsonOnly(card.card, card.card.assets.map { it.toAsset(stored = null) }),
      )
      CardJsonRead.Damaged -> CardFileRead.Failed(ImportFailure.Damaged(DamagedAs.Json))
      CardJsonRead.NotACard -> CardFileRead.Failed(ImportFailure.NotACharacterCard)
    }
  }
}

internal fun ByteArray.startsWith(prefix: ByteArray) =
  size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
