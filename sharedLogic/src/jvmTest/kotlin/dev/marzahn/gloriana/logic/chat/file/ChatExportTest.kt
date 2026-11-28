package dev.marzahn.gloriana.logic.chat.file

import androidx.room.useWriterConnection
import dev.marzahn.gloriana.logic.characters.ChatsOfDeletedCharacter
import dev.marzahn.gloriana.logic.characters.DeleteResult
import dev.marzahn.gloriana.logic.chat.ChatLibrary
import dev.marzahn.gloriana.logic.chat.ChatReadPoint
import dev.marzahn.gloriana.logic.chat.SwipeState
import dev.marzahn.gloriana.logic.chat.storage.CHATS_TABLE
import dev.marzahn.gloriana.logic.chat.storage.SWIPES_TABLE
import dev.marzahn.gloriana.logic.files.ByteSink
import dev.marzahn.gloriana.logic.files.ExportDestination
import dev.marzahn.gloriana.logic.files.ExportFailure
import dev.marzahn.gloriana.logic.files.ExportProblem
import dev.marzahn.gloriana.logic.files.ExportWrite
import dev.marzahn.gloriana.logic.files.MemoryDestination
import dev.marzahn.gloriana.logic.personas.PersonaFields
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class ChatExportTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun aKeptChatWhoseCharacterWasDeletedNamesTheChatTitleWhereverTheFileNamesTheCharacter() = runTest {
    ChatLibrary(directory).use { library ->
      val ada = library.character(name = "Ada")
      val chat = library.started(library.newChat(ada).copy(personaId = robin(library)))
      ChatRowsForTests(library, chat).apply {
        user("Hello.")
        character(TestSwipe("Hi."))
      }
      library.database.runSql("UPDATE $CHATS_TABLE SET title = 'A walk with Ada'")
      assertEquals(DeleteResult.Deleted, library.characters.repository.delete(ada.id, ChatsOfDeletedCharacter.Keep))

      val lines = jsonLines(exported(library, chat.id))

      assertEquals("A walk with Ada", lines.first().string("character_name"))
      assertEquals("Robin", lines.first().string("user_name"))
      assertEquals(listOf("A walk with Ada", "Robin", "A walk with Ada"), lines.drop(1).map { it.string("name") })
    }
  }

  @Test
  fun aChatWithItsCharacterNamesTheCharacterAndThePersona() = runTest {
    ChatLibrary(directory).use { library ->
      val chat = library.started(library.newChat(library.character(name = "Ada")).copy(personaId = robin(library)))
      ChatRowsForTests(library, chat).user("Hello.")

      val lines = jsonLines(exported(library, chat.id))

      assertEquals("Ada", lines.first().string("character_name"))
      assertEquals("Robin", lines.first().string("user_name"))
      assertEquals(listOf("Ada", "Robin"), lines.drop(1).map { it.string("name") })
    }
  }

  @Test
  fun eachMessageIsOneLineWhateverItsTextHolds() = runTest {
    ChatLibrary(directory).use { library ->
      val chat = library.started(library.newChat(library.character()))
      val text = "First line\nsecond line\r\n and \"quoted\" ünïcödé"
      ChatRowsForTests(library, chat).user(text)

      val file = exported(library, chat.id)

      assertEquals(3, file.decodeToString().lines().size)
      assertEquals(text, jsonLines(file).last().string("mes"))
    }
  }

  @Test
  fun everySwipeIsWrittenInOrderWithTheShownOneAsTheMessage() = runTest {
    ChatLibrary(directory).use { library ->
      val chat = library.started(library.newChat(library.character()))
      ChatRowsForTests(library, chat).character(
        TestSwipe("Failed.", state = SwipeState.Failed),
        TestSwipe("Shown.", reasoning = "Because."),
        TestSwipe("Cut off", state = SwipeState.Unfinished),
        shownSwipe = 1,
      )

      val reply = jsonLines(exported(library, chat.id)).last()

      val swipes = reply.getValue("swipes").jsonArray.map { it.jsonPrimitive.content }
      assertEquals(listOf("Failed.", "Shown.", "Cut off"), swipes)
      assertEquals(1, reply.string("swipe_id").toInt())
      assertEquals("Shown.", reply.string("mes"))
      assertEquals("Because.", reply.reasoning())
      val reasonings = reply.getValue("swipe_info").jsonArray.map { it.jsonObject.reasoning() }
      assertEquals(listOf(null, "Because.", null), reasonings)
    }
  }

  @Test
  fun aReplyBeingWrittenIsLeftOutAsTheChatLeavesItOut() = runTest {
    ChatLibrary(directory).use { library ->
      val chat = library.started(library.newChat(library.character()).copy(personaId = robin(library)))
      ChatRowsForTests(library, chat).apply {
        user("Hello.")
        replyBeingWritten("Half a rep")
        character(TestSwipe("An earlier take."), shownSwipe = 1)
      }

      val lines = jsonLines(exported(library, chat.id))

      assertEquals(listOf("Hello, Robin. I am Ada.", "Hello."), lines.drop(1).map { it.string("mes") })
      assertEquals(library.messages(chat.id).size, lines.size - 1)
    }
  }

  @Test
  fun aMessageWrittenWhileTheExportReadsIsNotInTheFile() = runTest {
    ChatLibrary(directory).use { library ->
      val chat = library.started(library.newChat(library.character()).copy(personaId = robin(library)))
      val rows = ChatRowsForTests(library, chat)
      rows.user("Before.")
      library.faults.onReaching[ChatReadPoint.ExportChatRowsRead] = {
        runBlocking(Dispatchers.IO) { rows.user("During.") }
      }

      val lines = jsonLines(exported(library, chat.id))

      assertEquals(listOf("Hello, Robin. I am Ada.", "Before."), lines.drop(1).map { it.string("mes") })
      assertEquals(3, library.messages(chat.id).size)
    }
  }

  @Test
  fun aChatThatIsGoneExportsNothing() = runTest {
    ChatLibrary(directory).use { library ->
      val destination = MemoryDestination()

      assertEquals(ChatExportResult.Gone, library.export.export(Uuid.random(), destination))
      assertEquals(0, destination.bytes.size)
    }
  }

  @Test
  fun aChatWithASwipeANewerVersionWroteExportsNothing() = runTest {
    ChatLibrary(directory).use { library ->
      val chat = library.started(library.newChat(library.character()))
      library.database.runSql("UPDATE $SWIPES_TABLE SET state = 'from a newer version'")
      val destination = MemoryDestination()

      assertEquals(ChatExportResult.Unreadable, library.export.export(chat.id, destination))
      assertEquals(0, destination.bytes.size)
    }
  }

  @Test
  fun aWriteThatFailsSaysWhy() = runTest {
    ChatLibrary(directory).use { library ->
      val chat = library.started(library.newChat(library.character()))
      val problem = ExportProblem(ExportFailure.StorageFull, incompleteFileLeft = true)
      val failing = object : ExportDestination {
        override fun write(content: (ByteSink) -> Unit): ExportWrite = ExportWrite.Failed(problem)
      }

      assertEquals(ChatExportResult.Failed(problem), library.export.export(chat.id, failing))
    }
  }

  private suspend fun robin(library: ChatLibrary): Uuid = library.personas.created(PersonaFields(name = "Robin")).id

  private suspend fun exported(library: ChatLibrary, chatId: Uuid): ByteArray =
    MemoryDestination().also { assertEquals(ChatExportResult.Exported, library.export.export(chatId, it)) }.bytes
}

private suspend fun GlorianaDatabase.runSql(sql: String) =
  useWriterConnection { connection -> connection.usePrepared(sql) { it.step() } }

private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

private fun JsonObject.reasoning(): String? = getValue("extra").jsonObject["reasoning"]?.jsonPrimitive?.content
