package dev.marzahn.gloriana.logic.storage

import androidx.room.useReaderConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import dev.marzahn.gloriana.logic.app.ApplicationScope
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GlorianaDatabaseTest {
  @TempDir
  lateinit var dataDirectory: Path

  private val databaseFile get() = dataDirectory.resolve(DATABASE_FILE_NAME)

  @Test
  fun theDatabaseRunsInWalModeInTheDataDirectory() = runTest {
    val database = desktopGlorianaDatabase(dataDirectory)
    val journalMode = database.useReaderConnection { connection ->
      connection.usePrepared("PRAGMA journal_mode") { it.step(); it.getText(0) }
    }
    database.close()

    assertEquals("wal", journalMode)
    assertTrue(databaseFile.exists())
  }

  @Test
  fun theStorageGraphOpensTheDatabaseAtStart() = runTest {
    val failures = mutableListOf<Throwable>()
    val app = koinApplication {
      modules(
        storageModule,
        module {
          single { desktopGlorianaDatabase(dataDirectory) }
          single { ApplicationScope { failures += it } }
        },
      )
    }
    val koin = app.koin
    koin.get<Tombstones>()
    val scope = koin.get<ApplicationScope>()
    scope.coroutineContext.job.children.toList().joinAll()
    koin.get<GlorianaDatabase>().close()
    app.close()

    assertEquals(emptyList(), failures)
    assertEquals("wal", rawConnection().use { it.text("PRAGMA journal_mode") })
  }

  @Test
  fun closingTheGraphCancelsTheApplicationScope() = runTest {
    val app = koinApplication {
      modules(storageModule, module { single { desktopGlorianaDatabase(dataDirectory) } })
    }
    val scope = app.koin.get<ApplicationScope>()
    scope.coroutineContext.job.children.toList().joinAll()
    app.koin.get<GlorianaDatabase>().close()

    app.close()

    assertTrue(scope.coroutineContext.job.isCancelled)
  }

  @Test
  fun aFileFromANewerVersionIsRefusedAndKept() = runTest {
    MigrationHarness(dataDirectory).createAt(version = 1) { connection ->
      connection.execSQL(
        "INSERT INTO tombstones (entityType, rowKey, deletedAt, updatedAt) " +
          "VALUES ('chats', x'0192f6a0d4c37a1b8c2d3e4f5a6b7c8d', 100, 101)",
      )
      connection.execSQL("PRAGMA user_version = ${DATABASE_VERSION + 1}")
    }

    val database = desktopGlorianaDatabase(dataDirectory)
    assertFailsWith<IllegalStateException> { database.useReaderConnection { } }
    database.close()

    assertEquals(1L, rawConnection().use { it.count("SELECT COUNT(*) FROM tombstones") })
  }

  private fun rawConnection() = BundledSQLiteDriver().open(databaseFile.toString())

  private fun SQLiteConnection.count(sql: String): Long = prepare(sql).use { it.step(); it.getLong(0) }

  private fun SQLiteConnection.text(sql: String): String = prepare(sql).use { it.step(); it.getText(0) }
}
