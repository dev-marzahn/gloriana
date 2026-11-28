package dev.marzahn.gloriana.logic.storage

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Upsert
import kotlin.jvm.JvmInline

@JvmInline
value class EntityType(val name: String)

class Tombstones(private val database: GlorianaDatabase, private val clock: UpdatedAtClock) {
  suspend fun record(entityType: EntityType, vararg keys: RowKey) {
    if (keys.isEmpty()) return
    val dao = database.tombstoneDao()
    database.writeTransaction {
      val now = clock.now()
      val previous = dao.find(entityType.name, keys.toList()).associate { it.rowKey to it.updatedAt }
      dao.upsert(
        keys.map { key ->
          TombstoneEntity(
            entityType = entityType.name,
            rowKey = key,
            deletedAt = now,
            updatedAt = clock.updatedAt(previous[key], now),
          )
        },
      )
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
  suspend fun upsert(tombstones: List<TombstoneEntity>)

  @Query("SELECT * FROM tombstones WHERE entityType = :entityType AND rowKey IN (:rowKeys)")
  suspend fun find(entityType: String, rowKeys: List<RowKey>): List<TombstoneEntity>

  @Query("SELECT * FROM tombstones ORDER BY updatedAt")
  suspend fun all(): List<TombstoneEntity>
}
