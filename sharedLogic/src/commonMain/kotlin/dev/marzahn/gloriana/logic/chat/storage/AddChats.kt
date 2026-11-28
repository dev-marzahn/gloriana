package dev.marzahn.gloriana.logic.chat.storage

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

private const val WITH_PERSONAS = 5
private const val WITH_CHATS = 6

internal object AddChats : Migration(WITH_PERSONAS, WITH_CHATS) {
  private val statements = listOf(
    "CREATE TABLE IF NOT EXISTS `chats` (`id` BLOB NOT NULL, `title` TEXT NOT NULL, `personaId` BLOB NOT NULL, " +
      "`presetId` BLOB NOT NULL, `connectionId` BLOB, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_chats_personaId` ON `chats` (`personaId`)",
    "CREATE INDEX IF NOT EXISTS `index_chats_presetId` ON `chats` (`presetId`)",
    "CREATE INDEX IF NOT EXISTS `index_chats_connectionId` ON `chats` (`connectionId`)",
    "CREATE TABLE IF NOT EXISTS `chat_characters` (`chatId` BLOB NOT NULL, `characterId` BLOB NOT NULL, " +
      "`position` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`chatId`, `characterId`), " +
      "FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
    "CREATE INDEX IF NOT EXISTS `index_chat_characters_characterId` ON `chat_characters` (`characterId`)",
    "CREATE TABLE IF NOT EXISTS `messages` (`id` BLOB NOT NULL, `chatId` BLOB NOT NULL, `position` INTEGER NOT NULL, " +
      "`role` TEXT NOT NULL, `speakingCharacterId` BLOB, `hidden` INTEGER NOT NULL, `shownSwipe` INTEGER NOT NULL, " +
      "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
      "FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_messages_chatId_position` ON `messages` (`chatId`, `position`)",
    "CREATE TABLE IF NOT EXISTS `swipes` (`id` BLOB NOT NULL, `messageId` BLOB NOT NULL, " +
      "`swipeIndex` INTEGER NOT NULL, `text` TEXT NOT NULL, `reasoning` TEXT NOT NULL, `state` TEXT NOT NULL, " +
      "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
      "FOREIGN KEY(`messageId`) REFERENCES `messages`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_swipes_messageId_swipeIndex` ON `swipes` (`messageId`, `swipeIndex`)",
    "CREATE TABLE IF NOT EXISTS `swipe_drafts` (`id` BLOB NOT NULL, `messageId` BLOB NOT NULL, " +
      "`text` TEXT NOT NULL, `reasoning` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, " +
      "PRIMARY KEY(`id`), " +
      "FOREIGN KEY(`messageId`) REFERENCES `messages`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_swipe_drafts_messageId` ON `swipe_drafts` (`messageId`)",
  )

  override fun migrate(connection: SQLiteConnection) {
    statements.forEach(connection::execSQL)
  }
}
