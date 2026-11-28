package dev.marzahn.gloriana.logic.personas.storage

import dev.marzahn.gloriana.logic.personas.PersonaFields
import dev.marzahn.gloriana.logic.settings.AlwaysSetDefault
import dev.marzahn.gloriana.logic.settings.DefaultRow
import dev.marzahn.gloriana.logic.settings.LibrarySetting
import dev.marzahn.gloriana.logic.settings.LibrarySettings
import dev.marzahn.gloriana.logic.settings.MakeDefaultResult
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.serializer
import kotlin.uuid.Uuid

internal val DefaultPersonaSetting = LibrarySetting("defaultPersona", Uuid.serializer())

internal val ShippedPersona = PersonaFields(name = "User")

internal class DefaultPersona(
  database: GlorianaDatabase,
  settings: LibrarySettings,
  dao: PersonaDao,
  private val writes: PersonaWrites,
) {
  private val default =
    AlwaysSetDefault(database, DefaultRow(database, settings, DefaultPersonaSetting, dao.observeIds(), dao::exists))

  fun observe(): Flow<Uuid?> = default.observe()

  suspend fun make(id: Uuid): MakeDefaultResult = default.make(id)

  suspend fun <T> unlessDefault(id: Uuid, refused: T, write: suspend () -> T): T =
    default.unlessDefault(id, refused, write)

  suspend fun ensureShipped() = default.ensure(writes::insertShipped)
}
