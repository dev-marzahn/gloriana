package dev.marzahn.gloriana.logic.settings.storage

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

internal const val LIBRARY_SETTINGS_TABLE = "library_settings"

@Entity(tableName = LIBRARY_SETTINGS_TABLE)
internal data class LibrarySettingEntity(
  @PrimaryKey val key: String,
  val value: String?,
  val updatedAt: Long,
)

@Dao
internal interface LibrarySettingDao {
  @Query("SELECT * FROM $LIBRARY_SETTINGS_TABLE WHERE `key` = :key")
  fun observe(key: String): Flow<LibrarySettingEntity?>

  @Query("SELECT * FROM $LIBRARY_SETTINGS_TABLE WHERE `key` = :key")
  suspend fun find(key: String): LibrarySettingEntity?

  @Upsert
  suspend fun upsert(setting: LibrarySettingEntity)
}
