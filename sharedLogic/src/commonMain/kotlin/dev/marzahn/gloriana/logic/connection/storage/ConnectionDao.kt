package dev.marzahn.gloriana.logic.connection.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.marzahn.gloriana.logic.storage.PlacedRow
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
internal interface ConnectionDao {
  @Query("SELECT * FROM $CONNECTIONS_TABLE ORDER BY position")
  fun observeAll(): Flow<List<ConnectionEntity>>

  @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM $CONNECTIONS_TABLE")
  suspend fun nextPosition(): Long

  @Query("SELECT id, position, updatedAt FROM $CONNECTIONS_TABLE ORDER BY position")
  suspend fun placedRows(): List<PlacedRow>

  @Query("UPDATE $CONNECTIONS_TABLE SET position = :position, updatedAt = :updatedAt WHERE id = :id")
  suspend fun place(id: Uuid, position: Long, updatedAt: Long)

  @Query("SELECT * FROM $CONNECTIONS_TABLE WHERE id = :id")
  fun observe(id: Uuid): Flow<ConnectionEntity?>

  @Query("SELECT * FROM $CONNECTIONS_TABLE WHERE id = :id")
  suspend fun find(id: Uuid): ConnectionEntity?

  @Query("SELECT EXISTS(SELECT 1 FROM $CONNECTIONS_TABLE WHERE id = :id)")
  suspend fun exists(id: Uuid): Boolean

  @Query("SELECT id, format, apiKey, headers FROM $CONNECTIONS_TABLE")
  suspend fun allSecretColumns(): List<SecretColumns>

  @Query("SELECT id, format, apiKey, headers FROM $CONNECTIONS_TABLE WHERE id = :id")
  suspend fun secretColumns(id: Uuid): SecretColumns?

  @Query("SELECT id FROM $CONNECTIONS_TABLE")
  fun observeIds(): Flow<List<Uuid>>

  @Query("SELECT id FROM $CONNECTIONS_TABLE WHERE nameKey = :nameKey")
  suspend fun idNamed(nameKey: String): Uuid?

  @Insert
  suspend fun insert(connection: ConnectionEntity)

  @Update
  suspend fun update(connection: ConnectionEntity)

  @Query("DELETE FROM $CONNECTIONS_TABLE WHERE id = :id")
  suspend fun delete(id: Uuid)
}
