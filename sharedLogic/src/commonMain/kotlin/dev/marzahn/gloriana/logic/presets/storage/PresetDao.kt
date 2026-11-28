package dev.marzahn.gloriana.logic.presets.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
internal interface PresetDao {
  @Query(
    "SELECT id, name, " +
      "(SELECT COUNT(*) FROM $PRESET_ENTRIES_TABLE WHERE presetId = p.id) AS entries, " +
      "(SELECT COUNT(*) FROM $PRESET_ENTRIES_TABLE WHERE presetId = p.id AND enabled) AS entriesOn " +
      "FROM $PRESETS_TABLE p ORDER BY nameKey, name",
  )
  fun summaries(): Flow<List<PresetSummaryRow>>

  @Query("SELECT id FROM $PRESETS_TABLE")
  fun observeIds(): Flow<List<Uuid>>

  @Query("SELECT EXISTS(SELECT 1 FROM $PRESETS_TABLE WHERE id = :id)")
  suspend fun exists(id: Uuid): Boolean

  @Query("SELECT name FROM $PRESETS_TABLE WHERE id = :id")
  suspend fun name(id: Uuid): String?

  @Transaction
  @Query("SELECT * FROM $PRESETS_TABLE WHERE id = :id")
  fun observeRows(id: Uuid): Flow<PresetRows?>

  @Transaction
  @Query("SELECT * FROM $PRESETS_TABLE WHERE id = :id")
  suspend fun rows(id: Uuid): PresetRows?

  @Insert
  suspend fun insert(preset: PresetEntity)

  @Update
  suspend fun update(preset: PresetEntity)

  @Query("DELETE FROM $PRESETS_TABLE WHERE id = :id")
  suspend fun delete(id: Uuid)

  @Query(
    "SELECT id FROM $PRESET_ENTRIES_TABLE WHERE id IN (:ids) AND presetId != :presetId " +
      "UNION SELECT id FROM $PRESET_GROUPS_TABLE WHERE id IN (:ids) AND presetId != :presetId",
  )
  suspend fun idsOfOtherPresets(presetId: Uuid, ids: List<Uuid>): List<Uuid>

  @Upsert
  suspend fun upsertEntries(entries: List<PresetEntryEntity>)

  @Upsert
  suspend fun upsertGroups(groups: List<PresetGroupEntity>)

  @Query("DELETE FROM $PRESET_ENTRIES_TABLE WHERE id IN (:ids)")
  suspend fun deleteEntries(ids: List<Uuid>)

  @Query("DELETE FROM $PRESET_GROUPS_TABLE WHERE id IN (:ids)")
  suspend fun deleteGroups(ids: List<Uuid>)
}
