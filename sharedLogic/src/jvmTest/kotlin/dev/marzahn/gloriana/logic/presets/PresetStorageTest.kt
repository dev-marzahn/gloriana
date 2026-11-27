package dev.marzahn.gloriana.logic.presets

import dev.marzahn.gloriana.logic.characters.MemoryDestination
import dev.marzahn.gloriana.logic.debug.FaultInjected
import dev.marzahn.gloriana.logic.debug.Faults
import dev.marzahn.gloriana.logic.presets.storage.PresetEntriesEntityType
import dev.marzahn.gloriana.logic.presets.storage.PresetGroupsEntityType
import dev.marzahn.gloriana.logic.presets.storage.PresetsEntityType
import dev.marzahn.gloriana.logic.storage.RowKey
import dev.marzahn.gloriana.logic.storage.desktopGlorianaDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class PresetStorageTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun aSaveWritesTheWholePresetAndReadsBackTheSame() = runTest {
    PresetLibrary(directory).use { library ->
      val preset = library.stored(library.imported("gloriana-groups.json").id)
      val edited = preset.copy(
        name = "  Night shift ",
        content = PresetContent(
          rows = preset.content.rows.reversed() + library.repository.newGroup("Late"),
          wrappers = preset.content.wrappers.with(WrapperPrompt.Impersonation, "[As {{user}}.]"),
        ),
      )

      assertEquals(PresetSaveResult.Saved, library.repository.save(edited))

      assertEquals(edited.copy(name = "Night shift"), library.stored(preset.id))
    }
  }

  @Test
  fun aSaveWithAnEmptyNameOrANegativeDepthIsRefusedWhole() = runTest {
    PresetLibrary(directory).use { library ->
      val preset = library.stored(library.imported("minimal.json").id)
      val main = preset.content.entries.first()
      val edited = preset.copy(
        name = " ",
        content = preset.content.copy(
          rows = preset.content.rows.map { if (it == main) main.copy(placement = EntryPlacement.InChat(-1)) else it },
        ),
      )

      assertEquals(
        PresetSaveResult.Refused(setOf(PresetProblem.EmptyName, PresetProblem.NegativeDepth(main.id))),
        library.repository.save(edited),
      )
      assertEquals(preset, library.stored(preset.id))
    }
  }

  @Test
  fun aSaveOfADeletedPresetIsGone() = runTest {
    PresetLibrary(directory).use { library ->
      val preset = library.stored(library.imported("minimal.json").id)
      library.repository.delete(preset.id)

      assertEquals(PresetSaveResult.Gone, library.repository.save(preset))
    }
  }

  @Test
  fun aSaveThatFailsPartWayLeavesThePresetAsItWas() = runTest {
    val database = desktopGlorianaDatabase(directory)
    PresetLibrary(directory, database, failingAt(PresetWritePoint.SaveRowWritten)).use { library ->
      val preset = library.stored(library.imported("gloriana-groups.json").id)
      val edited = preset.copy(
        name = "Changed",
        content = preset.content.copy(rows = preset.content.entries.map { it.copy(enabled = !it.enabled) }),
      )

      assertEquals(PresetSaveResult.Failed, library.repository.save(edited))

      assertEquals(preset, library.stored(preset.id))
    }
  }

  @Test
  fun anImportThatFailsPartWayAddsNoPreset() = runTest {
    val database = desktopGlorianaDatabase(directory)
    PresetLibrary(directory, database, failingAt(PresetWritePoint.InsertRowWritten)).use { library ->
      assertEquals(
        PresetImportResult.Failed(PresetImportFailure.CouldNotSave),
        library.import.import(PresetCorpus.file("minimal.json")),
      )
      assertEquals(emptyList(), library.repository.presets().first())
    }
  }

  @Test
  fun aDeleteRemovesEveryRowAndLeavesATombstoneForEach() = runTest {
    val database = desktopGlorianaDatabase(directory)
    PresetLibrary(directory, database).use { library ->
      val preset = library.stored(library.imported("gloriana-groups.json").id)

      assertEquals(PresetDeleteResult.Deleted, library.repository.delete(preset.id))

      assertNull(library.repository.preset(preset.id).first())
      val tombstones = database.tombstoneDao().all().map { it.entityType to it.rowKey }.toSet()
      val expected = setOf(PresetsEntityType.name to RowKey.Row(preset.id)) +
        preset.content.entries.map { PresetEntriesEntityType.name to RowKey.Row(it.id) } +
        preset.content.rows.filterIsInstance<PresetGroup>().map { PresetGroupsEntityType.name to RowKey.Row(it.id) }
      assertEquals(expected, tombstones)
      assertEquals(PresetDeleteResult.Gone, library.repository.delete(preset.id))
    }
  }

  @Test
  fun aSaveThatDropsAGroupLeavesItsTombstoneAndKeepsTheEntries() = runTest {
    val database = desktopGlorianaDatabase(directory)
    PresetLibrary(directory, database).use { library ->
      val preset = library.stored(library.imported("gloriana-groups.json").id)
      val dropped = preset.content.rows.filterIsInstance<PresetGroup>().first()

      library.repository.save(preset.copy(content = preset.content.copy(rows = preset.content.rows - dropped)))

      assertEquals(preset.content.entries, library.stored(preset.id).content.entries)
      assertEquals(
        listOf(PresetGroupsEntityType.name to RowKey.Row(dropped.id)),
        database.tombstoneDao().all().map { it.entityType to it.rowKey },
      )
    }
  }

  @Test
  fun aDeleteThatFailsPartWayLeavesThePresetWhole() = runTest {
    val database = desktopGlorianaDatabase(directory)
    PresetLibrary(directory, database, failingAt(PresetWritePoint.DeleteChildrenRemoved)).use { library ->
      val preset = library.stored(library.imported("gloriana-groups.json").id)

      assertEquals(PresetDeleteResult.Failed, library.repository.delete(preset.id))

      assertEquals(preset, library.stored(preset.id))
      assertEquals(emptyList(), database.tombstoneDao().all())
    }
  }

  @Test
  fun aSaveRefusesRepeatedRowsAndRowsOfAnotherPreset() = runTest {
    PresetLibrary(directory).use { library ->
      val preset = library.stored(library.imported("gloriana-groups.json").id)
      val other = library.stored(library.imported("minimal.json").id)
      val otherEntry = other.content.entries.first()
      val repeatedGroup = preset.content.rows.filterIsInstance<PresetGroup>().first()
      val entryAsGroup = PresetGroup(preset.content.entries.first().id, "Not a group")
      val edited = preset.copy(
        content = preset.content.copy(
          rows = preset.content.rows - preset.content.entries.first() + repeatedGroup + otherEntry + entryAsGroup +
            PresetGroup(other.content.entries.last().id, "Theirs"),
        ),
      )

      assertEquals(
        PresetSaveResult.Refused(setOf(PresetProblem.RepeatedRow(repeatedGroup.id))),
        library.repository.save(edited),
      )
      val withoutRepeat = edited.copy(content = edited.content.copy(rows = edited.content.rows.distinct()))
      assertEquals(
        PresetSaveResult.Refused(
          setOf(
            PresetProblem.ForeignRow(otherEntry.id),
            PresetProblem.ForeignRow(entryAsGroup.id),
            PresetProblem.ForeignRow(other.content.entries.last().id),
          ),
        ),
        library.repository.save(withoutRepeat),
      )
      assertEquals(preset, library.stored(preset.id))
      assertEquals(other, library.stored(other.id))
    }
  }

  @Test
  fun aRowThisVersionCantReadMakesThePresetUnreadable() = runTest {
    val database = desktopGlorianaDatabase(directory)
    PresetLibrary(directory, database).use { library ->
      val id = library.imported("minimal.json").id
      val rows = checkNotNull(database.presetDao().rows(id))
      database.presetDao().upsertEntries(listOf(rows.entries.first().copy(role = "narrator")))

      assertEquals(StoredPreset.Unreadable, library.repository.preset(id).first())
      assertEquals(PresetExportResult.Unreadable, library.repository.export(id, MemoryDestination()))
      database.presetDao().update(rows.preset.copy(wrappers = """{"a_newer_wrapper":"x"}"""))
      database.presetDao().upsertEntries(rows.entries)
      assertEquals(StoredPreset.Unreadable, library.repository.preset(id).first())
      database.presetDao().update(rows.preset.copy(sourceJson = "{not json"))
      assertEquals(StoredPreset.Unreadable, library.repository.preset(id).first())
      assertEquals(PresetExportResult.Unreadable, library.repository.export(id, MemoryDestination()))
    }
  }

  @Test
  fun theChatCountComesFromItsSeam() = runTest {
    PresetLibrary(directory, chats = { _, _ -> 2 }).use { library ->
      assertEquals(2, library.repository.chatsUsing(library.imported("minimal.json").id))
    }
  }

  @Test
  fun aDeleteOfAPresetNeverStoredIsGone() = runTest {
    PresetLibrary(directory).use { library ->
      assertEquals(PresetDeleteResult.Gone, library.repository.delete(Uuid.random()))
    }
  }

  private fun failingAt(failing: PresetWritePoint) = Faults { point ->
    if (point == failing) throw FaultInjected(point)
  }
}
