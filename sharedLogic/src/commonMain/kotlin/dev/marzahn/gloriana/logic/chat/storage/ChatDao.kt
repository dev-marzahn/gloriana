package dev.marzahn.gloriana.logic.chat.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.marzahn.gloriana.logic.storage.INSERT_TOMBSTONES
import dev.marzahn.gloriana.logic.storage.OR_RENEW_TOMBSTONES
import dev.marzahn.gloriana.logic.storage.UPDATED_AT_BUMPED_TO_NOW
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

private const val MESSAGES_OF_CHAT = "SELECT id FROM $MESSAGES_TABLE WHERE chatId = :chatId"

private const val JOIN_ROW_KEY = "CAST(chatId || characterId AS BLOB)"

@Dao
internal interface ChatDao {
  @Transaction
  @Query("SELECT * FROM $CHATS_TABLE WHERE id = :id")
  fun observeRows(id: Uuid): Flow<ChatRows?>

  @Query(
    "SELECT m.*, s.text AS shown_text, s.reasoning AS shown_reasoning, s.state AS shown_state " +
      "FROM $MESSAGES_TABLE m JOIN $SWIPES_TABLE s ON s.messageId = m.id AND s.swipeIndex = m.shownSwipe " +
      "WHERE m.chatId = :chatId ORDER BY m.position",
  )
  fun observeShownMessages(chatId: Uuid): Flow<List<ShownMessageRow>>

  @Transaction
  @Query("SELECT * FROM $CHATS_TABLE WHERE id = :id")
  suspend fun rows(id: Uuid): ChatRows?

  @Transaction
  @Query("SELECT * FROM $MESSAGES_TABLE WHERE chatId = :chatId ORDER BY position")
  suspend fun messagesWithSwipes(chatId: Uuid): List<MessageWithSwipes>

  @Insert
  suspend fun insert(chat: ChatEntity)

  @Insert
  suspend fun insert(character: ChatCharacterEntity)

  @Insert
  suspend fun insert(message: MessageEntity)

  @Insert
  suspend fun insert(swipe: SwipeEntity)
}

@Dao
internal interface ChatReferenceDao {
  @Query("SELECT COUNT(*) FROM $CHATS_TABLE WHERE personaId = :id")
  suspend fun countWithPersona(id: Uuid): Int

  @Query("SELECT COUNT(*) FROM $CHATS_TABLE WHERE presetId = :id")
  suspend fun countWithPreset(id: Uuid): Int

  @Query("SELECT COUNT(*) FROM $CHATS_TABLE WHERE connectionId = :id")
  suspend fun countWithConnection(id: Uuid): Int

  @Query("UPDATE $CHATS_TABLE SET personaId = :to, $UPDATED_AT_BUMPED_TO_NOW WHERE personaId = :from")
  suspend fun movePersona(from: Uuid, to: Uuid, now: Long)

  @Query("UPDATE $CHATS_TABLE SET presetId = :to, $UPDATED_AT_BUMPED_TO_NOW WHERE presetId = :from")
  suspend fun movePreset(from: Uuid, to: Uuid, now: Long)

  @Query("UPDATE $CHATS_TABLE SET connectionId = :to, $UPDATED_AT_BUMPED_TO_NOW WHERE connectionId = :from")
  suspend fun moveConnection(from: Uuid, to: Uuid?, now: Long)

  @Query("SELECT COUNT(DISTINCT chatId) FROM $CHAT_CHARACTERS_TABLE WHERE characterId = :characterId")
  suspend fun countWithCharacter(characterId: Uuid): Int

  @Query("SELECT DISTINCT chatId FROM $CHAT_CHARACTERS_TABLE WHERE characterId = :characterId")
  suspend fun chatsWithCharacter(characterId: Uuid): List<Uuid>

  @Query(
    "$INSERT_TOMBSTONES SELECT '$CHAT_CHARACTERS_TABLE', $JOIN_ROW_KEY, :now, :now FROM $CHAT_CHARACTERS_TABLE " +
      "WHERE characterId = :characterId $OR_RENEW_TOMBSTONES",
  )
  suspend fun tombstonePlacesOf(characterId: Uuid, now: Long)

  @Query(
    "UPDATE $CHATS_TABLE SET $UPDATED_AT_BUMPED_TO_NOW WHERE id IN " +
      "(SELECT chatId FROM $CHAT_CHARACTERS_TABLE WHERE characterId = :characterId)",
  )
  suspend fun touchChatsWith(characterId: Uuid, now: Long)

