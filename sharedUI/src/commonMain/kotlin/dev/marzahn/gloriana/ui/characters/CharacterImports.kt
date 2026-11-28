package dev.marzahn.gloriana.ui.characters

import dev.marzahn.gloriana.logic.app.ApplicationScope
import dev.marzahn.gloriana.logic.characters.AlreadyInLibrary
import dev.marzahn.gloriana.logic.characters.AlreadyInLibraryAnswer
import dev.marzahn.gloriana.logic.characters.FileImportResult
import dev.marzahn.gloriana.logic.characters.FileImportStatus
import dev.marzahn.gloriana.logic.files.PickedFile
import dev.marzahn.gloriana.ui.messages.AppMessage
import dev.marzahn.gloriana.ui.messages.AppMessages
import dev.marzahn.gloriana.ui.messages.MessageAction
import dev.marzahn.gloriana.ui.navigation.PageOpener
import dev.marzahn.gloriana.ui.navigation.ShellNavigator
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.import_failed_all
import dev.marzahn.gloriana.ui.resources.import_failed_some
import dev.marzahn.gloriana.ui.resources.open
import dev.marzahn.gloriana.ui.resources.snack_failed_one
import dev.marzahn.gloriana.ui.resources.snack_imported
import dev.marzahn.gloriana.ui.resources.snack_imported_one
import dev.marzahn.gloriana.ui.resources.snack_needs_answer
import dev.marzahn.gloriana.ui.resources.snack_timed_out
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import kotlin.concurrent.Volatile
import kotlin.uuid.Uuid

private class ActiveImport(val work: Deferred<List<FileImportResult>>) {
  @Volatile
  var timedOut = false
}

class CharacterImports(
  private val importer: CardImporter,
  private val scope: ApplicationScope,
  messages: AppMessages,
) {
  private val mutableState = MutableStateFlow(ImportState())
  private val announcer = ImportAnnouncer(messages, importer)
  private val pages = PageOpener<Uuid> { id -> scope.launch { announcer.announceImported(id) } }
  private val pendingAnswer = MutableStateFlow<CompletableDeferred<AlreadyInLibraryAnswer>?>(null)
  private val active = MutableStateFlow<ActiveImport?>(null)
  private val oneAtATime = Mutex()
  private var nextKey = 0L

  val state: StateFlow<ImportState> = mutableState.asStateFlow()

  private val inLibrary: Boolean get() = pages.shown

  suspend fun openPagesWhileShown(open: (Uuid) -> Unit): Nothing = pages.openWhileShown(open)

  fun start(files: List<PickedFile>) {
    if (files.isEmpty()) return
    val keys = files.map { nextKey++ }
    val tiles = files.mapIndexed { index, file ->
      RunningFile(keys[index], file.name, RunningStatus.Waiting, position = index + 1, of = files.size)
    }
    mutableState.update { it.copy(running = it.running + tiles, note = null) }
    scope.launch { oneAtATime.withLock { run(files, keys) } }
  }

  fun answer(answer: AlreadyInLibraryAnswer) {
    pendingAnswer.value?.complete(answer)
  }

  fun showQuestion() {
    mutableState.update { state -> state.copy(question = state.question?.copy(showing = true)) }
  }

  fun dismissNote() {
    mutableState.update { it.copy(note = null) }
  }

  fun backgroundTimeRanOut() {
    active.value?.let { import ->
      import.timedOut = true
      import.work.cancel()
    }
  }

  private suspend fun run(files: List<PickedFile>, keys: List<Long>) {
    val results = arrayOfNulls<FileImportResult>(files.size)
    val work = scope.async {
      importer.import(files, { question -> ask(question, keys) }) { index, status ->
        onStatus(keys[index], status) { results[index] = it }
      }
    }
    val import = ActiveImport(work).also { active.value = it }
    try {
      work.await()
    } catch (cancelled: CancellationException) {
      if (!import.timedOut) throw cancelled
    } finally {
      active.value = null
      mutableState.update { state -> state.copy(running = state.running.filterNot { it.key in keys }) }
    }
    val outcome = importOutcome(files.map { it.name }, results.toList(), import.timedOut)
    outcome.note?.let { note -> mutableState.update { it.copy(note = note) } }
    announcer.announce(outcome, pages)
  }

  private fun onStatus(key: Long, status: FileImportStatus, record: (FileImportResult) -> Unit) {
    when (status) {
      FileImportStatus.Waiting -> setStatus(key, RunningStatus.Waiting)
      FileImportStatus.Importing -> setStatus(key, RunningStatus.Importing)
      is FileImportStatus.Done -> {
        record(status.result)
        mutableState.update { state -> state.copy(running = state.running.filterNot { it.key == key }) }
      }
    }
  }

  private fun setStatus(key: Long, status: RunningStatus) {
    mutableState.update { state ->
      state.copy(running = state.running.map { if (it.key == key) it.copy(status = status) else it })
    }
  }

  private suspend fun ask(question: AlreadyInLibrary, keys: List<Long>): AlreadyInLibraryAnswer {
    val key = mutableState.value.running.firstOrNull { it.key in keys && it.status == RunningStatus.Importing }?.key
    val answer = CompletableDeferred<AlreadyInLibraryAnswer>().also { pendingAnswer.value = it }
    key?.let { setStatus(it, RunningStatus.NeedsAnswer) }
    val showing = inLibrary
    mutableState.update { it.copy(question = PendingQuestion(question, showing)) }
    if (!showing) announcer.needsAnswer(::showQuestion)
    return try {
      answer.await()
    } finally {
      pendingAnswer.value = null
      mutableState.update { it.copy(question = null) }
      key?.let { setStatus(it, RunningStatus.Importing) }
    }
  }

}

