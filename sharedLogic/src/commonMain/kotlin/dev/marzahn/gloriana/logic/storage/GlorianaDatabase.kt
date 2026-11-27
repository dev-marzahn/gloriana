package dev.marzahn.gloriana.logic.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.marzahn.gloriana.logic.characters.storage.AddCharacters
import dev.marzahn.gloriana.logic.characters.storage.CharacterAssetEntity
import dev.marzahn.gloriana.logic.characters.storage.CharacterChildDao
import dev.marzahn.gloriana.logic.characters.storage.CharacterDao
import dev.marzahn.gloriana.logic.characters.storage.CharacterEntity
import dev.marzahn.gloriana.logic.characters.storage.CharacterGreetingEntity
import dev.marzahn.gloriana.logic.connection.storage.ConnectionDao
import dev.marzahn.gloriana.logic.connection.storage.ConnectionEntity
import dev.marzahn.gloriana.logic.presets.storage.AddPresets
import dev.marzahn.gloriana.logic.presets.storage.PresetDao
import dev.marzahn.gloriana.logic.presets.storage.PresetEntity
import dev.marzahn.gloriana.logic.presets.storage.PresetEntryEntity
import dev.marzahn.gloriana.logic.presets.storage.PresetGroupEntity
import dev.marzahn.gloriana.logic.settings.storage.LibrarySettingDao
import dev.marzahn.gloriana.logic.settings.storage.LibrarySettingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

internal const val DATABASE_FILE_NAME = "library.db"
internal const val DATABASE_VERSION = 4

@Database(
  entities = [
    TombstoneEntity::class,
    CharacterEntity::class,
    CharacterGreetingEntity::class,
    CharacterAssetEntity::class,
    ConnectionEntity::class,
    LibrarySettingEntity::class,
    PresetEntity::class,
    PresetEntryEntity::class,
    PresetGroupEntity::class,
  ],
  version = DATABASE_VERSION,
)
@TypeConverters(StorageConverters::class)
abstract class GlorianaDatabase : RoomDatabase() {
  internal abstract fun tombstoneDao(): TombstoneDao

  internal abstract fun characterDao(): CharacterDao

  internal abstract fun characterChildDao(): CharacterChildDao

  internal abstract fun connectionDao(): ConnectionDao

  internal abstract fun librarySettingDao(): LibrarySettingDao

  internal abstract fun presetDao(): PresetDao
}

internal val migrations: List<Migration> = listOf(AddCharacters, AddConnectionsAndLibrarySettings, AddPresets)

internal fun RoomDatabase.Builder<GlorianaDatabase>.buildGlorianaDatabase(): GlorianaDatabase =
  setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
    .addMigrations(*migrations.toTypedArray())
    .build()
