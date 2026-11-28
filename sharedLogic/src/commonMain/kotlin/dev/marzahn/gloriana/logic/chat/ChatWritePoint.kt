package dev.marzahn.gloriana.logic.chat

import dev.marzahn.gloriana.logic.debug.FaultPoint

enum class ChatWritePoint : FaultPoint {
  StartChatRowWritten,
  StartGreetingWritten,
  DraftSwipeWritten,
  DeleteMessagesRemoved,
  ;

  override val key: String
    get() = "chats.$name"
}
