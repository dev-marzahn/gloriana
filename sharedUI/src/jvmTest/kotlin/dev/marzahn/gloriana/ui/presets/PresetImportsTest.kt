package dev.marzahn.gloriana.ui.presets

import androidx.room.execSQL
import androidx.room.useWriterConnection
import dev.marzahn.gloriana.logic.characters.DesktopExportDestination
import dev.marzahn.gloriana.logic.characters.DesktopPickedFile
import dev.marzahn.gloriana.logic.connection.ConnectionRepository
import dev.marzahn.gloriana.logic.files.ExportFailure
import dev.marzahn.gloriana.logic.files.OpenedFile
import dev.marzahn.gloriana.logic.files.PickedFile
import dev.marzahn.gloriana.logic.presets.PresetImportFailure
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import dev.marzahn.gloriana.ui.files.ExportState
import dev.marzahn.gloriana.ui.files.SaveTarget
import dev.marzahn.gloriana.ui.messages.AppMessages
import dev.marzahn.gloriana.ui.testing.LiveLibrary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

private const val WAIT_MILLIS = 10_000L
private const val QUIET_MILLIS = 500L

private const val PROMPTS =
  """"prompts": [{"identifier": "main", "name": "Main Prompt", "role": "system", "content": "Be vivid."}],
  "prompt_order": [{"character_id": 100001, "order": [{"identifier": "main", "enabled": true}]}]"""

class PresetImportsTest {
  private val directory: Path = Files.createTempDirectory("gloriana-preset-imports")

  @OptIn(ExperimentalPathApi::class)
  @AfterTest
  fun removeFiles() = directory.deleteRecursively()

  private fun file(name: String, extra: String = ""): PickedFile {
    val path = directory.resolve(name)
    path.writeText("{ $extra $PROMPTS }")
    return DesktopPickedFile(path)
  }

  private val tuned get() = file("Tuned.json", """"temperature": 0.7,""")

  private class Imports(val library: LiveLibrary) {
    val imports: PresetImports = library.koin.get()
    val sessions: PresetSessions = library.koin.get()
    val connections: ConnectionRepository = library.koin.get()
    val messages: AppMessages = library.koin.get()

    fun waitUntil(condition: (PresetImportState) -> Boolean): PresetImportState = runBlocking {
      withTimeout(WAIT_MILLIS) { imports.state.first(condition) }
    }

    fun openOffer(file: PickedFile): SamplerOfferState {
      imports.start(file)
      return checkNotNull(waitUntil { it.offer?.difference != null }.offer)
    }

    fun delete(id: Uuid) = runBlocking { connections.delete(id) }

    fun sql(statement: String) = runBlocking {
      library.koin.get<GlorianaDatabase>().useWriterConnection { it.execSQL(statement) }
    }

    fun presetId(): Uuid = runBlocking {
      withTimeout(WAIT_MILLIS) { library.addedPresetsChanges().first { it.isNotEmpty() } }.single().id
    }

    fun notes(): ImportNotes? = runBlocking {
      withTimeout(WAIT_MILLIS) { sessions.session(presetId()).first { it.importNotes != null }.importNotes }
    }

    fun copiedMessageShown(): Boolean = runBlocking {
      withTimeoutOrNull(QUIET_MILLIS) { messages.messages.first { it.text.startsWith("Copied") } } != null
    }
  }

  private fun withImports(block: Imports.() -> Unit) = LiveLibrary.use { Imports(it).block() }

  @Test
  fun aDeletedConnectionDropsOnlyItsChoiceAndTheDefaultIsPickedAgain() = withImports {
    val kept = library.created("Default")
    val gone = library.created("Spare")
    val offer = openOffer(tuned)
    assertEquals(kept.id, offer.picked.id)
    delete(gone.id)

    imports.pick(offer.choices.single { it.id == gone.id })

    val after = checkNotNull(waitUntil { state -> state.offer?.choices?.none { it.id == gone.id } == true }.offer)
    assertEquals(listOf(kept.id), after.choices.map { it.id })
    assertEquals(kept.id, after.picked.id)
  }

  @Test
  fun aGoneResultForAnEarlierPickNeverOverridesTheNewerPick() = withImports {
    library.created("Default")
    val wanted = library.created("Wanted")
    val gone = library.created("Spare")
    val offer = openOffer(tuned)
    delete(gone.id)

    imports.pick(offer.choices.single { it.id == gone.id })
    imports.pick(offer.choices.single { it.id == wanted.id })

    val after = checkNotNull(
      waitUntil { state ->
        val shown = state.offer
        shown != null && shown.choices.none { it.id == gone.id } && shown.difference?.connectionId == wanted.id
      }.offer,
    )
    assertEquals(wanted.id, after.picked.id)
  }

