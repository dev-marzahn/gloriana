package dev.marzahn.gloriana.logic.chat.storage

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import dev.marzahn.gloriana.logic.chat.Chat
import dev.marzahn.gloriana.logic.chat.ChatMessage
import dev.marzahn.gloriana.logic.chat.MessageRole
import dev.marzahn.gloriana.logic.chat.Swipe
import dev.marzahn.gloriana.logic.chat.SwipeState
import dev.marzahn.gloriana.logic.storage.EntityType
import kotlin.uuid.Uuid

internal const val CHATS_TABLE = "chats"
internal const val CHAT_CHARACTERS_TABLE = "chat_characters"
internal const val MESSAGES_TABLE = "messages"
internal const val SWIPES_TABLE = "swipes"
internal const val SWIPE_DRAFTS_TABLE = "swipe_drafts"

internal val ChatsEntityType = EntityType(CHATS_TABLE)
internal val ChatCharactersEntityType = EntityType(CHAT_CHARACTERS_TABLE)
internal val MessagesEntityType = EntityType(MESSAGES_TABLE)
internal val SwipesEntityType = EntityType(SWIPES_TABLE)
internal val SwipeDraftsEntityType = EntityType(SWIPE_DRAFTS_TABLE)

@Entity(tableName = CHATS_TABLE, indices = [Index("personaId"), Index("presetId"), Index("connectionId")])
internal data class ChatEntity(
  @PrimaryKey val id: Uuid,
  val title: String,
  val personaId: Uuid,
  val presetId: Uuid,
  val connectionId: Uuid?,
  val updatedAt: Long,
)

@Entity(
  tableName = CHAT_CHARACTERS_TABLE,
  primaryKeys = ["chatId", "characterId"],
  foreignKeys = [ForeignKey(entity = ChatEntity::class, parentColumns = ["id"], childColumns = ["chatId"])],
  indices = [Index("characterId")],
)
internal data class ChatCharacterEntity(
  val chatId: Uuid,
  val characterId: Uuid,
  val position: Int,
  val updatedAt: Long,
)

@Entity(
  tableName = MESSAGES_TABLE,
  foreignKeys = [ForeignKey(entity = ChatEntity::class, parentColumns = ["id"], childColumns = ["chatId"])],
  indices = [Index("chatId", "position", unique = true)],
)
@TypeConverters(ChatConverters::class)
internal data class MessageEntity(
  @PrimaryKey val id: Uuid,
  val chatId: Uuid,
  val position: Long,
  val role: MessageRole,
  val speakingCharacterId: Uuid?,
  val hidden: Boolean,
  val shownSwipe: Int,
  val updatedAt: Long,
)

@Entity(
  tableName = SWIPES_TABLE,
  foreignKeys = [ForeignKey(entity = MessageEntity::class, parentColumns = ["id"], childColumns = ["messageId"])],
  indices = [Index("messageId", "swipeIndex", unique = true)],
)
@TypeConverters(ChatConverters::class)
internal data class SwipeEntity(
  @PrimaryKey val id: Uuid,
  val messageId: Uuid,
  val swipeIndex: Int,
  val text: String,
  val reasoning: String,
  val state: SwipeState,
  val updatedAt: Long,
)

@Entity(
  tableName = SWIPE_DRAFTS_TABLE,
  foreignKeys = [ForeignKey(entity = MessageEntity::class, parentColumns = ["id"], childColumns = ["messageId"])],
  indices = [Index("messageId", unique = true)],
)
internal data class SwipeDraftEntity(
  @PrimaryKey val id: Uuid,
  val messageId: Uuid,
  val text: String,
  val reasoning: String,
  val updatedAt: Long,
)

internal data class ChatRows(
  @Embedded val chat: ChatEntity,
  @Relation(parentColumn = "id", entityColumn = "chatId")
  val characters: List<ChatCharacterEntity>,
) {
  fun toChat() = Chat(
    id = chat.id,
    title = chat.title,
    characterIds = characters.sortedBy { it.position }.map { it.characterId },
    personaId = chat.personaId,
    presetId = chat.presetId,
    connectionId = chat.connectionId,
  )
}

internal data class ShownMessageRow(
  @Embedded val message: MessageEntity,
  @Embedded(prefix = "shown_") val shown: ShownSwipeColumns,
) {
  fun toMessage() = ChatMessage(
    id = message.id,
    position = message.position,
    role = message.role,
    speakingCharacterId = message.speakingCharacterId,
    hidden = message.hidden,
    shown = Swipe(message.shownSwipe, shown.text, shown.reasoning, shown.state),
  )
}

internal data class MessageWithSwipes(
  @Embedded val message: MessageEntity,
  @Relation(parentColumn = "id", entityColumn = "messageId")
  val swipes: List<SwipeEntity>,
) {
  val orderedSwipes: List<Swipe>
    get() = swipes.sortedBy { it.swipeIndex }.map { Swipe(it.swipeIndex, it.text, it.reasoning, it.state) }
}

@TypeConverters(ChatConverters::class)
internal data class ShownSwipeColumns(val text: String, val reasoning: String, val state: SwipeState)

internal object ChatConverters {
  @TypeConverter
  fun roleToText(role: MessageRole): String = when (role) {
    MessageRole.User -> "user"
    MessageRole.Character -> "character"
  }

  @TypeConverter
  fun textToRole(text: String): MessageRole = when (text) {
    "user" -> MessageRole.User
    "character" -> MessageRole.Character
    else -> error("Unknown message role $text")
  }

  @TypeConverter
  fun stateToText(state: SwipeState): String = when (state) {
    SwipeState.Complete -> "complete"
    SwipeState.Unfinished -> "unfinished"
    SwipeState.Failed -> "failed"
  }

  @TypeConverter
  fun textToState(text: String): SwipeState = when (text) {
    "complete" -> SwipeState.Complete
    "unfinished" -> SwipeState.Unfinished
    "failed" -> SwipeState.Failed
    else -> error("Unknown Swipe state $text")
  }
}
