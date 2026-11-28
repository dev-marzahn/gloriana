package dev.marzahn.gloriana.logic.chat.file

import dev.marzahn.gloriana.logic.chat.MessageRole
import dev.marzahn.gloriana.logic.chat.Swipe

internal data class ChatFile(val userName: String, val characterName: String, val messages: List<ChatFileMessage>)

internal data class ChatFileMessage(
  val speaker: String,
  val role: MessageRole,
  val hidden: Boolean,
  val swipes: List<Swipe>,
  val shownAt: Int,
) {
  init {
    require(shownAt in swipes.indices) { "The shown Swipe must be one of the message's Swipes" }
  }

  val shown: Swipe get() = swipes[shownAt]
}
