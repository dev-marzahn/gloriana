package dev.marzahn.gloriana.logic.generation

import dev.marzahn.gloriana.logic.characters.CharacterLookup
import dev.marzahn.gloriana.logic.chat.Chat
import dev.marzahn.gloriana.logic.chat.ChatRepository
import dev.marzahn.gloriana.logic.connection.Connection
import dev.marzahn.gloriana.logic.connection.ConnectionRead
import dev.marzahn.gloriana.logic.connection.ConnectionRepository
import dev.marzahn.gloriana.logic.connection.api.SecretsCheck
import dev.marzahn.gloriana.logic.personas.PersonaPrompts
import dev.marzahn.gloriana.logic.presets.PresetRepository
import dev.marzahn.gloriana.logic.presets.StoredPreset
import dev.marzahn.gloriana.logic.prompt.PromptSource
import kotlinx.coroutines.flow.first
import kotlin.uuid.Uuid

internal class ReplyMaterial(val characterId: Uuid, val prompt: PromptSource, val connection: Connection)

internal sealed interface Preparation {
  class Ready(val material: ReplyMaterial) : Preparation

  class Refused(val result: ReplyStartResult) : Preparation
}

internal class ReplyPreparation(
  private val chats: ChatRepository,
  private val characters: CharacterLookup,
  private val personas: PersonaPrompts,
  private val presets: PresetRepository,
  private val connections: ConnectionRepository,
  private val secrets: SecretsCheck,
) {
  suspend fun prepare(chatId: Uuid): Preparation =
    chats.chat(chatId).first()?.let { prepared(it) } ?: Preparation.Refused(ReplyStartResult.ChatGone)

  private suspend fun prepared(chat: Chat): Preparation {
    val character = chat.characterIds.firstOrNull()?.let { characters.character(it) }
    val persona = personas.persona(chat.personaId)
    val preset = presets.preset(chat.presetId).first()
    val connection = chat.connectionId?.let { connections.read(it) }
    return when {
      character == null -> Preparation.Refused(ReplyStartResult.CharacterGone)
      persona == null -> Preparation.Refused(ReplyStartResult.PersonaGone)
      preset == null -> Preparation.Refused(ReplyStartResult.PresetGone)
      preset !is StoredPreset.Readable -> Preparation.Refused(ReplyStartResult.PresetUnreadable)
      connection == null -> Preparation.Refused(ReplyStartResult.NoConnection)
      connection == ConnectionRead.Gone -> Preparation.Refused(ReplyStartResult.ConnectionGone)
      connection !is ConnectionRead.Readable -> Preparation.Refused(ReplyStartResult.ConnectionUnreadable)
      else -> withKeysOpen(
        ReplyMaterial(
          character.id,
          PromptSource(character.fields, persona, preset.preset.content),
          connection.connection,
        ),
      )
    }
  }

  private suspend fun withKeysOpen(material: ReplyMaterial): Preparation =
    secrets.refusal(material.connection)
      ?.let { Preparation.Refused(ReplyStartResult.KeysNotReady(material.connection, it)) }
      ?: Preparation.Ready(material)
}
