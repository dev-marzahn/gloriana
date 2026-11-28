package dev.marzahn.gloriana.logic.storage

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

private const val WITH_CHATS = 6
private const val WITH_USER_ORDER = 7

private class TableAtVersion6(val name: String, val shownBy: String, val nameKeyOnlySorted: Boolean)

private val tablesAtVersion6 = listOf(
  TableAtVersion6("personas", shownBy = "id", nameKeyOnlySorted = true),
  TableAtVersion6("presets", shownBy = "nameKey, name, id", nameKeyOnlySorted = true),
  TableAtVersion6("connections", shownBy = "nameKey, id", nameKeyOnlySorted = false),
)

internal object AddUserOrder : Migration(WITH_CHATS, WITH_USER_ORDER) {
  override fun migrate(connection: SQLiteConnection) {
    tablesAtVersion6.forEach { table ->
      connection.backfillPosition(table)
      if (table.nameKeyOnlySorted) connection.dropNameKey(table.name)
    }
  }

  private fun SQLiteConnection.backfillPosition(table: TableAtVersion6) {
    val name = table.name
    execSQL("ALTER TABLE `$name` ADD COLUMN `position` INTEGER NOT NULL DEFAULT 0")
    execSQL(
      "UPDATE `$name` SET position = (SELECT ranked.place FROM " +
        "(SELECT id, ROW_NUMBER() OVER (ORDER BY ${table.shownBy}) - 1 AS place FROM `$name`) AS ranked " +
        "WHERE ranked.id = `$name`.id)",
    )
  }

  private fun SQLiteConnection.dropNameKey(name: String) {
    execSQL("DROP INDEX `index_${name}_nameKey`")
    execSQL("ALTER TABLE `$name` DROP COLUMN `nameKey`")
  }
}
