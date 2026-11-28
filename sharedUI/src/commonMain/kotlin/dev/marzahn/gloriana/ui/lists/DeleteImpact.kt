package dev.marzahn.gloriana.ui.lists

import dev.marzahn.gloriana.logic.app.failingAs

sealed interface DeleteImpact {
  data object NoChats : DeleteImpact

  data class MovesChats(val chats: Int, val defaultName: String) : DeleteImpact

  data object Unknown : DeleteImpact
}

suspend fun readDeleteImpact(chatsUsing: suspend () -> Int, defaultName: suspend () -> String?): DeleteImpact =
  failingAs(DeleteImpact.Unknown) {
    when (val chats = chatsUsing()) {
      0 -> DeleteImpact.NoChats
      else -> defaultName()?.let { DeleteImpact.MovesChats(chats, it) } ?: DeleteImpact.Unknown
    }
  }