  @Test
  fun anUnreadableDifferenceClosesTheOfferWithTheCopyFailedNote() = withImports {
    library.created("Default")
    val broken = library.created("Broken")
    val offer = openOffer(tuned)
    sql("UPDATE connections SET format = 'AFormatFromANewerVersion' WHERE name = 'Broken'")

    imports.pick(offer.choices.single { it.id == broken.id })

    waitUntil { it.offer == null }
    assertEquals(CopyFailed.OnConnection(CopyFailure.Other, "Broken"), notes()?.copyFailed)
  }

  @Test
  fun theCopyWritesToTheConnectionTheDifferenceWasFoundFor() = withImports {
    library.created("Default")
    val target = library.created("Target")
    val offer = openOffer(tuned)
    imports.pick(offer.choices.single { it.id == target.id })
    waitUntil { it.offer?.difference?.connectionId == target.id }

    imports.copy()

    waitUntil { it.offer == null }
    val byId = library.connections().associateBy { it.id }
    assertEquals(0.7, byId.getValue(target.id).fields.samplers.temperature)
    assertNull(byId.values.single { it.id != target.id }.fields.samplers.temperature)
  }

  @Test
  fun aCopyTheStoreRefusesLeavesTheCopyFailedNote() = withImports {
    library.created("Default")
    val offer = openOffer(tuned)
    sql("CREATE TRIGGER refuse_update BEFORE UPDATE ON connections BEGIN SELECT RAISE(ABORT, 'refused'); END")

    imports.copy()

    val notes = runBlocking {
      withTimeout(WAIT_MILLIS) { sessions.session(offer.presetId).first { it.importNotes != null }.importNotes }
    }
    assertEquals(CopyFailed.OnConnection(CopyFailure.Other, "Default"), notes?.copyFailed)
    assertNull(library.connections().single().fields.samplers.temperature)
    assertEquals(false, copiedMessageShown())
  }

  @Test
  fun aCopyThatChangesNothingShowsNoCopiedMessage() = withImports {
    library.created("Default")
    openOffer(tuned)
    imports.copy()
    waitUntil { it.offer == null && it.running == null }
    copiedMessageShown()

    val again = openOffer(file("Tuned again.json", """"temperature": 0.7,"""))
    assertEquals(emptyList(), again.difference?.changes)
    imports.copy()

    waitUntil { it.offer == null }
    assertEquals(false, copiedMessageShown())
  }

  @Test
  fun queuedFilesWithTheSameNameGetTheirOwnRowsAndTheFailureKeepsItsImport() = withImports {
    val gate = CompletableDeferred<Unit>()
    val held = object : PickedFile {
      override val name = "Same.json"

      override fun open(): OpenedFile = runBlocking {
        gate.await()
        OpenedFile.Unavailable
      }
    }

    imports.start(held)
    imports.start(file("Same.json"))
    val queued = waitUntil { it.running != null && it.waiting.size == 1 }
    val ids = listOf(checkNotNull(queued.running).id, queued.waiting.single().id)
    assertNotEquals(ids[0], ids[1])
    gate.complete(Unit)

    val failure = waitUntil { it.failures.isNotEmpty() }.failures.single()
    assertEquals(ids[0], failure.importId)
    assertEquals(PresetImportFailure.CantBeOpened, failure.reason)
  }

  @Test
  fun anExportOfAPresetThisVersionCantReadShowsTheExportFailedNote() = withImports {
    imports.start(file("Plain.json"))
    val id = presetId()
    sql("UPDATE preset_entries SET role = 'narrator'")
    val exports: PresetExports = library.koin.get()

    exports.start(id, SaveTarget(DesktopExportDestination(directory.resolve("out.json")), "out.json"))

    val state = runBlocking { withTimeout(WAIT_MILLIS) { exports.runs.state(id).first { it is ExportState.NotSaved } } }
    assertEquals(ExportFailure.StoredFileUnreadable, assertIs<ExportState.NotSaved>(state).problem.reason)
  }

  @Test
  fun samplersWithNoConnectionAndSkippedCredentialsLeaveTheirNotes() = withImports {
    imports.start(file("Proxied.json", """"temperature": 0.7, "reverse_proxy": "https://proxy.example","""))

    val notes = checkNotNull(notes())
    assertEquals(true, notes.samplersDroppedNoConnection)
    assertEquals(true, notes.credentialsSkipped)
    assertEquals("Proxied.json", notes.fileName)
  }
}
