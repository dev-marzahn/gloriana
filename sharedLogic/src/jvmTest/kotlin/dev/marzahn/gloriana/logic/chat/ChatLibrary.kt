package dev.marzahn.gloriana.logic.chat

import dev.marzahn.gloriana.logic.app.ApplicationScope
import dev.marzahn.gloriana.logic.app.ProblemLog
import dev.marzahn.gloriana.logic.app.ShippedDefaults
import dev.marzahn.gloriana.logic.app.ShippedDefaultsAtStart
import dev.marzahn.gloriana.logic.characters.Character
import dev.marzahn.gloriana.logic.characters.CharacterLibrary
import dev.marzahn.gloriana.logic.characters.CharacterLookup
import dev.marzahn.gloriana.logic.chat.storage.ChatRemovals
import dev.marzahn.gloriana.logic.chat.storage.ChatWrites
import dev.marzahn.gloriana.logic.connection.ConnectionLookup
import dev.marzahn.gloriana.logic.chat.storage.LeftoverDrafts
import dev.marzahn.gloriana.logic.debug.FaultInjected
import dev.marzahn.gloriana.logic.debug.FaultPoint
import dev.marzahn.gloriana.logic.debug.Faults
import dev.marzahn.gloriana.logic.files.CorpusFile
import dev.marzahn.gloriana.logic.personas.PersonaLibrary
import dev.marzahn.gloriana.logic.presets.PresetLibrary
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import dev.marzahn.gloriana.logic.storage.TestClock
import dev.marzahn.gloriana.logic.storage.Tombstones
import dev.marzahn.gloriana.logic.storage.UpdatedAtClock
import dev.marzahn.gloriana.logic.storage.UuidV7IdFactory
import dev.marzahn.gloriana.logic.storage.desktopGlorianaDatabase
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Path
import kotlin.test.assertIs
import kotlin.uuid.Uuid

private val nothingLogged = ProblemLog { message, cause -> throw AssertionError("Logged: $message", cause) }

class FaultSwitch : Faults {
  var failing: FaultPoint? = null

  override fun reached(point: FaultPoint) {
    if (point == failing) throw FaultInjected(point)
  }
}

class ChatLibrary(
  directory: Path,
  val database: GlorianaDatabase = desktopGlorianaDatabase(directory),
) : AutoCloseable {
  val faults = FaultSwitch()
  val characters = CharacterLibrary(directory, faults = faults, database = database)
  val personas = PersonaLibrary(directory, database, faults)
  val presets = PresetLibrary(directory, database, faults)
  val connections = presets.connections
  private val updatedAt = UpdatedAtClock(TestClock(epochMillis = 1_000))
  private val scope = ApplicationScope { throw it }
  private val shippedDefaults =
    ShippedDefaultsAtStart(ShippedDefaults(personas.default, presets.default, nothingLogged), scope)
  private val tombstones = Tombstones(database, updatedAt)
  val problems = mutableListOf<String>()
  internal val leftoverDrafts = LeftoverDrafts(
    database,
    database.swipeDraftDao(),
    UuidV7IdFactory,
    updatedAt,
    tombstones,
    faults,
  ) { message, _ -> problems += message }
  val repository = ChatRepository(
    database.chatDao(),
    ChatWrites(database, database.chatDao(), UuidV7IdFactory, updatedAt, faults),
    ChatRemovals(database, database.chatRemovalDao(), updatedAt, tombstones, faults),
    ChatOpenings(
      CharacterLookup(database.characterDao()),
      personas.prompts,
      presets.repository,
      ConnectionLookup(database.connectionDao()),
    ),
    shippedDefaults,
    personas.prompts,
    presets.repository,
    connections.repository,
  )

  suspend fun shippedDefaultsWritten() = check(shippedDefaults.missing.await().isEmpty())

  suspend fun character(
    name: String = "Ada",
    firstMessage: String = "Hello, {{user}}. I am {{char}}.",
    alternateGreetings: List<String> = emptyList(),
  ): Character = characters.importedCharacter(
    CorpusFile(
      "$name.json",
      Json.encodeToString(
        buildJsonObject {
          put("spec", "chara_card_v2")
          put("spec_version", "2.0")
          put(
            "data",
            buildJsonObject {
              put("name", name)
              put("first_mes", firstMessage)
              put("alternate_greetings", JsonArray(alternateGreetings.map(::JsonPrimitive)))
            },
          )
        },
      ).toByteArray(),
    ),
  )

  suspend fun newChat(character: Character): NewChat {
    shippedDefaultsWritten()
    val defaults = repository.newChatDefaults()
    return NewChat(
      characterId = character.id,
      personaId = checkNotNull(defaults.personaId),
      presetId = checkNotNull(defaults.presetId),
      connectionId = defaults.connectionId,
    )
  }

  suspend fun started(chat: NewChat): Chat =
    stored(assertIs<StartChatResult.Started>(repository.start(chat)).chatId)

  suspend fun stored(id: Uuid): Chat = checkNotNull(repository.chat(id).first())

  suspend fun messages(chatId: Uuid): List<ChatMessage> = repository.messages(chatId).first()

  override fun close() {
    scope.cancel()
    database.close()
  }
}
