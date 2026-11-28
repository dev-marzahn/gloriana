package dev.marzahn.gloriana.logic.personas.storage

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

private const val WITH_PRESETS = 4
private const val WITH_PERSONAS = 5

internal object AddPersonas : Migration(WITH_PRESETS, WITH_PERSONAS) {
  private val statements = listOf(
    "CREATE TABLE IF NOT EXISTS `personas` (`id` BLOB NOT NULL, `name` TEXT NOT NULL, `nameKey` TEXT NOT NULL, " +
      "`description` TEXT NOT NULL, `personaLabel` TEXT NOT NULL, `personaNote` TEXT NOT NULL, " +
      "`pronoun_subject` TEXT, `pronoun_objective` TEXT, `pronoun_possessive` TEXT, " +
      "`pronoun_possessivePronoun` TEXT, `pronoun_reflexive` TEXT, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_personas_nameKey` ON `personas` (`nameKey`)",
  )

  override fun migrate(connection: SQLiteConnection) {
    statements.forEach(connection::execSQL)
  }
}
