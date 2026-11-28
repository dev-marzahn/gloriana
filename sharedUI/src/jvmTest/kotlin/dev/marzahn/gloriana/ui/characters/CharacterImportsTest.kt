package dev.marzahn.gloriana.ui.characters

import dev.marzahn.gloriana.logic.app.ApplicationScope
import dev.marzahn.gloriana.logic.characters.AlreadyInLibrary
import dev.marzahn.gloriana.logic.characters.AlreadyInLibraryAnswer
import dev.marzahn.gloriana.logic.characters.AlreadyInLibraryChoice
import dev.marzahn.gloriana.logic.characters.AlreadyInLibraryQuestion
import dev.marzahn.gloriana.logic.characters.CharacterSummary
import dev.marzahn.gloriana.logic.characters.DamagedAs
import dev.marzahn.gloriana.logic.characters.FileImportResult
import dev.marzahn.gloriana.logic.characters.FileImportStatus
import dev.marzahn.gloriana.logic.characters.ImportFailure
import dev.marzahn.gloriana.logic.characters.ImportStatusListener
import dev.marzahn.gloriana.logic.files.OpenedFile
import dev.marzahn.gloriana.logic.files.PickedFile
import dev.marzahn.gloriana.logic.tasks.KeepAlive
import dev.marzahn.gloriana.logic.tasks.KeepAliveLosses
import dev.marzahn.gloriana.logic.tasks.ProcessHold
import dev.marzahn.gloriana.logic.tasks.TaskHold
import dev.marzahn.gloriana.logic.tasks.TaskNotice
import dev.marzahn.gloriana.logic.tasks.TaskProgress
import dev.marzahn.gloriana.logic.tasks.TaskRunner
import dev.marzahn.gloriana.ui.messages.AppMessages
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.import_notice_detail
import dev.marzahn.gloriana.ui.resources.import_notice_title
import dev.marzahn.gloriana.ui.resources.snack_imported
import dev.marzahn.gloriana.ui.resources.snack_imported_one
import dev.marzahn.gloriana.ui.resources.snack_needs_answer
import dev.marzahn.gloriana.ui.resources.snack_timed_out
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

private val NO_MORE_MESSAGES = 300.milliseconds

private class NamedFile(override val name: String) : PickedFile {
  override fun open(): OpenedFile = OpenedFile.Unavailable
}

private sealed interface Step {
  class Result(val result: FileImportResult) : Step

  class Ask(val existing: String, val then: (AlreadyInLibraryAnswer) -> FileImportResult) : Step

  data object Hang : Step

  data object HangThenWindDownSlowly : Step
}

private class ScriptedImporter(
  private val steps: Map<String, Step>,
  private val names: Map<Uuid, String>,
) : CardImporter {
  val hanging = CompletableDeferred<Unit>()
  val woundDown = CompletableDeferred<Unit>()

  override suspend fun import(
    files: List<PickedFile>,
    alreadyInLibrary: AlreadyInLibraryQuestion,
    listener: ImportStatusListener,
  ): List<FileImportResult> {
    files.indices.forEach { listener.onStatus(it, FileImportStatus.Waiting) }
    return files.mapIndexed { index, file ->
      listener.onStatus(index, FileImportStatus.Importing)
      val result = when (val step = steps.getValue(file.name)) {
        is Step.Result -> step.result
        is Step.Ask -> step.then(alreadyInLibrary.ask(question(file.name, step.existing, files.size - index - 1)))
        Step.Hang -> {
          hanging.complete(Unit)
          awaitCancellation()
        }
        Step.HangThenWindDownSlowly -> {
          hanging.complete(Unit)
          try {
            awaitCancellation()
          } finally {
            withContext(NonCancellable) { woundDown.await() }
          }
        }
      }
      listener.onStatus(index, FileImportStatus.Done(result))
      result
    }
  }

  override suspend fun nameOf(id: Uuid): String? = names[id]

  private fun question(file: String, existing: String, left: Int) =
    AlreadyInLibrary(file, CharacterSummary(Uuid.random(), existing, null), left, false, false)
}

private class RecordingKeepAlive : KeepAlive {
  private var session: Pair<Flow<TaskNotice?>, KeepAliveLosses>? = null
  var started = 0
  var stopped = 0

  fun shown(): Flow<TaskNotice?> = checkNotNull(session).first

  fun losses(): KeepAliveLosses = checkNotNull(session).second

  override fun start(opening: TaskNotice, shown: Flow<TaskNotice?>, losses: KeepAliveLosses) {
    session = shown to losses
    started++
  }

  override fun stop() {
    stopped++
  }

