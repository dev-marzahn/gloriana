package dev.marzahn.gloriana.logic.connection

import dev.marzahn.gloriana.logic.connection.storage.ConnectionWrites
import dev.marzahn.gloriana.logic.connection.storage.DefaultConnection
import dev.marzahn.gloriana.logic.debug.Faults
import dev.marzahn.gloriana.logic.keys.KeyVault
import dev.marzahn.gloriana.logic.keys.TinkKeyVault
import dev.marzahn.gloriana.logic.keys.Vaults
import dev.marzahn.gloriana.logic.settings.LibrarySettings
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import dev.marzahn.gloriana.logic.storage.IdFactory
import dev.marzahn.gloriana.logic.storage.TestClock
import dev.marzahn.gloriana.logic.storage.Tombstones
import dev.marzahn.gloriana.logic.storage.UpdatedAtClock
import dev.marzahn.gloriana.logic.storage.UuidV7IdFactory
import dev.marzahn.gloriana.logic.storage.desktopGlorianaDatabase
import kotlinx.coroutines.flow.first
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertIs
import kotlin.uuid.Uuid

class ConnectionLibrary(
  val directory: Path,
  val database: GlorianaDatabase = desktopGlorianaDatabase(directory),
  vaults: Vaults = Vaults(directory),
  faults: Faults = Faults.None,
  keyFaults: Faults = Faults.None,
  ids: IdFactory = UuidV7IdFactory,
  vaultAround: ConnectionLibrary.(KeyVault) -> KeyVault = { it },
) : AutoCloseable {
  val clock = TestClock(epochMillis = 1_000)
  private val updatedAt = UpdatedAtClock(clock)
  val settings = LibrarySettings(database, updatedAt)
  internal val vault: TinkKeyVault =
    vaults.start(faults = keyFaults, stored = StoredConnectionIds(database.connectionDao()))
  private val default = DefaultConnection(settings, database.connectionDao())
  val repository = ConnectionRepository(
    dao = database.connectionDao(),
    writes = ConnectionWrites(database, default, Tombstones(database, updatedAt), updatedAt, faults),
    default = default,
    vault = vaultAround(vault),
    ids = ids,
    chats = ConnectionChats.NoneYet,
  )

  suspend fun created(draft: ConnectionDraft): Connection =
    stored(assertIs<ConnectionSaveResult.Saved>(repository.create(draft)).id)

  suspend fun stored(id: Uuid): Connection = checkNotNull(repository.connection(id).first())

  suspend fun list(): List<Connection> = repository.connections().first()

  suspend fun default(): Uuid? = repository.defaultConnection().first()

  fun dataKeyFileHolds(id: Uuid): Boolean =
    dataKeyFile().let { Files.exists(it) && id.toString() in Files.readString(it) }

  fun dataKeyFile(): Path = directory.resolve("keys/connections.json")

  override fun close() = database.close()
}

fun draft(
  name: String = "Wren's host",
  baseUrl: String = "https://api.example.com/v1",
  model: String = "model-1",
  key: KeyInput = KeyInput.Kept,
  headers: List<HeaderDraft> = emptyList(),
  samplers: Samplers = Samplers(),
  format: ApiFormat = ApiFormat.OpenAiChatCompletions,
) = ConnectionDraft(
  fields = ConnectionFields(
    name = name,
    format = format,
    baseUrl = baseUrl,
    model = model,
    samplers = samplers,
  ),
  key = key,
  headers = headers,
)

fun entered(value: String) = HeaderValueInput.Entered(value)
