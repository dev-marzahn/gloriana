package dev.marzahn.gloriana.logic.keys

import dev.marzahn.gloriana.logic.assets.LibraryDirectory
import dev.marzahn.gloriana.logic.debug.Faults
import java.nio.file.Path
import kotlin.test.assertIs
import kotlin.uuid.Uuid

val CheapPassphrase = Argon2idCost(memoryKib = 64, iterations = 1, lanes = 1)

class FakeKeychain : OsKeychain {
  val secrets = mutableMapOf<String, ByteArray>()
  val malformed = mutableSetOf<String>()
  var reads = 0
  var unreachable = false
  var noKeychain = false
  var notFoundConfirmed = true
  var writesAreLost = false

  override fun read(account: String): KeychainSecret {
    reads++
    return when {
      noKeychain -> KeychainSecret.NoKeychain
      unreachable -> KeychainSecret.Unreachable
      account in malformed -> KeychainSecret.Malformed
      else -> secrets[account]?.let { KeychainSecret.Found(it.copyOf()) } ?: KeychainSecret.NotFound(notFoundConfirmed)
    }
  }

  override fun write(account: String, secret: ByteArray): KeychainWrite = when {
    noKeychain -> KeychainWrite.NoKeychain
    unreachable -> KeychainWrite.Unreachable
    else -> KeychainWrite.Written.also { if (!writesAreLost) secrets[account] = secret.copyOf() }
  }

  override fun delete(account: String) {
    secrets -= account
  }

  fun replaceEverySecret(with: ByteArray) {
    secrets.keys.forEach { secrets[it] = with.copyOf() }
  }
}

class Vaults(private val library: Path) {
  val keychain = FakeKeychain()

  val everyFiledConnection = StoredConnections { DataKeyFile(library.resolve("keys/connections.json")).read().keys }

  internal fun start(
    faults: Faults = Faults.None,
    cost: Argon2idCost = CheapPassphrase,
    stored: StoredConnections = everyFiledConnection,
  ) = TinkKeyVault(
    library = LibraryDirectory { library },
    device = KeychainKeyStore(keychain),
    faults = faults,
    stored = stored,
    passphraseCost = cost,
  )
}

suspend fun KeyVault.sealed(slot: SecretSlot, value: String): SealedSecret =
  assertIs<SealResult.Sealed>(seal(slot, value)).secret

suspend fun KeyVault.opened(slot: SecretSlot, secret: SealedSecret): String =
  assertIs<OpenResult.Opened>(open(slot, secret)).value

fun apiKey(connectionId: Uuid) = SecretSlot.ApiKey(connectionId)

fun header(connectionId: Uuid, name: String) = SecretSlot.HeaderValue(connectionId, HeaderName.of(name))

fun String.passphrase(): CharArray = toCharArray()
