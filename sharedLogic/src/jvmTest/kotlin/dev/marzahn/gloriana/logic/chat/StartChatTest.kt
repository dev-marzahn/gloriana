package dev.marzahn.gloriana.logic.chat

import dev.marzahn.gloriana.logic.connection.draft
import dev.marzahn.gloriana.logic.settings.MakeDefaultResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class StartChatTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun aChatStartsWithTheDefaultConnectionPersonaAndPresetAndTheGreetingAsItsFirstMessage() = runTest {
    ChatLibrary(directory).use { library ->
      val connection = library.connections.created(draft())
      assertEquals(MakeDefaultResult.Made, library.connections.repository.makeDefault(connection.id))
      val ada = library.character()
      val newChat = library.newChat(ada)

      val chat = library.started(newChat)

      assertEquals(
        Chat(chat.id, "Ada", listOf(ada.id), newChat.personaId, newChat.presetId, connection.id),
        chat,
      )
      val greeting = library.messages(chat.id).single()
      assertEquals(
        ChatMessage(
          id = greeting.id,
          position = 0,
          role = MessageRole.Character,
          speakingCharacterId = ada.id,
          hidden = false,
          shown = Swipe(0, "Hello, User. I am Ada.", reasoning = "", SwipeState.Complete),
        ),
        greeting,
      )
    }
  }

  @Test
  fun aChatStartsWithNoConnectionWhenNoneIsTheDefault() = runTest {
    ChatLibrary(directory).use { library ->
      val chat = library.started(library.newChat(library.character()))

      assertNull(chat.connectionId)
    }
  }

  @Test
  fun theNameMacrosAreFilledInAnyCaseWithTheCharactersNameAndThePersonasName() = runTest {
    ChatLibrary(directory).use { library ->
      val ada = library.character(firstMessage = "{{CHAR}} waves at {{User}}; {{char}} smiles.")

      val chat = library.started(library.newChat(ada))

      assertEquals("Ada waves at User; Ada smiles.", library.messages(chat.id).single().shown.text)
    }
  }

  @Test
  fun anEmptyGreetingStartsAChatWithNoMessages() = runTest {
    ChatLibrary(directory).use { library ->
      val chat = library.started(library.newChat(library.character(firstMessage = "")))

      assertEquals(emptyList(), library.messages(chat.id))
    }
  }

  @Test
  fun anAlternateGreetingCanOpenTheChat() = runTest {
    ChatLibrary(directory).use { library ->
      val ada = library.character(alternateGreetings = listOf("First other.", "Second other, {{user}}."))

      val chat = library.started(library.newChat(ada).copy(greeting = Greeting.Alternate(1)))

      assertEquals("Second other, User.", library.messages(chat.id).single().shown.text)
    }
  }

  @Test
  fun eachSettingThatIsGoneRefusesTheStartAndStoresNoChat() = runTest {
    ChatLibrary(directory).use { library ->
      val newChat = library.newChat(library.character())
      val refusals = mapOf(
        newChat.copy(characterId = Uuid.random()) to StartChatResult.CharacterGone,
        newChat.copy(personaId = Uuid.random()) to StartChatResult.PersonaGone,
        newChat.copy(greeting = Greeting.Alternate(0)) to StartChatResult.GreetingGone,
        newChat.copy(presetId = Uuid.random()) to StartChatResult.PresetGone,
        newChat.copy(connectionId = Uuid.random()) to StartChatResult.ConnectionGone,
      )

      refusals.forEach { (chat, refused) -> assertEquals(refused, library.repository.start(chat)) }
      assertEquals(0, library.personas.repository.chatsUsing(newChat.personaId))
    }
  }

  @Test
  fun aStartThatFailsPartWayStoresNeitherTheChatNorItsGreeting() = runTest {
    ChatLibrary(directory).use { library ->
      val newChat = library.newChat(library.character())
      library.faults.failing = ChatWritePoint.StartGreetingWritten

      assertEquals(StartChatResult.Failed, library.repository.start(newChat))

      assertEquals(0, library.presets.repository.chatsUsing(newChat.presetId))
    }
  }
}
