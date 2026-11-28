package dev.marzahn.gloriana.logic.connection.storage

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

private const val WITH_CHARACTERS = 2
private const val WITH_CONNECTIONS = 3

internal object AddConnectionsAndLibrarySettings : Migration(WITH_CHARACTERS, WITH_CONNECTIONS) {
  private val statements = listOf(
    "CREATE TABLE IF NOT EXISTS `connections` (`id` BLOB NOT NULL, `name` TEXT NOT NULL, " +
      "`nameKey` TEXT NOT NULL, `format` TEXT NOT NULL, `baseUrl` TEXT NOT NULL, `model` TEXT NOT NULL, " +
      "`contextSize` INTEGER NOT NULL, `maxReplyTokens` INTEGER NOT NULL, `streaming` INTEGER NOT NULL, " +
      "`includeUsage` INTEGER NOT NULL, `timeoutSeconds` INTEGER NOT NULL, `samplers` TEXT NOT NULL, " +
      "`customBody` TEXT NOT NULL, `headers` TEXT NOT NULL, `apiKey` TEXT, `updatedAt` INTEGER NOT NULL, " +
      "PRIMARY KEY(`id`))",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_connections_nameKey` ON `connections` (`nameKey`)",
    "CREATE TABLE IF NOT EXISTS `library_settings` (`key` TEXT NOT NULL, `value` TEXT, " +
      "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`key`))",
  )

  override fun migrate(connection: SQLiteConnection) {
    statements.forEach(connection::execSQL)
  }
}
