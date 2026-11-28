package dev.marzahn.gloriana.logic.chat.file

import dev.marzahn.gloriana.logic.chat.Chat
import dev.marzahn.gloriana.logic.chat.ChatLibrary
import dev.marzahn.gloriana.logic.chat.MessageRole
import dev.marzahn.gloriana.logic.chat.SwipeState
import dev.marzahn.gloriana.logic.chat.storage.MessageEntity
import dev.marzahn.gloriana.logic.chat.storage.SwipeDraftEntity
import dev.marzahn.gloriana.logic.chat.storage.SwipeEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.uuid.Uuid

internal fun jsonLines(file: ByteArray): List<JsonObject> =
  file.decodeToString().split("\n").filter { it.isNotEmpty() }.map { Json.parseToJsonElement(it).jsonObject }

data class TestSwipe(val text: String, val reasoning: String = "", val state: SwipeState = SwipeState.Complete)

class ChatRowsForTests(private val library: ChatLibrary, private val chat: Chat) {
  private var nextPosition = 1L

  suspend fun user(text: String, hidden: Boolean = false) =
    message(MessageRole.User, speaker = null, hidden, shownSwipe = 0, listOf(TestSwipe(text)))

  suspend fun character(vararg swipes: TestSwipe, shownSwipe: Int = 0, hidden: Boolean = false) =
    message(MessageRole.Character, chat.characterIds.firstOrNull(), hidden, shownSwipe, swipes.toList())

  suspend fun replyBeingWritten(draft: String) {
    val id = message(MessageRole.Character, chat.characterIds.firstOrNull(), hidden = false, shownSwipe = 0, listOf())
    library.database.swipeDraftDao().upsert(SwipeDraftEntity(Uuid.random(), id, draft, reasoning = "", updatedAt = 1))
  }

  suspend fun swipeOn(messageId: Uuid, index: Int, swipe: TestSwipe) {
    library.database.chatDao().insert(
      SwipeEntity(Uuid.random(), messageId, index, swipe.text, swipe.reasoning, swipe.state, updatedAt = 1),
    )
  }

  private suspend fun message(
    role: MessageRole,
    speaker: Uuid?,
    hidden: Boolean,
    shownSwipe: Int,
    swipes: List<TestSwipe>,
  ): Uuid {
    val id = Uuid.random()
    library.database.chatDao().insert(
      MessageEntity(id, chat.id, nextPosition++, role, speaker, hidden, shownSwipe, updatedAt = 1),
    )
    swipes.forEachIndexed { index, swipe -> swipeOn(id, index, swipe) }
    return id
  }
}
