package dev.marzahn.gloriana.logic.library

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.marzahn.gloriana.logic.chat.ChatEntity
import dev.marzahn.gloriana.logic.chat.storage.ChatDao

@Database(entities = [ChatEntity::class], version = 1)
abstract class LibraryDatabase : RoomDatabase() {
  abstract fun chatDao(): ChatDao
}
