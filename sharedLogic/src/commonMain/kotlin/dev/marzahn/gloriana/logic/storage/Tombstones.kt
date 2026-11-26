package dev.marzahn.gloriana.logic.storage

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import kotlin.jvm.JvmInline

@JvmInline
value class EntityType(val name: String)

class Tombstones(private val database: GlorianaDatabase, private val clock: UpdatedAtClock) {
  suspend fun record(entityType: EntityType, key: RowKey) {
    val dao = database.tombstoneDao()
    database.useWriterConnection { connection ->
      connection.immediateTransaction {
        val now = clock.now()
        val previous = dao.find(entityType.name, key)
        dao.upsert(
          TombstoneEntity(
            entityType = entityType.name,
            rowKey = key,
            deletedAt = now,
            updatedAt = clock.updatedAt(previous?.updatedAt, now),
          ),
        )
      }
    }
  }
}

@Entity(tableName = "tombstones", primaryKeys = ["entityType", "rowKey"])
internal data class TombstoneEntity(
  val entityType: String,
  val rowKey: RowKey,
  val deletedAt: Long,
  val updatedAt: Long,
)

@Dao
internal interface TombstoneDao {
  @Upsert
  suspend fun upsert(tombstone: TombstoneEntity)

  @Query("SELECT * FROM tombstones WHERE entityType = :entityType AND rowKey = :rowKey")
  suspend fun find(entityType: String, rowKey: RowKey): TombstoneEntity?

  @Query("SELECT * FROM tombstones ORDER BY updatedAt")
  suspend fun all(): List<TombstoneEntity>
}