private class ImportAnnouncer(
  private val messages: AppMessages,
  private val importer: CardImporter,
) {
  suspend fun needsAnswer(showQuestion: () -> Unit) {
    val open = openAction {
      it.showLibrary()
      showQuestion()
    }
    messages.show(AppMessage(getString(Res.string.snack_needs_answer), open))
  }

  suspend fun announce(outcome: ImportOutcome, pages: PageOpener<Uuid>) {
    when (outcome) {
      ImportOutcome.NothingImported -> Unit
      is ImportOutcome.TimedOut -> if (!pages.shown) showOpeningLibrary(timedOut(outcome.note))
      is ImportOutcome.Failed -> if (!pages.shown) showOpeningLibrary(failureCount(outcome.note))
      is ImportOutcome.OneImported -> if (!pages.offer(outcome.id)) announceImported(outcome.id)
      is ImportOutcome.SeveralImported ->
        messages.show(AppMessage(getPluralString(Res.plurals.snack_imported, outcome.count, outcome.count)))
    }
  }

  private suspend fun showOpeningLibrary(text: String) {
    messages.show(AppMessage(text, openAction { it.showLibrary() }))
  }

  suspend fun announceImported(id: Uuid) {
    val name = importer.nameOf(id) ?: return
    messages.show(AppMessage(getString(Res.string.snack_imported_one, name), openAction { it.openCharacter(id) }))
  }

  private suspend fun timedOut(note: ImportNote.TimedOut): String =
    getPluralString(Res.plurals.snack_timed_out, note.notImported, note.notImported, note.picked)

  private suspend fun failureCount(note: ImportNote.Failed): String {
    val failed = note.failures.size
    return when {
      note.picked == 1 -> getString(Res.string.snack_failed_one, note.failures.single().name)
      failed == note.picked -> getString(Res.string.import_failed_all, note.picked)
      else -> getPluralString(Res.plurals.import_failed_some, failed, failed, note.picked)
    }
  }

  private suspend fun openAction(perform: (ShellNavigator) -> Unit) = MessageAction(getString(Res.string.open), perform)
}
