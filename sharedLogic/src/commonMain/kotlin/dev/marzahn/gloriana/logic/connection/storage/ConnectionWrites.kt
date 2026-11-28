package dev.marzahn.gloriana.logic.connection.storage

import dev.marzahn.gloriana.logic.connection.Connection
import dev.marzahn.gloriana.logic.connection.ConnectionFields
import dev.marzahn.gloriana.logic.connection.ConnectionProblem
import dev.marzahn.gloriana.logic.connection.ConnectionSaveResult
import dev.marzahn.gloriana.logic.connection.ConnectionWritePoint
import dev.marzahn.gloriana.logic.connection.MakeDefaultResult
import dev.marzahn.gloriana.logic.connection.Samplers
import dev.marzahn.gloriana.logic.connection.SamplersUpdateResult
import dev.marzahn.gloriana.logic.connection.samplerProblems
import dev.marzahn.gloriana.logic.debug.Faults
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import dev.marzahn.gloriana.logic.storage.RowKey
import dev.marzahn.gloriana.logic.storage.StoredJson
import dev.marzahn.gloriana.logic.storage.Tombstones
import dev.marzahn.gloriana.logic.storage.UpdatedAtClock
import dev.marzahn.gloriana.logic.storage.nameKey
import dev.marzahn.gloriana.logic.storage.writeTransaction
import kotlin.uuid.Uuid

internal enum class RowDelete { Deleted, Gone }

internal class ConnectionWrites(
  private val database: GlorianaDatabase,
  private val dao: ConnectionDao,
  private val default: DefaultConnection,
  private val tombstones: Tombstones,
  private val clock: UpdatedAtClock,
  private val faults: Faults,
) {
  suspend fun nameTakenByAnother(id: Uuid, name: String): Boolean =
    dao.idNamed(nameKey(name)).let { it != null && it != id }

  suspend fun insert(connection: Connection): ConnectionSaveResult = database.writeTransaction {
    if (nameTakenByAnother(connection.id, connection.fields.name)) {
      ConnectionSaveResult.Refused(setOf(ConnectionProblem.NameTaken))
    } else {
      dao.insert(connection.toEntity(clock.updatedAt(previous = null)))
      faults.reached(ConnectionWritePoint.InsertRowWritten)
      default.claimIfUnset(connection.id)
      ConnectionSaveResult.Saved(connection.id)
    }
  }

  suspend fun replace(connection: Connection): ConnectionSaveResult = database.writeTransaction {
    val previous = dao.find(connection.id)
    when {
      previous == null -> ConnectionSaveResult.Gone
      nameTakenByAnother(connection.id, connection.fields.name) ->
        ConnectionSaveResult.Refused(setOf(ConnectionProblem.NameTaken))
      else -> {
        dao.update(connection.toEntity(clock.updatedAt(previous.updatedAt)))
        faults.reached(ConnectionWritePoint.UpdateRowWritten)
        ConnectionSaveResult.Saved(connection.id)
      }
    }
  }

  suspend fun updateSamplers(id: Uuid, change: (ConnectionFields) -> Samplers): SamplersUpdateResult =
    database.writeTransaction {
      val previous = dao.find(id) ?: return@writeTransaction SamplersUpdateResult.Gone
      when (val stored = previous.decoded()) {
        StoredConnection.Unreadable -> SamplersUpdateResult.Unreadable
        is StoredConnection.Readable -> stored.connection.fields.let { fields ->
          writeSamplers(previous, fields, change(fields))
        }
      }
    }

  private suspend fun writeSamplers(
    previous: ConnectionEntity,
    before: ConnectionFields,
    samplers: Samplers,
  ): SamplersUpdateResult {
    val problems = samplerProblems(samplers, before.format.samplerRanges).toSet()
    return when {
      problems.isNotEmpty() -> SamplersUpdateResult.Refused(problems)
      samplers == before.samplers -> SamplersUpdateResult.Unchanged
      else -> {
        dao.update(
          previous.copy(
            samplers = StoredJson.encodeToString(samplers),
            updatedAt = clock.updatedAt(previous.updatedAt),
          ),
        )
        faults.reached(ConnectionWritePoint.UpdateRowWritten)
        SamplersUpdateResult.Updated
      }
    }
  }

  suspend fun delete(id: Uuid): RowDelete = database.writeTransaction {
    if (dao.find(id) == null) {
      RowDelete.Gone
    } else {
      dao.delete(id)
      faults.reached(ConnectionWritePoint.DeleteRowRemoved)
      tombstones.record(ConnectionsEntityType, RowKey.Row(id))
      default.clearIf(id)
      RowDelete.Deleted
    }
  }

  suspend fun makeDefault(id: Uuid): MakeDefaultResult = database.writeTransaction {
    if (dao.find(id) == null) {
      MakeDefaultResult.Gone
    } else {
      default.set(id)
      MakeDefaultResult.Made
    }
  }
}
