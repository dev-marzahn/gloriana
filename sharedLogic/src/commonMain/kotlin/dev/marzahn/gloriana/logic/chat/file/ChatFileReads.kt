package dev.marzahn.gloriana.logic.chat.file

import dev.marzahn.gloriana.logic.app.failingAs
import dev.marzahn.gloriana.logic.characters.CharacterLookup
import dev.marzahn.gloriana.logic.chat.ChatReadPoint
import dev.marzahn.gloriana.logic.chat.MessageRole
import dev.marzahn.gloriana.logic.chat.storage.ChatDao
import dev.marzahn.gloriana.logic.chat.storage.ChatRows
import dev.marzahn.gloriana.logic.chat.storage.MessageWithSwipes
import dev.marzahn.gloriana.logic.debug.Faults
import dev.marzahn.gloriana.logic.personas.PersonaPrompts
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import dev.marzahn.gloriana.logic.storage.readTransaction
import kotlin.uuid.Uuid

internal sealed interface ChatFileRead {
  data class Read(val file: ChatFile) : ChatFileRead

  data object Gone : ChatFileRead

  data object Unreadable : ChatFileRead
}

internal class ChatFileReads(
  private val database: GlorianaDatabase,
  private val dao: ChatDao,
  private val characters: CharacterLookup,
  private val personas: PersonaPrompts,
  private val faults: Faults,
) {
  suspend fun read(chatId: Uuid): ChatFileRead = failingAs(ChatFileRead.Unreadable) {
    database.readTransaction { dao.rows(chatId)?.let { ChatFileRead.Read(fileOf(it)) } ?: ChatFileRead.Gone }
  }

  private suspend fun fileOf(rows: ChatRows): ChatFile {
    val names = CharacterNames(rows.chat.title, characterNames(rows))
    val persona = checkNotNull(personas.persona(rows.chat.personaId)) { "A chat's Persona is always stored" }
    faults.reached(ChatReadPoint.ExportChatRowsRead)
    return ChatFile(
      userName = persona.name,
      characterName = names.of(rows.characters.minByOrNull { it.position }?.characterId),
      messages = dao.messagesWithSwipes(rows.chat.id).mapNotNull { it.inFile(persona.name, names) },
    )
  }

  private suspend fun characterNames(rows: ChatRows): Map<Uuid, String> =
    rows.characters
      .mapNotNull { place -> characters.character(place.characterId)?.let { place.characterId to it.fields.name } }
      .toMap()
}

private class CharacterNames(private val keptChatTitle: String, private val names: Map<Uuid, String>) {
  fun of(characterId: Uuid?): String = characterId?.let(names::get) ?: keptChatTitle
}

private fun MessageWithSwipes.inFile(userName: String, names: CharacterNames): ChatFileMessage? {
  val swipes = orderedSwipes
  val shownAt = swipes.indexOfFirst { it.index == message.shownSwipe }.takeIf { it >= 0 } ?: return null
  return ChatFileMessage(
    speaker = when (message.role) {
      MessageRole.User -> userName
      MessageRole.Character -> names.of(message.speakingCharacterId)
    },
    role = message.role,
    hidden = message.hidden,
    swipes = swipes,
    shownAt = shownAt,
  )
}
