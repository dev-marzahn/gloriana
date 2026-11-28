package dev.marzahn.gloriana.logic.chat

import dev.marzahn.gloriana.logic.characters.CharacterWritePoint
import dev.marzahn.gloriana.logic.characters.ChatsOfDeletedCharacter
import dev.marzahn.gloriana.logic.characters.DeleteResult
import dev.marzahn.gloriana.logic.chat.storage.ChatCharactersEntityType
import dev.marzahn.gloriana.logic.chat.storage.ChatsEntityType
import dev.marzahn.gloriana.logic.chat.storage.MessagesEntityType
import dev.marzahn.gloriana.logic.chat.storage.SwipeDraftEntity
import dev.marzahn.gloriana.logic.chat.storage.SwipeDraftsEntityType
import dev.marzahn.gloriana.logic.chat.storage.SwipesEntityType
import dev.marzahn.gloriana.logic.storage.RowKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class DeletedCharacterChatsTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun theDeleteWarningCountsTheChatsWithTheCharacter() = runTest {
    ChatLibrary(directory).use { library ->
      val ada = library.character()
      val newChat = library.newChat(ada)
      repeat(2) { library.started(newChat) }
      library.started(library.newChat(library.character(name = "Bo")))

      assertEquals(2, library.characters.repository.chatsWith(ada.id))
    }
  }

  @Test
  fun deletingACharacterAndItsChatsRemovesEveryRowOfThoseChatsWithTombstones() = runTest {
    ChatLibrary(directory).use { library ->
      val ada = library.character()
      val chat = library.started(library.newChat(ada))
      val other = library.started(library.newChat(library.character(name = "Bo")))
      val greeting = library.messages(chat.id).single().id
      library.database.swipeDraftDao().upsert(SwipeDraftEntity(Uuid.random(), greeting, "", "", updatedAt = 1))

      val deleted = library.characters.repository.delete(ada.id, ChatsOfDeletedCharacter.Delete)

      assertEquals(DeleteResult.Deleted, deleted)
      assertNull(library.repository.chat(chat.id).first())
      assertNull(library.database.swipeDraftDao().message(greeting))
      assertEquals(other, library.stored(other.id))
      val chatTypes =
        listOf(SwipeDraftsEntityType, SwipesEntityType, MessagesEntityType, ChatCharactersEntityType, ChatsEntityType)
          .map { it.name }
      assertEquals(
        chatTypes.toSet(),
        library.database.tombstoneDao().all().map { it.entityType }.filter { it in chatTypes }.toSet(),
      )
    }
  }

  @Test
  fun deletingACharacterAndKeepingItsChatsTakesItOutOfThemWithTombstones() = runTest {
    ChatLibrary(directory).use { library ->
      val ada = library.character()
      val chat = library.started(library.newChat(ada))
      val greeting = library.messages(chat.id).single()
      val updatedBefore = library.chatUpdatedAt(chat.id)

      val deleted = library.characters.repository.delete(ada.id, ChatsOfDeletedCharacter.Keep)

      assertEquals(DeleteResult.Deleted, deleted)
      assertTrue(library.chatUpdatedAt(chat.id) > updatedBefore)
      assertEquals(chat.copy(characterIds = emptyList()), library.stored(chat.id))
      assertEquals(listOf(greeting.copy(speakingCharacterId = null)), library.messages(chat.id))
      assertEquals(
        listOf(RowKey.JoinRow(chat.id, ada.id)),
        library.database.tombstoneDao().all().filter { it.entityType == ChatCharactersEntityType.name }
          .map { it.rowKey },
      )
    }
  }

  @Test
  fun aCharacterDeleteThatFailsPartWayKeepsItsChats() = runTest {
    ChatLibrary(directory).use { library ->
      val ada = library.character()
      val chat = library.started(library.newChat(ada))
      library.faults.failing = CharacterWritePoint.DeleteRowRemoved

      val deleted = library.characters.repository.delete(ada.id, ChatsOfDeletedCharacter.Delete)

      assertEquals(DeleteResult.Failed, deleted)
      assertEquals(chat, library.stored(chat.id))
      assertEquals(1, library.messages(chat.id).size)
    }
  }

  private suspend fun ChatLibrary.chatUpdatedAt(id: Uuid): Long =
    checkNotNull(database.chatDao().observeRows(id).first()).chat.updatedAt
}
