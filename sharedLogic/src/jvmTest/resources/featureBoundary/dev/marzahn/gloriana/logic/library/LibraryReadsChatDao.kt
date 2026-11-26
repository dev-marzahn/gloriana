package dev.marzahn.gloriana.logic.library

import dev.marzahn.gloriana.logic.chat.ChatRepository
import dev.marzahn.gloriana.logic.chat.storage.ChatDao

class LibraryReadsChatDao(private val dao: ChatDao, private val chats: ChatRepository) {
  suspend fun titles(): List<String> = dao.all().map { it.title } + chats.chats().map { it.title }
}
