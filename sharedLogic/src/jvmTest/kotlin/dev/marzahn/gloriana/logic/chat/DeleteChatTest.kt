package dev.marzahn.gloriana.logic.chat

import dev.marzahn.gloriana.logic.chat.storage.ChatCharactersEntityType
import dev.marzahn.gloriana.logic.chat.storage.ChatsEntityType
import dev.marzahn.gloriana.logic.chat.storage.MessageEntity
import dev.marzahn.gloriana.logic.chat.storage.MessagesEntityType
import dev.marzahn.gloriana.logic.chat.storage.SwipeDraftEntity
import dev.marzahn.gloriana.logic.chat.storage.SwipeDraftsEntityType
import dev.marzahn.gloriana.logic.chat.storage.SwipesEntityType
import dev.marzahn.gloriana.logic.storage.writeTransaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

private const val MORE_THAN_SQLITE_BINDS = 33_000

class DeleteChatTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun deletingAChatRemovesEveryRowOfItWithATombstoneEachAndLeavesOtherChats() = runTest {
    ChatLibrary(directory).use { library ->
      val ada = library.character()
      val chat = library.started(library.newChat(ada))
      val other = library.started(library.newChat(ada))
      val greeting = library.messages(chat.id).single().id
      val draft = Uuid.random()
      library.database.swipeDraftDao().upsert(SwipeDraftEntity(draft, greeting, "", "", updatedAt = 1))

      val deleted = library.repository.delete(chat.id)

      assertEquals(ChatDeleteResult.Deleted, deleted)
      assertNull(library.repository.chat(chat.id).first())
      assertNull(library.database.swipeDraftDao().message(greeting))
      assertEquals(other, library.stored(other.id))
      assertEquals(1, library.messages(other.id).size)
      assertEquals(
        mapOf(
          SwipeDraftsEntityType.name to 1,
          SwipesEntityType.name to 1,
          MessagesEntityType.name to 1,
          ChatCharactersEntityType.name to 1,
          ChatsEntityType.name to 1,
        ),
        library.database.tombstoneDao().all().groupingBy { it.entityType }.eachCount(),
      )
    }
  }

  @Test
  fun deletingAChatThatIsGoneSaysSo() = runTest {
    ChatLibrary(directory).use { library ->
      assertEquals(ChatDeleteResult.Gone, library.repository.delete(Uuid.random()))
    }
  }

  @Test
  fun aChatDeleteThatFailsPartWayKeepsTheWholeChat() = runTest {
    ChatLibrary(directory).use { library ->
      val chat = library.started(library.newChat(library.character()))
      val messages = library.messages(chat.id)
      library.faults.failing = ChatWritePoint.DeleteMessagesRemoved

      val deleted = library.repository.delete(chat.id)

      assertEquals(ChatDeleteResult.Failed, deleted)
      assertEquals(chat, library.stored(chat.id))
      assertEquals(messages, library.messages(chat.id))
      assertEquals(emptyList(), library.database.tombstoneDao().all())
    }
  }

  @Test
  fun aChatWithMoreMessagesThanSqliteBindsInOneQueryIsDeleted() = runTest {
    ChatLibrary(directory).use { library ->
      val chat = library.started(library.newChat(library.character(firstMessage = "")))
      library.database.writeTransaction {
        repeat(MORE_THAN_SQLITE_BINDS) { position ->
          library.database.chatDao().insert(
            MessageEntity(Uuid.random(), chat.id, position.toLong(), MessageRole.User, null, false, 0, updatedAt = 1),
          )
        }
      }

      val deleted = library.repository.delete(chat.id)

      assertEquals(ChatDeleteResult.Deleted, deleted)
      assertEquals(
        MORE_THAN_SQLITE_BINDS,
        library.database.tombstoneDao().all().count { it.entityType == MessagesEntityType.name },
      )
    }
  }
}
