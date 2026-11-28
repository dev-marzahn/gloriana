package dev.marzahn.gloriana.logic.chat.storage

import dev.marzahn.gloriana.logic.storage.ChatsWithCharacter
import dev.marzahn.gloriana.logic.storage.UpdatedAtClock
import kotlin.uuid.Uuid

internal class CharacterChats(
  private val dao: ChatReferenceDao,
  private val removals: ChatRemovals,
  private val clock: UpdatedAtClock,
) : ChatsWithCharacter {
  override suspend fun count(characterId: Uuid): Int = dao.countWithCharacter(characterId)

  override suspend fun delete(characterId: Uuid) {
    dao.chatsWithCharacter(characterId).forEach { removals.delete(it) }
  }

  override suspend fun keepWithout(characterId: Uuid) {
    val now = clock.now()
    dao.touchChatsWith(characterId, now)
    dao.tombstonePlacesOf(characterId, now)
    dao.removeFromChats(characterId)
    dao.clearSpeaker(characterId, now)
  }
}
