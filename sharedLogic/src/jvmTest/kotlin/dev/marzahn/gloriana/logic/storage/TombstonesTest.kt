package dev.marzahn.gloriana.logic.storage

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.time.Instant

class TombstonesTest {
  @TempDir
  lateinit var dataDirectory: Path

  private val clock = TestClock(epochMillis = 5_000)
  private lateinit var database: GlorianaDatabase
  private lateinit var tombstones: Tombstones

  @BeforeEach
  fun open() {
    database = desktopGlorianaDatabase(dataDirectory)
    tombstones = Tombstones(database, UpdatedAtClock(clock))
  }

  @AfterEach
  fun close() {
    database.close()
  }

  @Test
  fun aTombstoneIsWrittenAndReadBack() = runTest {
    val row = RowKey.Row(UuidV7IdFactory.newId())
    val joinRow = RowKey.JoinRow(UuidV7IdFactory.newId(), UuidV7IdFactory.newId())

    tombstones.record(EntityType("characters"), row)
    tombstones.record(EntityType("chat_characters"), joinRow)

    assertEquals(
      setOf(
        TombstoneEntity("characters", row, deletedAt = 5_000, updatedAt = 5_000),
        TombstoneEntity("chat_characters", joinRow, deletedAt = 5_000, updatedAt = 5_000),
      ),
      storedTombstones().toSet(),
    )
  }

  @Test
  fun deletingARowAgainKeepsOneTombstoneWhoseUpdatedAtGrows() = runTest {
    val row = RowKey.Row(UuidV7IdFactory.newId())
    tombstones.record(EntityType("characters"), row)

    clock.epochMillis = 4_000
    tombstones.record(EntityType("characters"), row)

    assertEquals(
      listOf(TombstoneEntity("characters", row, deletedAt = 4_000, updatedAt = 5_001)),
      storedTombstones(),
    )
  }

  @Test
  fun aTombstoneInADeleteThatFailsIsNotKept() = runTest {
    assertFailsWith<IllegalStateException> {
      database.useWriterConnection { connection ->
        connection.immediateTransaction {
          tombstones.record(EntityType("characters"), RowKey.Row(UuidV7IdFactory.newId()))
          error("the delete failed")
        }
      }
    }

    assertEquals(emptyList(), storedTombstones())
  }

  @Test
  fun aFirstTombstoneReadsTheClockOnce() = runTest {
    val advancing = AdvancingClock(epochMillis = 7_000)
    val row = RowKey.Row(UuidV7IdFactory.newId())

    Tombstones(database, UpdatedAtClock(advancing)).record(EntityType("characters"), row)

    assertEquals(listOf(TombstoneEntity("characters", row, deletedAt = 7_000, updatedAt = 7_000)), storedTombstones())
  }

  @Test
  fun recordHoldsTheWriteLockWhileItReadsAndWrites() = runTest {
    val probe = WriteLockProbeClock(dataDirectory.resolve(DATABASE_FILE_NAME).toString())

    Tombstones(database, UpdatedAtClock(probe)).record(EntityType("characters"), RowKey.Row(UuidV7IdFactory.newId()))

    assertEquals(listOf(true), probe.writeLockHeldAtEachRead)
  }

  private suspend fun storedTombstones() = database.tombstoneDao().all()

  private class AdvancingClock(private var epochMillis: Long) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(epochMillis++)
  }

  private class WriteLockProbeClock(private val databasePath: String) : Clock {
    val writeLockHeldAtEachRead = mutableListOf<Boolean>()

    override fun now(): Instant {
      writeLockHeldAtEachRead += BundledSQLiteDriver().open(databasePath).use { connection ->
        connection.execSQL("PRAGMA busy_timeout = 0")
        val held = runCatching { connection.execSQL("BEGIN IMMEDIATE") }.isFailure
        if (!held) connection.execSQL("ROLLBACK")
        held
      }
      return Instant.fromEpochMilliseconds(1_000)
    }
  }
}
