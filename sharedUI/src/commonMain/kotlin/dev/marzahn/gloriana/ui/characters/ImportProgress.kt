package dev.marzahn.gloriana.ui.characters

import dev.marzahn.gloriana.logic.characters.AlreadyInLibrary
import dev.marzahn.gloriana.logic.characters.FileImportResult
import dev.marzahn.gloriana.logic.characters.ImportFailure
import kotlin.uuid.Uuid

enum class RunningStatus { Waiting, Importing, NeedsAnswer }

data class RunningFile(val key: Long, val name: String, val status: RunningStatus, val position: Int, val of: Int)

data class FailedFile(val name: String, val reason: ImportFailure)

sealed interface ImportNote {
  data class Failed(val picked: Int, val failures: List<FailedFile>) : ImportNote

  data class TimedOut(val picked: Int, val cutOff: List<String>, val failures: List<FailedFile>) : ImportNote {
    val notImported: Int get() = cutOff.size + failures.size
  }
}

data class PendingQuestion(val question: AlreadyInLibrary, val showing: Boolean)

data class ImportState(
  val running: List<RunningFile> = emptyList(),
  val question: PendingQuestion? = null,
  val note: ImportNote? = null,
)

sealed interface ImportOutcome {
  val note: ImportNote? get() = null

  data object NothingImported : ImportOutcome

  data class TimedOut(override val note: ImportNote.TimedOut) : ImportOutcome

  data class Failed(override val note: ImportNote.Failed) : ImportOutcome

  data class OneImported(val id: Uuid) : ImportOutcome

  data class SeveralImported(val count: Int) : ImportOutcome
}

fun importOutcome(names: List<String>, results: List<FileImportResult?>, timedOut: Boolean): ImportOutcome {
  val failures = names.zip(results).mapNotNull { (name, result) ->
    (result as? FileImportResult.Failed)?.let { FailedFile(name, it.reason) }
  }
  val cutOff = names.zip(results).filter { (_, result) -> result == null }.map { it.first }
  val written = results.mapNotNull { result ->
    when (result) {
      is FileImportResult.Imported -> result.id
      is FileImportResult.Updated -> result.id
      else -> null
    }
  }
  return when {
    timedOut && cutOff.isNotEmpty() -> ImportOutcome.TimedOut(ImportNote.TimedOut(names.size, cutOff, failures))
    failures.isNotEmpty() -> ImportOutcome.Failed(ImportNote.Failed(names.size, failures))
    written.isEmpty() -> ImportOutcome.NothingImported
    names.size == 1 -> ImportOutcome.OneImported(written.single())
    else -> ImportOutcome.SeveralImported(written.size)
  }
}