  @Query("DELETE FROM $CHAT_CHARACTERS_TABLE WHERE characterId = :characterId")
  suspend fun removeFromChats(characterId: Uuid)

  @Query(
    "UPDATE $MESSAGES_TABLE SET speakingCharacterId = NULL, $UPDATED_AT_BUMPED_TO_NOW " +
      "WHERE speakingCharacterId = :characterId",
  )
  suspend fun clearSpeaker(characterId: Uuid, now: Long)
}

@Dao
internal interface ChatRemovalDao {
  @Query("SELECT EXISTS(SELECT 1 FROM $CHATS_TABLE WHERE id = :chatId)")
  suspend fun exists(chatId: Uuid): Boolean

  @Query(
    "$INSERT_TOMBSTONES SELECT '$SWIPE_DRAFTS_TABLE', id, :now, :now FROM $SWIPE_DRAFTS_TABLE " +
      "WHERE messageId IN ($MESSAGES_OF_CHAT) $OR_RENEW_TOMBSTONES",
  )
  suspend fun tombstoneDraftsOf(chatId: Uuid, now: Long)

  @Query(
    "$INSERT_TOMBSTONES SELECT '$SWIPES_TABLE', id, :now, :now FROM $SWIPES_TABLE " +
      "WHERE messageId IN ($MESSAGES_OF_CHAT) $OR_RENEW_TOMBSTONES",
  )
  suspend fun tombstoneSwipesOf(chatId: Uuid, now: Long)

  @Query(
    "$INSERT_TOMBSTONES SELECT '$MESSAGES_TABLE', id, :now, :now FROM $MESSAGES_TABLE " +
      "WHERE chatId = :chatId $OR_RENEW_TOMBSTONES",
  )
  suspend fun tombstoneMessagesOf(chatId: Uuid, now: Long)

  @Query(
    "$INSERT_TOMBSTONES SELECT '$CHAT_CHARACTERS_TABLE', $JOIN_ROW_KEY, :now, :now FROM $CHAT_CHARACTERS_TABLE " +
      "WHERE chatId = :chatId $OR_RENEW_TOMBSTONES",
  )
  suspend fun tombstoneCharactersOf(chatId: Uuid, now: Long)

  @Query("DELETE FROM $SWIPE_DRAFTS_TABLE WHERE messageId IN ($MESSAGES_OF_CHAT)")
  suspend fun deleteDraftsOf(chatId: Uuid)

  @Query("DELETE FROM $SWIPES_TABLE WHERE messageId IN ($MESSAGES_OF_CHAT)")
  suspend fun deleteSwipesOf(chatId: Uuid)

  @Query("DELETE FROM $MESSAGES_TABLE WHERE chatId = :chatId")
  suspend fun deleteMessagesOf(chatId: Uuid)

  @Query("DELETE FROM $CHAT_CHARACTERS_TABLE WHERE chatId = :chatId")
  suspend fun deleteCharactersOf(chatId: Uuid)

  @Query("DELETE FROM $CHATS_TABLE WHERE id = :chatId")
  suspend fun delete(chatId: Uuid)
}

@Dao
internal interface SwipeDraftDao {
  @Query("SELECT * FROM $SWIPE_DRAFTS_TABLE")
  suspend fun all(): List<SwipeDraftEntity>

  @Query("SELECT MAX(swipeIndex) FROM $SWIPES_TABLE WHERE messageId = :messageId")
  suspend fun lastSwipeIndex(messageId: Uuid): Int?

  @Query("SELECT EXISTS(SELECT 1 FROM $SWIPES_TABLE WHERE messageId = :messageId AND swipeIndex = :swipeIndex)")
  suspend fun swipeExists(messageId: Uuid, swipeIndex: Int): Boolean

  @Query("SELECT * FROM $MESSAGES_TABLE WHERE id = :id")
  suspend fun message(id: Uuid): MessageEntity?

  @Insert
  suspend fun insert(swipe: SwipeEntity)

  @Upsert
  suspend fun upsert(draft: SwipeDraftEntity)

  @Query("UPDATE $MESSAGES_TABLE SET shownSwipe = :shownSwipe, updatedAt = :updatedAt WHERE id = :id")
  suspend fun showSwipe(id: Uuid, shownSwipe: Int, updatedAt: Long)

  @Query("DELETE FROM $SWIPE_DRAFTS_TABLE WHERE id = :id")
  suspend fun delete(id: Uuid)

  @Query("DELETE FROM $MESSAGES_TABLE WHERE id = :id")
  suspend fun deleteMessage(id: Uuid)
}
