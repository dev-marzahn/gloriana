package dev.marzahn.gloriana.logic.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.marzahn.gloriana.logic.chat.ChatEntity
import dev.marzahn.gloriana.logic.chat.storage.ChatDao

@Database(entities = [ChatEntity::class], version = 1)
abstract class HelperDatabase : RoomDatabase()

class HelperReadsChats(val dao: ChatDao)
