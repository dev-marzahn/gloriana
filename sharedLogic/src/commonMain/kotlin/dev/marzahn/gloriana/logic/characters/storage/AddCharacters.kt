package dev.marzahn.gloriana.logic.characters.storage

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal object AddCharacters : Migration(1, 2) {
  private val statements = listOf(
    "CREATE TABLE IF NOT EXISTS `characters` (`id` BLOB NOT NULL, `name` TEXT NOT NULL, " +
      "`nickname` TEXT NOT NULL, `description` TEXT NOT NULL, `personality` TEXT NOT NULL, " +
      "`scenario` TEXT NOT NULL, `firstMessage` TEXT NOT NULL, `exampleMessages` TEXT NOT NULL, " +
      "`creator` TEXT NOT NULL, `characterVersion` TEXT NOT NULL, `creatorNotes` TEXT NOT NULL, " +
      "`systemPrompt` TEXT NOT NULL, `postHistoryInstructions` TEXT NOT NULL, `avatarHash` TEXT, " +
      "`keptFields` TEXT NOT NULL, `cardJson` TEXT NOT NULL, `nameKey` TEXT NOT NULL, " +
      "`creatorKey` TEXT NOT NULL, `importedAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
      "`note_text` TEXT, `note_depth` INTEGER, `note_role` TEXT, `pronoun_subject` TEXT, " +
      "`pronoun_objective` TEXT, `pronoun_possessive` TEXT, `pronoun_possessivePronoun` TEXT, " +
      "`pronoun_reflexive` TEXT, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_characters_importedAt` ON `characters` (`importedAt`)",
    "CREATE INDEX IF NOT EXISTS `index_characters_nameKey_creatorKey` ON `characters` (`nameKey`, `creatorKey`)",
    "CREATE TABLE IF NOT EXISTS `character_greetings` (`id` BLOB NOT NULL, `characterId` BLOB NOT NULL, " +
      "`kind` TEXT NOT NULL, `position` INTEGER NOT NULL, `text` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, " +
      "PRIMARY KEY(`id`), FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) " +
      "ON UPDATE NO ACTION ON DELETE NO ACTION )",
    "CREATE INDEX IF NOT EXISTS `index_character_greetings_characterId` ON `character_greetings` (`characterId`)",
    "CREATE TABLE IF NOT EXISTS `character_assets` (`id` BLOB NOT NULL, `characterId` BLOB NOT NULL, " +
      "`position` INTEGER NOT NULL, `type` TEXT NOT NULL, `name` TEXT NOT NULL, `extension` TEXT NOT NULL, " +
      "`hash` TEXT, `uri` TEXT, `otherKeys` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
      "FOREIGN KEY(`characterId`) REFERENCES `characters`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
    "CREATE INDEX IF NOT EXISTS `index_character_assets_characterId` ON `character_assets` (`characterId`)",
    "CREATE INDEX IF NOT EXISTS `index_character_assets_hash` ON `character_assets` (`hash`)",
  )

  override fun migrate(connection: SQLiteConnection) {
    statements.forEach(connection::execSQL)
  }
}
