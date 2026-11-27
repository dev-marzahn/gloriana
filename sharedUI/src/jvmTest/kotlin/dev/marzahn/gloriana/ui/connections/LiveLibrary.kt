package dev.marzahn.gloriana.ui.connections

import androidx.compose.runtime.Composable
import dev.marzahn.gloriana.logic.assets.LibraryDirectory
import dev.marzahn.gloriana.logic.characters.AvatarTranscoder
import dev.marzahn.gloriana.logic.connection.ApiFormat
import dev.marzahn.gloriana.logic.connection.Connection
import dev.marzahn.gloriana.logic.connection.ConnectionDraft
import dev.marzahn.gloriana.logic.connection.ConnectionFields
import dev.marzahn.gloriana.logic.connection.ConnectionRepository
import dev.marzahn.gloriana.logic.connection.ConnectionSaveResult
import dev.marzahn.gloriana.logic.connection.KeyInput
import dev.marzahn.gloriana.logic.debug.DebuggableBuild
import dev.marzahn.gloriana.logic.di.logicModule
import dev.marzahn.gloriana.logic.keys.DeviceKeyStore
import dev.marzahn.gloriana.logic.keys.KeyVault
import dev.marzahn.gloriana.logic.keys.KeychainKeyStore
import dev.marzahn.gloriana.logic.keys.KeychainSecret
import dev.marzahn.gloriana.logic.keys.KeychainWrite
import dev.marzahn.gloriana.logic.keys.OsKeychain
import dev.marzahn.gloriana.logic.keys.PassphraseResult
import dev.marzahn.gloriana.logic.storage.GlorianaDatabase
import dev.marzahn.gloriana.logic.storage.desktopGlorianaDatabase
import dev.marzahn.gloriana.ui.di.uiModule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.compose.KoinIsolatedContext
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

class MemoryKeychain : OsKeychain {
  private val secrets = mutableMapOf<String, ByteArray>()

  override fun read(account: String): KeychainSecret =
    secrets[account]?.let { KeychainSecret.Found(it.copyOf()) } ?: KeychainSecret.NotFound(confirmed = true)

  override fun write(account: String, secret: ByteArray): KeychainWrite {
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

class LiveLibrary private constructor(private val directory: Path, private val device: DeviceKeyStore) {
  private var application = startApplication()

  val koin: Koin get() = application.koin

  private val repository: ConnectionRepository get() = application.koin.get()

  private val vault: KeyVault get() = application.koin.get()

  @Composable
  fun Show(content: @Composable () -> Unit) {
    KoinIsolatedContext(application, content)
  }

  fun created(name: String, key: KeyInput = KeyInput.Kept): Connection = runBlocking {
    val draft = ConnectionDraft(
      fields = ConnectionFields(
        name = name,
        format = ApiFormat.OpenAiChatCompletions,
        baseUrl = "https://api.example.com/v1",
        model = "model-1",
      ),
      key = key,
      headers = emptyList(),
    )
    val id = assertIs<ConnectionSaveResult.Saved>(repository.create(draft)).id
    checkNotNull(repository.connection(id).first())
  }

  fun connections(): List<Connection> = runBlocking { repository.connections().first() }

  fun default(): Uuid? = runBlocking { repository.defaultConnection().first() }

  fun protectWithPassphrase(passphrase: String) = runBlocking {
    vault.prepare()
    assertEquals(PassphraseResult.Accepted, vault.setPassphrase(passphrase.toCharArray()))
  }

  fun damagePassphraseKeyset() {
    directory.resolve("keys/keyset-passphrase.json").writeText("damaged")
  }

  fun damageDeviceKeyset() {
    directory.resolve("keys/keyset-device.json").writeText("damaged")
  }

  fun restart() {
    stopApplication()
    application = startApplication()
  }

  @OptIn(ExperimentalPathApi::class)
  fun close() {
    stopApplication()
    directory.deleteRecursively()
  }

  private fun stopApplication() {
    application.koin.get<GlorianaDatabase>().close()
    application.close()
  }

  private fun startApplication(): KoinApplication {
    val platform = module {
      single<LibraryDirectory> { LibraryDirectory { directory } }
      single { desktopGlorianaDatabase(directory) }
      single<DebuggableBuild> { DebuggableBuild { false } }
      single<AvatarTranscoder> { AvatarTranscoder { _, _ -> null } }
      single<DeviceKeyStore> { device }
    }
    return koinApplication { modules(logicModule, uiModule, platform) }
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
