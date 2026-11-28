package dev.marzahn.gloriana.logic.chat.storage

import dev.marzahn.gloriana.logic.chat.ChatOpening
import dev.marzahn.gloriana.logic.chat.ChatWritePoint
import dev.marzahn.gloriana.logic.chat.MessageRole
import dev.marzahn.gloriana.logic.chat.NewChat
import dev.marzahn.gloriana.logic.chat.StartChatResult
import dev.marzahn.gloriana.logic.chat.SwipeState
import dev.marzahn.gloriana.logic.debug.Faults
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import dev.marzahn.gloriana.logic.storage.IdFactory
import dev.marzahn.gloriana.logic.storage.UpdatedAtClock
import dev.marzahn.gloriana.logic.storage.writeTransaction
import kotlin.uuid.Uuid

private const val FIRST_POSITION = 0L

internal class ChatWrites(
  private val database: GlorianaDatabase,
  private val dao: ChatDao,
  private val ids: IdFactory,
  private val clock: UpdatedAtClock,
  private val faults: Faults,
) {
  suspend fun start(chat: NewChat, opening: suspend (NewChat) -> ChatOpening): StartChatResult =
    database.writeTransaction {
      when (val opened = opening(chat)) {
        is ChatOpening.Refused -> opened.result
        is ChatOpening.Ready -> StartChatResult.Started(insert(chat, opened.title, opened.greeting))
      }
    }

  private suspend fun insert(chat: NewChat, title: String, greeting: String?): Uuid {
    val now = clock.now()
    val chatId = ids.newId()
    dao.insert(ChatEntity(chatId, title, chat.personaId, chat.presetId, chat.connectionId, updatedAt = now))
    dao.insert(ChatCharacterEntity(chatId, chat.characterId, position = 0, updatedAt = now))
    faults.reached(ChatWritePoint.StartChatRowWritten)
    if (greeting != null) {
      val messageId = ids.newId()
      dao.insert(
        MessageEntity(
          id = messageId,
          chatId = chatId,
          position = FIRST_POSITION,
          role = MessageRole.Character,
          speakingCharacterId = chat.characterId,
          hidden = false,
          shownSwipe = 0,
          updatedAt = now,
        ),
      )
      dao.insert(SwipeEntity(ids.newId(), messageId, 0, greeting, reasoning = "", SwipeState.Complete, now))
      faults.reached(ChatWritePoint.StartGreetingWritten)
    }
    return chatId
  }
}
