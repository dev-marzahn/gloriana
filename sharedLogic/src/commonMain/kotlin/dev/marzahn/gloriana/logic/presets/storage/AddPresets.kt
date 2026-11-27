package dev.marzahn.gloriana.logic.presets.storage

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

private const val WITH_CONNECTIONS = 3
private const val WITH_PRESETS = 4

internal object AddPresets : Migration(WITH_CONNECTIONS, WITH_PRESETS) {
  private val statements = listOf(
    "CREATE TABLE IF NOT EXISTS `presets` (`id` BLOB NOT NULL, `name` TEXT NOT NULL, `nameKey` TEXT NOT NULL, " +
      "`wrappers` TEXT NOT NULL, `sourceJson` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_presets_nameKey` ON `presets` (`nameKey`)",
    "CREATE TABLE IF NOT EXISTS `preset_entries` (`id` BLOB NOT NULL, `presetId` BLOB NOT NULL, " +
      "`position` INTEGER NOT NULL, `identifier` TEXT NOT NULL, `name` TEXT NOT NULL, `text` TEXT NOT NULL, " +
      "`filledByApp` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `role` TEXT NOT NULL, `inChatDepth` INTEGER, " +
      "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`presetId`) REFERENCES `presets`(`id`) " +
      "ON UPDATE NO ACTION ON DELETE NO ACTION )",
    "CREATE INDEX IF NOT EXISTS `index_preset_entries_presetId` ON `preset_entries` (`presetId`)",
    "CREATE TABLE IF NOT EXISTS `preset_groups` (`id` BLOB NOT NULL, `presetId` BLOB NOT NULL, " +
      "`position` INTEGER NOT NULL, `name` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
      "FOREIGN KEY(`presetId`) REFERENCES `presets`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
    "CREATE INDEX IF NOT EXISTS `index_preset_groups_presetId` ON `preset_groups` (`presetId`)",
  )

  override fun migrate(connection: SQLiteConnection) {
    statements.forEach(connection::execSQL)
  }
}
