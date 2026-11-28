package dev.marzahn.gloriana.logic.presets.storage

import dev.marzahn.gloriana.logic.debug.Faults
import dev.marzahn.gloriana.logic.presets.Preset
import dev.marzahn.gloriana.logic.presets.PresetContent
import dev.marzahn.gloriana.logic.presets.PresetDeleteResult
import dev.marzahn.gloriana.logic.presets.PresetGroup
import dev.marzahn.gloriana.logic.presets.PresetProblem
import dev.marzahn.gloriana.logic.presets.PresetSaveResult
import dev.marzahn.gloriana.logic.presets.PresetWritePoint
import dev.marzahn.gloriana.logic.storage.ChatSetting
import dev.marzahn.gloriana.logic.storage.ChatsMoved
import dev.marzahn.gloriana.logic.storage.ChatsUsing
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import dev.marzahn.gloriana.logic.storage.IdFactory
import dev.marzahn.gloriana.logic.storage.moveAll
import dev.marzahn.gloriana.logic.storage.OrderColumn
import dev.marzahn.gloriana.logic.storage.RowKey
import dev.marzahn.gloriana.logic.storage.StoredJson
import dev.marzahn.gloriana.logic.storage.Tombstones
import dev.marzahn.gloriana.logic.storage.UpdatedAtClock
import dev.marzahn.gloriana.logic.storage.UserOrder
import dev.marzahn.gloriana.logic.storage.writeTransaction
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.Uuid

internal class PresetWrites(
  private val database: GlorianaDatabase,
  private val dao: PresetDao,
  private val ids: IdFactory,
  private val clock: UpdatedAtClock,
  private val tombstones: Tombstones,
  private val chats: ChatsUsing,
  private val faults: Faults,
) {
  val order =
    UserOrder(database, clock, faults, OrderColumn(dao::placedRows, dao::place, PresetWritePoint.MoveRowsWritten))

  suspend fun insert(name: String, content: PresetContent, source: JsonObject): Uuid = database.writeTransaction {
    val id = ids.newId()
    val now = clock.now()
    dao.insert(
      PresetEntity(
        id = id,
        name = name,
        wrappers = storedWrappers(content.wrappers),
        sourceJson = StoredJson.encodeToString(source),
        position = dao.nextPosition(),
        updatedAt = now,
      ),
    )
    faults.reached(PresetWritePoint.InsertRowWritten)
    syncChildren(id, content, kept = emptyList(), now)
    id
  }

  suspend fun save(edited: Preset): PresetSaveResult = database.writeTransaction {
    val preset = edited.copy(name = edited.name.trim())
    val previous = dao.rows(preset.id) ?: return@writeTransaction PresetSaveResult.Gone
    val problems = problems(preset, previous)
    if (problems.isNotEmpty()) return@writeTransaction PresetSaveResult.Refused(problems)
    val now = clock.now()
    dao.update(
      previous.preset.copy(
        name = preset.name,
        wrappers = storedWrappers(preset.content.wrappers),
        updatedAt = clock.updatedAt(previous.preset.updatedAt, now),
      ),
    )
    faults.reached(PresetWritePoint.SaveRowWritten)
    syncChildren(preset.id, preset.content, previous.children, now)
    PresetSaveResult.Saved
  }

  suspend fun delete(id: Uuid, chatsMoveTo: Uuid?): PresetDeleteResult = database.writeTransaction {
    val previous = dao.rows(id) ?: return@writeTransaction PresetDeleteResult.Gone
    if (chats.moveAll(ChatSetting.Preset, id, toDefault = chatsMoveTo) == ChatsMoved.NoDefaultToMoveTo) {
      return@writeTransaction PresetDeleteResult.ChatsHaveNoDefault
    }
    remove(previous.children)
    faults.reached(PresetWritePoint.DeleteChildrenRemoved)
    dao.delete(id)
    faults.reached(PresetWritePoint.DeleteRowRemoved)
    tombstones.record(PresetsEntityType, RowKey.Row(id))
    PresetDeleteResult.Deleted
  }

  private suspend fun problems(preset: Preset, previous: PresetRows): Set<PresetProblem> {
    val rows = preset.content.rows
    val rowIds = rows.map { it.id }
    val storedEntries = previous.entries.map { it.id }.toSet()
    val repeated = rowIds.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    val unknownEntries = preset.content.entries.map { it.id }.filter { it !in storedEntries }
    val groupsOnEntries = rows.filter { it is PresetGroup && it.id in storedEntries }.map { it.id }
    val foreign = unknownEntries + groupsOnEntries + dao.idsOfOtherPresets(preset.id, rowIds)
    val negativeDepths = preset.content.entries.filter { it.placement.hasNegativeDepth }.map { it.id }
    return setOfNotNull(PresetProblem.EmptyName.takeIf { preset.name.isEmpty() }) +
      repeated.map(PresetProblem::RepeatedRow) +
      foreign.map(PresetProblem::ForeignRow) +
      negativeDepths.map(PresetProblem::NegativeDepth)
  }

  private suspend fun syncChildren(presetId: Uuid, content: PresetContent, kept: List<PresetChildEntity>, now: Long) {
    val keptById = kept.associateBy { it.id }
    val wanted = content.rows.mapIndexed { position, row ->
      val previous = keptById[row.id]
      val asKept = row.toEntity(presetId, position, updatedAt = previous?.updatedAt ?: now)
      if (asKept == previous) null else asKept.stamped(clock.updatedAt(previous?.updatedAt, now))
    }
    val rowIds = content.rows.map { it.id }.toSet()
    remove(kept.filter { it.id !in rowIds })
    dao.upsertEntries(wanted.filterIsInstance<PresetEntryEntity>())
    dao.upsertGroups(wanted.filterIsInstance<PresetGroupEntity>())
  }

  private suspend fun remove(children: List<PresetChildEntity>) {
    val entries = children.filterIsInstance<PresetEntryEntity>().map { it.id }
    val groups = children.filterIsInstance<PresetGroupEntity>().map { it.id }
    dao.deleteEntries(entries)
    dao.deleteGroups(groups)
    tombstones.record(PresetEntriesEntityType, *entries.map(RowKey::Row).toTypedArray())
    tombstones.record(PresetGroupsEntityType, *groups.map(RowKey::Row).toTypedArray())
  }
}
