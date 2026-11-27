package dev.marzahn.gloriana.logic.connection

import kotlin.uuid.Uuid

fun interface ConnectionChats {
  suspend fun count(connectionId: Uuid): Int

  companion object {
    val NoneYet = ConnectionChats { 0 }
  }
}
