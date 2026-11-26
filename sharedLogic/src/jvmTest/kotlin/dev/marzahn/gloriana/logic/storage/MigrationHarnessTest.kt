package dev.marzahn.gloriana.logic.storage

import androidx.sqlite.execSQL
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class MigrationHarnessTest {
  @TempDir
  lateinit var dataDirectory: Path

  @Test
  fun rowsWrittenOnTheFirstVersionAreReadBackOnTheLatest() = runTest {
    val harness = MigrationHarness(dataDirectory)
    harness.createAt(version = 1) { connection ->
      connection.execSQL(
        "INSERT INTO tombstones (entityType, rowKey, deletedAt, updatedAt) " +
          "VALUES ('chats', x'0192f6a0d4c37a1b8c2d3e4f5a6b7c8d', 100, 101)",
      )
    }

    val tombstones = harness.migrateToLatest { it.tombstoneDao().all() }

    assertEquals(
      listOf(
        TombstoneEntity(
          entityType = "chats",
          rowKey = RowKey.Row(Uuid.parse("0192f6a0-d4c3-7a1b-8c2d-3e4f5a6b7c8d")),
          deletedAt = 100,
          updatedAt = 101,
        ),
      ),
      tombstones,
    )
  }
}
