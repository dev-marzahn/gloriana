package dev.marzahn.gloriana.logic.personas.storage

import dev.marzahn.gloriana.logic.debug.Faults
import dev.marzahn.gloriana.logic.personas.PersonaDeleteResult
import dev.marzahn.gloriana.logic.personas.PersonaFields
import dev.marzahn.gloriana.logic.personas.PersonaSaveResult
import dev.marzahn.gloriana.logic.personas.PersonaWritePoint
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import dev.marzahn.gloriana.logic.storage.IdFactory
import dev.marzahn.gloriana.logic.storage.RowKey
import dev.marzahn.gloriana.logic.storage.Tombstones
import dev.marzahn.gloriana.logic.storage.UpdatedAtClock
import dev.marzahn.gloriana.logic.storage.writeTransaction
import kotlin.uuid.Uuid

internal class PersonaWrites(
  private val database: GlorianaDatabase,
  private val dao: PersonaDao,
  private val ids: IdFactory,
  private val clock: UpdatedAtClock,
  private val tombstones: Tombstones,
  private val faults: Faults,
) {
  suspend fun insert(fields: PersonaFields): PersonaSaveResult =
    refusedOr(fields) { PersonaSaveResult.Saved(inserted(it)) }

  suspend fun insertShipped(): Uuid = inserted(ShippedPersona)

  private suspend fun inserted(fields: PersonaFields): Uuid = database.writeTransaction {
    val id = ids.newId()
    dao.insert(fields.toEntity(id, clock.updatedAt(previous = null)))
    faults.reached(PersonaWritePoint.InsertRowWritten)
    id
  }

  suspend fun update(id: Uuid, fields: PersonaFields): PersonaSaveResult = refusedOr(fields) { named ->
    database.writeTransaction {
      val previous = dao.find(id) ?: return@writeTransaction PersonaSaveResult.Gone
      dao.update(named.toEntity(id, clock.updatedAt(previous.updatedAt)))
      faults.reached(PersonaWritePoint.UpdateRowWritten)
      PersonaSaveResult.Saved(id)
    }
  }

  suspend fun delete(id: Uuid): PersonaDeleteResult = database.writeTransaction {
    if (dao.find(id) == null) return@writeTransaction PersonaDeleteResult.Gone
    dao.delete(id)
    faults.reached(PersonaWritePoint.DeleteRowRemoved)
    tombstones.record(PersonasEntityType, RowKey.Row(id))
    PersonaDeleteResult.Deleted
  }

  private suspend fun refusedOr(
    fields: PersonaFields,
    write: suspend (PersonaFields) -> PersonaSaveResult,
  ): PersonaSaveResult {
    val normalised = fields.normalised()
    return if (normalised.name.isEmpty()) PersonaSaveResult.EmptyName else write(normalised)
  }
}
