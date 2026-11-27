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
import dev.marzahn.gloriana.logic.characters.OpenedFile
import dev.marzahn.gloriana.logic.characters.PickedFile
import dev.marzahn.gloriana.ui.messages.AppMessages
import dev.marzahn.gloriana.ui.resources.Res
import dev.marzahn.gloriana.ui.resources.snack_imported
import dev.marzahn.gloriana.ui.resources.snack_imported_one
import dev.marzahn.gloriana.ui.resources.snack_needs_answer
import dev.marzahn.gloriana.ui.resources.snack_timed_out
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

private class NamedFile(override val name: String) : PickedFile {
  override fun open(): OpenedFile = OpenedFile.Unavailable
}

private sealed interface Step {
  class Result(val result: FileImportResult) : Step

  class Ask(val existing: String, val then: (AlreadyInLibraryAnswer) -> FileImportResult) : Step

  data object Hang : Step
}

private class ScriptedImporter(
  private val steps: Map<String, Step>,
  private val names: Map<Uuid, String>,
) : CardImporter {
  val hanging = CompletableDeferred<Unit>()

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
      }
      listener.onStatus(index, FileImportStatus.Done(result))
      result
    }
  }

  override suspend fun nameOf(id: Uuid): String? = names[id]

  private fun question(file: String, existing: String, left: Int) =
    AlreadyInLibrary(file, CharacterSummary(Uuid.random(), existing, null), left, false, false)
}

@OptIn(ExperimentalCoroutinesApi::class)
class CharacterImportsTest {
  private val importDispatcher = StandardTestDispatcher()
  private val scope = ApplicationScope(importDispatcher) { throw it }
  private val messages = AppMessages()
  private val oren = Uuid.random()
  private val wren = Uuid.random()
  private val opened = mutableListOf<Uuid>()

  @AfterTest
  fun stop() = scope.cancel()

  private fun imports(vararg steps: Pair<String, Step>) =
    CharacterImports(ScriptedImporter(steps.toMap(), mapOf(oren to "Oren", wren to "Wren")), scope, messages)

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
    val imports = CharacterImports(importer, scope, messages)
    imports.start(listOf(NamedFile("oren.png"), NamedFile("slow.png"), NamedFile("wren.json")))
    importer.hanging.await()
    imports.backgroundTimeRanOut()
    val note = imports.state.first { it.note != null }.note as ImportNote.TimedOut
    assertEquals(listOf("slow.png", "wren.json"), note.cutOff)
    assertEquals(getPluralString(Res.plurals.snack_timed_out, 2, 2, 3), messages.messages.first().text)
    assertEquals(emptyList(), imports.state.value.running)
  }
}
