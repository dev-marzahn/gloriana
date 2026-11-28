package dev.marzahn.gloriana.ui.characters

import dev.marzahn.gloriana.logic.app.ApplicationScope
import dev.marzahn.gloriana.logic.characters.AlreadyInLibrary
import dev.marzahn.gloriana.logic.characters.AlreadyInLibraryAnswer
import dev.marzahn.gloriana.logic.characters.FileImportResult
import dev.marzahn.gloriana.logic.characters.FileImportStatus
import dev.marzahn.gloriana.logic.files.PickedFile
import dev.marzahn.gloriana.logic.tasks.TaskNotice
import dev.marzahn.gloriana.logic.tasks.TaskProgress
import dev.marzahn.gloriana.logic.tasks.TaskRunner
import dev.marzahn.gloriana.ui.messages.AppMessage
import dev.marzahn.gloriana.ui.messages.AppMessages
import dev.marzahn.gloriana.ui.messages.MessageAction
import dev.marzahn.gloriana.ui.navigation.PageOpener
import dev.marzahn.gloriana.ui.navigation.ShellNavigator
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.import_failed_all
import dev.marzahn.gloriana.ui.resources.import_failed_some
import dev.marzahn.gloriana.ui.resources.import_notice_detail
import dev.marzahn.gloriana.ui.resources.import_notice_title
import dev.marzahn.gloriana.ui.resources.open
import dev.marzahn.gloriana.ui.resources.snack_failed_one
import dev.marzahn.gloriana.ui.resources.snack_imported
import dev.marzahn.gloriana.ui.resources.snack_imported_one
import dev.marzahn.gloriana.ui.resources.snack_needs_answer
import dev.marzahn.gloriana.ui.resources.snack_timed_out
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import kotlin.concurrent.Volatile
import kotlin.uuid.Uuid

private data class PickProgress(val importing: Int, val done: Int)

private class Pick(val files: List<PickedFile>, val keys: List<Long>) {
  val results = arrayOfNulls<FileImportResult>(files.size)
  val progress = MutableStateFlow(PickProgress(importing = 1, done = 0))

  val names: List<String> get() = files.map { it.name }

  val notice: Flow<TaskNotice> = progress.map { (importing, done) ->
    TaskNotice(
      title = getString(Res.string.import_notice_title),
      detail = getString(Res.string.import_notice_detail, importing, files.size),
      progress = TaskProgress(done, files.size),
    )
  }
}

private class ImportQueue(first: Pick) {
  private val picks = MutableStateFlow<List<Pick>?>(listOf(first))

  @Volatile
  var task: Job? = null

  @OptIn(ExperimentalCoroutinesApi::class)
  val notice: Flow<TaskNotice> =
    picks.mapNotNull { it?.firstOrNull() }.distinctUntilChanged().flatMapLatest { it.notice }

  val head: Pick? get() = picks.value?.firstOrNull()

  fun offer(pick: Pick): Boolean {
    if (task?.isCancelled == true) return false
    return picks.getAndUpdate { it?.plus(pick) } != null
  }

  fun finishHead(): Pick? = picks.updateAndGet { it?.drop(1)?.ifEmpty { null } }?.first()

  fun close(): List<Pick> = picks.getAndUpdate { null }.orEmpty()
}

private class ImportQueues(private val launch: (ImportQueue) -> Job) {
  private val current = MutableStateFlow<ImportQueue?>(null)

  fun enqueue(pick: Pick) {
    do {
      val queue = current.value
      if (queue?.offer(pick) == true) return
    } while (!launchReplacing(queue, pick))
  }

  private fun launchReplacing(ended: ImportQueue?, pick: Pick): Boolean {
    val fresh = ImportQueue(pick)
    if (!current.compareAndSet(ended, fresh)) return false
    fresh.task = launch(fresh)
    return true
  }
}

class CharacterImports(
  private val importer: CardImporter,
  private val runner: TaskRunner,
  private val scope: ApplicationScope,
  messages: AppMessages,
) {
  private val mutableState = MutableStateFlow(ImportState())
  private val announcer = ImportAnnouncer(messages, importer)
  private val pages = PageOpener<Uuid> { id -> scope.launch { announcer.announceImported(id) } }
  private val pendingAnswer = MutableStateFlow<CompletableDeferred<AlreadyInLibraryAnswer>?>(null)
  private val queues = ImportQueues { queue ->
    runner.launch(queue.notice, onBackgroundTimeOut = { reportCutOff(queue) }) { importAll(queue) }
  }
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
    queues.enqueue(Pick(files, keys))
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

  private suspend fun importAll(queue: ImportQueue) {
    var pick = queue.head ?: return
    while (true) {
      val importing = pick
      importer.import(importing.files, { question -> ask(question, importing.keys) }) { index, status ->
        onStatus(importing, index, status)
      }
      pick = withContext(NonCancellable) {
        report(importing.keys, importOutcome(importing.names, importing.results.toList(), timedOut = false))
        queue.finishHead()
      } ?: return
    }
  }

  private suspend fun reportCutOff(queue: ImportQueue) {
    val cutOff = queue.close()
    val outcome = importOutcome(cutOff.flatMap { it.names }, cutOff.flatMap { it.results.toList() }, timedOut = true)
    report(cutOff.flatMap { it.keys }, outcome)
  }

  private suspend fun report(keys: List<Long>, outcome: ImportOutcome) {
    mutableState.update { state -> state.copy(running = state.running.filterNot { it.key in keys }) }
    outcome.note?.let { note -> mutableState.update { it.copy(note = it.note.joinedBy(note)) } }
    announcer.announce(outcome, pages)
  }

  private fun onStatus(pick: Pick, index: Int, status: FileImportStatus) {
    val key = pick.keys[index]
    when (status) {
      FileImportStatus.Waiting -> setStatus(key, RunningStatus.Waiting)
      FileImportStatus.Importing -> {
        pick.progress.update { it.copy(importing = index + 1) }
        setStatus(key, RunningStatus.Importing)
      }
      is FileImportStatus.Done -> {
        pick.results[index] = status.result
        pick.progress.update { it.copy(done = it.done + 1) }
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
