package dev.marzahn.gloriana.logic.chat

import dev.marzahn.gloriana.logic.chat.storage.ChatDao

class ChatRepository(private val dao: ChatDao) {
  suspend fun chats(): List<Chat> = dao.all().map { Chat(it.title) }
}
