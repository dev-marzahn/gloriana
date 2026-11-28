package dev.marzahn.gloriana.logic.chat.storage

import dev.marzahn.gloriana.logic.storage.ChatSetting
import dev.marzahn.gloriana.logic.storage.ChatsUsing
import dev.marzahn.gloriana.logic.storage.UpdatedAtClock
import kotlin.uuid.Uuid

internal class ChatReferences(private val dao: ChatReferenceDao, private val clock: UpdatedAtClock) : ChatsUsing {
  override suspend fun count(setting: ChatSetting, id: Uuid): Int = when (setting) {
    ChatSetting.Connection -> dao.countWithConnection(id)
    ChatSetting.Preset -> dao.countWithPreset(id)
    ChatSetting.Persona -> dao.countWithPersona(id)
  }

  override suspend fun move(setting: ChatSetting.EveryChatHasOne, from: Uuid, to: Uuid) = when (setting) {
    ChatSetting.Preset -> dao.movePreset(from, to, clock.now())
    ChatSetting.Persona -> dao.movePersona(from, to, clock.now())
  }

  override suspend fun move(setting: ChatSetting.Connection, from: Uuid, to: Uuid?) =
    dao.moveConnection(from, to, clock.now())
}
