package dev.marzahn.gloriana.logic.personas.storage

import dev.marzahn.gloriana.logic.debug.Faults
import dev.marzahn.gloriana.logic.personas.PersonaDeleteResult
import dev.marzahn.gloriana.logic.personas.PersonaFields
import dev.marzahn.gloriana.logic.personas.PersonaSaveResult
import dev.marzahn.gloriana.logic.personas.PersonaWritePoint
import dev.marzahn.gloriana.logic.storage.ChatSetting
import dev.marzahn.gloriana.logic.storage.ChatsMoved
import dev.marzahn.gloriana.logic.storage.ChatsUsing
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import dev.marzahn.gloriana.logic.storage.IdFactory
import dev.marzahn.gloriana.logic.storage.moveAll
import dev.marzahn.gloriana.logic.storage.OrderColumn
import dev.marzahn.gloriana.logic.storage.RowKey
import dev.marzahn.gloriana.logic.storage.Tombstones
import dev.marzahn.gloriana.logic.storage.UpdatedAtClock
import dev.marzahn.gloriana.logic.storage.UserOrder
import dev.marzahn.gloriana.logic.storage.writeTransaction
import kotlin.uuid.Uuid

internal class PersonaWrites(
  private val database: GlorianaDatabase,
  private val dao: PersonaDao,
  private val ids: IdFactory,
  private val clock: UpdatedAtClock,
  private val tombstones: Tombstones,
  private val chats: ChatsUsing,
  private val faults: Faults,
) {
  val order =
    UserOrder(database, clock, faults, OrderColumn(dao::placedRows, dao::place, PersonaWritePoint.MoveRowsWritten))

  suspend fun insert(id: Uuid, fields: PersonaFields): PersonaSaveResult =
    refusedOr(fields) { PersonaSaveResult.Saved(inserted(id, it)) }

  suspend fun insertShipped(): Uuid = inserted(ids.newId(), ShippedPersona)

  private suspend fun inserted(id: Uuid, fields: PersonaFields): Uuid = database.writeTransaction {
    dao.insert(fields.toEntity(id, dao.nextPosition(), clock.updatedAt(previous = null)))
    faults.reached(PersonaWritePoint.InsertRowWritten)
    id
  }

  suspend fun update(id: Uuid, fields: PersonaFields): PersonaSaveResult = refusedOr(fields) { named ->
    database.writeTransaction {
      val previous = dao.find(id) ?: return@writeTransaction PersonaSaveResult.Gone
      dao.update(named.toEntity(id, previous.position, clock.updatedAt(previous.updatedAt)))
      faults.reached(PersonaWritePoint.UpdateRowWritten)
      PersonaSaveResult.Saved(id)
    }
  }

  suspend fun delete(id: Uuid, chatsMoveTo: Uuid?): PersonaDeleteResult = database.writeTransaction {
    if (dao.find(id) == null) return@writeTransaction PersonaDeleteResult.Gone
    if (chats.moveAll(ChatSetting.Persona, id, toDefault = chatsMoveTo) == ChatsMoved.NoDefaultToMoveTo) {
      return@writeTransaction PersonaDeleteResult.ChatsHaveNoDefault
    }
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