  override fun holdWhileRunning(): TaskHold = TaskHold { }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CharacterImportsTest {
  private val importDispatcher = StandardTestDispatcher()
  private val scope = ApplicationScope(importDispatcher) { throw it }
  private val messages = AppMessages()
  private val keepAlive = RecordingKeepAlive()
  private val runner = TaskRunner(scope, keepAlive)
  private val oren = Uuid.random()
  private val wren = Uuid.random()
  private val opened = mutableListOf<Uuid>()

  @AfterTest
  fun stop() = scope.cancel()

  private fun imports(vararg steps: Pair<String, Step>) =
    CharacterImports(ScriptedImporter(steps.toMap(), mapOf(oren to "Oren", wren to "Wren")), runner, scope, messages)

  private fun imported(id: Uuid) = Step.Result(FileImportResult.Imported(id))

  private fun showLibrary(imports: CharacterImports): Job =
    scope.launch(start = CoroutineStart.UNDISPATCHED) { imports.openPagesWhileShown { opened += it } }

  private fun runImportTest(block: suspend TestScope.() -> Unit) = runTest(importDispatcher, testBody = block)

  @Test
  fun `one file imported in Library opens its page`() = runImportTest {
    val imports = imports("oren.png" to imported(oren))
    showLibrary(imports)
    imports.start(listOf(NamedFile("oren.png")))
    advanceUntilIdle()
    assertEquals(listOf(oren), opened)
    assertEquals(emptyList(), imports.state.value.running)
  }

  @Test
  fun `one file imported after Library closed says so and opens no page later`() = runImportTest {
    val imports = imports("oren.png" to imported(oren))
    showLibrary(imports).cancelAndJoin()
    imports.start(listOf(NamedFile("oren.png")))
    assertEquals(getString(Res.string.snack_imported_one, "Oren"), messages.messages.first().text)
    showLibrary(imports)
    advanceUntilIdle()
    assertEquals(emptyList(), opened)
  }

  @Test
  fun `one file imported outside Library says so, with Open`() = runImportTest {
    val imports = imports("oren.png" to imported(oren))
    imports.start(listOf(NamedFile("oren.png")))
    val message = messages.messages.first()
    assertEquals(getString(Res.string.snack_imported_one, "Oren"), message.text)
    assertIs<Any>(message.action)
  }

  @Test
  fun `several files imported say how many, without an action`() = runImportTest {
    val imports = imports("oren.png" to imported(oren), "wren.json" to imported(wren))
    showLibrary(imports)
    imports.start(listOf(NamedFile("oren.png"), NamedFile("wren.json")))
    val message = messages.messages.first()
    assertEquals(getPluralString(Res.plurals.snack_imported, 2, 2), message.text)
    assertEquals(null, message.action)
  }

  @Test
  fun `failures in Library leave the note and no Snackbar`() = runImportTest {
    val broken = Step.Result(FileImportResult.Failed(ImportFailure.Damaged(DamagedAs.Png)))
    val imports = imports("oren.png" to imported(oren), "broken.png" to broken)
    showLibrary(imports)
    imports.start(listOf(NamedFile("oren.png"), NamedFile("broken.png")))
    val note = imports.state.first { it.note != null }.note
    assertEquals(listOf("broken.png"), (note as ImportNote.Failed).failures.map { it.name })
  }

  @Test
  fun `a question outside Library waits at its file and asks through a Snackbar`() = runImportTest {
    var answered: AlreadyInLibraryChoice? = null
    val ask = Step.Ask("Oren") { answer ->
      answered = answer.choice
      FileImportResult.Imported(oren)
    }
    val imports = imports("oren.png" to ask)
    imports.start(listOf(NamedFile("oren.png")))
    assertEquals(getString(Res.string.snack_needs_answer), messages.messages.first().text)
    val waiting = imports.state.first { it.question != null }
    assertEquals(false, waiting.question?.showing)
    assertEquals(RunningStatus.NeedsAnswer, waiting.running.single().status)
    imports.showQuestion()
    imports.answer(AlreadyInLibraryAnswer(AlreadyInLibraryChoice.Copy))
    imports.state.first { it.running.isEmpty() }
    assertEquals(AlreadyInLibraryChoice.Copy, answered)
  }

  @Test
  fun `background time running out reports the files cut off`() = runImportTest {
    val steps = mapOf("oren.png" to imported(oren), "slow.png" to Step.Hang, "wren.json" to imported(wren))
    val importer = ScriptedImporter(steps, emptyMap())
    val imports = CharacterImports(importer, runner, scope, messages)
    imports.start(listOf(NamedFile("oren.png"), NamedFile("slow.png"), NamedFile("wren.json")))
    importer.hanging.await()
    keepAlive.losses().backgroundTimeRanOut()
    val note = imports.state.first { it.note != null }.note as ImportNote.TimedOut
    assertEquals(listOf("slow.png", "wren.json"), note.cutOff)
    assertEquals(getPluralString(Res.plurals.snack_timed_out, 2, 2, 3), messages.messages.first().text)
    assertEquals(emptyList(), imports.state.value.running)
  }

  @Test
  fun `background time running out cuts off a pick still waiting for the one before`() = runImportTest {
    val steps = mapOf("slow.png" to Step.Hang, "oren.png" to imported(oren))
    val importer = ScriptedImporter(steps, emptyMap())
    val imports = CharacterImports(importer, runner, scope, messages)
    imports.start(listOf(NamedFile("slow.png")))
    imports.start(listOf(NamedFile("oren.png")))
    importer.hanging.await()
    keepAlive.losses().backgroundTimeRanOut()
    advanceUntilIdle()
    val note = imports.state.value.note as ImportNote.TimedOut
    assertEquals(listOf("slow.png", "oren.png"), note.cutOff)
    assertEquals(2, note.picked)
    assertEquals(emptyList(), imports.state.value.running)
    assertEquals(emptyList(), opened)
    assertEquals(getPluralString(Res.plurals.snack_timed_out, 2, 2, 2), messages.messages.first().text)
    val another = withContext(Dispatchers.Default) { withTimeoutOrNull(NO_MORE_MESSAGES) { messages.messages.first() } }
    assertEquals(null, another)
  }

  @Test
  fun `a pick started while a cut-off import winds down is imported, not reported cut off`() = runImportTest {
    val steps = mapOf("slow.png" to Step.HangThenWindDownSlowly, "oren.png" to imported(oren))
    val importer = ScriptedImporter(steps, mapOf(oren to "Oren"))
    val imports = CharacterImports(importer, runner, scope, messages)
    imports.start(listOf(NamedFile("slow.png")))
    importer.hanging.await()
    keepAlive.losses().backgroundTimeRanOut()
    advanceUntilIdle()
    imports.start(listOf(NamedFile("oren.png")))
    assertEquals(getString(Res.string.snack_imported_one, "Oren"), messages.messages.first().text)
    importer.woundDown.complete(Unit)
    advanceUntilIdle()
    val note = imports.state.value.note as ImportNote.TimedOut
    assertEquals(listOf("slow.png"), note.cutOff)
    assertEquals(1, note.picked)
    assertEquals(getPluralString(Res.plurals.snack_timed_out, 1, 1, 1), messages.messages.first().text)
  }

  @Test
  fun `a pick that started after another stays queued and imports once it ends`() = runImportTest {
    val imports = imports("oren.png" to imported(oren), "wren.json" to imported(wren))
    imports.start(listOf(NamedFile("oren.png")))
    imports.start(listOf(NamedFile("wren.json")))
    val announced = messages.messages.take(2).toList().map { it.text }
    assertEquals(listOf("Oren", "Wren").map { getString(Res.string.snack_imported_one, it) }, announced)
    runner.processHold.first { it == ProcessHold.Released }
    assertEquals(1, keepAlive.started)
  }

  @Test
  fun `failures of picks that end while the note shows add to it`() = runImportTest {
    val broken = Step.Result(FileImportResult.Failed(ImportFailure.Damaged(DamagedAs.Png)))
    val imports = imports("a.png" to broken, "b.png" to broken)
    showLibrary(imports)
    imports.start(listOf(NamedFile("a.png")))
    imports.start(listOf(NamedFile("b.png")))
    advanceUntilIdle()
    val note = imports.state.value.note as ImportNote.Failed
    assertEquals(listOf("a.png", "b.png"), note.failures.map { it.name })
    assertEquals(2, note.picked)
  }

  @Test
  fun `the import holds the process while it runs and says which file it is on`() = runImportTest {
    val ask = Step.Ask("Wren") { FileImportResult.Imported(wren) }
    val imports = imports("oren.png" to imported(oren), "wren.json" to ask)
    imports.start(listOf(NamedFile("oren.png"), NamedFile("wren.json")))
    imports.state.first { it.question != null }
    val notice = keepAlive.shown().first { it?.progress?.done == 1 }
    assertEquals(getString(Res.string.import_notice_title), notice?.title)
    assertEquals(getString(Res.string.import_notice_detail, 2, 2), notice?.detail)
    assertEquals(TaskProgress(done = 1, of = 2), notice?.progress)
    assertEquals(1, keepAlive.started)
    assertEquals(ProcessHold.Held, runner.processHold.value)
    imports.answer(AlreadyInLibraryAnswer(AlreadyInLibraryChoice.Copy))
    runner.processHold.first { it == ProcessHold.Released }
    assertEquals(1, keepAlive.stopped)
  }
}
