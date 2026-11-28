package dev.marzahn.gloriana.logic.personas.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
internal interface PersonaDao {
  @Query("SELECT * FROM $PERSONAS_TABLE ORDER BY nameKey, name")
  fun observeAll(): Flow<List<PersonaEntity>>

  @Query("SELECT * FROM $PERSONAS_TABLE WHERE id = :id")
  fun observe(id: Uuid): Flow<PersonaEntity?>

  @Query("SELECT id FROM $PERSONAS_TABLE")
  fun observeIds(): Flow<List<Uuid>>

  @Query("SELECT * FROM $PERSONAS_TABLE WHERE id = :id")
  suspend fun find(id: Uuid): PersonaEntity?

  @Query("SELECT EXISTS(SELECT 1 FROM $PERSONAS_TABLE WHERE id = :id)")
  suspend fun exists(id: Uuid): Boolean

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
