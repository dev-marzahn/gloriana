package dev.marzahn.gloriana.logic.connection.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
internal interface ConnectionDao {
  @Query("SELECT * FROM $CONNECTIONS_TABLE ORDER BY nameKey, name")
  fun observeAll(): Flow<List<ConnectionEntity>>

  @Query("SELECT * FROM $CONNECTIONS_TABLE WHERE id = :id")
  fun observe(id: Uuid): Flow<ConnectionEntity?>

  @Query("SELECT * FROM $CONNECTIONS_TABLE WHERE id = :id")
  suspend fun find(id: Uuid): ConnectionEntity?

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
