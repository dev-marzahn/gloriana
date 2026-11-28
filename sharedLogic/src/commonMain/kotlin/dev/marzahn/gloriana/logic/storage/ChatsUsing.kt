package dev.marzahn.gloriana.logic.storage

import kotlin.uuid.Uuid

sealed interface ChatSetting {
  sealed interface EveryChatHasOne : ChatSetting

  data object Connection : ChatSetting

  data object Preset : EveryChatHasOne

  data object Persona : EveryChatHasOne
}

interface ChatsUsing {
  suspend fun count(setting: ChatSetting, id: Uuid): Int

  suspend fun move(setting: ChatSetting.EveryChatHasOne, from: Uuid, to: Uuid)

  suspend fun move(setting: ChatSetting.Connection, from: Uuid, to: Uuid?)
}

enum class ChatsMoved { All, NoDefaultToMoveTo }

suspend fun ChatsUsing.moveAll(setting: ChatSetting.EveryChatHasOne, from: Uuid, toDefault: Uuid?): ChatsMoved =
  when {
    toDefault != null -> {
      move(setting, from, toDefault)
      ChatsMoved.All
    }
    count(setting, from) == 0 -> ChatsMoved.All
    else -> ChatsMoved.NoDefaultToMoveTo
  }
