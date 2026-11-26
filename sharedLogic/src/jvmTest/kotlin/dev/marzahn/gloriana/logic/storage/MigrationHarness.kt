package dev.marzahn.gloriana.logic.storage

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.nio.file.Path
import kotlin.io.path.Path

class MigrationHarness(private val dataDirectory: Path) {
  private val helper = MigrationTestHelper(
    schemaDirectoryPath = Path(checkNotNull(System.getProperty("gloriana.roomSchemas"))),
    databasePath = dataDirectory.resolve(DATABASE_FILE_NAME),
    driver = BundledSQLiteDriver(),
    databaseClass = GlorianaDatabase::class,
    databaseFactory = ::GlorianaDatabase_Impl,
  )

  fun createAt(version: Int, write: (SQLiteConnection) -> Unit) {
    helper.createDatabase(version).use(write)
  }

  suspend fun <T> migrateToLatest(read: suspend (GlorianaDatabase) -> T): T {
    helper.runMigrationsAndValidate(DATABASE_VERSION, migrations).close()
    val database = desktopGlorianaDatabase(dataDirectory)
    try {
      return read(database)
    } finally {
      database.close()
    }
  }
}
