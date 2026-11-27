package dev.marzahn.gloriana.logic.characters.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
internal interface CharacterDao {
  @Query("SELECT id, name, avatarHash FROM characters ORDER BY importedAt DESC")
  fun summaries(): Flow<List<CharacterSummaryRow>>

  @Transaction
  @Query("SELECT * FROM characters WHERE id = :id")
  fun observeRows(id: Uuid): Flow<CharacterRows?>

  @Transaction
  @Query("SELECT * FROM characters WHERE id = :id")
  suspend fun rows(id: Uuid): CharacterRows?

  @Query(
    "SELECT id, name, avatarHash FROM characters WHERE nameKey = :nameKey AND creatorKey = :creatorKey " +
      "ORDER BY importedAt DESC LIMIT 1",
  )
  suspend fun lastImportedMatch(nameKey: String, creatorKey: String): CharacterSummaryRow?

  @Query("SELECT MAX(importedAt) FROM characters")
  suspend fun latestImportedAt(): Long?

  @Query(
    "SELECT avatarHash FROM characters WHERE avatarHash IS NOT NULL " +
      "UNION SELECT hash FROM character_assets WHERE hash IS NOT NULL",
  )
  suspend fun storedHashes(): List<String>

  @Insert
  suspend fun insert(character: CharacterEntity)

  @Update
  suspend fun update(character: CharacterEntity)

  @Query("DELETE FROM characters WHERE id = :id")
  suspend fun delete(id: Uuid)
}

@Dao
internal interface CharacterChildDao {
  @Insert
  suspend fun insertGreetings(greetings: List<CharacterGreetingEntity>)

  @Insert
  suspend fun insertAssets(assets: List<CharacterAssetEntity>)

  @Query("DELETE FROM character_greetings WHERE characterId = :characterId")
  suspend fun deleteGreetings(characterId: Uuid)

  @Query("DELETE FROM character_assets WHERE characterId = :characterId")
  suspend fun deleteAssets(characterId: Uuid)
}
