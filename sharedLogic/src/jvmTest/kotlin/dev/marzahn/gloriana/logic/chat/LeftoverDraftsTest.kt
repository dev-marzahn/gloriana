package dev.marzahn.gloriana.logic.chat

import dev.marzahn.gloriana.logic.chat.storage.MessageEntity
import dev.marzahn.gloriana.logic.chat.storage.MessagesEntityType
import dev.marzahn.gloriana.logic.chat.storage.SwipeDraftEntity
import dev.marzahn.gloriana.logic.chat.storage.SwipeDraftsEntityType
import dev.marzahn.gloriana.logic.chat.storage.SwipeEntity
import dev.marzahn.gloriana.logic.storage.EntityType
import dev.marzahn.gloriana.logic.storage.RowKey
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class LeftoverDraftsTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun aDraftWithTextIsKeptAsAnUnfinishedSwipeAfterTheLastAndShown() = runTest {
    ChatLibrary(directory).use { library ->
      val chat = library.started(library.newChat(library.character()))
      val greeting = library.messages(chat.id).single()
      val draft = library.draft(greeting.id, text = "Half a reply", reasoning = "thinking")

      assertTrue(library.leftoverDrafts.keepAsUnfinished())

      assertEquals(
        Swipe(1, "Half a reply", "thinking", SwipeState.Unfinished),
        library.messages(chat.id).single().shown,
      )
      assertEquals(listOf(SwipeDraftsEntityType to RowKey.Row(draft)), library.tombstones())
      assertEquals(emptyList(), library.database.swipeDraftDao().all())
    }
  }

  @Test
  fun anEmptyDraftOnARetryKeepsShowingTheSwipeShownBeforeIt() = runTest {
    ChatLibrary(directory).use { library ->
      val chat = library.started(library.newChat(library.character()))
      val greeting = library.messages(chat.id).single()
      library.swipes(greeting.id, 1, 2)
      library.draft(greeting.id)

      assertTrue(library.leftoverDrafts.keepAsUnfinished())

      assertEquals(greeting, library.messages(chat.id).single())
    }
  }

  @Test
  fun aDraftWithTextOnARetryFromAnEarlierSwipeGoesAfterTheLastSwipe() = runTest {
    ChatLibrary(directory).use { library ->
      val chat = library.started(library.newChat(library.character()))
      val greeting = library.messages(chat.id).single()
      library.swipes(greeting.id, 1, 2)
      library.draft(greeting.id, text = "Another take")

      assertTrue(library.leftoverDrafts.keepAsUnfinished())

      assertEquals(Swipe(3, "Another take", "", SwipeState.Unfinished), library.messages(chat.id).single().shown)
    }
  }

  @Test
  fun anEmptyDraftOnAReplyWithNoSwipesLeavesNoReply() = runTest {
    ChatLibrary(directory).use { library ->
      val chat = library.started(library.newChat(library.character(firstMessage = "")))
      val reply = library.reply(chat)
      val draft = library.draft(reply)

      assertTrue(library.leftoverDrafts.keepAsUnfinished())

      assertNull(library.database.swipeDraftDao().message(reply))
      assertEquals(
        setOf(SwipeDraftsEntityType to RowKey.Row(draft), MessagesEntityType to RowKey.Row(reply)),
        library.tombstones().toSet(),
      )
    }
  }

  @Test
  fun aDraftThatCannotBeKeptIsLoggedAndLeftWhileTheOthersAreKept() = runTest {
    ChatLibrary(directory).use { library ->
      val ada = library.character()
      val greeting = library.messages(library.started(library.newChat(ada)).id).single()
      val failing = library.draft(greeting.id, text = "Half a reply")
      val reply = library.reply(library.started(library.newChat(library.character(name = "Bo", firstMessage = ""))))
      library.draft(reply)
      library.faults.failing = ChatWritePoint.DraftSwipeWritten

      assertFalse(library.leftoverDrafts.keepAsUnfinished())

      assertEquals(listOf(failing), library.database.swipeDraftDao().all().map { it.id })
      assertNull(library.database.swipeDraftDao().message(reply))
      assertEquals(1, library.problems.size)
    }
  }

  private suspend fun ChatLibrary.draft(messageId: Uuid, text: String = "", reasoning: String = "") =
    Uuid.random().also { id ->
      database.swipeDraftDao().upsert(SwipeDraftEntity(id, messageId, text, reasoning, updatedAt = 1))
    }

  private suspend fun ChatLibrary.swipes(messageId: Uuid, vararg indexes: Int) = indexes.forEach { index ->
    database.swipeDraftDao().insert(
      SwipeEntity(Uuid.random(), messageId, index, "Take $index", "", SwipeState.Complete, updatedAt = 1),
    )
  }

  private suspend fun ChatLibrary.reply(chat: Chat) = Uuid.random().also { id ->
    database.chatDao().insert(
      MessageEntity(id, chat.id, 0, MessageRole.Character, chat.characterIds.single(), false, 0, updatedAt = 1),
    )
  }

  private suspend fun ChatLibrary.tombstones() =
    database.tombstoneDao().all().map { EntityType(it.entityType) to it.rowKey }
}
