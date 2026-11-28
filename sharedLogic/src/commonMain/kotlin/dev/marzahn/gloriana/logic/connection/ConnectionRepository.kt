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
import dev.marzahn.gloriana.logic.storage.ChatSetting
import dev.marzahn.gloriana.logic.storage.ChatsUsing
import dev.marzahn.gloriana.logic.storage.IdFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

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
  vault: KeyVault,
  private val ids: IdFactory,
  private val chats: ChatsUsing,
) {
  private val secrets = ConnectionSecrets(vault)
  private val erasure = DataKeyErasure(vault, dao)
  private val rowAndDataKeyWrites = Mutex()

  fun connections(): Flow<List<Connection>> = dao.observeAll().map { rows -> rows.mapNotNull { it.readableOrNull() } }

  fun connection(id: Uuid): Flow<Connection?> = dao.observe(id).map { it?.readableOrNull() }

  fun defaultConnection(): Flow<Uuid?> = default.observe()

  suspend fun chatsUsing(id: Uuid): Int = chats.count(ChatSetting.Connection, id)

  suspend fun create(draft: ConnectionDraft): ConnectionSaveResult =
    save { writes.ready(SaveTarget.New(ids.newId()), draft) }

  suspend fun update(id: Uuid, draft: ConnectionDraft): ConnectionSaveResult = save {
    when (val read = read(id)) {
      ConnectionRead.Gone -> Prepared.Stop(ConnectionSaveResult.Gone)
      ConnectionRead.Unreadable -> Prepared.Stop(ConnectionSaveResult.Unreadable)
      is ConnectionRead.Readable -> writes.ready(SaveTarget.Existing(read.connection), draft)
    }
  }

  suspend fun read(id: Uuid): ConnectionRead = when (val stored = dao.find(id)?.decoded()) {
    null -> ConnectionRead.Gone
    StoredConnection.Unreadable -> ConnectionRead.Unreadable
    is StoredConnection.Readable -> ConnectionRead.Readable(stored.connection)
  }

  suspend fun updateSamplers(id: Uuid, change: (ConnectionFields) -> Samplers): SamplersUpdateResult =
    rowAndDataKeyWrites.withLock {
      failingAs({ if (it.isStorageFull()) SamplersUpdateResult.StorageFull else SamplersUpdateResult.Failed }) {
        writes.updateSamplers(id, change)
      }
    }

  suspend fun makeDefault(id: Uuid): MakeDefaultResult = failingAs(MakeDefaultResult.Failed) { writes.makeDefault(id) }

  suspend fun delete(id: Uuid): ConnectionDeleteResult = rowAndDataKeyWrites.withLock {
    failingAs(ConnectionDeleteResult.Failed) {
      when (writes.delete(id)) {
        RowDelete.Gone -> ConnectionDeleteResult.Gone
        RowDelete.Deleted ->
          if (erasure.erase(id)) ConnectionDeleteResult.Deleted else ConnectionDeleteResult.DeletedKeyLeftForNextStart
      }
    }
  }

  private suspend fun save(prepare: suspend () -> Prepared): ConnectionSaveResult = rowAndDataKeyWrites.withLock {
    when (val prepared = failingAs({ Prepared.Stop(saveFailure(it)) }) { prepare() }) {
      is Prepared.Stop -> prepared.result
      is Prepared.Ready -> failingAs(::saveFailure) { writes.sealAndWrite(secrets, prepared) }.also {
        if (prepared.mayHaveMadeOrLeftADataKey) erasure.eraseUnlessStoredValuesNeedIt(prepared.target.id)
      }
    }
  }
}

private suspend fun ConnectionWrites.sealAndWrite(
  secrets: ConnectionSecrets,
  ready: Prepared.Ready,
): ConnectionSaveResult =
  when (val sealing = secrets.seal(ready.plan)) {
    Sealing.NeedsPassphrase -> ConnectionSaveResult.NeedsPassphrase
    is Sealing.Blocked -> ConnectionSaveResult.Blocked(sealing.why)
    is Sealing.Refused -> ConnectionSaveResult.Refused(sealing.problems)
    is Sealing.Sealed -> Connection(ready.target.id, ready.fields, sealing.key, sealing.headers).let { connection ->
      when (ready.target) {
        is SaveTarget.New -> insert(connection)
        is SaveTarget.Existing -> replace(connection)
      }
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
