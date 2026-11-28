package dev.marzahn.gloriana.ui.presets

import androidx.room.execSQL
import androidx.room.useWriterConnection
import dev.marzahn.gloriana.logic.characters.DesktopPickedFile
import dev.marzahn.gloriana.logic.presets.EntryPlacement
import dev.marzahn.gloriana.logic.presets.Preset
import dev.marzahn.gloriana.logic.presets.PresetContent
import dev.marzahn.gloriana.logic.presets.PresetImport
import dev.marzahn.gloriana.logic.presets.PresetImportResult
import dev.marzahn.gloriana.logic.presets.PresetRepository
import dev.marzahn.gloriana.logic.presets.StoredPreset
import dev.marzahn.gloriana.logic.presets.WrapperPrompt
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import dev.marzahn.gloriana.ui.state.SaveFailure
import dev.marzahn.gloriana.ui.testing.LiveLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

private const val WAIT_MILLIS = 10_000L

private const val PRESET =
  """{"prompts": [
    {"identifier": "main", "name": "Main Prompt", "role": "system", "content": "Be vivid."},
    {"identifier": "nsfw", "name": "Auxiliary Prompt", "role": "system", "content": "Stay in character.",
     "injection_position": 1, "injection_depth": 4}
  ],
  "prompt_order": [{"character_id": 100001, "order": [
    {"identifier": "main", "enabled": true},
    {"identifier": "nsfw", "enabled": true}
  ]}]}"""

class PresetCommandsTest {
  private class Fixture(val library: LiveLibrary) {
    val sessions: PresetSessions = library.koin.get()
    val commands: PresetCommands = library.koin.get()
    private val repository: PresetRepository = library.koin.get()

    val stored: Preset = runBlocking {
      val file = Files.createTempFile("gloriana-preset", ".json").apply { writeText(PRESET) }
      val result = library.koin.get<PresetImport>().import(DesktopPickedFile(file))
      file.deleteIfExists()
      read(assertIs<PresetImportResult.Imported>(result).id)
    }

    val id: Uuid get() = stored.id


    fun read(id: Uuid = stored.id): Preset = runBlocking {
      assertIs<StoredPreset.Readable>(repository.preset(id).first()).preset
    }

    fun draft(): PresetContent? = sessions.current(id).unsaved?.draft

    fun turnOff(entry: Int, pageShows: PresetContent = stored.content) =
      sessions.edit(id, pageShows, PresetIntent.SetOn(stored.content.entries[entry].id, on = false))

    fun awaitIdle() = runBlocking {
      withTimeout(WAIT_MILLIS) { sessions.session(id).first { it.isIdle } }
    }

    fun awaitStoreHolds(content: PresetContent) = runBlocking {
      withTimeout(WAIT_MILLIS) {
        repository.preset(id).first { (it as? StoredPreset.Readable)?.preset?.content == content }
      }
    }
  }

  private fun withPreset(block: Fixture.() -> Unit) = LiveLibrary.use { Fixture(it).block() }

  @Test
  fun aDeleteTheStoreRefusesKeepsThePresetAndShowsTheNote() = withPreset {
    runBlocking {
      library.koin.get<GlorianaDatabase>().useWriterConnection {
        it.execSQL("CREATE TRIGGER refuse_delete BEFORE DELETE ON presets BEGIN SELECT RAISE(ABORT, 'refused'); END")
      }
    }
    commands.askToDelete(stored)
    runBlocking {
      withTimeout(WAIT_MILLIS) { sessions.session(id).first { it.activity is PresetActivity.ConfirmingDelete } }
    }

    commands.confirmDelete(stored)
    awaitIdle()

    assertTrue(sessions.current(id).notDeleted)
    assertEquals(stored, read())
  }

  @Test
  fun anEditOnAPageWhoseStoredValueLagsKeepsTheSavedChange() = withPreset {
    turnOff(0)
    val saved = checkNotNull(draft())
    commands.save(stored)
    awaitIdle()
    awaitStoreHolds(saved)

    turnOff(1)

    assertEquals(listOf(false, false), draft()?.entries?.map { it.enabled })
  }

  @Test
  fun theStoredValueTakesOverFromTheSavedBaselineOnceItArrives() = withPreset {
    turnOff(0)
    val saved = checkNotNull(draft())
    commands.save(stored)
    awaitIdle()
    awaitStoreHolds(saved)

    sessions.storedNow(id, saved)
    assertEquals(PresetSession(), sessions.current(id))
    turnOff(1, pageShows = saved)

    val unsaved = checkNotNull(sessions.current(id).unsaved)
    assertEquals(saved, unsaved.baseline)
    assertEquals(listOf(false, false), unsaved.draft.entries.map { it.enabled })
  }

  @Test
  fun aStoredValueThatDiffersFromTheSaveStillEndsTheSavedBaseline() = withPreset {
    turnOff(0)
    val saved = checkNotNull(draft())
    commands.save(stored)
    awaitIdle()
    awaitStoreHolds(saved)

    val normalised = saved.copy(wrappers = saved.wrappers.with(WrapperPrompt.NewChat, "Changed in storage"))
    sessions.storedNow(id, normalised)

    val page = sessions.current(id).unsavedOver(normalised)
    assertEquals(normalised, page.baseline)
    assertFalse(page.hasChanges)
  }

  @Test
  fun thePagesStateFollowsTheStoredValueAfterASave() = withPreset {
    turnOff(0)
    val saved = checkNotNull(draft())
    commands.save(stored)
    awaitIdle()
    awaitStoreHolds(saved)
    val page: PresetViewModel = library.koin.get()

    val shown = runBlocking(Dispatchers.Main) {
      page.show(id)
      withTimeout(WAIT_MILLIS) { page.state.first { (it as? PresetPageState.Shown)?.unsaved?.stored == saved } }
    }

    assertFalse(assertIs<PresetPageState.Shown>(shown).hasChanges)
  }

  @Test
  fun anEditDuringSavingStaysAsTheDraftAfterTheSave() = withPreset {
    turnOff(0)
    val saved = checkNotNull(draft())
    commands.save(stored)
    turnOff(1)

    awaitIdle()
    awaitStoreHolds(saved)

    assertEquals(listOf(false, true), read().content.entries.map { it.enabled })
    assertEquals(listOf(false, false), draft()?.entries?.map { it.enabled })
  }

  @Test
  fun aRefusedSaveKeepsTheDraftAndShowsTheNote() = withPreset {
    val inChat = stored.content.entries.first { it.placement is EntryPlacement.InChat }
    sessions.edit(id, stored.content, PresetIntent.SetPlacement(inChat.id, EntryPlacement.InChat(-1)))
    val draft = draft()

    commands.save(stored)
    awaitIdle()

    assertEquals(SaveFailure.Other, sessions.current(id).notSaved)
    assertEquals(draft, draft())
    assertEquals(stored.content, read().content)
  }

  @Test
  fun anEditFromAPageShowingAnOlderRowKeepsTheNewerRowUnderTheEdits() = withPreset {
    turnOff(0)
    val newer = stored.content.copy(wrappers = stored.content.wrappers.with(WrapperPrompt.NewChat, "From storage"))
    sessions.storedNow(id, newer)

    turnOff(1, pageShows = stored.content)

    val unsaved = checkNotNull(sessions.current(id).unsaved)
    assertEquals(newer, unsaved.baseline)
    assertEquals("From storage", unsaved.draft.wrappers[WrapperPrompt.NewChat])
    assertEquals(listOf(false, false), unsaved.draft.entries.map { it.enabled })
  }
}
