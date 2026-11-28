package dev.marzahn.gloriana.logic.chat

import dev.marzahn.gloriana.logic.characters.Character
import dev.marzahn.gloriana.logic.characters.CharacterLookup
import dev.marzahn.gloriana.logic.connection.ConnectionLookup
import dev.marzahn.gloriana.logic.personas.PersonaPrompts
import dev.marzahn.gloriana.logic.presets.PresetRepository
import dev.marzahn.gloriana.logic.prompt.MacroNames
import dev.marzahn.gloriana.logic.prompt.withNamesFilled

internal sealed interface ChatOpening {
  data class Refused(val result: StartChatResult) : ChatOpening

  data class Ready(val title: String, val greeting: String?) : ChatOpening
}

internal class ChatOpenings(
  private val characters: CharacterLookup,
  private val personas: PersonaPrompts,
  private val presets: PresetRepository,
  private val connections: ConnectionLookup,
) {
  suspend fun opening(chat: NewChat): ChatOpening {
    val character = characters.character(chat.characterId)
    val persona = personas.persona(chat.personaId)
    val greeting = character?.greeting(chat.greeting)
    return when {
      character == null -> ChatOpening.Refused(StartChatResult.CharacterGone)
      persona == null -> ChatOpening.Refused(StartChatResult.PersonaGone)
      greeting == null -> ChatOpening.Refused(StartChatResult.GreetingGone)
      !presets.exists(chat.presetId) -> ChatOpening.Refused(StartChatResult.PresetGone)
      chat.connectionId != null && !connections.exists(chat.connectionId) ->
        ChatOpening.Refused(StartChatResult.ConnectionGone)
      else -> ChatOpening.Ready(
        title = character.fields.name,
        greeting = greeting.withNamesFilled(MacroNames(character.fields.name, persona.name)).ifBlank { null },
      )
    }
  }
}

private fun Character.greeting(greeting: Greeting): String? = when (greeting) {
  Greeting.First -> fields.firstMessage
  is Greeting.Alternate -> fields.alternateGreetings.getOrNull(greeting.index)
}
