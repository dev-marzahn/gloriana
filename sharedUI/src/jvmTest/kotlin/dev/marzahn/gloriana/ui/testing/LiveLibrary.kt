package dev.marzahn.gloriana.ui.testing

import androidx.compose.runtime.Composable
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import dev.marzahn.gloriana.logic.app.ShippedDefaultsAtStart
import dev.marzahn.gloriana.logic.assets.LibraryDirectory
import dev.marzahn.gloriana.logic.characters.AvatarTranscoder
import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.Connection
import dev.marzahn.gloriana.logic.connection.ConnectionDraft
import dev.marzahn.gloriana.logic.connection.ConnectionFields
import dev.marzahn.gloriana.logic.connection.ConnectionRepository
import dev.marzahn.gloriana.logic.connection.ConnectionSaveResult
import dev.marzahn.gloriana.logic.connection.GlobalModelSettings
import dev.marzahn.gloriana.logic.connection.HeaderDraft
import dev.marzahn.gloriana.logic.connection.KeyInput
import dev.marzahn.gloriana.logic.connection.Samplers
import dev.marzahn.gloriana.logic.debug.DebuggableBuild
import dev.marzahn.gloriana.logic.debug.FaultPoint
import dev.marzahn.gloriana.logic.debug.Faults
import dev.marzahn.gloriana.logic.di.logicModule
import dev.marzahn.gloriana.logic.keys.DeviceKeyStore
import dev.marzahn.gloriana.logic.keys.KeyVault
import dev.marzahn.gloriana.logic.keys.KeyWritePoint
import dev.marzahn.gloriana.logic.keys.KeychainKeyStore
import dev.marzahn.gloriana.logic.keys.KeychainSecret
import dev.marzahn.gloriana.logic.keys.KeychainWrite
import dev.marzahn.gloriana.logic.keys.OsKeychain
import dev.marzahn.gloriana.logic.keys.PassphraseResult
import dev.marzahn.gloriana.logic.keys.deviceKeysetFileIn
import dev.marzahn.gloriana.logic.keys.passphraseKeysetFileIn
import dev.marzahn.gloriana.logic.presets.PresetRepository
import dev.marzahn.gloriana.logic.presets.PresetSummary
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import dev.marzahn.gloriana.logic.storage.UpdatedAtClock
import dev.marzahn.gloriana.logic.storage.desktopGlorianaDatabase
import dev.marzahn.gloriana.logic.tasks.KeepAlive
import dev.marzahn.gloriana.logic.tasks.KeepAliveLosses
import dev.marzahn.gloriana.logic.tasks.TaskHold
import dev.marzahn.gloriana.logic.tasks.TaskNotice
import dev.marzahn.gloriana.ui.di.uiModule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.compose.KoinIsolatedContext
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

class MemoryKeychain : OsKeychain {
  private val secrets = mutableMapOf<String, ByteArray>()

  @Volatile
  var unreachable = false

  val accounts: Set<String> get() = secrets.keys.toSet()

  override fun read(account: String): KeychainSecret = when {
    unreachable -> KeychainSecret.Unreachable
    else -> secrets[account]?.let { KeychainSecret.Found(it.copyOf()) } ?: KeychainSecret.NotFound(confirmed = true)
  }

  override fun write(account: String, secret: ByteArray): KeychainWrite {
    if (unreachable) return KeychainWrite.Unreachable
    secrets[account] = secret.copyOf()
    return KeychainWrite.Written
  }

  override fun delete(account: String) {
    secrets -= account
  }
}

object NoKeychain : OsKeychain {
  override fun read(account: String): KeychainSecret = KeychainSecret.NoKeychain

  override fun write(account: String, secret: ByteArray): KeychainWrite = KeychainWrite.NoKeychain

  override fun delete(account: String) = Unit
}

private object HeldInProcess : KeepAlive {
  override fun start(opening: TaskNotice, shown: Flow<TaskNotice?>, losses: KeepAliveLosses) = Unit

  override fun stop() = Unit

  override fun holdWhileRunning(): TaskHold = TaskHold { }
}

private class QueryThreads {
  private val threads = Executors.newFixedThreadPool(QUERY_THREADS)

  val dispatcher: CoroutineDispatcher = threads.asCoroutineDispatcher()

  fun finishRunningQueries() {
    threads.shutdown()
    check(threads.awaitTermination(QUERY_FINISH_SECONDS, TimeUnit.SECONDS)) { "A query is still running" }
  }

  private companion object {
    const val QUERY_THREADS = 4
    const val QUERY_FINISH_SECONDS = 10L
  }
}

class LiveLibrary private constructor(private val directory: Path, private val device: DeviceKeyStore) {
  var keysetWritesFail = false

  private var whenTheNextKeysetWriteIsRefused: (() -> Unit)? = null

  private val deviceKeysetFile: Path get() = deviceKeysetFileIn(directory)

  val writes = WriteGate()

  private lateinit var queries: QueryThreads

  private var application = startApplication()

  val koin: Koin get() = application.koin

  private val repository: ConnectionRepository get() = application.koin.get()

