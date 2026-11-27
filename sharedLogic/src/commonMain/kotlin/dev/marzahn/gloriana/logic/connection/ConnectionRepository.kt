package dev.marzahn.gloriana.logic.connection

import dev.marzahn.gloriana.logic.app.failingAs
import dev.marzahn.gloriana.logic.assets.isStorageFull
import dev.marzahn.gloriana.logic.connection.storage.ConnectionDao
import dev.marzahn.gloriana.logic.connection.storage.ConnectionWrites
import dev.marzahn.gloriana.logic.connection.storage.DefaultConnection
import dev.marzahn.gloriana.logic.connection.storage.RowDelete
import dev.marzahn.gloriana.logic.connection.storage.StoredConnection
import dev.marzahn.gloriana.logic.connection.storage.decoded
import dev.marzahn.gloriana.logic.connection.storage.readableOrNull
import dev.marzahn.gloriana.logic.keys.KeyVault
import dev.marzahn.gloriana.logic.storage.IdFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

private sealed interface SaveRequest {
  class Create(val id: Uuid) : SaveRequest

  class Update(val id: Uuid) : SaveRequest
}

private sealed interface SaveTarget {
  val id: Uuid
  val stored: Connection?

  class New(override val id: Uuid) : SaveTarget {
    override val stored: Connection? = null
  }

  class Existing(override val stored: Connection) : SaveTarget {
    override val id: Uuid = stored.id
  }
}

private sealed interface Prepared {
  class Stop(val result: ConnectionSaveResult) : Prepared

  class Ready(val target: SaveTarget, val fields: ConnectionFields, val plan: SecretsPlan) : Prepared {
    val mayHaveMadeOrLeftADataKey: Boolean get() = plan.holdsAny || target.stored?.holdsSecrets == true
  }
}

class ConnectionRepository internal constructor(
  private val dao: ConnectionDao,
  private val writes: ConnectionWrites,
  private val default: DefaultConnection,
  private val vault: KeyVault,
  private val ids: IdFactory,
  private val chats: ConnectionChats,
) {
  private val secrets = ConnectionSecrets(vault)
  private val erasure = DataKeyErasure(vault, dao)

  fun connections(): Flow<List<Connection>> = dao.observeAll().map { rows -> rows.mapNotNull { it.readableOrNull() } }

  fun connection(id: Uuid): Flow<Connection?> = dao.observe(id).map { it?.readableOrNull() }

  fun defaultConnection(): Flow<Uuid?> = default.observe()

  suspend fun chatsUsing(id: Uuid): Int = chats.count(id)

  suspend fun create(draft: ConnectionDraft): ConnectionSaveResult = save(SaveRequest.Create(ids.newId()), draft)

  suspend fun update(id: Uuid, draft: ConnectionDraft): ConnectionSaveResult = save(SaveRequest.Update(id), draft)

  suspend fun read(id: Uuid): ConnectionRead = when (val stored = dao.find(id)?.decoded()) {
    null -> ConnectionRead.Gone
    StoredConnection.Unreadable -> ConnectionRead.Unreadable
    is StoredConnection.Readable -> ConnectionRead.Readable(stored.connection)
  }

  suspend fun updateSamplers(id: Uuid, change: (ConnectionFields) -> Samplers): SamplersUpdateResult =
    erasure.oneAtATime {
    failingAs({ if (it.isStorageFull()) SamplersUpdateResult.StorageFull else SamplersUpdateResult.Failed }) {
      writes.updateSamplers(id, change)
    }
  }

  suspend fun makeDefault(id: Uuid): MakeDefaultResult = failingAs(MakeDefaultResult.Failed) { writes.makeDefault(id) }

  suspend fun delete(id: Uuid): ConnectionDeleteResult = erasure.oneAtATime {
    failingAs(ConnectionDeleteResult.Failed) {
      when (writes.delete(id)) {
        RowDelete.Gone -> ConnectionDeleteResult.Gone
        RowDelete.Deleted ->
          if (erase(id)) ConnectionDeleteResult.Deleted else ConnectionDeleteResult.DeletedKeyLeftForNextStart
      }
    }
  }

  private suspend fun save(request: SaveRequest, draft: ConnectionDraft): ConnectionSaveResult = erasure.oneAtATime {
    when (val prepared = failingAs({ Prepared.Stop(saveFailure(it)) }) { writes.prepared(dao, request, draft) }) {
      is Prepared.Stop -> prepared.result
      is Prepared.Ready -> failingAs(::saveFailure) { writes.sealAndWrite(secrets, prepared) }.also {
        if (prepared.mayHaveMadeOrLeftADataKey) eraseUnlessStoredValuesNeedIt(prepared.target.id)
      }
    }
  }
}

private suspend fun ConnectionWrites.sealAndWrite(secrets: ConnectionSecrets, ready: Prepared.Ready) =
  when (val sealing = secrets.seal(ready.plan)) {
    Sealing.NeedsPassphrase -> ConnectionSaveResult.NeedsPassphrase
    Sealing.Locked -> ConnectionSaveResult.Locked
    Sealing.Unavailable -> ConnectionSaveResult.KeysUnavailable
    Sealing.Unreadable -> ConnectionSaveResult.KeysUnreadable
    is Sealing.Refused -> ConnectionSaveResult.Refused(sealing.problems)
    is Sealing.Sealed -> Connection(ready.target.id, ready.fields, sealing.key, sealing.headers).let { connection ->
      when (ready.target) {
        is SaveTarget.New -> insert(connection)
        is SaveTarget.Existing -> replace(connection)
      }
    }
  }

private suspend fun ConnectionWrites.prepared(dao: ConnectionDao, request: SaveRequest, draft: ConnectionDraft) =
  when (request) {
    is SaveRequest.Create -> ready(SaveTarget.New(request.id), draft)
    is SaveRequest.Update -> when (val stored = dao.find(request.id)?.decoded()) {
      null -> Prepared.Stop(ConnectionSaveResult.Gone)
      StoredConnection.Unreadable -> Prepared.Stop(ConnectionSaveResult.Unreadable)
      is StoredConnection.Readable -> ready(SaveTarget.Existing(stored.connection), draft)
    }
  }

private suspend fun ConnectionWrites.ready(target: SaveTarget, draft: ConnectionDraft): Prepared {
  val nameTaken = nameTakenByAnother(target.id, draft.fields.normalised().name)
  return when (val check = draft.check(target.stored, setOfNotNull(ConnectionProblem.NameTaken.takeIf { nameTaken }))) {
    is DraftCheck.Ready -> Prepared.Ready(target, check.fields, secretsPlan(target.id, check.sources))
    is DraftCheck.Refused -> Prepared.Stop(ConnectionSaveResult.Refused(check.problems))
  }
}

private fun saveFailure(error: Exception): ConnectionSaveResult =
  if (error.isStorageFull()) ConnectionSaveResult.StorageFull else ConnectionSaveResult.Failed
