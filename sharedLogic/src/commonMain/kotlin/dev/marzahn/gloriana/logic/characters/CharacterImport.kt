package dev.marzahn.gloriana.logic.characters

import kotlin.uuid.Uuid

sealed interface FileImportResult {
  data class Imported(val id: Uuid) : FileImportResult

  data class Updated(val id: Uuid) : FileImportResult

  data object LeftOut : FileImportResult

  data class Failed(val reason: ImportFailure) : FileImportResult
}

sealed interface ImportFailure {
  data object CantBeOpened : ImportFailure

  data object Damaged : ImportFailure

  data object NotACharacterCard : ImportFailure

  data class TooLarge(val limit: SizeLimit) : ImportFailure

  data object Unsafe : ImportFailure

  data object StorageFull : ImportFailure

  data object CouldNotSave : ImportFailure
}

enum class SizeLimit {
  CardJson,
  ZipEntryCount,
  ZipEntrySize,
}

sealed interface FileImportStatus {
  data object Waiting : FileImportStatus

  data object Importing : FileImportStatus

  data class Done(val result: FileImportResult) : FileImportStatus
}

data class AlreadyInLibrary(
  val fileName: String,
  val existing: CharacterSummary,
  val filesLeftAfterThis: Int,
  val losesEmbeddedLorebook: Boolean,
)

enum class AlreadyInLibraryChoice {
  Copy,
  Update,
  Cancel,
}

data class AlreadyInLibraryAnswer(val choice: AlreadyInLibraryChoice, val forTheRestOfThePick: Boolean = false)

fun interface AlreadyInLibraryQuestion {
  suspend fun ask(question: AlreadyInLibrary): AlreadyInLibraryAnswer
}

fun interface ImportStatusListener {
  fun onStatus(fileIndex: Int, status: FileImportStatus)
}

fun interface ImportHold {
  suspend fun awaitRelease(fileName: String)

  companion object {
    val None = ImportHold { }
  }
}
