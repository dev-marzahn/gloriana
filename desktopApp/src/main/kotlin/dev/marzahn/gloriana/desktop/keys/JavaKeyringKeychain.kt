package dev.marzahn.gloriana.desktop.keys

import com.github.javakeyring.BackendNotSupportedException
import com.github.javakeyring.Keyring
import com.github.javakeyring.KeyringStorageType
import com.github.javakeyring.PasswordAccessException
import dev.marzahn.gloriana.logic.keys.KeychainSecret
import dev.marzahn.gloriana.logic.keys.KeychainWrite
import dev.marzahn.gloriana.logic.keys.OsKeychain
import java.util.Base64

private val notFoundMessages = mapOf(
  KeyringStorageType.OSX_KEYCHAIN to Regex("No stored credentials match .*"),
  KeyringStorageType.GNOME_KEYRING to Regex("No stored credentials match .*"),
  KeyringStorageType.WINDOWS_CREDENTIAL_STORE to Regex("Error code 1168|Password not Found"),
  KeyringStorageType.KWALLET to Regex("Password is not in wallet"),
)
private val backendsThatReportErrorsAsNotFound = setOf(KeyringStorageType.GNOME_KEYRING)

interface KeyringSession : AutoCloseable {
  val backend: KeyringStorageType

  fun read(service: String, account: String): String

  fun write(service: String, account: String, value: String)

  fun delete(service: String, account: String)
}

private class JavaKeyringSession(private val keyring: Keyring) : KeyringSession {
  override val backend: KeyringStorageType = keyring.keyringStorageType

  override fun read(service: String, account: String): String = keyring.getPassword(service, account)

  override fun write(service: String, account: String, value: String) = keyring.setPassword(service, account, value)

  override fun delete(service: String, account: String) = keyring.deletePassword(service, account)

  override fun close() = keyring.close()
}

class JavaKeyringKeychain(
  private val service: String,
  private val sessions: () -> KeyringSession = { JavaKeyringSession(Keyring.create()) },
) : OsKeychain {
  override fun read(account: String): KeychainSecret = withSession(failed = { it }) { session ->
    try {
      decoded(session.read(service, account))
    } catch (failure: PasswordAccessException) {
      keychainReadFailure(failure.message, session.backend)
    }
  }

  override fun write(account: String, secret: ByteArray): KeychainWrite =
    withSession(failed = ::writeFailure) { session ->
      session.write(service, account, Base64.getEncoder().encodeToString(secret))
      KeychainWrite.Written
    }

  override fun delete(account: String) = withSession(failed = { }) { session -> session.delete(service, account) }

  @Suppress("TooGenericExceptionCaught")
  private fun <T> withSession(failed: (KeychainSecret) -> T, action: (KeyringSession) -> T): T = try {
    sessions().use(action)
  } catch (_: BackendNotSupportedException) {
    failed(KeychainSecret.NoKeychain)
  } catch (_: LinkageError) {
    failed(KeychainSecret.NoKeychain)
  } catch (_: PasswordAccessException) {
    failed(KeychainSecret.Unreachable)
  } catch (_: RuntimeException) {
    failed(KeychainSecret.Unreachable)
  }
}

private fun decoded(stored: String): KeychainSecret = try {
  KeychainSecret.Found(Base64.getDecoder().decode(stored))
} catch (_: IllegalArgumentException) {
  KeychainSecret.Malformed
}

private fun writeFailure(secret: KeychainSecret): KeychainWrite =
  if (secret == KeychainSecret.NoKeychain) KeychainWrite.NoKeychain else KeychainWrite.Unreachable

internal fun keychainReadFailure(message: String?, backend: KeyringStorageType): KeychainSecret {
  val notFound = notFoundMessages[backend]
  return if (message != null && notFound != null && notFound.matches(message)) {
    KeychainSecret.NotFound(confirmed = backend !in backendsThatReportErrorsAsNotFound)
  } else {
    KeychainSecret.Unreachable
  }
}
