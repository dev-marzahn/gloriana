package dev.marzahn.gloriana.logic.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

internal const val DATABASE_FILE_NAME = "library.db"
internal const val DATABASE_VERSION = 1

@Database(entities = [TombstoneEntity::class], version = DATABASE_VERSION)
@TypeConverters(StorageConverters::class)
abstract class GlorianaDatabase : RoomDatabase() {
  internal abstract fun tombstoneDao(): TombstoneDao
}

internal val migrations: List<Migration> = emptyList()

internal fun RoomDatabase.Builder<GlorianaDatabase>.buildGlorianaDatabase(): GlorianaDatabase =
  setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
    .addMigrations(*migrations.toTypedArray())
    .build()
