package dev.marzahn.gloriana.logic.presets

import dev.marzahn.gloriana.logic.app.failingAs
import dev.marzahn.gloriana.logic.assets.isStorageFull
import dev.marzahn.gloriana.logic.files.JSON_FILE_LIMIT_BYTES
import dev.marzahn.gloriana.logic.files.LimitedRead
import dev.marzahn.gloriana.logic.files.OpenedFile
import dev.marzahn.gloriana.logic.files.PickedFile
import dev.marzahn.gloriana.logic.files.readWithin
import dev.marzahn.gloriana.logic.files.utf8Text
import dev.marzahn.gloriana.logic.presets.file.PresetFileRead
import dev.marzahn.gloriana.logic.presets.file.PresetJsonReader
import dev.marzahn.gloriana.logic.presets.storage.PresetWrites
import dev.marzahn.gloriana.logic.storage.IdFactory

private const val JSON_EXTENSION = ".json"

private sealed interface FileBytes {
  class Read(val bytes: ByteArray) : FileBytes

  data object TooLarge : FileBytes

  data object Unreadable : FileBytes
}

class PresetImport internal constructor(private val writes: PresetWrites, ids: IdFactory) {
  private val reader = PresetJsonReader(ids)

  suspend fun import(file: PickedFile): PresetImportResult = when (val bytes = readFile(file)) {
    FileBytes.Unreadable -> failed(PresetImportFailure.CantBeOpened)
    FileBytes.TooLarge -> failed(PresetImportFailure.TooLarge)
    is FileBytes.Read -> when (val read = utf8Text(bytes.bytes)?.let(reader::read) ?: PresetFileRead.Damaged) {
      PresetFileRead.Damaged -> failed(PresetImportFailure.Damaged)
      PresetFileRead.NotAPreset -> failed(PresetImportFailure.NotAPreset)
      is PresetFileRead.Read -> stored(presetName(file.name), read)
    }
  }

  private suspend fun readFile(file: PickedFile): FileBytes = failingAs(FileBytes.Unreadable) {
    when (val opened = file.open()) {
      OpenedFile.Unavailable -> FileBytes.Unreadable
      is OpenedFile.Opened -> when (val read = opened.source.use { readWithin(JSON_FILE_LIMIT_BYTES, it::read) }) {
        is LimitedRead.Read -> FileBytes.Read(read.bytes)
        LimitedRead.TooLarge -> FileBytes.TooLarge
      }
    }
  }

  private suspend fun stored(name: String, read: PresetFileRead.Read): PresetImportResult = failingAs(::saveFailure) {
    PresetImportResult.Imported(writes.insert(name, read.content, read.source), read.samplers, read.credentialsSkipped)
  }

  private fun saveFailure(error: Exception) =
    failed(if (error.isStorageFull()) PresetImportFailure.StorageFull else PresetImportFailure.CouldNotSave)

  private fun failed(reason: PresetImportFailure) = PresetImportResult.Failed(reason)
}

internal fun presetName(fileName: String): String {
  val trimmed = fileName.trim()
  val withoutExtension = if (trimmed.endsWith(JSON_EXTENSION, ignoreCase = true)) {
    trimmed.dropLast(JSON_EXTENSION.length).trim()
  } else {
    trimmed
  }
  return withoutExtension.ifEmpty { trimmed }
}
