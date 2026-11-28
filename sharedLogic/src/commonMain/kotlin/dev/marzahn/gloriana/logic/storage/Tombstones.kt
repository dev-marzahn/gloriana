package dev.marzahn.gloriana.logic.storage

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Upsert
import kotlin.jvm.JvmInline

private const val KEYS_PER_LOOKUP = 10_000

const val TOMBSTONES_TABLE = "tombstones"

const val INSERT_TOMBSTONES = "INSERT INTO $TOMBSTONES_TABLE (entityType, rowKey, deletedAt, updatedAt)"

const val OR_RENEW_TOMBSTONES =
  "ON CONFLICT(entityType, rowKey) DO UPDATE SET deletedAt = :now, $UPDATED_AT_BUMPED_TO_NOW"

@JvmInline
value class EntityType(val name: String)

class Tombstones(private val database: GlorianaDatabase, private val clock: UpdatedAtClock) {
  suspend fun record(entityType: EntityType, vararg keys: RowKey) = record(entityType, keys.asList())

  suspend fun record(entityType: EntityType, keys: List<RowKey>) {
    if (keys.isEmpty()) return
    val dao = database.tombstoneDao()
    database.writeTransaction {
      val now = clock.now()
      keys.chunked(KEYS_PER_LOOKUP).forEach { chunk ->
        val previous = dao.find(entityType.name, chunk).associate { it.rowKey to it.updatedAt }
        dao.upsert(
          chunk.map { key ->
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
}

@Entity(tableName = TOMBSTONES_TABLE,primaryKeys = ["entityType", "rowKey"])
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

  @Query("SELECT * FROM $TOMBSTONES_TABLE WHERE entityType = :entityType AND rowKey IN (:rowKeys)")
  suspend fun find(entityType: String, rowKeys: List<RowKey>): List<TombstoneEntity>

  @Query("SELECT * FROM $TOMBSTONES_TABLE ORDER BY updatedAt")
  suspend fun all(): List<TombstoneEntity>
}
