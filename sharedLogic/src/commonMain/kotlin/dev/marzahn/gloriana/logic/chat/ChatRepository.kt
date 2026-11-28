package dev.marzahn.gloriana.logic.chat

import dev.marzahn.gloriana.logic.app.ShippedDefaultsAtStart
import dev.marzahn.gloriana.logic.app.failingAs
import dev.marzahn.gloriana.logic.assets.isStorageFull
import dev.marzahn.gloriana.logic.chat.storage.ChatDao
import dev.marzahn.gloriana.logic.chat.storage.ChatRemovals
import dev.marzahn.gloriana.logic.chat.storage.ChatWrites
import dev.marzahn.gloriana.logic.connection.ConnectionRepository
import dev.marzahn.gloriana.logic.personas.PersonaPrompts
import dev.marzahn.gloriana.logic.presets.PresetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

class ChatRepository internal constructor(
  private val dao: ChatDao,
  private val writes: ChatWrites,
  private val removals: ChatRemovals,
  private val openings: ChatOpenings,
  private val shippedDefaults: ShippedDefaultsAtStart,
  private val personas: PersonaPrompts,
  private val presets: PresetRepository,
  private val connections: ConnectionRepository,
) {
  fun chat(id: Uuid): Flow<Chat?> = dao.observeRows(id).map { it?.toChat() }

  fun messages(chatId: Uuid): Flow<List<ChatMessage>> =
    dao.observeShownMessages(chatId).map { rows -> rows.map { it.toMessage() } }

  suspend fun newChatDefaults(): NewChatDefaults {
    shippedDefaults.missing.await()
    return NewChatDefaults(
      personaId = personas.defaultPersonaId(),
      presetId = presets.defaultPreset().first(),
      connectionId = connections.defaultConnection().first(),
    )
  }

  suspend fun start(chat: NewChat): StartChatResult =
    failingAs({ if (it.isStorageFull()) StartChatResult.StorageFull else StartChatResult.Failed }) {
      writes.start(chat, openings::opening)
    }

  suspend fun delete(chatId: Uuid): ChatDeleteResult = failingAs(ChatDeleteResult.Failed) {
    if (removals.delete(chatId)) ChatDeleteResult.Deleted else ChatDeleteResult.Gone
  }
}