  private val vault: KeyVault get() = application.koin.get()

  private val presetRepository: PresetRepository get() = application.koin.get()

  @Composable
  fun Show(content: @Composable () -> Unit) {
    KoinIsolatedContext(application, content)
  }

  fun created(
    name: String,
    key: KeyInput = KeyInput.Kept,
    headers: List<HeaderDraft> = emptyList(),
    baseUrl: String = "https://api.example.com/v1",
  ): Connection = runBlocking {
    val draft = ConnectionDraft(
      fields = ConnectionFields(
        name = name,
        format = ApiFormat.OpenAiChatCompletions,
        baseUrl = baseUrl,
        model = "model-1",
      ),
      key = key,
      headers = headers,
    )
    val id = assertIs<ConnectionSaveResult.Saved>(repository.create(draft)).connection.id
    checkNotNull(repository.connection(id).first())
  }

  fun connections(): List<Connection> = runBlocking { repository.connections().first() }

  fun default(): Uuid? = runBlocking { repository.defaultConnection().first() }

  fun shippedPreset(): PresetSummary = runBlocking {
    val shipped = checkNotNull(presetRepository.defaultPreset().first())
    presetRepository.presets().first().single { it.id == shipped }
  }

  fun addedPresetsChanges(): Flow<List<PresetSummary>> =
    combine(presetRepository.presets(), presetRepository.defaultPreset()) { presets, shipped ->
      presets.filter { it.id != shipped }
    }

  fun addedPresets(): List<PresetSummary> = runBlocking { addedPresetsChanges().first() }

  fun globalSamplers(): Samplers = runBlocking { application.koin.get<GlobalModelSettings>().currentSamplers() }

  fun protectWithPassphrase(passphrase: String) = runBlocking {
    vault.prepare()
    assertEquals(PassphraseResult.Accepted, vault.setPassphrase(passphrase.toCharArray()))
  }

  fun unlocks(passphrase: String): PassphraseResult = runBlocking {
    vault.prepare()
    vault.unlock(passphrase.toCharArray())
  }

  fun passphraseKeysetReadable(readable: Boolean) {
    check(passphraseKeysetFileIn(directory).toFile().setReadable(readable))
  }

  fun damagePassphraseKeyset() {
    passphraseKeysetFileIn(directory).writeText("damaged")
  }

  fun damageDeviceKeyset() {
    deviceKeysetFile.parent.createDirectories()
    deviceKeysetFile.writeText("damaged")
  }

  fun loseTheDeviceKeysetFile() {
    deviceKeysetFile.deleteIfExists()
  }

  fun keysetFiles(): List<String> =
    deviceKeysetFile.parent.takeIf { it.exists() }?.listDirectoryEntries("keyset-*")?.map { it.name }.orEmpty()

  fun needKeys() = runBlocking { vault.prepare() }

  fun restart() {
    stopApplication()
    application = startApplication()
  }

  fun restartWithConnectionsUnreadable() {
    stopApplication()
    BundledSQLiteDriver().open(directory.resolve("library.db").toString()).use { it.execSQL("DROP TABLE connections") }
    application = startApplication()
  }

  @OptIn(ExperimentalPathApi::class)
  fun close() {
    stopApplication()
    directory.deleteRecursively()
  }

  private fun stopApplication() {
    queries.finishRunningQueries()
    application.koin.get<GlorianaDatabase>().close()
    application.close()
  }

  private fun startApplication(): KoinApplication {
    queries = QueryThreads()
    val platform = module {
      single<LibraryDirectory> { LibraryDirectory { directory } }
      single { desktopGlorianaDatabase(directory, queries.dispatcher) }
      single<DebuggableBuild> { DebuggableBuild { false } }
      single<AvatarTranscoder> { AvatarTranscoder { _, _ -> null } }
      single<DeviceKeyStore> { device }
      single<Faults> { Faults(::failKeysetWrite) }
      single { UpdatedAtClock(writes) }
      single<KeepAlive> { HeldInProcess }
    }
    return koinApplication { modules(logicModule, uiModule, platform) }
      .also { started -> runBlocking { started.koin.get<ShippedDefaultsAtStart>().missing.await() } }
  }

  fun refuseTheNextKeysetWrite(alongside: () -> Unit) {
    whenTheNextKeysetWriteIsRefused = alongside
  }

  private fun failKeysetWrite(point: FaultPoint) {
    if (point != KeyWritePoint.NewKeysetStaged) return
    whenTheNextKeysetWriteIsRefused?.let { alongside ->
      whenTheNextKeysetWriteIsRefused = null
      alongside()
      throw IOException("keyset write refused")
    }
    if (keysetWritesFail) throw IOException("keyset write refused")
  }

  companion object {
    fun start(keychain: OsKeychain) = LiveLibrary(Files.createTempDirectory("gloriana-ui"), KeychainKeyStore(keychain))

    inline fun <T> use(keychain: OsKeychain = MemoryKeychain(), block: (LiveLibrary) -> T): T {
      val library = start(keychain)
      return try {
        block(library)
      } finally {
        library.close()
      }
    }
  }
}
