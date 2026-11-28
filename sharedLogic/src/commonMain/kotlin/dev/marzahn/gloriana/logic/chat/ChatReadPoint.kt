package dev.marzahn.gloriana.logic.chat

import dev.marzahn.gloriana.logic.debug.FaultPoint

enum class ChatReadPoint : FaultPoint {
  ExportChatRowsRead,
  ;

  override val key: String
    get() = "chats.$name"
}
