package dev.marzahn.gloriana.logic.chat

import dev.marzahn.gloriana.logic.connection.ConnectionDeleteResult
import dev.marzahn.gloriana.logic.connection.ConnectionWritePoint
import dev.marzahn.gloriana.logic.connection.draft
import dev.marzahn.gloriana.logic.personas.PersonaDeleteResult
import dev.marzahn.gloriana.logic.personas.PersonaWritePoint
import dev.marzahn.gloriana.logic.personas.wren
import dev.marzahn.gloriana.logic.presets.PresetDeleteResult
import dev.marzahn.gloriana.logic.presets.PresetWritePoint
import dev.marzahn.gloriana.logic.settings.MakeDefaultResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class DeletedChatSettingsTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun theDeleteWarningsCountTheChatsUsingEachSetting() = runTest {
    ChatLibrary(directory).use { library ->
      val connection = library.connections.created(draft())
      library.connections.repository.makeDefault(connection.id)
      val newChat = library.newChat(library.character())
      repeat(2) { library.started(newChat) }

      assertEquals(2, library.personas.repository.chatsUsing(newChat.personaId))
      assertEquals(2, library.presets.repository.chatsUsing(newChat.presetId))
      assertEquals(2, library.connections.repository.chatsUsing(connection.id))
      assertEquals(0, library.personas.repository.chatsUsing(Uuid.random()))
    }
  }

  @Test
  fun deletingAPersonaMovesItsChatsToTheDefaultPersona() = runTest {
    ChatLibrary(directory).use { library ->
      val newChat = library.newChat(library.character())
      val wren = library.personas.created(wren())
      val chat = library.started(newChat.copy(personaId = wren.id))

      assertEquals(PersonaDeleteResult.Deleted, library.personas.repository.delete(wren.id))

      assertEquals(newChat.personaId, library.stored(chat.id).personaId)
    }
  }

  @Test
  fun aPersonaDeleteThatFailsPartWayLeavesItsChatsOnIt() = runTest {
    ChatLibrary(directory).use { library ->
      val newChat = library.newChat(library.character())
      val wren = library.personas.created(wren())
      val chat = library.started(newChat.copy(personaId = wren.id))
      library.faults.failing = PersonaWritePoint.DeleteRowRemoved

      assertEquals(PersonaDeleteResult.Failed, library.personas.repository.delete(wren.id))

      assertEquals(wren.id, library.stored(chat.id).personaId)
    }
  }

  @Test
  fun aPersonaWithChatsAndNoDefaultToMoveThemToIsKept() = runTest {
    ChatLibrary(directory).use { library ->
      val newChat = library.newChat(library.character())
      val wren = library.personas.created(wren())
      val chat = library.started(newChat.copy(personaId = wren.id))

      val deleted = library.personas.writes.delete(wren.id, chatsMoveTo = null)

      assertEquals(PersonaDeleteResult.ChatsHaveNoDefault, deleted)
      assertEquals(wren, library.personas.stored(wren.id))
      assertEquals(wren.id, library.stored(chat.id).personaId)
    }
  }

  @Test
  fun aPresetWithChatsAndNoDefaultToMoveThemToIsKept() = runTest {
    ChatLibrary(directory).use { library ->
      val newChat = library.newChat(library.character())
      val minimal = library.presets.imported("minimal.json").id
      val chat = library.started(newChat.copy(presetId = minimal))

      val deleted = library.presets.writes.delete(minimal, chatsMoveTo = null)

      assertEquals(PresetDeleteResult.ChatsHaveNoDefault, deleted)
      assertTrue(library.presets.repository.exists(minimal))
      assertEquals(minimal, library.stored(chat.id).presetId)
    }
  }

  @Test
  fun deletingAPresetMovesItsChatsToTheDefaultPreset() = runTest {
    ChatLibrary(directory).use { library ->
      val newChat = library.newChat(library.character())
      val minimal = library.presets.imported("minimal.json").id
      val chat = library.started(newChat.copy(presetId = minimal))

      assertEquals(PresetDeleteResult.Deleted, library.presets.repository.delete(minimal))

      assertEquals(newChat.presetId, library.stored(chat.id).presetId)
    }
  }

  @Test
  fun aPresetDeleteThatFailsPartWayLeavesItsChatsOnIt() = runTest {
    ChatLibrary(directory).use { library ->
      val newChat = library.newChat(library.character())
      val minimal = library.presets.imported("minimal.json").id
      val chat = library.started(newChat.copy(presetId = minimal))
      library.faults.failing = PresetWritePoint.DeleteRowRemoved

      assertEquals(PresetDeleteResult.Failed, library.presets.repository.delete(minimal))

      assertEquals(minimal, library.stored(chat.id).presetId)
    }
  }

  @Test
  fun deletingAConnectionMovesItsChatsToTheDefaultConnection() = runTest {
    ChatLibrary(directory).use { library ->
      val default = library.connections.created(draft(name = "Default host"))
      val other = library.connections.created(draft(name = "Other host"))
      assertEquals(MakeDefaultResult.Made, library.connections.repository.makeDefault(default.id))
      val chat = library.started(library.newChat(library.character()).copy(connectionId = other.id))

      assertEquals(ConnectionDeleteResult.Deleted, library.connections.repository.delete(other.id))

      assertEquals(default.id, library.stored(chat.id).connectionId)
    }
  }

  @Test
  fun deletingTheDefaultConnectionLeavesItsChatsWithNone() = runTest {
    ChatLibrary(directory).use { library ->
      val default = library.connections.created(draft())
      assertEquals(MakeDefaultResult.Made, library.connections.repository.makeDefault(default.id))
      val chat = library.started(library.newChat(library.character()))

      assertEquals(ConnectionDeleteResult.Deleted, library.connections.repository.delete(default.id))

      assertEquals(null, library.stored(chat.id).connectionId)
      assertEquals(null, library.connections.default())
    }
  }

  @Test
  fun deletingAConnectionWhenNoneIsTheDefaultLeavesItsChatsWithNone() = runTest {
    ChatLibrary(directory).use { library ->
      val connection = library.connections.created(draft())
      val chat = library.started(library.newChat(library.character()).copy(connectionId = connection.id))

      assertEquals(ConnectionDeleteResult.Deleted, library.connections.repository.delete(connection.id))

      assertEquals(null, library.stored(chat.id).connectionId)
    }
  }

  @Test
  fun aConnectionDeleteThatFailsPartWayLeavesItsChatsOnIt() = runTest {
    ChatLibrary(directory).use { library ->
      val connection = library.connections.created(draft())
      val chat = library.started(library.newChat(library.character()).copy(connectionId = connection.id))
      library.faults.failing = ConnectionWritePoint.DeleteRowRemoved

      assertEquals(ConnectionDeleteResult.Failed, library.connections.repository.delete(connection.id))

      assertEquals(connection.id, library.stored(chat.id).connectionId)
    }
  }
}
