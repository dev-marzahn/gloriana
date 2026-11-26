package dev.marzahn.gloriana.logic

import androidx.room.Dao
import androidx.room.Query

@Dao
interface LogicRootDao {
  @Query("SELECT COUNT(*) FROM chats")
  suspend fun count(): Int
}
