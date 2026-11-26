package dev.marzahn.gloriana.logic.chat.storage

import androidx.room.Dao
import androidx.room.Query
import dev.marzahn.gloriana.logic.chat.ChatEntity

@Dao
interface ChatDao {
  @Query("SELECT * FROM chats")
  suspend fun all(): List<ChatEntity>
}
