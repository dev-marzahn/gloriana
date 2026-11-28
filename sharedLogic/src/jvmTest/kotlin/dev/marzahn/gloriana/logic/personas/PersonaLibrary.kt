package dev.marzahn.gloriana.logic.personas

import dev.marzahn.gloriana.logic.debug.Faults
import dev.marzahn.gloriana.logic.personas.storage.DefaultPersona
import dev.marzahn.gloriana.logic.personas.storage.PersonaWrites
import dev.marzahn.gloriana.logic.settings.LibrarySettings
import dev.marzahn.gloriana.logic.storage.ChatsUsing
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import dev.marzahn.gloriana.logic.storage.TestClock
import dev.marzahn.gloriana.logic.storage.Tombstones
import dev.marzahn.gloriana.logic.storage.UpdatedAtClock
import dev.marzahn.gloriana.logic.storage.UuidV7IdFactory
import dev.marzahn.gloriana.logic.storage.desktopGlorianaDatabase
import kotlinx.coroutines.flow.first
import java.nio.file.Path
import kotlin.test.assertIs
import kotlin.uuid.Uuid

class PersonaLibrary(
  directory: Path,
  val database: GlorianaDatabase = desktopGlorianaDatabase(directory),
  faults: Faults = Faults.None,
  chats: ChatsUsing = ChatsUsing.NoneYet,
) : AutoCloseable {
  private val updatedAt = UpdatedAtClock(TestClock(epochMillis = 1_000))
  val settings = LibrarySettings(database, updatedAt)
  private val writes = PersonaWrites(
    database,
    database.personaDao(),
    UuidV7IdFactory,
    updatedAt,
    Tombstones(database, updatedAt),
    faults,
  )
  internal val default = DefaultPersona(database, settings, database.personaDao(), writes)
  val repository = PersonaRepository(database.personaDao(), writes, default, chats)
  val prompts = PersonaPrompts(database.personaDao())

  suspend fun created(fields: PersonaFields): Persona =
    stored(assertIs<PersonaSaveResult.Saved>(repository.create(fields)).id)

  suspend fun stored(id: Uuid): Persona = checkNotNull(repository.persona(id).first())

  suspend fun defaultId(): Uuid? = repository.defaultPersona().first()

  override fun close() = database.close()
}

fun wren() = PersonaFields(
  name = "Wren",
  description = "A night-shift archivist.",
  personaLabel = "Wren, noir story",
  personaNote = "The quiet one.",
)
