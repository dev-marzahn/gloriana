package dev.marzahn.gloriana.logic.chat

import androidx.room.Entity
import androidx.room.PrimaryKey

class ChatRows {
  @Entity(tableName = "chat_rows")
  data class Row(@PrimaryKey val id: ByteArray)
}
