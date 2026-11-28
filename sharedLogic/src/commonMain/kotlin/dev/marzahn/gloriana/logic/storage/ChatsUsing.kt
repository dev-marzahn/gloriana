package dev.marzahn.gloriana.logic.storage

import kotlin.uuid.Uuid

enum class ChatSetting { Connection, Preset, Persona }

fun interface ChatsUsing {
  suspend fun count(setting: ChatSetting, id: Uuid): Int

  companion object {
    val NoneYet = ChatsUsing { _, _ -> 0 }
  }
}
