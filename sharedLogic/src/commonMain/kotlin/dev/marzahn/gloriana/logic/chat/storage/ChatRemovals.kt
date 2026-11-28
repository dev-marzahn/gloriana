package dev.marzahn.gloriana.logic.chat.storage

import dev.marzahn.gloriana.logic.chat.ChatWritePoint
import dev.marzahn.gloriana.logic.debug.Faults
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import dev.marzahn.gloriana.logic.storage.RowKey
import dev.marzahn.gloriana.logic.storage.Tombstones
import dev.marzahn.gloriana.logic.storage.UpdatedAtClock
import dev.marzahn.gloriana.logic.storage.writeTransaction
import kotlin.uuid.Uuid

internal class ChatRemovals(
  private val database: GlorianaDatabase,
  private val dao: ChatRemovalDao,
  private val clock: UpdatedAtClock,
  private val tombstones: Tombstones,
  private val faults: Faults,
) {
  suspend fun delete(chatId: Uuid): Boolean = database.writeTransaction {
    val found = dao.exists(chatId)
    if (found) remove(chatId)
    found
  }

  private suspend fun remove(chatId: Uuid) {
    val now = clock.now()
    dao.tombstoneDraftsOf(chatId, now)
    dao.tombstoneSwipesOf(chatId, now)
    dao.tombstoneMessagesOf(chatId, now)
    dao.tombstoneCharactersOf(chatId, now)
    dao.deleteDraftsOf(chatId)
    dao.deleteSwipesOf(chatId)
    dao.deleteMessagesOf(chatId)
    faults.reached(ChatWritePoint.DeleteMessagesRemoved)
    dao.deleteCharactersOf(chatId)
    dao.delete(chatId)
    tombstones.record(ChatsEntityType, RowKey.Row(chatId))
  }
}
