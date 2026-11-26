package dev.marzahn.gloriana.logic.chat

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(@PrimaryKey val id: ByteArray, val title: String)

data class Chat(val title: String)
