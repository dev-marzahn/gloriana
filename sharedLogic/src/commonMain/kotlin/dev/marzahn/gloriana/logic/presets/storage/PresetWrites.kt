package dev.marzahn.gloriana.logic.presets.storage

import dev.marzahn.gloriana.logic.debug.Faults
import dev.marzahn.gloriana.logic.presets.Preset
import dev.marzahn.gloriana.logic.presets.PresetContent
import dev.marzahn.gloriana.logic.presets.PresetDeleteResult
import dev.marzahn.gloriana.logic.presets.PresetEntry
import dev.marzahn.gloriana.logic.presets.PresetProblem
import dev.marzahn.gloriana.logic.presets.PresetSaveResult
import dev.marzahn.gloriana.logic.presets.PresetWritePoint
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import dev.marzahn.gloriana.logic.storage.IdFactory
import dev.marzahn.gloriana.logic.storage.RowKey
import dev.marzahn.gloriana.logic.storage.StoredJson
import dev.marzahn.gloriana.logic.storage.Tombstones
import dev.marzahn.gloriana.logic.storage.UpdatedAtClock
import dev.marzahn.gloriana.logic.storage.nameKey
import dev.marzahn.gloriana.logic.storage.writeTransaction
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.Uuid

internal class PresetWrites(
  private val database: GlorianaDatabase,
  private val ids: IdFactory,
  private val clock: UpdatedAtClock,
  private val tombstones: Tombstones,
  private val faults: Faults,
) {
  private val dao = database.presetDao()

  suspend fun insert(name: String, content: PresetContent, source: JsonObject): Uuid = database.writeTransaction {
    val id = ids.newId()
    val now = clock.now()
    dao.insert(
      PresetEntity(
        id = id,
        name = name,
        nameKey = nameKey(name),
        wrappers = storedWrappers(content.wrappers),
        sourceJson = StoredJson.encodeToString(source),
        updatedAt = now,
      ),
    )
    faults.reached(PresetWritePoint.InsertRowWritten)
    syncChildren(id, content, kept = emptyList(), now)
    id
  }

  suspend fun save(preset: Preset): PresetSaveResult = database.writeTransaction {
    val previous = dao.rows(preset.id) ?: return@writeTransaction PresetSaveResult.Gone
    val problems = foreignRows(preset, previous)
    if (problems.isNotEmpty()) return@writeTransaction PresetSaveResult.Refused(problems)
    val now = clock.now()
    dao.update(
      previous.preset.copy(
        name = preset.name,
        nameKey = nameKey(preset.name),
        wrappers = storedWrappers(preset.content.wrappers),
        updatedAt = clock.updatedAt(previous.preset.updatedAt, now),
      ),
    )
    faults.reached(PresetWritePoint.SaveRowWritten)
    syncChildren(preset.id, preset.content, previous.children, now)
    PresetSaveResult.Saved
  }

  suspend fun delete(id: Uuid): PresetDeleteResult = database.writeTransaction {
    val previous = dao.rows(id) ?: return@writeTransaction PresetDeleteResult.Gone
    remove(previous.children)
    faults.reached(PresetWritePoint.DeleteChildrenRemoved)
    dao.delete(id)
    tombstones.record(PresetsEntityType, RowKey.Row(id))
    PresetDeleteResult.Deleted
  }

  private suspend fun foreignRows(preset: Preset, previous: PresetRows): Set<PresetProblem> {
    val storedEntries = previous.entries.map { it.id }.toSet()
    val unknownEntries = preset.content.entries.map { it.id }.filter { it !in storedEntries }
    val otherPresets = dao.idsOfOtherPresets(preset.id, preset.content.rows.map { it.id })
    val groupsOnEntries = preset.content.rows.filter { it !is PresetEntry && it.id in storedEntries }.map { it.id }
    return (unknownEntries + otherPresets + groupsOnEntries).map(PresetProblem::ForeignRow).toSet()
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
    dao.deleteEntries(children.filterIsInstance<PresetEntryEntity>().map { it.id })
    dao.deleteGroups(children.filterIsInstance<PresetGroupEntity>().map { it.id })
    children.forEach { tombstones.record(it.entityType, RowKey.Row(it.id)) }
  }
}
