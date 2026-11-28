package dev.marzahn.gloriana.logic.chat

import kotlin.uuid.Uuid

data class Chat(
  val id: Uuid,
  val title: String,
  val characterIds: List<Uuid>,
  val personaId: Uuid,
  val presetId: Uuid,
  val connectionId: Uuid?,
)

enum class MessageRole { User, Character }

enum class SwipeState { Complete, Unfinished, Failed }

data class Swipe(val index: Int, val text: String, val reasoning: String, val state: SwipeState)

data class ChatMessage(
  val id: Uuid,
  val position: Long,
  val role: MessageRole,
  val speakingCharacterId: Uuid?,
  val hidden: Boolean,
  val shown: Swipe,
)

data class NewChatDefaults(val personaId: Uuid?, val presetId: Uuid?, val connectionId: Uuid?)

sealed interface Greeting {
  data object First : Greeting

  data class Alternate(val index: Int) : Greeting
}

data class NewChat(
  val characterId: Uuid,
  val personaId: Uuid,
  val presetId: Uuid,
  val connectionId: Uuid?,
  val greeting: Greeting = Greeting.First,
)

enum class ChatDeleteResult { Deleted, Gone, Failed }

sealed interface StartChatResult {
  data class Started(val chatId: Uuid) : StartChatResult

  data object CharacterGone : StartChatResult

  data object GreetingGone : StartChatResult

  data object PersonaGone : StartChatResult

  data object PresetGone : StartChatResult

  data object ConnectionGone : StartChatResult

  data object StorageFull : StartChatResult

  data object Failed : StartChatResult
}
