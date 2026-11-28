package dev.marzahn.gloriana.ui.presets

import androidx.room.execSQL
import androidx.room.useWriterConnection
import dev.marzahn.gloriana.logic.characters.DesktopPickedFile
import dev.marzahn.gloriana.logic.presets.EntryPlacement
import dev.marzahn.gloriana.logic.presets.Preset
import dev.marzahn.gloriana.logic.presets.PresetContent
import dev.marzahn.gloriana.logic.presets.PresetEntry
import dev.marzahn.gloriana.logic.presets.PresetImport
import dev.marzahn.gloriana.logic.presets.PresetImportResult
import dev.marzahn.gloriana.logic.presets.PresetRepository
import dev.marzahn.gloriana.logic.presets.StoredPreset
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import dev.marzahn.gloriana.ui.connections.LiveLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
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

    fun turnOff(entry: Int, over: PresetContent = stored.content) = sessions.edit(id, over) { content ->
      val off = content.entries[entry].id
      content.copy(
        rows = content.rows.map { row -> if (row is PresetEntry && row.id == off) row.copy(enabled = false) else row },
      )
    }

    fun awaitIdle() = runBlocking {
      withTimeout(WAIT_MILLIS) { sessions.session(id).first { it.activity == PresetActivity.Idle } }
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

    assertEquals(PresetNote.NotDeleted, sessions.current(id).note)
    assertEquals(stored, read())
  }

  @Test
  fun anEditOnAPageWhoseStoredValueLagsKeepsTheSavedChange() = withPreset {
    turnOff(0)
    val saved = checkNotNull(sessions.current(id).draft)
    commands.save(stored)
    awaitIdle()
    awaitStoreHolds(saved)

    turnOff(1, over = stored.content)

    assertEquals(listOf(false, false), sessions.current(id).draft?.entries?.map { it.enabled })
  }

  @Test
  fun theSavedBaselineIsDroppedOnceThePagesStoredValueHoldsIt() = withPreset {
    turnOff(0)
    val saved = checkNotNull(sessions.current(id).draft)
    commands.save(stored)
    awaitIdle()
    awaitStoreHolds(saved)

    turnOff(1, over = saved)

    val session = sessions.current(id)
    assertNull(session.savedAhead)
    assertEquals(listOf(false, false), session.draft?.entries?.map { it.enabled })
  }

  @Test
  fun thePagesStateDropsTheSavedBaselineOnceItsStoredValueHoldsIt() = withPreset {
    turnOff(0)
    val saved = checkNotNull(sessions.current(id).draft)
    commands.save(stored)
    awaitIdle()
    awaitStoreHolds(saved)
    val page: PresetViewModel = library.koin.get()

    runBlocking(Dispatchers.Main) {
      page.show(id)
      withTimeout(WAIT_MILLIS) { page.state.first { (it as? PresetPageState.Shown)?.stored?.content == saved } }
    }

    assertNull(sessions.current(id).savedAhead)
  }

  @Test
  fun anEditDuringSavingStaysAsTheDraftAfterTheSave() = withPreset {
    turnOff(0)
    val saved = checkNotNull(sessions.current(id).draft)
    commands.save(stored)
    turnOff(1)

    awaitIdle()
    awaitStoreHolds(saved)

    assertEquals(listOf(false, true), read().content.entries.map { it.enabled })
    assertEquals(listOf(false, false), sessions.current(id).draft?.entries?.map { it.enabled })
  }

  @Test
  fun aRefusedSaveKeepsTheDraftAndShowsTheNote() = withPreset {
    sessions.edit(id, stored.content) { content ->
      content.copy(
        rows = content.rows.map { row ->
          if (row is PresetEntry && row.placement is EntryPlacement.InChat) {
            row.copy(placement = EntryPlacement.InChat(-1))
          } else {
            row
          }
        },
      )
    }
    val draft = sessions.current(id).draft

    commands.save(stored)
    awaitIdle()

    assertEquals(PresetNote.NotSaved, sessions.current(id).note)
    assertEquals(draft, sessions.current(id).draft)
    assertEquals(stored.content, read().content)
  }
}
