package dev.marzahn.gloriana.logic.personas.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.marzahn.gloriana.logic.storage.PlacedRow
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
internal interface PersonaDao {
  @Query("SELECT * FROM $PERSONAS_TABLE ORDER BY position")
  fun observeAll(): Flow<List<PersonaEntity>>

  @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM $PERSONAS_TABLE")
  suspend fun nextPosition(): Long

  @Query("SELECT id, position, updatedAt FROM $PERSONAS_TABLE ORDER BY position")
  suspend fun placedRows(): List<PlacedRow>

  @Query("UPDATE $PERSONAS_TABLE SET position = :position, updatedAt = :updatedAt WHERE id = :id")
  suspend fun place(id: Uuid, position: Long, updatedAt: Long)

  @Query("SELECT * FROM $PERSONAS_TABLE WHERE id = :id")
  fun observe(id: Uuid): Flow<PersonaEntity?>

  @Query("SELECT id FROM $PERSONAS_TABLE")
  fun observeIds(): Flow<List<Uuid>>

  @Query("SELECT * FROM $PERSONAS_TABLE WHERE id = :id")
  suspend fun find(id: Uuid): PersonaEntity?

  @Query("SELECT EXISTS(SELECT 1 FROM $PERSONAS_TABLE WHERE id = :id)")
  suspend fun exists(id: Uuid): Boolean

  @Query("SELECT name FROM $PERSONAS_TABLE WHERE id = :id")
  suspend fun name(id: Uuid): String?

  @Query(
    "SELECT name, description, pronoun_subject, pronoun_objective, pronoun_possessive, " +
      "pronoun_possessivePronoun, pronoun_reflexive FROM $PERSONAS_TABLE WHERE id = :id",
  )
  suspend fun findInPrompt(id: Uuid): PersonaInPromptRow?

  @Insert
  suspend fun insert(persona: PersonaEntity)

  @Update
  suspend fun update(persona: PersonaEntity)

  @Query("DELETE FROM $PERSONAS_TABLE WHERE id = :id")
  suspend fun delete(id: Uuid)
}
