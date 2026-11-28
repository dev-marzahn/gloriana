package dev.marzahn.gloriana.logic.chat.storage

import dev.marzahn.gloriana.logic.chat.MessageRole
import dev.marzahn.gloriana.logic.chat.ReplyDraft
import dev.marzahn.gloriana.logic.chat.ReplyText
import dev.marzahn.gloriana.logic.chat.SwipeState
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import dev.marzahn.gloriana.logic.storage.IdFactory
import dev.marzahn.gloriana.logic.storage.UpdatedAtClock
import dev.marzahn.gloriana.logic.storage.writeTransaction
import kotlin.uuid.Uuid

internal class ReplyRows(
  private val database: GlorianaDatabase,
  private val chats: ChatDao,
  private val drafts: SwipeDraftDao,
  private val settling: DraftSettling,
  private val ids: IdFactory,
  private val clock: UpdatedAtClock,
) {
  suspend fun startReply(chatId: Uuid, userText: String, speakingCharacterId: Uuid): ReplyDraft? =
    database.writeTransaction {
      if (!chats.exists(chatId)) return@writeTransaction null
      val next = (chats.lastMessage(chatId)?.position ?: -1) + 1
      val now = clock.now()
      val userMessage = message(chatId, next, MessageRole.User, speakingCharacterId = null, now)
      chats.insert(userMessage)
      chats.insert(SwipeEntity(ids.newId(), userMessage.id, 0, userText, reasoning = "", SwipeState.Complete, now))
      val reply = message(chatId, next + 1, MessageRole.Character, speakingCharacterId, now)
      chats.insert(reply)
      emptyDraft(reply.id, now)
    }

  suspend fun startRetry(chatId: Uuid): ReplyDraft? = database.writeTransaction {
    lastReply(chatId)
      ?.takeIf { reply -> keptLeftoverDraft(reply) != DraftSettled.MessageRemoved }
      ?.let { reply -> emptyDraft(reply.id, clock.now()) }
  }

  private suspend fun keptLeftoverDraft(reply: MessageEntity): DraftSettled? =
    drafts.forMessage(reply.id)?.let { leftover -> settling.settle(leftover, SwipeState.Unfinished) }

  suspend fun save(draft: ReplyDraft, text: ReplyText) = database.writeTransaction {
    if (drafts.message(draft.messageId) != null) drafts.upsert(draft.entity(text, clock.now()))
  }

  suspend fun settle(draft: ReplyDraft, text: ReplyText, state: SwipeState): DraftSettled? =
    database.writeTransaction {
      drafts.message(draft.messageId)?.let { settling.settle(draft.entity(text, clock.now()), state) }
    }

  private suspend fun lastReply(chatId: Uuid): MessageEntity? = chats.lastMessage(chatId)
    ?.takeIf { it.role == MessageRole.Character && chats.hasEarlier(chatId, MessageRole.User, it.position) }

  private suspend fun emptyDraft(messageId: Uuid, now: Long): ReplyDraft {
    val draft = ReplyDraft(ids.newId(), messageId)
    drafts.upsert(draft.entity(ReplyText.Empty, now))
    return draft
  }

  private fun message(chatId: Uuid, position: Long, role: MessageRole, speakingCharacterId: Uuid?, now: Long) =
    MessageEntity(
      id = ids.newId(),
      chatId = chatId,
      position = position,
      role = role,
      speakingCharacterId = speakingCharacterId,
      hidden = false,
      shownSwipe = 0,
      updatedAt = now,
    )
}

private fun ReplyDraft.entity(text: ReplyText, now: Long) =
  SwipeDraftEntity(id, messageId, text.text, text.reasoning, updatedAt = now)
