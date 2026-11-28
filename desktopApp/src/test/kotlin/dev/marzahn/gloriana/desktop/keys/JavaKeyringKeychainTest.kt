package dev.marzahn.gloriana.desktop.keys

import com.github.javakeyring.BackendNotSupportedException
import com.github.javakeyring.KeyringStorageType
import com.github.javakeyring.PasswordAccessException
import dev.marzahn.gloriana.logic.keys.KeychainSecret
import dev.marzahn.gloriana.logic.keys.KeychainWrite
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

private const val ACCOUNT = "keyset-0011223344556677"
private const val SERVICE = "Gloriana Test"

private object JavaKeyring104Messages {
  const val MAC_NOT_FOUND = "No stored credentials match $SERVICE account: $ACCOUNT"
  const val SECRET_SERVICE_NOT_FOUND = "No stored credentials match $SERVICE account: $ACCOUNT"
  const val WINDOWS_NOT_FOUND = "Error code 1168"
  const val WINDOWS_EMPTY = "Password not Found"
  const val KWALLET_NOT_FOUND = "Password is not in wallet"
  const val MAC_DENIED = "Failed to get credential. User interaction is not allowed."
  const val TOO_MANY = "Too many stored credentials match $SERVICE account: $ACCOUNT"
}

private class FakeSession(
  override val backend: KeyringStorageType,
  private val reading: () -> String,
) : KeyringSession {
  override fun read(service: String, account: String): String = reading()

  override fun write(service: String, account: String, value: String) = Unit

  override fun delete(service: String, account: String) = Unit

  override fun close() = Unit
}

class JavaKeyringKeychainTest {
  private fun failing(backend: KeyringStorageType, message: String) =
    JavaKeyringKeychain(SERVICE) { FakeSession(backend) { throw PasswordAccessException(message) } }.read(ACCOUNT)

  @Test
  fun eachBackendsOwnNotFoundIsConfirmed() {
    assertEquals(confirmed, failing(KeyringStorageType.OSX_KEYCHAIN, JavaKeyring104Messages.MAC_NOT_FOUND))
    assertEquals(
      confirmed,
      failing(KeyringStorageType.WINDOWS_CREDENTIAL_STORE, JavaKeyring104Messages.WINDOWS_NOT_FOUND),
    )
    assertEquals(confirmed, failing(KeyringStorageType.WINDOWS_CREDENTIAL_STORE, JavaKeyring104Messages.WINDOWS_EMPTY))
    assertEquals(confirmed, failing(KeyringStorageType.KWALLET, JavaKeyring104Messages.KWALLET_NOT_FOUND))
  }

  @Test
  fun theSecretServicesNotFoundIsUnconfirmedSinceItsCollectionReturnsNoItemsOnADbusError() {
    assertEquals(
      KeychainSecret.NotFound(confirmed = false),
      failing(KeyringStorageType.GNOME_KEYRING, JavaKeyring104Messages.SECRET_SERVICE_NOT_FOUND),
    )
  }

  @Test
  fun anotherBackendsMessageOrAnyOtherFailureIsUnreachable() {
    assertEquals(
      KeychainSecret.Unreachable,
      failing(KeyringStorageType.KWALLET, JavaKeyring104Messages.SECRET_SERVICE_NOT_FOUND),
    )
    assertEquals(
      KeychainSecret.Unreachable,
      failing(KeyringStorageType.OSX_KEYCHAIN, JavaKeyring104Messages.MAC_DENIED),
    )
    assertEquals(KeychainSecret.Unreachable, failing(KeyringStorageType.GNOME_KEYRING, JavaKeyring104Messages.TOO_MANY))
  }

  @Test
  fun aMalformedEntryIsMalformed() {
    val read = JavaKeyringKeychain(SERVICE) { FakeSession(KeyringStorageType.OSX_KEYCHAIN) { "not base64!" } }

    assertEquals(KeychainSecret.Malformed, read.read(ACCOUNT))
  }

  @Test
  fun aStoredSecretIsFound() {
    val secret = ByteArray(32) { it.toByte() }
    val keychain = JavaKeyringKeychain(SERVICE) {
      FakeSession(KeyringStorageType.OSX_KEYCHAIN) { Base64.getEncoder().encodeToString(secret) }
    }

    assertContentEquals(secret, assertIs<KeychainSecret.Found>(keychain.read(ACCOUNT)).bytes)
  }

  @Test
  fun noBackendOrAMissingNativeLibraryIsNoKeychain() {
    val none = JavaKeyringKeychain(SERVICE) { throw BackendNotSupportedException("none") }
    val unlinked = JavaKeyringKeychain(SERVICE) { throw UnsatisfiedLinkError("no jnidispatch") }

    assertEquals(KeychainSecret.NoKeychain, none.read(ACCOUNT))
    assertEquals(KeychainWrite.NoKeychain, none.write(ACCOUNT, ByteArray(32)))
    assertEquals(KeychainSecret.NoKeychain, unlinked.read(ACCOUNT))
  }

  @Test
  fun aFailingBackendIsUnreachable() {
    val failing = JavaKeyringKeychain(SERVICE) { throw IllegalStateException("D-Bus is not up") }

    assertEquals(KeychainSecret.Unreachable, failing.read(ACCOUNT))
    assertEquals(KeychainWrite.Unreachable, failing.write(ACCOUNT, ByteArray(32)))
  }

  private val confirmed = KeychainSecret.NotFound(confirmed = true)
}
